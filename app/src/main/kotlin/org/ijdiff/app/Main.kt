package org.ijdiff.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.nullableFlag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import org.ijdiff.model.config.IjDiffConfig
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.DiffViewMode
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.ui.compose.shell.launchComposeMainWindow
import org.ijdiff.ui.compose.theme.ComposeAppTheme
import org.ijdiff.ui.swing.shell.MainWindow
import org.ijdiff.ui.swing.theme.AppTheme
import org.ijdiff.ui.swing.theme.DiffThemeManager
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import javax.swing.SwingUtilities
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class IjDiffCli : CliktCommand(name = "ijdiff") {

    private val configFile by option(
        "-c", "--config",
        help = "Path to TOML config file (defaults to ./ijdiff.toml or ~/.config/ijdiff/config.toml)"
    )

    private val fontFamily by option(
        "--font-family",
        help = "Editor font family matching [font] family in ijdiff.toml (e.g., 'JetBrains Mono', 'Ioskeley Mono')"
    )

    private val fontSize by option(
        "--font-size",
        help = "Editor font size in pt matching [font] size in ijdiff.toml (${IjDiffConfig.MIN_FONT_SIZE}..${IjDiffConfig.MAX_FONT_SIZE})"
    ).int()

    private val uiFontFamily by option(
        "--ui-font-family",
        help = "UI chrome font family matching [ui.font] family in ijdiff.toml (e.g., 'Inter', 'Segoe UI')"
    )

    private val uiFontSize by option(
        "--ui-font-size",
        help = "UI chrome font size in pt matching [ui.font] size in ijdiff.toml (${IjDiffConfig.MIN_FONT_SIZE}..${IjDiffConfig.MAX_FONT_SIZE})"
    ).int()

    private val unified by option(
        "-u", "--unified",
        help = "Open in Unified (single-pane) diff mode instead of Side-by-Side (matches unified in ijdiff.toml)"
    ).nullableFlag("--no-unified")

    private val whitespace by option(
        "-w", "--whitespace",
        help = "Whitespace comparison policy: default, trim, or ignore (matches whitespace in ijdiff.toml)"
    ).choice(
        "default" to "default",
        "trim" to "trim",
        "ignore" to "ignore"
    )

    private val uiToolkit by option(
        "--ui",
        help = "Desktop UI toolkit: swing (Phase 1 FlatLaf/RSyntaxTextArea) or compose (Phase 2 Compose Multiplatform) (matches ui in ijdiff.toml)"
    ).choice(
        "swing" to "swing",
        "compose" to "compose"
    )

    private val theme by option(
        "--theme",
        help = "UI color theme: dark (Darcula) or light (IntelliJ Light) (matches theme in ijdiff.toml)"
    ).choice(
        "dark" to AppTheme.DARK,
        "light" to AppTheme.LIGHT
    )

    private val wait by option(
        "--wait",
        help = "Wait until the window is closed before returning (enabled automatically for path pairs / git difftool)"
    ).flag(default = false)

    private val printGitConfig by option(
        "--git-config",
        help = "Print git difftool configuration snippet and exit"
    ).flag(default = false)

    private val loadSamples by option(
        "--load-samples",
        help = "Load the built-in automated testing sample corpus (all 12 file pairs + directory tree) into tabs"
    ).flag(default = false)

    private val paths by argument(
        name = "paths",
        help = "Optional <left> and <right> file or directory paths to compare"
    ).multiple()

    override fun run() {
        if (printGitConfig) {
            println(
                """
                # Add the following to your ~/.gitconfig:
                [diff]
                    tool = ijdiff
                [difftool "ijdiff"]
                    cmd = ijdiff "${'$'}LOCAL" "${'$'}REMOTE"
                [difftool]
                    prompt = false
                """.trimIndent()
            )
            return
        }

        val resolvedConfigPath = IjDiffConfig.resolveConfigPath(
            explicitPath = configFile?.let { Path.of(it) }
        )
        val fileConfig = IjDiffConfig.load(resolvedConfigPath)
        val effectiveConfig = fileConfig.copy(
            fontFamily = fontFamily ?: fileConfig.fontFamily,
            fontSize = fontSize ?: fileConfig.fontSize,
            uiFontFamily = uiFontFamily ?: fileConfig.uiFontFamily,
            uiFontSize = uiFontSize ?: fileConfig.uiFontSize,
            theme = theme?.let { if (it.isDark) "dark" else "light" } ?: fileConfig.theme,
            ui = uiToolkit ?: fileConfig.ui,
            unified = unified ?: fileConfig.unified,
            whitespace = whitespace ?: fileConfig.whitespace
        )
        val initialTheme = when (effectiveConfig.theme?.lowercase()) {
            "light" -> AppTheme.LIGHT
            else -> theme ?: AppTheme.DARK
        }

        val viewMode = if (effectiveConfig.unified) DiffViewMode.UNIFIED else DiffViewMode.SIDE_BY_SIDE
        val policy = effectiveConfig.resolveComparisonPolicy()

        // Validate paths early and preload/compute diff on a background thread while the UI toolkit initializes
        var preloadedPaths: Pair<Path, Path>? = null
        if (!loadSamples && paths.size >= 2) {
            val left = Path.of(paths[0]).toAbsolutePath().normalize()
            val right = Path.of(paths[1]).toAbsolutePath().normalize()
            if (!left.exists() || !right.exists()) {
                System.err.println("Error: Both paths must exist:\n  left:  $left\n  right: $right")
                return
            }
            preloadedPaths = left to right
        }

        val preloadFuture = preloadedPaths?.let { (left, right) ->
            CompletableFuture.supplyAsync {
                val title = "${left.name} <-> ${right.name}"
                if (left.isDirectory() && right.isDirectory()) {
                    val dirSession = DirDiffSession(leftRoot = left, rightRoot = right)
                    dirSession.scanSync()
                    null to (title to dirSession)
                } else {
                    val leftDocFuture = CompletableFuture.supplyAsync { DiffDocument.fromFile(left) }
                    val rightDoc = DiffDocument.fromFile(right)
                    val leftDoc = leftDocFuture.join()
                    val fileSession = TwoWayDiffSession(
                        leftDocument = leftDoc,
                        rightDocument = rightDoc,
                        initialComparisonPolicy = policy,
                        initialViewMode = viewMode
                    )
                    (title to fileSession) to null
                }
            }
        }

        if (effectiveConfig.ui.equals("compose", ignoreCase = true)) {
            val composeTheme = if (initialTheme.isDark) ComposeAppTheme.DARK else ComposeAppTheme.LIGHT
            launchComposeMainWindow(
                initialTheme = composeTheme,
                config = effectiveConfig,
                defaultPolicy = policy,
                defaultViewMode = viewMode,
                configPath = resolvedConfigPath,
                loadSamples = loadSamples,
                paths = paths,
                preloadFuture = preloadFuture
            )
            return
        }

        val closeLatch = CountDownLatch(1)

        SwingUtilities.invokeLater {
            DiffThemeManager.initialize(theme = initialTheme, config = effectiveConfig)
            val window = MainWindow(
                defaultPolicy = policy,
                defaultViewMode = viewMode,
                configPath = resolvedConfigPath
            )
            window.addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) {
                    closeLatch.countDown()
                }
            })

            if (loadSamples) {
                window.loadAllBuiltInSamples()
            } else if (preloadFuture != null) {
                val (filePair, dirPair) = preloadFuture.join()
                if (filePair != null) {
                    window.openFileSession(filePair.first, filePair.second)
                } else if (dirPair != null) {
                    window.openDirectorySession(dirPair.first, dirPair.second)
                }
            } else {
                // Open with a sample scratch comparison when launched with no arguments
                window.openBlankScratchDiff(
                    leftInitial = """
                        package example

                        class Calculator {
                            fun compute(a: Int, b: Int): Int {
                                return a + b
                            }
                        }
                    """.trimIndent(),
                    rightInitial = """
                        package example

                        class Calculator {
                            fun compute(first: Int, second: Int): Int {
                                val sum = first + second
                                return sum
                            }

                            fun reset() {}
                        }
                    """.trimIndent()
                )
            }

            window.isVisible = true
        }

        if (wait || paths.size >= 2) {
            closeLatch.await()
        }
    }
}

fun main(args: Array<String>) = IjDiffCli().main(args)
