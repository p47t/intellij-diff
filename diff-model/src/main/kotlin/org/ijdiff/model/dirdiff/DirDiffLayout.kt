package org.ijdiff.model.dirdiff

/**
 * UI-toolkit-independent sizing rules for the master (file list) pane of the directory comparison view.
 *
 * The master pane is sized to fit its visible rows, so a short list leaves more room for the
 * embedded file diff preview below it. The pane never grows past [DEFAULT_MAX_FRACTION] of the
 * available height; longer lists scroll inside the pane.
 */
object DirDiffLayout {

    /** Largest share of the available height the master pane may take. */
    const val DEFAULT_MAX_FRACTION = 0.4f

    /** Rows the master pane always has room for, even when the list is empty or very short. */
    const val DEFAULT_MIN_VISIBLE_ROWS = 2

    /**
     * Returns the preferred master pane height, in the same unit as the inputs (pixels or dp).
     *
     * @param itemCount number of rows currently shown (after filters)
     * @param rowHeight height of one row
     * @param headerHeight height of the column header plus any fixed chrome (borders, insets)
     * @param availableHeight total height shared by the master pane and the detail pane
     * @param maxFraction largest share of [availableHeight] the master pane may take
     * @param minVisibleRows rows the pane always has room for
     */
    fun computeMasterPaneHeight(
        itemCount: Int,
        rowHeight: Float,
        headerHeight: Float,
        availableHeight: Float,
        maxFraction: Float = DEFAULT_MAX_FRACTION,
        minVisibleRows: Int = DEFAULT_MIN_VISIBLE_ROWS
    ): Float {
        if (availableHeight <= 0f) return 0f
        val rows = itemCount.coerceAtLeast(minVisibleRows.coerceAtLeast(0))
        val contentHeight = headerHeight + rows * rowHeight
        val maxHeight = availableHeight * maxFraction.coerceIn(0f, 1f)
        val minHeight = (headerHeight + minVisibleRows.coerceAtLeast(0) * rowHeight).coerceAtMost(maxHeight)
        return contentHeight.coerceIn(minHeight, maxHeight)
    }

    /** Integer (pixel) convenience overload of [computeMasterPaneHeight]. */
    fun computeMasterPaneHeight(
        itemCount: Int,
        rowHeight: Int,
        headerHeight: Int,
        availableHeight: Int,
        maxFraction: Float = DEFAULT_MAX_FRACTION,
        minVisibleRows: Int = DEFAULT_MIN_VISIBLE_ROWS
    ): Int = computeMasterPaneHeight(
        itemCount = itemCount,
        rowHeight = rowHeight.toFloat(),
        headerHeight = headerHeight.toFloat(),
        availableHeight = availableHeight.toFloat(),
        maxFraction = maxFraction,
        minVisibleRows = minVisibleRows
    ).toInt()
}
