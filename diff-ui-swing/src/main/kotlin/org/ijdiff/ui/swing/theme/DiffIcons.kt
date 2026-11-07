package org.ijdiff.ui.swing.theme

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Path2D
import javax.swing.Icon
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Resolution-independent Java2D vector icons for UI toolbars, tabs, and gutters.
 * Avoids relying on Unicode symbol glyphs (`↶`, `⟳`, `↑`, `↓`, `📁`, `×`) that may render as
 * missing-glyph boxes (`□`) depending on the active system or custom UI font, and scales
 * proportionally with the active UI and editor font sizes.
 */
object DiffIcons {

    private fun iconColor(): Color = DiffThemeManager.palette.editorFg

    private fun scaledSize(baseAt13Pt: Int): Int {
        val uiSize = DiffThemeManager.uiFontSpec.value.size
        return ((baseAt13Pt * uiSize) / 13.0).roundToInt().coerceAtLeast(baseAt13Pt)
    }

    fun arrowUp(size: Int = 0): Icon = object : Icon {
        private fun resolvedSize(): Int = if (size > 0) size else scaledSize(13)
        override fun getIconWidth(): Int = resolvedSize()
        override fun getIconHeight(): Int = resolvedSize()
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                val s = resolvedSize().toDouble()
                val strokeW = (s * 0.13).toFloat().coerceAtLeast(1.5f)
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = c?.foreground ?: iconColor()
                g2.stroke = BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val cx = x + s * 0.5
                val top = y + s * 0.15
                val bottom = y + s * 0.85
                val wingX = s * 0.30
                val wingY = y + s * 0.45
                val path = Path2D.Double().apply {
                    moveTo(cx, bottom)
                    lineTo(cx, top)
                    moveTo(cx - wingX, wingY)
                    lineTo(cx, top)
                    lineTo(cx + wingX, wingY)
                }
                g2.draw(path)
            } finally {
                g2.dispose()
            }
        }
    }

    fun arrowDown(size: Int = 0): Icon = object : Icon {
        private fun resolvedSize(): Int = if (size > 0) size else scaledSize(13)
        override fun getIconWidth(): Int = resolvedSize()
        override fun getIconHeight(): Int = resolvedSize()
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                val s = resolvedSize().toDouble()
                val strokeW = (s * 0.13).toFloat().coerceAtLeast(1.5f)
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = c?.foreground ?: iconColor()
                g2.stroke = BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val cx = x + s * 0.5
                val top = y + s * 0.15
                val bottom = y + s * 0.85
                val wingX = s * 0.30
                val wingY = y + s * 0.55
                val path = Path2D.Double().apply {
                    moveTo(cx, top)
                    lineTo(cx, bottom)
                    moveTo(cx - wingX, wingY)
                    lineTo(cx, bottom)
                    lineTo(cx + wingX, wingY)
                }
                g2.draw(path)
            } finally {
                g2.dispose()
            }
        }
    }

    fun undo(size: Int = 0): Icon = object : Icon {
        private fun resolvedSize(): Int = if (size > 0) size else scaledSize(13)
        override fun getIconWidth(): Int = resolvedSize()
        override fun getIconHeight(): Int = resolvedSize()
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                val s = resolvedSize().toDouble()
                val strokeW = (s * 0.125).toFloat().coerceAtLeast(1.5f)
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val baseColor = c?.foreground ?: iconColor()
                g2.color = if (c?.isEnabled == false) {
                    Color(baseColor.red, baseColor.green, baseColor.blue, 100)
                } else {
                    baseColor
                }
                g2.stroke = BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val pad = s * 0.16
                val arc = Arc2D.Double(x + pad, y + pad * 1.25, s - pad * 2.0, s - pad * 2.0, 180.0, -230.0, Arc2D.OPEN)
                g2.draw(arc)
                val headSize = s * 0.30
                val headTop = y + pad * 1.35
                val head = Path2D.Double().apply {
                    moveTo(x + pad, headTop)
                    lineTo(x + pad, headTop + headSize)
                    lineTo(x + pad + headSize, headTop + headSize)
                }
                g2.draw(head)
            } finally {
                g2.dispose()
            }
        }
    }

    fun refresh(size: Int = 0): Icon = object : Icon {
        private fun resolvedSize(): Int = if (size > 0) size else scaledSize(13)
        override fun getIconWidth(): Int = resolvedSize()
        override fun getIconHeight(): Int = resolvedSize()
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                val s = resolvedSize().toDouble()
                val strokeW = (s * 0.125).toFloat().coerceAtLeast(1.5f)
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = c?.foreground ?: iconColor()
                g2.stroke = BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val pad = s * 0.16
                val arc = Arc2D.Double(x + pad, y + pad, s - pad * 2.0, s - pad * 2.0, 45.0, 270.0, Arc2D.OPEN)
                g2.draw(arc)
                val headSize = s * 0.30
                val rx = x + s - pad * 1.2
                val head = Path2D.Double().apply {
                    moveTo(rx, y + pad)
                    lineTo(rx, y + pad + headSize)
                    lineTo(rx - headSize, y + pad + headSize)
                }
                g2.draw(head)
            } finally {
                g2.dispose()
            }
        }
    }

    fun folder(size: Int = 0): Icon = object : Icon {
        private fun resolvedSize(): Int = if (size > 0) size else scaledSize(14)
        override fun getIconWidth(): Int = resolvedSize()
        override fun getIconHeight(): Int = resolvedSize()
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                val s = resolvedSize().toDouble()
                val strokeW = (s * 0.12).toFloat().coerceAtLeast(1.4f)
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = DiffThemeManager.palette.modifiedBorder
                g2.stroke = BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val padX = s * 0.10
                val topTabY = y + s * 0.20
                val bodyTopY = y + s * 0.34
                val bottomY = y + s * 0.84
                val path = Path2D.Double().apply {
                    moveTo(x + padX, topTabY)
                    lineTo(x + s * 0.42, topTabY)
                    lineTo(x + s * 0.54, bodyTopY)
                    lineTo(x + s - padX, bodyTopY)
                    lineTo(x + s - padX, bottomY)
                    lineTo(x + padX, bottomY)
                    closePath()
                }
                g2.draw(path)
            } finally {
                g2.dispose()
            }
        }
    }

    fun closeTab(size: Int = 0): Icon = object : Icon {
        private fun resolvedSize(): Int = if (size > 0) size else scaledSize(11)
        override fun getIconWidth(): Int = resolvedSize()
        override fun getIconHeight(): Int = resolvedSize()
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                val s = resolvedSize().toDouble()
                val strokeW = (s * 0.13).toFloat().coerceAtLeast(1.5f)
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = c?.foreground ?: iconColor()
                g2.stroke = BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val pad = s * 0.20
                val path = Path2D.Double().apply {
                    moveTo(x + pad, y + pad)
                    lineTo(x + s - pad, y + s - pad)
                    moveTo(x + s - pad, y + pad)
                    lineTo(x + pad, y + s - pad)
                }
                g2.draw(path)
            } finally {
                g2.dispose()
            }
        }
    }

    /**
     * Paints a crisp double-chevron `>>` or `<<` inside the editor gutter chunk button,
     * scaling proportionally with the button's [width] and [height].
     */
    fun paintDoubleChevron(
        g2: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        pointRight: Boolean,
        color: Color
    ) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val oldStroke = g2.stroke
        val minDim = min(width, height).toDouble().coerceAtLeast(12.0)
        val strokeW = (minDim * 0.11).toFloat().coerceAtLeast(1.6f)
        g2.color = color
        g2.stroke = BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val cy = y + height / 2.0
        val cx = x + width / 2.0
        val dy = (minDim * 0.27).coerceAtLeast(3.2)
        val dx = (width * 0.16).coerceAtLeast(2.6)
        val gap = (width * 0.12).coerceAtLeast(2.2)
        val path = Path2D.Double()
        if (pointRight) {
            // First chevron >
            path.moveTo(cx - gap - dx, cy - dy)
            path.lineTo(cx - gap, cy)
            path.lineTo(cx - gap - dx, cy + dy)
            // Second chevron >
            path.moveTo(cx + gap - dx, cy - dy)
            path.lineTo(cx + gap, cy)
            path.lineTo(cx + gap - dx, cy + dy)
        } else {
            // First chevron <
            path.moveTo(cx - gap + dx, cy - dy)
            path.lineTo(cx - gap, cy)
            path.lineTo(cx - gap + dx, cy + dy)
            // Second chevron <
            path.moveTo(cx + gap + dx, cy - dy)
            path.lineTo(cx + gap, cy)
            path.lineTo(cx + gap + dx, cy + dy)
        }
        g2.draw(path)
        g2.stroke = oldStroke
    }
}
