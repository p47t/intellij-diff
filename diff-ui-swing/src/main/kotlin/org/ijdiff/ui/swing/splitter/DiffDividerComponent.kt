package org.ijdiff.ui.swing.splitter

import com.intellij.diff.fragments.LineFragment
import org.ijdiff.core.DiffDividerGeometry
import org.ijdiff.core.getDiffType
import org.ijdiff.ui.swing.editor.DiffEditorPane
import org.ijdiff.ui.swing.theme.DiffThemeManager
import java.awt.BasicStroke
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Path2D
import javax.swing.JComponent
import kotlin.math.max

/**
 * Center 36px splitter component between the Left and Right [DiffEditorPane]s.
 * Paints smooth cubic Bezier polygons connecting each [LineFragment] across both scroll positions
 * using [DiffDividerGeometry] (ported from IntelliJ's `DiffDividerDrawUtil`).
 */
class DiffDividerComponent(
    private val leftPane: DiffEditorPane,
    private val rightPane: DiffEditorPane,
    private val onFragmentClicked: (Int) -> Unit
) : JComponent() {

    private var fragments: List<LineFragment> = emptyList()
    private var activeFragmentIndex: Int = -1

    init {
        isOpaque = true
        preferredSize = Dimension(36, 100)
        minimumSize = Dimension(32, 50)

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val idx = findPolygonAtY(e.y.toFloat())
                if (idx >= 0) {
                    onFragmentClicked(idx)
                }
            }
        })
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val idx = findPolygonAtY(e.y.toFloat())
                cursor = if (idx >= 0) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
            }
        })
    }

    fun updateState(newFragments: List<LineFragment>, currentFragmentIdx: Int) {
        fragments = newFragments
        activeFragmentIndex = currentFragmentIdx
        repaint()
    }

    private fun findPolygonAtY(y: Float): Int {
        if (y !in 0f..height.toFloat()) return -1
        for ((index, fragment) in fragments.withIndex()) {
            val (sy1, ey1) = leftPane.getViewportYRange(fragment.startLine1, fragment.endLine1)
            val (sy2, ey2) = rightPane.getViewportYRange(fragment.startLine2, fragment.endLine2)
            val minY = minOf(sy1, sy2) - 4f
            val maxY = maxOf(ey1, ey2) + 4f
            if (y in minY..maxY) return index
        }
        return -1
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val palette = DiffThemeManager.palette
            val w = width.toFloat()

            g2.clipRect(0, 0, width, height)
            g2.color = palette.dividerBg
            g2.fillRect(0, 0, width, height)

            // Subtle vertical side borders
            g2.color = palette.gutterBorder
            g2.stroke = BasicStroke(1f)
            g2.drawLine(0, 0, 0, height)
            g2.drawLine(width - 1, 0, width - 1, height)

            val viewportHeight = max(
                leftPane.scrollPane.viewport.height,
                rightPane.scrollPane.viewport.height
            ).takeIf { it > 0 }?.coerceAtMost(height) ?: height
            val h = viewportHeight.toFloat()
            g2.clipRect(0, 0, width, viewportHeight)

            for ((index, fragment) in fragments.withIndex()) {
                val (sy1, ey1) = leftPane.getViewportYRange(fragment.startLine1, fragment.endLine1)
                val (sy2, ey2) = rightPane.getViewportYRange(fragment.startLine2, fragment.endLine2)

                if (maxOf(ey1, ey2) < -20f || minOf(sy1, sy2) > h + 20f) continue

                val type = fragment.getDiffType()
                val poly = DiffDividerGeometry.createPolygon(
                    width = w,
                    startY1 = sy1,
                    endY1 = max(sy1 + 2f, ey1),
                    startY2 = sy2,
                    endY2 = max(sy2 + 2f, ey2),
                    type = type
                )

                val path = Path2D.Float()
                path.moveTo(poly.topCurve.startX, poly.topCurve.startY)
                path.curveTo(
                    poly.topCurve.ctrl1X, poly.topCurve.ctrl1Y,
                    poly.topCurve.ctrl2X, poly.topCurve.ctrl2Y,
                    poly.topCurve.endX, poly.topCurve.endY
                )
                path.lineTo(poly.bottomCurve.startX, poly.bottomCurve.startY)
                path.curveTo(
                    poly.bottomCurve.ctrl1X, poly.bottomCurve.ctrl1Y,
                    poly.bottomCurve.ctrl2X, poly.bottomCurve.ctrl2Y,
                    poly.bottomCurve.endX, poly.bottomCurve.endY
                )
                path.closePath()

                g2.color = palette.getLineBg(type)
                g2.fill(path)

                // Draw top and bottom Bezier borders
                val isActive = (index == activeFragmentIndex)
                g2.color = if (isActive) palette.activeFragmentBorder else palette.getBorderColor(type)
                g2.stroke = BasicStroke(if (isActive) 1.6f else 1.0f)

                val topEdge = Path2D.Float()
                topEdge.moveTo(poly.topCurve.startX, poly.topCurve.startY)
                topEdge.curveTo(
                    poly.topCurve.ctrl1X, poly.topCurve.ctrl1Y,
                    poly.topCurve.ctrl2X, poly.topCurve.ctrl2Y,
                    poly.topCurve.endX, poly.topCurve.endY
                )
                g2.draw(topEdge)

                val bottomEdge = Path2D.Float()
                bottomEdge.moveTo(poly.bottomCurve.startX, poly.bottomCurve.startY)
                bottomEdge.curveTo(
                    poly.bottomCurve.ctrl1X, poly.bottomCurve.ctrl1Y,
                    poly.bottomCurve.ctrl2X, poly.bottomCurve.ctrl2Y,
                    poly.bottomCurve.endX, poly.bottomCurve.endY
                )
                g2.draw(bottomEdge)
            }
        } finally {
            g2.dispose()
        }
    }
}

/**
 * Right-side vertical overview bar (minimap stripe) displaying proportional diff ticks across the entire file.
 */
class DiffOverviewBar(
    private val onLineClicked: (Int) -> Unit
) : JComponent() {

    private var fragments: List<LineFragment> = emptyList()
    private var totalLines: Int = 1

    init {
        preferredSize = Dimension(14, 100)
        minimumSize = Dimension(12, 50)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)

        addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (height <= 0 || totalLines <= 0) return
                val targetLine = ((e.y.toDouble() / height.toDouble()) * totalLines).toInt().coerceIn(0, totalLines - 1)
                onLineClicked(targetLine)
            }
        })
    }

    fun updateState(newFragments: List<LineFragment>, maxLineCount: Int) {
        fragments = newFragments
        totalLines = maxLineCount.coerceAtLeast(1)
        repaint()
    }

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            val palette = DiffThemeManager.palette
            g2.color = palette.gutterBg
            g2.fillRect(0, 0, width, height)

            val h = height.toFloat().coerceAtLeast(1f)
            val linesF = totalLines.toFloat().coerceAtLeast(1f)

            for (fragment in fragments) {
                val startLine = max(fragment.startLine1, fragment.startLine2)
                val endLine = max(fragment.endLine1, fragment.endLine2)
                val y = ((startLine / linesF) * h).toInt()
                val barH = max(3, (((endLine - startLine).coerceAtLeast(1) / linesF) * h).toInt())
                g2.color = palette.getBorderColor(fragment.getDiffType())
                g2.fillRect(2, y, width - 4, barH)
            }

            g2.color = palette.gutterBorder
            g2.drawLine(0, 0, 0, height)
        } finally {
            g2.dispose()
        }
    }
}
