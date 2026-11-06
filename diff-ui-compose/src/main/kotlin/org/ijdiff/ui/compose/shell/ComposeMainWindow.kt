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
import androidx.compose.material.Text
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
import org.ijdiff.ui.compose.components.ComposeVectorIcon
import org.ijdiff.ui.compose.components.ToolbarActionButton
import org.ijdiff.ui.compose.components.ToolbarDropdownSelector
import org.ijdiff.ui.compose.components.VectorIconKind
import org.ijdiff.ui.compose.dirdiff.ComposeDirDiffView
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
                        .border(0.5.dp, palette.gutterBorder)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    ToolbarActionButton(
                        text = "Compare Paths...",
                        onClick = { showCompareModal = true }
                    )
                    Spacer(Modifier.width(6.dp))
                    ToolbarActionButton(
                        text = "+ Scratch Diff",
                        onClick = { controller.openBlankScratchDiff() }
                    )
                    Spacer(Modifier.width(6.dp))
                    ToolbarActionButton(
                        text = "Load Sample Suite",
                        onClick = { controller.loadAllBuiltInSamples() }
                    )

                    Spacer(Modifier.weight(1f))

                    ToolbarActionButton(
                        text = "Fonts: ${themeState.editorFontFamilyName} (${themeState.editorFontSizePt}pt) | UI: ${themeState.uiFontFamilyName} (${themeState.uiFontSizePt}pt)",
                        active = showFontDialog,
                        onClick = { showFontDialog = !showFontDialog }
                    )
                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = "Theme: ",
                        color = palette.editorFg,
                        fontFamily = themeState.uiFontFamily,
                        fontSize = (themeState.uiFontSizePt - 1).coerceAtLeast(10).sp
                    )
                    ToolbarDropdownSelector(
                        selected = themeState.theme,
                        options = ComposeAppTheme.entries,
                        labelFor = { it.displayName },
                        onSelected = { themeState.theme = it }
                    )
                }

                // Tab Bar
                if (controller.tabs.isNotEmpty()) {
                    val tabScroll = rememberScrollState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .background(palette.gutterBg)
                            .border(0.5.dp, palette.gutterBorder)
                            .horizontalScroll(tabScroll)
                            .padding(horizontal = 6.dp)
                    ) {
                        for (tab in controller.tabs) {
                            val isSelected = tab.id == controller.activeTabId
                            val isDirty = (rev >= 0) && (tab is ComposeDiffTab.FileTab) && tab.session.hasUnsavedChanges()
                            val label = if (isDirty) "${tab.baseTitle} *" else tab.baseTitle
                            val tabBg = if (isSelected) palette.editorBg else palette.toolbarBg

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(26.dp)
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(tabBg)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) palette.accentBlue else palette.buttonBorder,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .clickable { controller.activeTabId = tab.id }
                                    .padding(horizontal = 8.dp)
                            ) {
                                if (tab is ComposeDiffTab.DirectoryTab) {
                                    ComposeVectorIcon(
                                        kind = VectorIconKind.FOLDER,
                                        tint = palette.modifiedBorder,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                }
                                Text(
                                    text = label,
                                    color = palette.editorFg,
                                    fontFamily = themeState.uiFontFamily,
                                    fontSize = (themeState.uiFontSizePt - 1).coerceAtLeast(10).sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(14.dp)
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
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }
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
                            Text(
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier
                            .width(420.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.toolbarBg)
                            .border(1.dp, palette.buttonBorder, RoundedCornerShape(6.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Unsaved Changes",
                            color = palette.editorFg,
                            fontFamily = themeState.uiFontFamily,
                            fontSize = (themeState.uiFontSizePt + 1).sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tab '${tabToConfirm.baseTitle}' has unsaved changes. Save before closing?",
                            color = palette.editorFg,
                            fontFamily = themeState.uiFontFamily,
                            fontSize = themeState.uiFontSizePt.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ToolbarActionButton(
                                text = "Save & Close",
                                bold = true,
                                onClick = {
                                    tabToConfirm.session.saveAll()
                                    controller.closeTab(tabToConfirm.id)
                                    pendingCloseTab = null
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            ToolbarActionButton(
                                text = "Discard",
                                onClick = {
                                    controller.closeTab(tabToConfirm.id)
                                    pendingCloseTab = null
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            ToolbarActionButton(
                                text = "Cancel",
                                onClick = { pendingCloseTab = null }
                            )
                        }
                    }
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.toolbarBg)
                .border(1.dp, palette.buttonBorder, RoundedCornerShape(6.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Editor & UI Font Configuration (Compose)",
                color = palette.editorFg,
                fontFamily = themeState.uiFontFamily,
                fontSize = (themeState.uiFontSizePt + 1).sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            ModalInputRow("Editor Font ([font] family):", editorFamily) { editorFamily = it }
            Spacer(Modifier.height(8.dp))
            ModalInputRow("Editor Size ([font] size):", editorSizeStr) { editorSizeStr = it }
            Spacer(Modifier.height(8.dp))
            ModalInputRow("UI Font ([ui.font] family):", uiFamily) { uiFamily = it }
            Spacer(Modifier.height(8.dp))
            ModalInputRow("UI Size ([ui.font] size):", uiSizeStr) { uiSizeStr = it }
            Spacer(Modifier.height(10.dp))

            Text(
                text = "TOML Config: $configPath",
                color = palette.gutterFg,
                fontFamily = themeState.uiFontFamily,
                fontSize = (themeState.uiFontSizePt - 1).coerceAtLeast(10).sp
            )
            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
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

                ToolbarActionButton(
                    text = "Apply & Save to Config",
                    bold = true,
                    onClick = { applyChanges(saveToDisk = true) }
                )
                Spacer(Modifier.width(6.dp))
                ToolbarActionButton(
                    text = "Apply Only",
                    onClick = { applyChanges(saveToDisk = false) }
                )
                Spacer(Modifier.width(6.dp))
                ToolbarActionButton(
                    text = "Cancel",
                    onClick = onClose
                )
            }
        }
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

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(palette.toolbarBg)
                .border(1.dp, palette.buttonBorder, RoundedCornerShape(6.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "Compare Files or Folders",
                color = palette.editorFg,
                fontFamily = themeState.uiFontFamily,
                fontSize = (themeState.uiFontSizePt + 1).sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            ModalInputRow("Left Path:", leftPathStr) { leftPathStr = it }
            Spacer(Modifier.height(8.dp))
            ModalInputRow("Right Path:", rightPathStr) { rightPathStr = it }

            if (errorMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = errorMsg!!,
                    color = palette.deletedBorder,
                    fontFamily = themeState.uiFontFamily,
                    fontSize = (themeState.uiFontSizePt - 1).coerceAtLeast(10).sp
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                ToolbarActionButton(
                    text = "Compare",
                    bold = true,
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
                Spacer(Modifier.width(6.dp))
                ToolbarActionButton(text = "Cancel", onClick = onCancel)
            }
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
        Text(
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
                .background(palette.editorBg, RoundedCornerShape(4.dp))
                .border(1.dp, palette.buttonBorder, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp)
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
