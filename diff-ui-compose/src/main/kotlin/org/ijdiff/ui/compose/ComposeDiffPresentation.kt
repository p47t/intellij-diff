package org.ijdiff.ui.compose

import kotlinx.coroutines.flow.StateFlow
import org.ijdiff.core.DiffDividerGeometry
import org.ijdiff.core.TextDiffType
import org.ijdiff.core.getDiffType
import org.ijdiff.model.dirdiff.DirDiffEntry
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.twoway.ChunkDirection
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.model.twoway.TwoWayDiffState
import kotlin.math.max

/**
 * Phase 2 UI Bridge for Compose Multiplatform Desktop + JetBrains Jewel (`:diff-ui-compose`).
 *
 * Maps [TwoWayDiffSession] and [DirDiffSession] from `:diff-model` into immutable Compose-ready
 * presentation models and Bezier canvas path commands with zero Swing (`java.awt` / `javax.swing`) dependencies.
 */
class ComposeDiffPresentation(
    val session: TwoWayDiffSession
) {
    data class ComposeColorRgba(val argb: Long)

    data class ComposeBezierPolygon(
        val fragmentIndex: Int,
        val polygon: DiffDividerGeometry.DividerPolygon,
        val fillColor: ComposeColorRgba,
        val strokeColor: ComposeColorRgba,
        val isSelected: Boolean
    )

    val state: StateFlow<TwoWayDiffState>
        get() = session.state

    /**
     * Computes the list of Bezier polygons to draw inside a Compose `Canvas` divider of [dividerWidthPx]
     * given the current line height and left/right scroll offsets in pixels.
     */
    fun computeDividerPolygons(
        dividerWidthPx: Float,
        lineHeightPx: Float,
        leftScrollYPx: Float,
        rightScrollYPx: Float,
        isDarkTheme: Boolean = true
    ): List<ComposeBezierPolygon> {
        val currentState = session.state.value
        return currentState.fragments.mapIndexed { index, fragment ->
            val sy1 = fragment.startLine1 * lineHeightPx - leftScrollYPx
            val ey1 = max(sy1 + 2f, fragment.endLine1 * lineHeightPx - leftScrollYPx)
            val sy2 = fragment.startLine2 * lineHeightPx - rightScrollYPx
            val ey2 = max(sy2 + 2f, fragment.endLine2 * lineHeightPx - rightScrollYPx)
            val type = fragment.getDiffType()

            val geometry = DiffDividerGeometry.createPolygon(
                width = dividerWidthPx,
                startY1 = sy1,
                endY1 = ey1,
                startY2 = sy2,
                endY2 = ey2,
                type = type
            )

            ComposeBezierPolygon(
                fragmentIndex = index,
                polygon = geometry,
                fillColor = resolveFillColor(type, isDarkTheme),
                strokeColor = resolveStrokeColor(type, isDarkTheme),
                isSelected = (index == currentState.currentFragmentIndex)
            )
        }
    }

    fun onAcceptChunk(fragmentIndex: Int, direction: ChunkDirection, append: Boolean = false) {
        val fragment = session.state.value.fragments.getOrNull(fragmentIndex) ?: return
        if (append) {
            session.appendChunk(fragment, direction)
        } else {
            session.replaceChunk(fragment, direction)
        }
    }

    private fun resolveFillColor(type: TextDiffType, isDark: Boolean): ComposeColorRgba {
        return if (isDark) {
            when (type) {
                TextDiffType.INSERTED -> ComposeColorRgba(0xFF233B29)
                TextDiffType.DELETED -> ComposeColorRgba(0xFF402929)
                TextDiffType.MODIFIED, TextDiffType.CONFLICT -> ComposeColorRgba(0xFF24344D)
            }
        } else {
            when (type) {
                TextDiffType.INSERTED -> ComposeColorRgba(0xFFE6F6E6)
                TextDiffType.DELETED -> ComposeColorRgba(0xFFFCE8E8)
                TextDiffType.MODIFIED, TextDiffType.CONFLICT -> ComposeColorRgba(0xFFE8F0FE)
            }
        }
    }

    private fun resolveStrokeColor(type: TextDiffType, isDark: Boolean): ComposeColorRgba {
        return if (isDark) {
            when (type) {
                TextDiffType.INSERTED -> ComposeColorRgba(0xFF437A4F)
                TextDiffType.DELETED -> ComposeColorRgba(0xFF8C4646)
                TextDiffType.MODIFIED, TextDiffType.CONFLICT -> ComposeColorRgba(0xFF466DA6)
            }
        } else {
            when (type) {
                TextDiffType.INSERTED -> ComposeColorRgba(0xFF7EC47E)
                TextDiffType.DELETED -> ComposeColorRgba(0xFFE08B8B)
                TextDiffType.MODIFIED, TextDiffType.CONFLICT -> ComposeColorRgba(0xFF8AB4F8)
            }
        }
    }
}

class ComposeDirDiffPresentation(
    val dirSession: DirDiffSession
) {
    val entries: StateFlow<List<DirDiffEntry>>
        get() = dirSession.filteredEntries

    val previewPresentation: ComposeDiffPresentation?
        get() = dirSession.previewSession.value?.let { ComposeDiffPresentation(it) }
}
