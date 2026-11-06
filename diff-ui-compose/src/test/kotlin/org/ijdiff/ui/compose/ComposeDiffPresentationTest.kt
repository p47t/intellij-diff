package org.ijdiff.ui.compose

import org.ijdiff.core.TextDiffType
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.ChunkDirection
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComposeDiffPresentationTest {

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
}
