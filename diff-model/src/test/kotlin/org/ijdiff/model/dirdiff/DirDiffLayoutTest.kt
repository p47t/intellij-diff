package org.ijdiff.model.dirdiff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DirDiffLayoutTest {

    @Test
    fun `short list shrinks master pane to fit its rows`() {
        val h = DirDiffLayout.computeMasterPaneHeight(
            itemCount = 3, rowHeight = 24, headerHeight = 28, availableHeight = 1000
        )
        assertEquals(28 + 3 * 24, h)
    }

    @Test
    fun `long list is capped at max fraction of available height`() {
        val h = DirDiffLayout.computeMasterPaneHeight(
            itemCount = 500, rowHeight = 24, headerHeight = 28, availableHeight = 1000, maxFraction = 0.4f
        )
        assertEquals(400, h)
    }

    @Test
    fun `empty list keeps room for minimum visible rows`() {
        val h = DirDiffLayout.computeMasterPaneHeight(
            itemCount = 0, rowHeight = 24, headerHeight = 28, availableHeight = 1000, minVisibleRows = 2
        )
        assertEquals(28 + 2 * 24, h)
    }

    @Test
    fun `tiny window never exceeds max fraction even for minimum rows`() {
        val h = DirDiffLayout.computeMasterPaneHeight(
            itemCount = 1, rowHeight = 24, headerHeight = 28, availableHeight = 100, maxFraction = 0.4f
        )
        assertEquals(40, h)
    }

    @Test
    fun `non-positive available height yields zero`() {
        assertEquals(0, DirDiffLayout.computeMasterPaneHeight(5, 24, 28, 0))
    }
}
