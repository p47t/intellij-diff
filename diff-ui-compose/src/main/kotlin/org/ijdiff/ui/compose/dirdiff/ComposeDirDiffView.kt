package org.ijdiff.ui.compose.dirdiff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ijdiff.model.dirdiff.DirCompareMode
import org.ijdiff.model.dirdiff.DirDiffEntry
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.dirdiff.DirDiffStatus
import org.ijdiff.model.dirdiff.DirSyncAction
import org.ijdiff.ui.compose.components.ToolbarActionButton
import org.ijdiff.ui.compose.components.ToolbarDropdownSelector
import org.ijdiff.ui.compose.components.VectorIconKind
import org.ijdiff.ui.compose.theme.LocalComposeThemeState
import org.ijdiff.ui.compose.twoway.ComposeTwoWayDiffView

/**
 * Phase 2 Compose Multiplatform Desktop Master-Detail Directory Comparison & Synchronization Viewer (`DirDiffViewer`).
 * Top pane: Filter/Sync toolbar + Two-sided file comparison table.
 * Bottom pane: Embedded [ComposeTwoWayDiffView] live-previewing and editing the selected file pair.
 */
@Composable
fun ComposeDirDiffView(
    session: DirDiffSession
) {
    val theme = LocalComposeThemeState.current
    val palette = theme.palette

    val allEntries by session.allEntries.collectAsState()
    val filteredEntries by session.filteredEntries.collectAsState()
    val compareMode by session.compareMode.collectAsState()
    val statusFilter by session.statusFilter.collectAsState()
    val globFilter by session.globFilter.collectAsState()
    val selectedEntry by session.selectedEntry.collectAsState()
    val previewSession by session.previewSession.collectAsState()

    LaunchedEffect(session) {
        if (session.allEntries.value.isEmpty()) {
            session.scanSync()
        }
        if (session.selectedEntry.value == null && session.filteredEntries.value.isNotEmpty()) {
            val firstText = session.filteredEntries.value.firstOrNull { !it.isBinary }
                ?: session.filteredEntries.value.first()
            session.selectEntry(firstText)
        }
    }

    fun cycleActionFor(entry: DirDiffEntry): DirSyncAction {
        val order = when (entry.status) {
            DirDiffStatus.DELETED -> listOf(DirSyncAction.COPY_TO_RIGHT, DirSyncAction.DELETE_LEFT, DirSyncAction.SKIP)
            DirDiffStatus.ADDED -> listOf(DirSyncAction.COPY_TO_LEFT, DirSyncAction.DELETE_RIGHT, DirSyncAction.SKIP)
            DirDiffStatus.MODIFIED -> listOf(DirSyncAction.COPY_TO_RIGHT, DirSyncAction.COPY_TO_LEFT, DirSyncAction.SKIP)
            DirDiffStatus.EQUAL -> listOf(DirSyncAction.SKIP)
        }
        val idx = order.indexOf(entry.syncAction)
        return order[(idx + 1) % order.size]
    }

    fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        return "%.1f MB".format(mb)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.appBg)
    ) {
        // Filter & Sync Toolbar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.toolbarBg)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            ToolbarActionButton(
                text = "Rescan",
                icon = VectorIconKind.REFRESH,
                onClick = { session.scanSync() }
            )
            Spacer(Modifier.width(6.dp))

            ToolbarDropdownSelector(
                selected = compareMode,
                options = DirCompareMode.entries,
                labelFor = { it.displayName },
                onSelected = { session.setCompareMode(it) }
            )
            Spacer(Modifier.width(10.dp))

            ToolbarActionButton(
                text = "* Modified",
                active = DirDiffStatus.MODIFIED in statusFilter,
                onClick = { session.toggleStatusFilter(DirDiffStatus.MODIFIED) }
            )
            Spacer(Modifier.width(3.dp))
            ToolbarActionButton(
                text = "- Left Only",
                active = DirDiffStatus.DELETED in statusFilter,
                onClick = { session.toggleStatusFilter(DirDiffStatus.DELETED) }
            )
            Spacer(Modifier.width(3.dp))
            ToolbarActionButton(
                text = "+ Right Only",
                active = DirDiffStatus.ADDED in statusFilter,
                onClick = { session.toggleStatusFilter(DirDiffStatus.ADDED) }
            )
            Spacer(Modifier.width(3.dp))
            ToolbarActionButton(
                text = "= Equal",
                active = DirDiffStatus.EQUAL in statusFilter,
                onClick = { session.toggleStatusFilter(DirDiffStatus.EQUAL) }
            )
            Spacer(Modifier.width(10.dp))

            Text(
                text = "Filter: ",
                color = palette.editorFg,
                fontFamily = theme.uiFontFamily,
                fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp
            )
            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .width(150.dp)
                    .height(26.dp)
                    .background(palette.editorBg, RoundedCornerShape(4.dp))
                    .border(1.dp, palette.buttonBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp)
            ) {
                if (globFilter.isEmpty()) {
                    Text(
                        text = "*.kt; !*.tmp",
                        color = palette.gutterFg,
                        fontFamily = theme.uiFontFamily,
                        fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp
                    )
                }
                BasicTextField(
                    value = globFilter,
                    onValueChange = { session.setGlobFilter(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = palette.editorFg,
                        fontFamily = theme.uiFontFamily,
                        fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp
                    ),
                    cursorBrush = SolidColor(palette.editorFg),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.width(10.dp))

            ToolbarActionButton(
                text = "Sync Selected",
                enabled = selectedEntry != null && selectedEntry?.syncAction != DirSyncAction.SKIP,
                onClick = {
                    selectedEntry?.let { session.synchronizeEntries(listOf(it)) }
                }
            )
            Spacer(Modifier.width(4.dp))
            ToolbarActionButton(
                text = "Sync All",
                bold = true,
                enabled = filteredEntries.any { it.syncAction != DirSyncAction.SKIP },
                onClick = {
                    val actionable = filteredEntries.filter { it.syncAction != DirSyncAction.SKIP }
                    if (actionable.isNotEmpty()) {
                        session.synchronizeEntries(actionable)
                    }
                }
            )

            Spacer(Modifier.weight(1f))

            val modCount = remember(allEntries) { allEntries.count { it.status == DirDiffStatus.MODIFIED } }
            val delCount = remember(allEntries) { allEntries.count { it.status == DirDiffStatus.DELETED } }
            val addCount = remember(allEntries) { allEntries.count { it.status == DirDiffStatus.ADDED } }
            Text(
                text = "${filteredEntries.size} shown (Modified: $modCount, Left Only: $delCount, Right Only: $addCount)",
                color = palette.editorFg,
                fontFamily = theme.uiFontFamily,
                fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Master Table (36% height)
        val tableScroll = rememberScrollState()
        val rowHeightDp = (theme.uiFontSizePt + 11).coerceAtLeast(24).dp

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.36f)
                .background(palette.editorBg)
                .border(0.5.dp, palette.gutterBorder)
        ) {
            // Table Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeightDp)
                    .background(palette.gutterBg)
                    .border(0.5.dp, palette.gutterBorder)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "Left File",
                    color = palette.editorFg,
                    fontFamily = theme.uiFontFamily,
                    fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(0.38f)
                )
                Text(
                    text = "Left Size",
                    color = palette.editorFg,
                    fontFamily = theme.uiFontFamily,
                    fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(90.dp).padding(end = 8.dp)
                )
                Text(
                    text = "Action",
                    color = palette.editorFg,
                    fontFamily = theme.uiFontFamily,
                    fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(86.dp)
                )
                Text(
                    text = "Right File",
                    color = palette.editorFg,
                    fontFamily = theme.uiFontFamily,
                    fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(0.38f).padding(start = 8.dp)
                )
                Text(
                    text = "Right Size",
                    color = palette.editorFg,
                    fontFamily = theme.uiFontFamily,
                    fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(90.dp)
                )
            }

            // Table Rows
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(tableScroll)
            ) {
                for (entry in filteredEntries) {
                    val isSelected = selectedEntry?.relativePath == entry.relativePath
                    val rowBg = when {
                        isSelected -> palette.accentBlue.copy(alpha = if (palette.isDark) 0.42f else 0.25f)
                        entry.status == DirDiffStatus.ADDED -> palette.insertedLineBg
                        entry.status == DirDiffStatus.DELETED -> palette.deletedLineBg
                        entry.status == DirDiffStatus.MODIFIED -> palette.modifiedLineBg
                        else -> palette.editorBg
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rowHeightDp)
                            .background(rowBg)
                            .clickable { session.selectEntry(entry) }
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = if (entry.leftFile != null) entry.relativePath else "",
                            color = palette.editorFg,
                            fontFamily = theme.uiFontFamily,
                            fontSize = theme.uiFontSizePt.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(0.38f)
                        )
                        Text(
                            text = entry.leftFile?.size?.let { formatSize(it) } ?: "",
                            color = palette.editorFg,
                            fontFamily = theme.uiFontFamily,
                            fontSize = theme.uiFontSizePt.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(90.dp).padding(end = 8.dp)
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .width(86.dp)
                                .fillMaxHeight()
                                .clickable {
                                    session.setSyncAction(entry.relativePath, cycleActionFor(entry))
                                }
                        ) {
                            Text(
                                text = "${entry.status.symbol}  ${entry.syncAction.symbol}",
                                color = palette.editorFg,
                                fontFamily = theme.editorFontFamily,
                                fontSize = theme.uiFontSizePt.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = if (entry.rightFile != null) entry.relativePath else "",
                            color = palette.editorFg,
                            fontFamily = theme.uiFontFamily,
                            fontSize = theme.uiFontSizePt.sp,
                            maxLines = 1,
                            modifier = Modifier.weight(0.38f).padding(start = 8.dp)
                        )
                        Text(
                            text = entry.rightFile?.size?.let { formatSize(it) } ?: "",
                            color = palette.editorFg,
                            fontFamily = theme.uiFontFamily,
                            fontSize = theme.uiFontSizePt.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(90.dp)
                        )
                    }
                }
            }
        }

        // Detail Embedded 2-Way Diff Preview (64% height)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.64f)
                .clipToBounds()
                .background(palette.editorBg)
        ) {
            val activePreview = previewSession
            if (activePreview != null) {
                ComposeTwoWayDiffView(session = activePreview)
            } else {
                val msg = when {
                    selectedEntry == null -> "Select a file pair above to view its diff"
                    selectedEntry?.isBinary == true -> "Binary file (${selectedEntry?.relativePath}) - cannot display text diff"
                    else -> "No preview available"
                }
                Text(
                    text = msg,
                    color = palette.gutterFg,
                    fontFamily = theme.uiFontFamily,
                    fontSize = theme.uiFontSizePt.sp,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}
