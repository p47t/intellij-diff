package org.ijdiff.ui.compose.twoway

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import kotlinx.coroutines.launch
import org.ijdiff.core.TextDiffType
import org.ijdiff.core.UnifiedFragmentBuilder
import org.ijdiff.core.getDiffType
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.ChunkDirection
import org.ijdiff.model.twoway.DiffViewMode
import org.ijdiff.model.twoway.HighlightPolicy
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.ui.compose.ComposeDiffPresentation
import org.ijdiff.ui.compose.components.AppButton
import org.ijdiff.ui.compose.components.AppHorizontalDivider
import org.ijdiff.ui.compose.components.AppSelect
import org.ijdiff.ui.compose.components.AppText
import org.ijdiff.ui.compose.components.AppToolbarSeparator
import org.ijdiff.ui.compose.components.ButtonVariant
import org.ijdiff.ui.compose.components.ComposeVectorIcon
import org.ijdiff.ui.compose.components.VectorIconKind
import org.ijdiff.ui.compose.syntax.ComposeSyntaxHighlighter
import org.ijdiff.ui.compose.theme.AppTheme
import org.ijdiff.ui.compose.theme.LocalComposeThemeState
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Phase 2 Compose Multiplatform Desktop 2-Way Diff Viewer (`Side-by-Side` & `Unified`).
 * Features synchronized scrolling via [org.ijdiff.core.LineNumberConvertor], interactive `>>`/`<<` chunk patching,
 * smooth cubic Bezier curve divider in a Compose [Canvas], overview minimap bar, freehand editing, and explicit Save (`Ctrl+S`).
 */
@Composable
fun ComposeTwoWayDiffView(
    session: TwoWayDiffSession,
    onDirtyStateChanged: () -> Unit = {}
) {
    val theme = LocalComposeThemeState.current
    val palette = theme.palette
    val state by session.state.collectAsState()
    val viewMode by session.viewMode.collectAsState()
    val comparisonPolicy by session.comparisonPolicy.collectAsState()
    val highlightPolicy by session.highlightPolicy.collectAsState()
    val leftDirty by session.leftDocument.isDirty.collectAsState()
    val rightDirty by session.rightDocument.isDirty.collectAsState()
    val leftCanUndo by session.leftDocument.canUndo.collectAsState()
    val rightCanUndo by session.rightDocument.canUndo.collectAsState()

    var editMode by remember { mutableStateOf(false) }
    var appendChunksMode by remember { mutableStateOf(false) }

    val leftListState = rememberLazyListState()
    val rightListState = rememberLazyListState()
    val unifiedListState = rememberLazyListState()
    val leftHorizontalScroll = rememberScrollState()
    val rightHorizontalScroll = rememberScrollState()

    SynchronizeHorizontalScrolling(leftHorizontalScroll, rightHorizontalScroll)
    val scope = rememberCoroutineScope()

    val lineHeightDp: Dp = (theme.editorFontSizePt + 8).dp
    val density = LocalDensity.current
    val lineHeightInt = with(density) { lineHeightDp.roundToPx().coerceAtLeast(16) }
    val lineHeightPx = lineHeightInt.toFloat()

    val leftScrollPx = leftListState.firstVisibleItemIndex * lineHeightPx + leftListState.firstVisibleItemScrollOffset
    val rightScrollPx = rightListState.firstVisibleItemIndex * lineHeightPx + rightListState.firstVisibleItemScrollOffset

    // Synchronize left <-> right scroll via LineNumberConvertor without ping-pong feedback
    val syncTracker = remember {
        object {
            var lastSyncedLeftIndex = -1
            var lastSyncedLeftOffset = -1
            var lastSyncedRightIndex = -1
            var lastSyncedRightOffset = -1
        }
    }

    LaunchedEffect(leftListState.firstVisibleItemIndex, leftListState.firstVisibleItemScrollOffset) {
        val curLeftIdx = leftListState.firstVisibleItemIndex
        val curLeftOff = leftListState.firstVisibleItemScrollOffset
        if (curLeftIdx == syncTracker.lastSyncedLeftIndex && curLeftOff == syncTracker.lastSyncedLeftOffset) {
            syncTracker.lastSyncedLeftIndex = -1
            syncTracker.lastSyncedLeftOffset = -1
            return@LaunchedEffect
        }
        if (rightListState.isScrollInProgress && !leftListState.isScrollInProgress) {
            return@LaunchedEffect
        }
        val leftY = curLeftIdx * lineHeightPx + curLeftOff
        val targetRightPx = state.lineConvertor
            .convertApproximatePixel1(leftY, lineHeightPx)
            .coerceAtLeast(0f)
            .roundToInt()
        val anchorLine2 = targetRightPx / lineHeightInt
        val subLineOffset2 = targetRightPx % lineHeightInt
        if (rightListState.firstVisibleItemIndex != anchorLine2 ||
            rightListState.firstVisibleItemScrollOffset != subLineOffset2
        ) {
            rightListState.scrollToItem(anchorLine2, subLineOffset2)
            syncTracker.lastSyncedRightIndex = rightListState.firstVisibleItemIndex
            syncTracker.lastSyncedRightOffset = rightListState.firstVisibleItemScrollOffset
        }
    }

    LaunchedEffect(rightListState.firstVisibleItemIndex, rightListState.firstVisibleItemScrollOffset) {
        val curRightIdx = rightListState.firstVisibleItemIndex
        val curRightOff = rightListState.firstVisibleItemScrollOffset
        if (curRightIdx == syncTracker.lastSyncedRightIndex && curRightOff == syncTracker.lastSyncedRightOffset) {
            syncTracker.lastSyncedRightIndex = -1
            syncTracker.lastSyncedRightOffset = -1
            return@LaunchedEffect
        }
        if (leftListState.isScrollInProgress && !rightListState.isScrollInProgress) {
            return@LaunchedEffect
        }
        val rightY = curRightIdx * lineHeightPx + curRightOff
        val targetLeftPx = state.lineConvertor
            .convertApproximatePixelInv1(rightY, lineHeightPx)
            .coerceAtLeast(0f)
            .roundToInt()
        val anchorLine1 = targetLeftPx / lineHeightInt
        val subLineOffset1 = targetLeftPx % lineHeightInt
        if (leftListState.firstVisibleItemIndex != anchorLine1 ||
            leftListState.firstVisibleItemScrollOffset != subLineOffset1
        ) {
            leftListState.scrollToItem(anchorLine1, subLineOffset1)
            syncTracker.lastSyncedLeftIndex = leftListState.firstVisibleItemIndex
            syncTracker.lastSyncedLeftOffset = leftListState.firstVisibleItemScrollOffset
        }
    }

    fun scrollToFragment(fragment: LineFragment) {
        scope.launch {
            if (viewMode == DiffViewMode.UNIFIED) {
                val blockIdx = state.fragments.indexOf(fragment)
                val uLine = state.unifiedResult.blocks.getOrNull(blockIdx)?.startLine ?: fragment.startLine1
                unifiedListState.animateScrollToItem((uLine - 3).coerceAtLeast(0), 0)
            } else {
                val targetLine = (fragment.startLine1 - 3).coerceAtLeast(0)
                leftListState.animateScrollToItem(targetLine, 0)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(palette.appBg)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when {
                        (event.isCtrlPressed || event.isMetaPressed) && event.key == Key.S -> {
                            session.saveAll()
                            onDirtyStateChanged()
                            true
                        }
                        event.key == Key.F7 && event.isShiftPressed -> {
                            session.prevDifference()?.let { scrollToFragment(it) }
                            true
                        }
                        event.key == Key.F7 -> {
                            session.nextDifference()?.let { scrollToFragment(it) }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        // Toolbar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.toolbarBg)
                .padding(horizontal = AppTheme.spacing.toolbarHorizontalPadding, vertical = AppTheme.spacing.toolbarVerticalPadding)
        ) {
            AppButton(
                text = "Prev",
                icon = VectorIconKind.ARROW_UP,
                onClick = { session.prevDifference()?.let { scrollToFragment(it) } }
            )
            Spacer(Modifier.width(AppTheme.spacing.xs))
            AppButton(
                text = "Next",
                icon = VectorIconKind.ARROW_DOWN,
                onClick = { session.nextDifference()?.let { scrollToFragment(it) } }
            )

            AppToolbarSeparator()

            AppSelect(
                selected = viewMode,
                options = DiffViewMode.entries,
                labelFor = { it.displayName },
                onSelected = { session.setViewMode(it) }
            )
            Spacer(Modifier.width(AppTheme.spacing.sm))

            AppSelect(
                selected = comparisonPolicy,
                options = ComparisonPolicy.entries,
                labelFor = { it.name },
                onSelected = { session.setComparisonPolicy(it) }
            )
            Spacer(Modifier.width(AppTheme.spacing.sm))

            AppSelect(
                selected = highlightPolicy,
                options = HighlightPolicy.entries,
                labelFor = { it.displayName },
                onSelected = { session.setHighlightPolicy(it) }
            )

            AppToolbarSeparator()

            AppButton(
                text = if (appendChunksMode) "Chunk: Append" else "Chunk: Replace",
                variant = ButtonVariant.ToolbarToggle(appendChunksMode),
                onClick = { appendChunksMode = !appendChunksMode }
            )
            Spacer(Modifier.width(AppTheme.spacing.sm))

            AppButton(
                text = if (editMode) "Editing Text" else "Edit Text",
                variant = ButtonVariant.ToolbarToggle(editMode),
                onClick = { editMode = !editMode }
            )

            AppToolbarSeparator()

            AppButton(
                text = "Undo L",
                icon = VectorIconKind.UNDO,
                enabled = leftCanUndo,
                onClick = {
                    if (session.leftDocument.undo()) {
                        session.recomputeNow()
                        onDirtyStateChanged()
                    }
                }
            )
            Spacer(Modifier.width(AppTheme.spacing.xs))
            AppButton(
                text = "Undo R",
                icon = VectorIconKind.UNDO,
                enabled = rightCanUndo,
                onClick = {
                    if (session.rightDocument.undo()) {
                        session.recomputeNow()
                        onDirtyStateChanged()
                    }
                }
            )
            Spacer(Modifier.width(AppTheme.spacing.sm))

            val anyDirty = leftDirty || rightDirty
            AppButton(
                text = if (anyDirty) "Save *" else "Save",
                enabled = anyDirty,
                variant = if (anyDirty) ButtonVariant.ToolbarPrimary else ButtonVariant.ToolbarDefault,
                onClick = {
                    session.saveAll()
                    onDirtyStateChanged()
                }
            )

            Spacer(Modifier.weight(1f))

            val totalDiffs = state.fragments.size
            val statusStr = if (totalDiffs == 0) {
                "Contents are identical"
            } else {
                val cur = (state.currentFragmentIndex + 1).coerceIn(1, totalDiffs)
                "Difference $cur of $totalDiffs"
            }
            AppText(
                text = statusStr,
                color = palette.editorFg,
                fontFamily = theme.uiFontFamily,
                fontSize = theme.uiFontSizePt.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (viewMode == DiffViewMode.UNIFIED) {
            AppHorizontalDivider()
            ComposeUnifiedDiffPane(
                unifiedResult = state.unifiedResult,
                listState = unifiedListState,
                lineHeightDp = lineHeightDp
            )
        } else {
            AppHorizontalDivider()
            // File header strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.appBg)
                    .padding(horizontal = AppTheme.spacing.lg, vertical = AppTheme.spacing.xs)
            ) {
                val leftBadge = buildString {
                    append(session.leftDocument.title)
                    if (leftDirty) append(" *")
                    append("  (${session.leftDocument.lineSeparator.name})")
                    if (session.leftDocument.isReadOnly) append(" [Read-Only]")
                }
                val rightBadge = buildString {
                    append(session.rightDocument.title)
                    if (rightDirty) append(" *")
                    append("  (${session.rightDocument.lineSeparator.name})")
                    if (session.rightDocument.isReadOnly) append(" [Read-Only]")
                }
                AppText(
                    text = leftBadge,
                    color = palette.editorFg,
                    fontFamily = theme.uiFontFamily,
                    fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(42.dp))
                AppText(
                    text = rightBadge,
                    color = palette.editorFg,
                    fontFamily = theme.uiFontFamily,
                    fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            AppHorizontalDivider()

            // 4-Column Side-by-Side Layout: Left Pane | Bezier Divider | Right Pane | Overview Bar
            Row(modifier = Modifier.fillMaxSize().clipToBounds()) {
                ComposeDiffSidePane(
                    isLeft = true,
                    document = session.leftDocument,
                    fragments = state.fragments,
                    activeFragmentIndex = state.currentFragmentIndex,
                    listState = leftListState,
                    horizontalScroll = leftHorizontalScroll,
                    lineHeightDp = lineHeightDp,
                    editMode = editMode,
                    canTargetAcceptChunks = !session.rightDocument.isReadOnly,
                    onChunkAction = { fragment ->
                        if (appendChunksMode) {
                            session.appendChunk(fragment, ChunkDirection.LEFT_TO_RIGHT)
                        } else {
                            session.replaceChunk(fragment, ChunkDirection.LEFT_TO_RIGHT)
                        }
                        onDirtyStateChanged()
                    },
                    onTextEdited = { newText ->
                        session.leftDocument.setText(newText, recordUndo = true)
                        session.recomputeNow()
                        onDirtyStateChanged()
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                // Center Bezier Curve Divider Canvas
                ComposeBezierDividerCanvas(
                    session = session,
                    lineHeightPx = lineHeightPx,
                    leftScrollPx = leftScrollPx,
                    rightScrollPx = rightScrollPx,
                    onFragmentClicked = { idx ->
                        session.selectFragment(idx)?.let { scrollToFragment(it) }
                    },
                    modifier = Modifier.width(38.dp).fillMaxHeight()
                )

                ComposeDiffSidePane(
                    isLeft = false,
                    document = session.rightDocument,
                    fragments = state.fragments,
                    activeFragmentIndex = state.currentFragmentIndex,
                    listState = rightListState,
                    horizontalScroll = rightHorizontalScroll,
                    lineHeightDp = lineHeightDp,
                    editMode = editMode,
                    canTargetAcceptChunks = !session.leftDocument.isReadOnly,
                    onChunkAction = { fragment ->
                        if (appendChunksMode) {
                            session.appendChunk(fragment, ChunkDirection.RIGHT_TO_LEFT)
                        } else {
                            session.replaceChunk(fragment, ChunkDirection.RIGHT_TO_LEFT)
                        }
                        onDirtyStateChanged()
                    },
                    onTextEdited = { newText ->
                        session.rightDocument.setText(newText, recordUndo = true)
                        session.recomputeNow()
                        onDirtyStateChanged()
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                // Right Overview Minimap Bar
                ComposeOverviewBarCanvas(
                    fragments = state.fragments,
                    totalLines = max(session.leftDocument.lineCount, session.rightDocument.lineCount).coerceAtLeast(1),
                    firstVisibleLine = leftListState.firstVisibleItemIndex,
                    visibleLineCount = leftListState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(10),
                    onLineClicked = { targetLine ->
                        scope.launch {
                            leftListState.animateScrollToItem((targetLine - 3).coerceAtLeast(0))
                        }
                    },
                    modifier = Modifier.width(14.dp).fillMaxHeight()
                )
            }
        }
    }
}

@Composable
internal fun ComposeDiffSidePane(
    isLeft: Boolean,
    document: DiffDocument,
    fragments: List<LineFragment>,
    activeFragmentIndex: Int,
    listState: LazyListState,
    horizontalScroll: ScrollState,
    lineHeightDp: Dp,
    editMode: Boolean,
    canTargetAcceptChunks: Boolean,
    onChunkAction: (LineFragment) -> Unit,
    onTextEdited: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalComposeThemeState.current
    val palette = theme.palette
    val text by document.text.collectAsState()
    val lines = remember(text) { text.split('\n') }
    val contentWidth = rememberCodeWidth(lines)
    val editScroll = rememberScrollState()

    // Map each 0-based line to its containing fragment & inline highlight ranges
    data class LineFragmentInfo(
        val fragmentIndex: Int,
        val fragment: LineFragment,
        val diffType: TextDiffType,
        val isFirstLineOfFragment: Boolean,
        val isLastLineOfFragment: Boolean,
        val inlineRanges: List<ComposeSyntaxHighlighter.InlineHighlightRange>
    )

    val lineInfoMap = remember(text, fragments, palette) {
        val map = HashMap<Int, LineFragmentInfo>()
        val offsets = document.lineOffsets
        for ((idx, fragment) in fragments.withIndex()) {
            val startLine = if (isLeft) fragment.startLine1 else fragment.startLine2
            val endLine = if (isLeft) fragment.endLine1 else fragment.endLine2
            val baseOffset = if (isLeft) fragment.startOffset1 else fragment.startOffset2
            val diffType = fragment.getDiffType()
            val wordBg = palette.getWordBg(diffType)

            for (line in startLine until endLine) {
                if (line !in 0 until offsets.lineCount) continue
                val lineStartAbs = offsets.getLineStart(line)
                val lineEndAbs = offsets.getLineEnd(line)
                val inlineList = mutableListOf<ComposeSyntaxHighlighter.InlineHighlightRange>()
                fragment.innerFragments?.forEach { df ->
                    val relS = if (isLeft) df.startOffset1 else df.startOffset2
                    val relE = if (isLeft) df.endOffset1 else df.endOffset2
                    val absS = (baseOffset + relS).coerceIn(lineStartAbs, lineEndAbs)
                    val absE = (baseOffset + relE).coerceIn(lineStartAbs, lineEndAbs)
                    if (absS < absE) {
                        inlineList.add(
                            ComposeSyntaxHighlighter.InlineHighlightRange(
                                startCol = absS - lineStartAbs,
                                endCol = absE - lineStartAbs,
                                backgroundColor = wordBg
                            )
                        )
                    }
                }
                map[line] = LineFragmentInfo(
                    fragmentIndex = idx,
                    fragment = fragment,
                    diffType = diffType,
                    isFirstLineOfFragment = (line == startLine),
                    isLastLineOfFragment = (line == endLine - 1),
                    inlineRanges = inlineList
                )
            }
        }
        map
    }

    val emptyRangeMarkers = remember(fragments) {
        val markers = HashMap<Int, TextDiffType>()
        for (fragment in fragments) {
            val startLine = if (isLeft) fragment.startLine1 else fragment.startLine2
            val endLine = if (isLeft) fragment.endLine1 else fragment.endLine2
            if (startLine == endLine) {
                markers[startLine] = fragment.getDiffType()
            }
        }
        markers
    }

    Column(
        modifier = modifier
            .background(palette.editorBg)
            .horizontalWheelScroll(horizontalScroll)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (editMode && !document.isReadOnly) {
                BasicTextField(
                    value = text,
                    onValueChange = { onTextEdited(it) },
                    textStyle = TextStyle(
                        color = palette.editorFg,
                        fontFamily = theme.editorFontFamily,
                        fontSize = theme.editorFontSizePt.sp,
                        lineHeight = lineHeightDp.value.sp
                    ),
                    cursorBrush = SolidColor(palette.editorFg),
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(editScroll)
                        .horizontalScroll(horizontalScroll)
                        .width(contentWidth + 8.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            } else {
                val gutterDigits = max(3, lines.size.toString().length)
                val gutterNumWidth = (gutterDigits * (theme.editorFontSizePt * 0.68f).roundToInt() + 14).dp
                val actionColWidth = AppTheme.spacing.gutterActionWidth
                val chevronWidth = AppTheme.spacing.gutterIconWidth
                val chevronHeight = AppTheme.spacing.gutterIconHeight

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(lines) { lineIdx, lineStr ->
                        val info = lineInfoMap[lineIdx]
                        val emptyMarkerType = emptyRangeMarkers[lineIdx]
                        val lineBg = info?.let { palette.getLineBg(it.diffType) } ?: palette.editorBg
                        val isActiveFragment = info != null && info.fragmentIndex == activeFragmentIndex

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(lineHeightDp)
                                .background(lineBg)
                        ) {
                            // Gutter (Line Number + >> / << Chunk Button)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .height(lineHeightDp)
                                    .background(info?.let { palette.getLineBg(it.diffType) } ?: palette.gutterBg)
                            ) {
                                if (!isLeft) {
                                    // Right pane: << button on the left edge of gutter
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .width(actionColWidth)
                                            .fillMaxHeight()
                                            .let { mod ->
                                                if (info != null && info.isFirstLineOfFragment && canTargetAcceptChunks) {
                                                    mod.clickable { onChunkAction(info.fragment) }
                                                } else mod
                                            }
                                    ) {
                                        if (info != null && info.isFirstLineOfFragment && canTargetAcceptChunks) {
                                            ComposeVectorIcon(
                                                kind = VectorIconKind.CHEVRON_LEFT,
                                                tint = palette.getBorderColor(info.diffType),
                                                modifier = Modifier.width(chevronWidth).height(chevronHeight)
                                            )
                                        }
                                    }
                                }

                                AppText(
                                    text = (lineIdx + 1).toString(),
                                    color = palette.gutterFg,
                                    fontFamily = theme.editorFontFamily,
                                    fontSize = (theme.editorFontSizePt - 1).coerceAtLeast(9).sp,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier
                                        .width(gutterNumWidth)
                                        .padding(end = 6.dp)
                                )

                                if (isLeft) {
                                    // Left pane: >> button on the right edge of gutter
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .width(actionColWidth)
                                            .fillMaxHeight()
                                            .let { mod ->
                                                if (info != null && info.isFirstLineOfFragment && canTargetAcceptChunks) {
                                                    mod.clickable { onChunkAction(info.fragment) }
                                                } else mod
                                            }
                                    ) {
                                        if (info != null && info.isFirstLineOfFragment && canTargetAcceptChunks) {
                                            ComposeVectorIcon(
                                                kind = VectorIconKind.CHEVRON_RIGHT,
                                                tint = palette.getBorderColor(info.diffType),
                                                modifier = Modifier.width(chevronWidth).height(chevronHeight)
                                            )
                                        }
                                    }
                                }
                            }

                            // Gutter vertical divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(palette.gutterBorder)
                            )

                            // Code line with syntax + inline word diff highlights
                            Box(
                                contentAlignment = Alignment.CenterStart,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .horizontalScroll(horizontalScroll)
                                    .width(contentWidth)
                                    .padding(horizontal = 8.dp)
                            ) {
                                val nerdFam = theme.nerdFontFamily
                                val edSizePt = theme.editorFontSizePt
                                val annotated = remember(lineStr, palette, info?.inlineRanges, nerdFam, edSizePt) {
                                    ComposeSyntaxHighlighter.highlightLine(
                                        lineText = lineStr.replace("\t", "    "),
                                        palette = palette,
                                        inlineRanges = info?.inlineRanges ?: emptyList(),
                                        nerdFontFamily = nerdFam,
                                        baseFontSizeSp = edSizePt.toFloat()
                                    )
                                }
                                AppText(
                                    text = annotated,
                                    color = palette.editorFg,
                                    fontFamily = theme.editorFontFamily,
                                    fontSize = theme.editorFontSizePt.sp,
                                    softWrap = false,
                                    maxLines = 1
                                )

                                if (emptyMarkerType != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(2.dp)
                                            .align(Alignment.TopStart)
                                            .background(palette.getBorderColor(emptyMarkerType))
                                    )
                                }
                                if (isActiveFragment && info.isFirstLineOfFragment) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.5.dp)
                                            .align(Alignment.TopStart)
                                            .background(palette.activeFragmentBorder)
                                    )
                                }
                                if (isActiveFragment && info.isLastLineOfFragment) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(1.5.dp)
                                            .align(Alignment.BottomStart)
                                            .background(palette.activeFragmentBorder)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        CodeHorizontalScrollbar(horizontalScroll)
    }
}

@Composable
private fun ComposeBezierDividerCanvas(
    session: TwoWayDiffSession,
    lineHeightPx: Float,
    leftScrollPx: Float,
    rightScrollPx: Float,
    onFragmentClicked: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalComposeThemeState.current
    val palette = theme.palette
    val state by session.state.collectAsState()
    val presentation = remember(session) { ComposeDiffPresentation(session) }
    var hoveredFragmentIndex by remember { mutableStateOf(-1) }

    Canvas(
        modifier = modifier
            .clipToBounds()
            .background(palette.dividerBg)
            .pointerHoverIcon(if (hoveredFragmentIndex >= 0) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(state.fragments, lineHeightPx, leftScrollPx, rightScrollPx) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Move -> {
                                val pos = event.changes.firstOrNull()?.position
                                if (pos == null || pos.y !in 0f..size.height.toFloat()) {
                                    hoveredFragmentIndex = -1
                                } else {
                                    val polygons = presentation.computeDividerPolygons(
                                        dividerWidthPx = size.width.toFloat(),
                                        lineHeightPx = lineHeightPx,
                                        leftScrollYPx = leftScrollPx,
                                        rightScrollYPx = rightScrollPx,
                                        isDarkTheme = palette.isDark
                                    )
                                    val hit = polygons.firstOrNull { poly ->
                                        val p = poly.polygon
                                        val minY = minOf(p.startY1, p.startY2) - 4f
                                        val maxY = maxOf(p.endY1, p.endY2) + 4f
                                        pos.y in minY..maxY
                                    }
                                    hoveredFragmentIndex = hit?.fragmentIndex ?: -1
                                }
                            }
                            PointerEventType.Exit -> {
                                hoveredFragmentIndex = -1
                            }
                        }
                    }
                }
            }
            .pointerInput(state.fragments, lineHeightPx, leftScrollPx, rightScrollPx) {
                detectTapGestures { tapOffset ->
                    if (tapOffset.y !in 0f..size.height.toFloat()) return@detectTapGestures
                    val polygons = presentation.computeDividerPolygons(
                        dividerWidthPx = size.width.toFloat(),
                        lineHeightPx = lineHeightPx,
                        leftScrollYPx = leftScrollPx,
                        rightScrollYPx = rightScrollPx,
                        isDarkTheme = palette.isDark
                    )
                    val hit = polygons.firstOrNull { poly ->
                        val p = poly.polygon
                        val minY = minOf(p.startY1, p.startY2) - 4f
                        val maxY = maxOf(p.endY1, p.endY2) + 4f
                        tapOffset.y in minY..maxY
                    }
                    if (hit != null) {
                        onFragmentClicked(hit.fragmentIndex)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height

        clipRect(left = 0f, top = 0f, right = w, bottom = h) {
            val polygons = presentation.computeDividerPolygons(
                dividerWidthPx = w,
                lineHeightPx = lineHeightPx,
                leftScrollYPx = leftScrollPx,
                rightScrollYPx = rightScrollPx,
                isDarkTheme = palette.isDark
            )

            for (poly in polygons) {
                val p = poly.polygon
                if (maxOf(p.endY1, p.endY2) < 0f || minOf(p.startY1, p.startY2) > h) continue
                val isHovered = poly.fragmentIndex == hoveredFragmentIndex

                val path = Path().apply {
                    moveTo(0f, p.startY1)
                    cubicTo(
                        p.topCurve.ctrl1X, p.topCurve.ctrl1Y,
                        p.topCurve.ctrl2X, p.topCurve.ctrl2Y,
                        p.topCurve.endX, p.topCurve.endY
                    )
                    lineTo(w, p.endY2)
                    cubicTo(
                        p.bottomCurve.ctrl1X, p.bottomCurve.ctrl1Y,
                        p.bottomCurve.ctrl2X, p.bottomCurve.ctrl2Y,
                        p.bottomCurve.endX, p.bottomCurve.endY
                    )
                    close()
                }

                val baseFill = Color(poly.fillColor.argb)
                val fillColor = if (isHovered) {
                    baseFill.copy(alpha = (baseFill.alpha + 0.18f).coerceAtMost(0.85f))
                } else {
                    baseFill
                }
                drawPath(path, color = fillColor)

                val topStroke = Path().apply {
                    moveTo(0f, p.startY1)
                    cubicTo(
                        p.topCurve.ctrl1X, p.topCurve.ctrl1Y,
                        p.topCurve.ctrl2X, p.topCurve.ctrl2Y,
                        p.topCurve.endX, p.topCurve.endY
                    )
                }
                val bottomStroke = Path().apply {
                    moveTo(w, p.endY2)
                    cubicTo(
                        p.bottomCurve.ctrl1X, p.bottomCurve.ctrl1Y,
                        p.bottomCurve.ctrl2X, p.bottomCurve.ctrl2Y,
                        p.bottomCurve.endX, p.bottomCurve.endY
                    )
                }
                val strokeColor = when {
                    poly.isSelected -> palette.activeFragmentBorder
                    isHovered -> palette.activeFragmentBorder.copy(alpha = 0.85f)
                    else -> Color(poly.strokeColor.argb)
                }
                val strokeWidth = when {
                    poly.isSelected -> 2.0f
                    isHovered -> 1.6f
                    else -> 1.1f
                }
                drawPath(topStroke, color = strokeColor, style = Stroke(width = strokeWidth))
                drawPath(bottomStroke, color = strokeColor, style = Stroke(width = strokeWidth))
            }

            // Left and right border lines
            drawLine(palette.gutterBorder, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1f)
            drawLine(palette.gutterBorder, Offset(w - 1f, 0f), Offset(w - 1f, h), strokeWidth = 1f)
        }
    }
}

@Composable
private fun ComposeOverviewBarCanvas(
    fragments: List<LineFragment>,
    totalLines: Int,
    firstVisibleLine: Int,
    visibleLineCount: Int,
    onLineClicked: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalComposeThemeState.current.palette
    Canvas(
        modifier = modifier
            .clipToBounds()
            .background(palette.gutterBg)
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(totalLines) {
                detectTapGestures { offset ->
                    if (size.height > 0) {
                        val targetLine = ((offset.y / size.height) * totalLines).toInt().coerceIn(0, totalLines - 1)
                        onLineClicked(targetLine)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        clipRect(left = 0f, top = 0f, right = w, bottom = h) {
            val denom = totalLines.coerceAtLeast(1).toFloat()
            for (fragment in fragments) {
                val type = fragment.getDiffType()
                val y1 = (fragment.startLine1 / denom) * h
                val y2 = ((fragment.endLine1.coerceAtLeast(fragment.startLine1 + 1)) / denom) * h
                val barH = (y2 - y1).coerceAtLeast(3f)
                drawRect(
                    color = palette.getBorderColor(type),
                    topLeft = Offset(2f, y1),
                    size = Size(w - 4f, barH)
                )
            }

            // Sliding viewport indicator ("thumb")
            val thumbTop = ((firstVisibleLine.coerceAtLeast(0) / denom) * h).coerceIn(0f, h)
            val thumbHeight = ((visibleLineCount.coerceAtLeast(1) / denom) * h).coerceIn(10f, h)
            val clampedTop = thumbTop.coerceAtMost((h - thumbHeight).coerceAtLeast(0f))
            drawRect(
                color = palette.editorFg.copy(alpha = if (palette.isDark) 0.16f else 0.12f),
                topLeft = Offset(1f, clampedTop),
                size = Size((w - 2f).coerceAtLeast(2f), thumbHeight)
            )
            drawRect(
                color = palette.gutterFg.copy(alpha = 0.65f),
                topLeft = Offset(1f, clampedTop),
                size = Size((w - 2f).coerceAtLeast(2f), thumbHeight),
                style = Stroke(width = 1f)
            )

            drawLine(palette.gutterBorder, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1f)
        }
    }
}

@Composable
internal fun ComposeUnifiedDiffPane(
    unifiedResult: UnifiedFragmentBuilder.UnifiedDiffResult,
    listState: LazyListState,
    lineHeightDp: Dp,
    horizontalScroll: ScrollState = rememberScrollState()
) {
    val theme = LocalComposeThemeState.current
    val palette = theme.palette
    val lines = remember(unifiedResult.text) { unifiedResult.text.split('\n') }
    val contentWidth = rememberCodeWidth(lines)
    val colWidth = (theme.editorFontSizePt * 3 + 4).coerceAtLeast(42).dp

    Column(Modifier.fillMaxSize().background(palette.editorBg).horizontalWheelScroll(horizontalScroll)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            itemsIndexed(lines) { uLine, lineStr ->
                val mapping = unifiedResult.lineMappings.getOrNull(uLine)
                val diffType = mapping?.type
                val lineBg = diffType?.let { palette.getLineBg(it) } ?: palette.editorBg

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(lineHeightDp)
                        .background(lineBg)
                ) {
                    // Dual left/right line-number gutter
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(lineHeightDp)
                            .background(diffType?.let { palette.getLineBg(it) } ?: palette.gutterBg)
                    ) {
                        AppText(
                            text = if (mapping != null && mapping.leftLine >= 0) (mapping.leftLine + 1).toString() else "",
                            color = palette.gutterFg,
                            fontFamily = theme.editorFontFamily,
                            fontSize = (theme.editorFontSizePt - 1).coerceAtLeast(9).sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(colWidth).padding(end = 6.dp)
                        )
                        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.gutterBorder))
                        AppText(
                            text = if (mapping != null && mapping.rightLine >= 0) (mapping.rightLine + 1).toString() else "",
                            color = palette.gutterFg,
                            fontFamily = theme.editorFontFamily,
                            fontSize = (theme.editorFontSizePt - 1).coerceAtLeast(9).sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(colWidth).padding(end = 6.dp)
                        )
                        AppText(
                            text = when (diffType) {
                                TextDiffType.DELETED -> "-"
                                TextDiffType.INSERTED -> "+"
                                else -> " "
                            },
                            color = diffType?.let { palette.getBorderColor(it) } ?: palette.gutterFg,
                            fontFamily = theme.editorFontFamily,
                            fontSize = theme.editorFontSizePt.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(14.dp)
                        )
                    }

                    Box(Modifier.width(1.dp).fillMaxHeight().background(palette.gutterBorder))

                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .horizontalScroll(horizontalScroll)
                            .width(contentWidth)
                            .padding(horizontal = 8.dp)
                    ) {
                        val nerdFam = theme.nerdFontFamily
                        val edSizePt = theme.editorFontSizePt
                        val annotated = remember(lineStr, palette, nerdFam, edSizePt) {
                            ComposeSyntaxHighlighter.highlightLine(
                                lineText = lineStr.replace("\t", "    "),
                                palette = palette,
                                nerdFontFamily = nerdFam,
                                baseFontSizeSp = edSizePt.toFloat()
                            )
                        }
                        AppText(
                            text = annotated,
                            color = palette.editorFg,
                            fontFamily = theme.editorFontFamily,
                            fontSize = theme.editorFontSizePt.sp,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        CodeHorizontalScrollbar(horizontalScroll)
    }
}
