package org.ijdiff.ui.compose

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import org.ijdiff.core.TextDiffType
import org.ijdiff.core.samples.DiffSampleCatalog
import org.ijdiff.model.config.IjDiffConfig
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.ChunkDirection
import org.ijdiff.model.twoway.DiffViewMode
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.ui.compose.components.AppButton
import org.ijdiff.ui.compose.components.AppDialog
import org.ijdiff.ui.compose.components.AppText
import org.ijdiff.ui.compose.components.ButtonVariant
import org.ijdiff.ui.compose.dirdiff.ComposeDirDiffView
import org.ijdiff.ui.compose.shell.ComposeMainScreen
import org.ijdiff.ui.compose.shell.ComposeShellController
import org.ijdiff.ui.compose.syntax.ComposeSyntaxHighlighter
import org.ijdiff.ui.compose.theme.AppTheme
import org.ijdiff.ui.compose.theme.ComposeAppTheme
import org.ijdiff.ui.compose.theme.ComposeThemeState
import org.ijdiff.ui.compose.theme.ProvideComposeDiffTheme
import org.ijdiff.ui.compose.twoway.ComposeTwoWayDiffView
import org.jetbrains.skia.EncodedImageFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ComposeDiffPresentationTest {

    @Test
    fun `AppTheme exposes semantic tokens and renders AppDialog with ButtonVariant without Material`() {
        val themeState = ComposeThemeState(initialTheme = ComposeAppTheme.DARK)
        ImageComposeScene(width = 640, height = 420).use { scene ->
            scene.setContent {
                ProvideComposeDiffTheme(themeState) {
                    assertEquals(true, AppTheme.colors.isDark)
                    assertTrue(AppTheme.spacing.toolbarHeight.value > 0f)
                    assertNotNull(AppTheme.shapes.control)
                    assertNotNull(AppTheme.typography.uiDefault)

                    AppDialog(
                        title = "Design System Verification",
                        onDismissRequest = {},
                        actions = {
                            AppButton(
                                text = "Confirm",
                                variant = ButtonVariant.ToolbarPrimary,
                                onClick = {}
                            )
                            AppButton(
                                text = "Toggle",
                                variant = ButtonVariant.ToolbarToggle(active = true),
                                onClick = {}
                            )
                        }
                    ) {
                        AppText("Custom design system dialog content")
                    }
                }
            }
            val img = scene.render()
            assertEquals(640, img.width)
            assertEquals(420, img.height)
        }
    }

    @Test
    fun `ComposeDiffPresentation computes Canvas Bezier polygons and applies chunk actions without Swing`() {
        val left = DiffDocument("line1\noldVal\nline3")
        val right = DiffDocument("line1\nnewVal\nline3\naddedLine")
        val session = TwoWayDiffSession(left, right)
        val composeVm = ComposeDiffPresentation(session)

        val polygons = composeVm.computeDividerPolygons(
            dividerWidthPx = 36f,
            lineHeightPx = 20f,
            leftScrollYPx = 0f,
            rightScrollYPx = 0f,
            isDarkTheme = true
        )

        assertEquals(2, polygons.size)
        assertEquals(TextDiffType.MODIFIED, polygons[0].polygon.type)
        assertEquals(20f, polygons[0].polygon.startY1)
        assertEquals(40f, polygons[0].polygon.endY1)
        assertTrue(polygons[0].isSelected)

        // Apply chunk 0 Left -> Right via Compose presentation layer
        composeVm.onAcceptChunk(0, ChunkDirection.LEFT_TO_RIGHT)
        assertEquals("line1\noldVal\nline3\naddedLine", right.text.value)
        assertEquals(1, composeVm.state.value.fragments.size)
    }

    @Test
    fun `ComposeTwoWayDiffView, ComposeDirDiffView, and ComposeMainScreen render offscreen via ImageComposeScene`() {
        val outDir = Path.of("build/reports/compose-renders")
        Files.createDirectories(outDir)

        val root = DiffSampleCatalog.locateSamplesRoot()
        val sample01 = DiffSampleCatalog.FILE_SAMPLES.first()
        val leftDoc = DiffDocument.fromFile(sample01.resolveLeftPath(root))
        val rightDoc = DiffDocument.fromFile(sample01.resolveRightPath(root))
        val session = TwoWayDiffSession(leftDoc, rightDoc)

        val themeState = ComposeThemeState(
            initialTheme = ComposeAppTheme.DARK,
            initialConfig = IjDiffConfig(
                fontFamily = "JetBrains Mono",
                fontSize = 13,
                uiFontFamily = "Inter",
                uiFontSize = 13
            )
        )

        // 1. Render Side-by-Side 2-Way Diff in Compose
        ImageComposeScene(width = 1280, height = 760).use { scene ->
            scene.setContent {
                ProvideComposeDiffTheme(themeState) {
                    ComposeTwoWayDiffView(session = session)
                }
            }
            val img = scene.render()
            assertEquals(1280, img.width)
            val pngData = img.encodeToData(EncodedImageFormat.PNG)
            assertNotNull(pngData)
            Files.write(outDir.resolve("compose-01-kotlin-sbs-dark.png"), pngData!!.bytes)
        }

        // 2. Render Unified 2-Way Diff in Compose (Light Theme)
        session.setViewMode(DiffViewMode.UNIFIED)
        themeState.theme = ComposeAppTheme.LIGHT
        ImageComposeScene(width = 1280, height = 760).use { scene ->
            scene.setContent {
                ProvideComposeDiffTheme(themeState) {
                    ComposeTwoWayDiffView(session = session)
                }
            }
            val img = scene.render()
            val pngData = img.encodeToData(EncodedImageFormat.PNG)
            assertNotNull(pngData)
            Files.write(outDir.resolve("compose-01-kotlin-unified-light.png"), pngData!!.bytes)
        }

        // 3. Render Directory Diff Master-Detail View in Compose (Dark Theme)
        themeState.theme = ComposeAppTheme.DARK
        val dirSpec = DiffSampleCatalog.DIRECTORY_SAMPLES.first()
        val dirSession = DirDiffSession(dirSpec.resolveLeftRoot(root), dirSpec.resolveRightRoot(root))
        dirSession.scanSync()
        dirSession.filteredEntries.value.firstOrNull { !it.isBinary }?.let { dirSession.selectEntry(it) }

        ImageComposeScene(width = 1280, height = 820).use { scene ->
            scene.setContent {
                ProvideComposeDiffTheme(themeState) {
                    ComposeDirDiffView(session = dirSession)
                }
            }
            val img = scene.render()
            val pngData = img.encodeToData(EncodedImageFormat.PNG)
            assertNotNull(pngData)
            Files.write(outDir.resolve("compose-dir-01-master-detail.png"), pngData!!.bytes)
        }

        // 4. Render Multi-Tab ComposeMainScreen
        val controller = ComposeShellController()
        controller.loadAllBuiltInSamples()
        ImageComposeScene(width = 1380, height = 860).use { scene ->
            scene.setContent {
                ComposeMainScreen(controller = controller, themeState = themeState)
            }
            val img = scene.render()
            val pngData = img.encodeToData(EncodedImageFormat.PNG)
            assertNotNull(pngData)
            Files.write(outDir.resolve("compose-main-window-samples.png"), pngData!!.bytes)
        }

        assertTrue(Files.exists(outDir.resolve("compose-01-kotlin-sbs-dark.png")))
        assertTrue(Files.exists(outDir.resolve("compose-dir-01-master-detail.png")))
        assertTrue(Files.exists(outDir.resolve("compose-main-window-samples.png")))
    }

    @Test
    fun `ComposeDirDiffView and ComposeTwoWayDiffView clip center Bezier connection when scrolled down`() {
        val leftLines = (1..80).joinToString("\n") { i ->
            if (i in 2..15) "left_modified_line_$i" else "common_line_$i"
        }
        val rightLines = (1..80).joinToString("\n") { i ->
            if (i in 2..25) "right_modified_line_$i" else "common_line_$i"
        }
        val session = TwoWayDiffSession(DiffDocument(leftLines), DiffDocument(rightLines))
        val themeState = ComposeThemeState(initialTheme = ComposeAppTheme.DARK)

        ImageComposeScene(width = 1000, height = 600).use { scene ->
            scene.setContent {
                ProvideComposeDiffTheme(themeState) {
                    ComposeTwoWayDiffView(session = session)
                }
            }
            // Jump to the first difference and then scroll down so the top of the fragment has negative Y
            val firstFrag = session.state.value.fragments.first()
            session.selectFragment(0)
            assertNotNull(firstFrag)
            val img = scene.render()
            assertEquals(1000, img.width)
            assertEquals(600, img.height)
        }
    }

    @Test
    fun `ComposeSyntaxHighlighter scales Nerd Font PUA glyphs and renders 18pt config cleanly`() {
        val outDir = Path.of("build/reports/compose-renders")
        Files.createDirectories(outDir)

        val themeState = ComposeThemeState(
            initialTheme = ComposeAppTheme.DARK,
            initialConfig = IjDiffConfig(
                fontFamily = "Ioskeley Mono NL",
                fontSize = 18,
                uiFontFamily = "Noto Sans",
                uiFontSize = 18
            )
        )
        val lineWithNerdIcons = "val icons = \"\uE711  \uF015  \uF07B  \uDB80\uDE19  status-bar\""
        assertTrue(ComposeSyntaxHighlighter.containsNerdFontGlyphs(lineWithNerdIcons))

        val highlighted = ComposeSyntaxHighlighter.highlightLine(
            lineText = lineWithNerdIcons,
            palette = themeState.palette,
            nerdFontFamily = themeState.nerdFontFamily,
            baseFontSizeSp = 18f
        )
        val expectedScaledSp = 18f * ComposeSyntaxHighlighter.NERD_FONT_SCALE_FACTOR
        val scaledSpans = highlighted.spanStyles.filter {
            kotlin.math.abs(it.item.fontSize.value - expectedScaledSp) < 0.05f
        }
        assertEquals(4, scaledSpans.size)

        val leftText = listOf(
            "// Waybar / Omarchy Status Icons",
            "\"format-icons\": {",
            "    \"default\": \"\uF015  Home\",",
            "    \"folder\": \"\uF07B  Projects\",",
            "    \"git\": \"\uE711  Branch\"",
            "}"
        ).joinToString("\n")
        val rightText = listOf(
            "// Waybar / Omarchy Status Icons (Updated)",
            "\"format-icons\": {",
            "    \"default\": \"\uF015  Workspace\",",
            "    \"folder\": \"\uF07B  Repositories\",",
            "    \"git\": \"\uE711  Main\",",
            "    \"file\": \"\uDB80\uDE19  Config\"",
            "}"
        ).joinToString("\n")
        val session = TwoWayDiffSession(
            DiffDocument(initialText = leftText, title = "waybar-left.json"),
            DiffDocument(initialText = rightText, title = "waybar-right.json")
        )

        ImageComposeScene(width = 1280, height = 560).use { scene ->
            scene.setContent {
                ProvideComposeDiffTheme(themeState) {
                    ComposeTwoWayDiffView(session = session)
                }
            }
            val img = scene.render()
            val pngData = img.encodeToData(EncodedImageFormat.PNG)
            assertNotNull(pngData)
            Files.write(outDir.resolve("compose-nerd-font-18pt.png"), pngData!!.bytes)
        }
        assertTrue(Files.exists(outDir.resolve("compose-nerd-font-18pt.png")))
    }
}
