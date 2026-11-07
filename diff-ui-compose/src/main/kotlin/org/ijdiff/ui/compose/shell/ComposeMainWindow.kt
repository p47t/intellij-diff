package org.ijdiff.ui.compose.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.intellij.diff.comparison.ComparisonPolicy
import org.ijdiff.core.samples.DiffSampleCatalog
import org.ijdiff.model.config.IjDiffConfig
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.DiffViewMode
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.ui.compose.components.AppButton
import org.ijdiff.ui.compose.components.AppDialog
import org.ijdiff.ui.compose.components.AppHorizontalDivider
import org.ijdiff.ui.compose.components.AppSelect
import org.ijdiff.ui.compose.components.AppText
import org.ijdiff.ui.compose.components.ButtonVariant
import org.ijdiff.ui.compose.components.ComposeVectorIcon
import org.ijdiff.ui.compose.components.VectorIconKind
import org.ijdiff.ui.compose.dirdiff.ComposeDirDiffView
import org.ijdiff.ui.compose.theme.AppTheme
import org.ijdiff.ui.compose.theme.ComposeAppTheme
import org.ijdiff.ui.compose.theme.ComposeThemeState
import org.ijdiff.ui.compose.theme.ProvideComposeDiffTheme
import org.ijdiff.ui.compose.twoway.ComposeTwoWayDiffView
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

sealed class ComposeDiffTab(val id: String, val baseTitle: String) {
    class FileTab(
        id: String,
        baseTitle: String,
        val session: TwoWayDiffSession
    ) : ComposeDiffTab(id, baseTitle)

    class DirectoryTab(
        id: String,
        baseTitle: String,
        val session: DirDiffSession
    ) : ComposeDiffTab(id, baseTitle)
}

class ComposeShellController(
    val defaultPolicy: ComparisonPolicy = ComparisonPolicy.DEFAULT,
    val defaultViewMode: DiffViewMode = DiffViewMode.SIDE_BY_SIDE,
    val configPath: Path = IjDiffConfig.resolveConfigPath()
) {
    private var nextId = 1
    val tabs = mutableStateListOf<ComposeDiffTab>()
    var activeTabId by mutableStateOf<String?>(null)
    var dirtyRevision by mutableStateOf(0)

    fun notifyDirtyStateChanged() {
        dirtyRevision++
    }

    fun openComparison(leftPath: Path, rightPath: Path) {
        if (leftPath.isDirectory() && rightPath.isDirectory()) {
            openDirectoryComparison(leftPath, rightPath)
        } else {
            openFileComparison(leftPath, rightPath)
        }
    }

    fun openFileComparison(
        leftPath: Path,
        rightPath: Path,
        policy: ComparisonPolicy = defaultPolicy,
        viewMode: DiffViewMode = defaultViewMode
    ) {
        val leftDoc = DiffDocument.fromFile(leftPath)
        val rightDoc = DiffDocument.fromFile(rightPath)
        val session = TwoWayDiffSession(
            leftDocument = leftDoc,
            rightDocument = rightDoc,
            initialComparisonPolicy = policy,
            initialViewMode = viewMode
        )
        openFileSession("${leftPath.name} <-> ${rightPath.name}", session)
    }

    fun openFileSession(baseTitle: String, session: TwoWayDiffSession) {
        val id = "tab-${nextId++}"
        val tab = ComposeDiffTab.FileTab(
            id = id,
            baseTitle = baseTitle,
            session = session
        )
        tabs.add(tab)
        activeTabId = id
    }

    fun openDirectoryComparison(leftDir: Path, rightDir: Path) {
        val session = DirDiffSession(leftRoot = leftDir, rightRoot = rightDir)
        session.scanSync()
        openDirectorySession("${leftDir.name} <-> ${rightDir.name}", session)
    }

    fun openDirectorySession(baseTitle: String, session: DirDiffSession) {
        if (session.allEntries.value.isEmpty()) {
            session.scanSync()
        }
        val id = "tab-${nextId++}"
        val tab = ComposeDiffTab.DirectoryTab(
            id = id,
            baseTitle = baseTitle,
            session = session
        )
        tabs.add(tab)
        activeTabId = id
    }

    fun openBlankScratchDiff(
        leftInitial: String = "",
        rightInitial: String = ""
    ) {
        val leftDoc = DiffDocument(initialText = leftInitial, title = "Left Scratch Buffer")
        val rightDoc = DiffDocument(initialText = rightInitial, title = "Right Scratch Buffer")
        val session = TwoWayDiffSession(
            leftDocument = leftDoc,
            rightDocument = rightDoc,
            initialComparisonPolicy = defaultPolicy,
            initialViewMode = defaultViewMode
        )
        val id = "tab-${nextId++}"
        val tab = ComposeDiffTab.FileTab(
            id = id,
            baseTitle = "Scratch Diff",
            session = session
        )
        tabs.add(tab)
        activeTabId = id
    }

    fun loadAllBuiltInSamples() {
        runCatching {
            val root = DiffSampleCatalog.locateSamplesRoot()
            for (spec in DiffSampleCatalog.FILE_SAMPLES) {
                openFileComparison(
                    leftPath = spec.resolveLeftPath(root),
                    rightPath = spec.resolveRightPath(root)
                )
            }
            for (dirSpec in DiffSampleCatalog.DIRECTORY_SAMPLES) {
                openDirectoryComparison(
                    leftDir = dirSpec.resolveLeftRoot(root),
                    rightDir = dirSpec.resolveRightRoot(root)
                )
            }
        }
    }

    fun closeTab(tabId: String) {
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index >= 0) {
            tabs.removeAt(index)
            if (activeTabId == tabId) {
                activeTabId = tabs.getOrNull(index.coerceAtMost(tabs.lastIndex))?.id
            }
        }
    }
}

@Composable
fun ComposeMainScreen(
    controller: ComposeShellController,
    themeState: ComposeThemeState
) {
    ProvideComposeDiffTheme(themeState) {
        val palette = themeState.palette
        var showFontDialog by remember { mutableStateOf(false) }
        var showCompareModal by remember { mutableStateOf(false) }
        var pendingCloseTab by remember { mutableStateOf<ComposeDiffTab.FileTab?>(null) }

        // Read dirtyRevision so tab titles recompose when document dirty flags change
        val rev = controller.dirtyRevision

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.appBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Shell Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.toolbarBg)
                        .padding(horizontal = AppTheme.spacing.toolbarHorizontalPadding, vertical = AppTheme.spacing.xs)
                ) {
                    AppButton(
                        text = "Compare Paths...",
                        onClick = { showCompareModal = true }
                    )
                    Spacer(Modifier.width(AppTheme.spacing.xs))
                    AppButton(
                        text = "+ Scratch Diff",
                        onClick = { controller.openBlankScratchDiff() }
                    )
                    Spacer(Modifier.width(AppTheme.spacing.xs))
                    AppButton(
                        text = "Load Sample Suite",
                        onClick = { controller.loadAllBuiltInSamples() }
                    )

                    Spacer(Modifier.weight(1f))

                    AppButton(
                        text = "Fonts: ${themeState.editorFontFamilyName} (${themeState.editorFontSizePt}pt) | UI: ${themeState.uiFontFamilyName} (${themeState.uiFontSizePt}pt)",
                        variant = ButtonVariant.ToolbarToggle(showFontDialog),
                        onClick = { showFontDialog = !showFontDialog }
                    )
                    Spacer(Modifier.width(AppTheme.spacing.md))

                    AppText(
                        text = "Theme: ",
                        color = palette.editorFg,
                        fontFamily = themeState.uiFontFamily,
                        fontSize = (themeState.uiFontSizePt - 1).coerceAtLeast(10).sp
                    )
                    AppSelect(
                        selected = themeState.theme,
                        options = ComposeAppTheme.entries,
                        labelFor = { it.displayName },
                        onSelected = { themeState.theme = it }
                    )
                }
                AppHorizontalDivider()

                // Flat Tab Bar
                if (controller.tabs.isNotEmpty()) {
                    val tabScroll = rememberScrollState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppTheme.spacing.tabBarHeight)
                            .background(palette.gutterBg)
                            .horizontalScroll(tabScroll)
                    ) {
                        for (tab in controller.tabs) {
                            val isSelected = tab.id == controller.activeTabId
                            val isDirty = (rev >= 0) && (tab is ComposeDiffTab.FileTab) && tab.session.hasUnsavedChanges()
                            val label = if (isDirty) "${tab.baseTitle} *" else tab.baseTitle
                            val tabBg = if (isSelected) palette.editorBg else palette.toolbarBg

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(AppTheme.spacing.tabBarHeight)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(AppTheme.spacing.tabBarHeight)
                                        .background(tabBg)
                                        .clickable { controller.activeTabId = tab.id }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .height(AppTheme.spacing.tabBarHeight)
                                            .padding(horizontal = AppTheme.spacing.lg)
                                    ) {
                                        if (tab is ComposeDiffTab.DirectoryTab) {
                                            ComposeVectorIcon(
                                                kind = VectorIconKind.FOLDER,
                                                tint = palette.modifiedBorder,
                                                modifier = Modifier.size(AppTheme.spacing.iconSize)
                                            )
                                            Spacer(Modifier.width(5.dp))
                                        }
                                        AppText(
                                            text = label,
                                            color = if (isSelected) palette.editorFg else palette.gutterFg,
                                            fontFamily = themeState.uiFontFamily,
                                            fontSize = (themeState.uiFontSizePt - 1).coerceAtLeast(10).sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.width(AppTheme.spacing.md))
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(AppTheme.spacing.smallIconSize + 5.dp)
                                                .clickable {
                                                    if (tab is ComposeDiffTab.FileTab && tab.session.hasUnsavedChanges()) {
                                                        pendingCloseTab = tab
                                                    } else {
                                                        controller.closeTab(tab.id)
                                                    }
                                                }
                                        ) {
                                            ComposeVectorIcon(
                                                kind = VectorIconKind.CLOSE_X,
                                                tint = palette.gutterFg,
                                                modifier = Modifier.size(AppTheme.spacing.smallIconSize)
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(2.dp)
                                                    .align(Alignment.BottomStart)
                                                    .background(palette.accentBlue)
                                            )
                                        }
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(AppTheme.spacing.tabBarHeight)
                                        .background(palette.gutterBorder)
                                )
                            }
                        }
                    }
                    AppHorizontalDivider()
                }

                // Active Tab Content
                val activeTab = controller.tabs.firstOrNull { it.id == controller.activeTabId }
                Box(modifier = Modifier.fillMaxSize()) {
                    if (activeTab != null) {
                        key(activeTab.id) {
                            when (activeTab) {
                                is ComposeDiffTab.FileTab -> {
                                    ComposeTwoWayDiffView(
                                        session = activeTab.session,
                                        onDirtyStateChanged = { controller.notifyDirtyStateChanged() }
                                    )
                                }
                                is ComposeDiffTab.DirectoryTab -> {
                                    ComposeDirDiffView(session = activeTab.session)
                                }
                            }
                        }
                    } else {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            AppText(
                                text = "No open comparisons. Click '+ Scratch Diff' or 'Load Sample Suite' above.",
                                color = palette.gutterFg,
                                fontFamily = themeState.uiFontFamily,
                                fontSize = themeState.uiFontSizePt.sp
                            )
                        }
                    }
                }
            }

            // Font Configuration Overlay Modal
            if (showFontDialog) {
                ComposeFontConfigModal(
                    themeState = themeState,
                    configPath = controller.configPath,
                    onClose = { showFontDialog = false }
                )
            }

            // Compare Paths Modal
            if (showCompareModal) {
                ComposeComparePathsModal(
                    onCompare = { left, right ->
                        showCompareModal = false
                        controller.openComparison(left, right)
                    },
                    onCancel = { showCompareModal = false }
                )
            }

            // Unsaved Changes Confirmation Modal
            val tabToConfirm = pendingCloseTab
            if (tabToConfirm != null) {
                AppDialog(
                    title = "Unsaved Changes",
                    width = 420.dp,
                    onDismissRequest = { pendingCloseTab = null },
                    actions = {
                        AppButton(
                            text = "Save & Close",
                            variant = ButtonVariant.ToolbarPrimary,
                            onClick = {
                                tabToConfirm.session.saveAll()
                                controller.closeTab(tabToConfirm.id)
                                pendingCloseTab = null
                            }
                        )
                        Spacer(Modifier.width(AppTheme.spacing.sm))
                        AppButton(
                            text = "Discard",
                            onClick = {
                                controller.closeTab(tabToConfirm.id)
                                pendingCloseTab = null
                            }
                        )
                        Spacer(Modifier.width(AppTheme.spacing.sm))
                        AppButton(
                            text = "Cancel",
                            onClick = { pendingCloseTab = null }
                        )
                    }
                ) {
                    AppText(
                        text = "Tab '${tabToConfirm.baseTitle}' has unsaved changes. Save before closing?",
                        color = palette.editorFg,
                        fontFamily = themeState.uiFontFamily,
                        fontSize = themeState.uiFontSizePt.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposeFontConfigModal(
    themeState: ComposeThemeState,
    configPath: Path,
    onClose: () -> Unit
) {
    val palette = themeState.palette
    var editorFamily by remember { mutableStateOf(themeState.editorFontFamilyName) }
    var editorSizeStr by remember { mutableStateOf(themeState.editorFontSizePt.toString()) }
    var uiFamily by remember { mutableStateOf(themeState.uiFontFamilyName) }
    var uiSizeStr by remember { mutableStateOf(themeState.uiFontSizePt.toString()) }

    fun applyChanges(saveToDisk: Boolean) {
        val eFam = editorFamily.trim().ifEmpty { themeState.editorFontFamilyName }
        val eSize = (editorSizeStr.toIntOrNull() ?: themeState.editorFontSizePt)
            .coerceIn(IjDiffConfig.MIN_FONT_SIZE, IjDiffConfig.MAX_FONT_SIZE)
        val uFam = uiFamily.trim().ifEmpty { themeState.uiFontFamilyName }
        val uSize = (uiSizeStr.toIntOrNull() ?: themeState.uiFontSizePt)
            .coerceIn(IjDiffConfig.MIN_FONT_SIZE, IjDiffConfig.MAX_FONT_SIZE)

        themeState.editorFontFamilyName = eFam
        themeState.editorFontSizePt = eSize
        themeState.uiFontFamilyName = uFam
        themeState.uiFontSizePt = uSize

        if (saveToDisk) {
            val existing = IjDiffConfig.load(configPath)
            val updated = existing.copy(
                fontFamily = eFam,
                fontSize = eSize,
                uiFontFamily = uFam,
                uiFontSize = uSize,
                theme = if (themeState.theme.isDark) "dark" else "light",
                sourcePath = configPath
            )
            runCatching { IjDiffConfig.save(updated, configPath) }
        }
        onClose()
    }

    AppDialog(
        title = "Editor & UI Font Configuration (Compose)",
        width = 460.dp,
        onDismissRequest = onClose,
        actions = {
            AppButton(
                text = "Apply & Save to Config",
                variant = ButtonVariant.ToolbarPrimary,
                onClick = { applyChanges(saveToDisk = true) }
            )
            Spacer(Modifier.width(AppTheme.spacing.sm))
            AppButton(
                text = "Apply Only",
                onClick = { applyChanges(saveToDisk = false) }
            )
            Spacer(Modifier.width(AppTheme.spacing.sm))
            AppButton(
                text = "Cancel",
                onClick = onClose
            )
        }
    ) {
        ModalInputRow("Editor Font ([font] family):", editorFamily) { editorFamily = it }
        Spacer(Modifier.height(AppTheme.spacing.md))
        ModalInputRow("Editor Size ([font] size):", editorSizeStr) { editorSizeStr = it }
        Spacer(Modifier.height(AppTheme.spacing.md))
        ModalInputRow("UI Font ([ui.font] family):", uiFamily) { uiFamily = it }
        Spacer(Modifier.height(AppTheme.spacing.md))
        ModalInputRow("UI Size ([ui.font] size):", uiSizeStr) { uiSizeStr = it }
        Spacer(Modifier.height(10.dp))

        AppText(
            text = "TOML Config: $configPath",
            color = palette.gutterFg,
            fontFamily = themeState.uiFontFamily,
            fontSize = (themeState.uiFontSizePt - 1).coerceAtLeast(10).sp
        )
    }
}

@Composable
private fun ComposeComparePathsModal(
    onCompare: (Path, Path) -> Unit,
    onCancel: () -> Unit
) {
    val themeState = org.ijdiff.ui.compose.theme.LocalComposeThemeState.current
    val palette = themeState.palette
    var leftPathStr by remember { mutableStateOf("") }
    var rightPathStr by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AppDialog(
        title = "Compare Files or Folders",
        width = 500.dp,
        onDismissRequest = onCancel,
        actions = {
            AppButton(
                text = "Compare",
                variant = ButtonVariant.ToolbarPrimary,
                onClick = {
                    val p1 = Path.of(leftPathStr.trim())
                    val p2 = Path.of(rightPathStr.trim())
                    if (!p1.exists() || !p2.exists()) {
                        errorMsg = "Both paths must exist on disk."
                    } else {
                        onCompare(p1, p2)
                    }
                }
            )
            Spacer(Modifier.width(AppTheme.spacing.sm))
            AppButton(text = "Cancel", onClick = onCancel)
        }
    ) {
        ModalInputRow("Left Path:", leftPathStr) { leftPathStr = it }
        Spacer(Modifier.height(AppTheme.spacing.md))
        ModalInputRow("Right Path:", rightPathStr) { rightPathStr = it }

        if (errorMsg != null) {
            Spacer(Modifier.height(AppTheme.spacing.md))
            AppText(
                text = errorMsg!!,
                color = palette.deletedBorder,
                fontFamily = themeState.uiFontFamily,
                fontSize = (themeState.uiFontSizePt - 1).coerceAtLeast(10).sp
            )
        }
    }
}

@Composable
private fun ModalInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val theme = org.ijdiff.ui.compose.theme.LocalComposeThemeState.current
    val palette = theme.palette
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        AppText(
            text = label,
            color = palette.editorFg,
            fontFamily = theme.uiFontFamily,
            fontSize = theme.uiFontSizePt.sp,
            modifier = Modifier.width(190.dp)
        )
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .height(28.dp)
                .background(palette.editorBg, AppTheme.shapes.control)
                .border(1.dp, palette.buttonBorder, AppTheme.shapes.control)
                .padding(horizontal = AppTheme.spacing.md)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = palette.editorFg,
                    fontFamily = theme.uiFontFamily,
                    fontSize = theme.uiFontSizePt.sp
                ),
                cursorBrush = SolidColor(palette.editorFg),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Launches the standalone Phase 2 Compose Multiplatform Desktop window for `ijdiff`.
 */
fun launchComposeMainWindow(
    initialTheme: ComposeAppTheme,
    config: IjDiffConfig,
    defaultPolicy: ComparisonPolicy,
    defaultViewMode: DiffViewMode,
    configPath: Path,
    loadSamples: Boolean,
    paths: List<String>,
    preloadFuture: java.util.concurrent.CompletableFuture<Pair<Pair<String, TwoWayDiffSession>?, Pair<String, DirDiffSession>?>>? = null
) = application {
    val themeState = remember {
        ComposeThemeState(initialTheme = initialTheme, initialConfig = config)
    }
    val controller = remember {
        val preloaded = preloadFuture?.join()
        val preloadedFileSession = preloaded?.first
        val preloadedDirSession = preloaded?.second
        ComposeShellController(
            defaultPolicy = defaultPolicy,
            defaultViewMode = defaultViewMode,
            configPath = configPath
        ).apply {
            if (loadSamples) {
                loadAllBuiltInSamples()
            } else if (preloadedFileSession != null) {
                openFileSession(preloadedFileSession.first, preloadedFileSession.second)
            } else if (preloadedDirSession != null) {
                openDirectorySession(preloadedDirSession.first, preloadedDirSession.second)
            } else if (paths.size >= 2) {
                val left = Path.of(paths[0]).toAbsolutePath().normalize()
                val right = Path.of(paths[1]).toAbsolutePath().normalize()
                if (left.exists() && right.exists()) {
                    openComparison(left, right)
                }
            } else {
                openBlankScratchDiff(
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
        }
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "IntelliJ Diff Viewer (ijdiff — Compose Multiplatform)",
        state = rememberWindowState(width = 1380.dp, height = 860.dp)
    ) {
        ComposeMainScreen(controller = controller, themeState = themeState)
    }
}
