package org.ijdiff.ui.swing.theme

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon

/**
 * Resolution-independent Java2D vector icons for UI toolbars, tabs, and gutters.
 * Avoids relying on Unicode symbol glyphs (`↶`, `⟳`, `↑`, `↓`, `📁`, `×`) that may render as
 * missing-glyph boxes (`□`) depending on the active system or custom UI font.
 */
object DiffIcons {

    private fun iconColor(): Color = DiffThemeManager.palette.editorFg

    fun arrowUp(size: Int = 12): Icon = object : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = c?.foreground ?: iconColor()
                g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val cx = x + size / 2.0
                val top = y + 2.0
                val bottom = y + size - 2.0
                val path = Path2D.Double().apply {
                    moveTo(cx, bottom)
                    lineTo(cx, top)
                    moveTo(cx - 3.5, top + 3.5)
                    lineTo(cx, top)
                    lineTo(cx + 3.5, top + 3.5)
                }
                g2.draw(path)
            } finally {
                g2.dispose()
            }
        }
    }

    fun arrowDown(size: Int = 12): Icon = object : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = c?.foreground ?: iconColor()
                g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val cx = x + size / 2.0
                val top = y + 2.0
                val bottom = y + size - 2.0
                val path = Path2D.Double().apply {
                    moveTo(cx, top)
                    lineTo(cx, bottom)
                    moveTo(cx - 3.5, bottom - 3.5)
                    lineTo(cx, bottom)
                    lineTo(cx + 3.5, bottom - 3.5)
                }
                g2.draw(path)
            } finally {
                g2.dispose()
            }
        }
    }

    fun undo(size: Int = 12): Icon = object : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val baseColor = c?.foreground ?: iconColor()
                g2.color = if (c?.isEnabled == false) {
                    Color(baseColor.red, baseColor.green, baseColor.blue, 100)
                } else {
                    baseColor
                }
                g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val arc = Arc2D.Double(x + 2.0, y + 3.0, size - 4.0, size - 4.0, 180.0, -230.0, Arc2D.OPEN)
                g2.draw(arc)
                val head = Path2D.Double().apply {
                    moveTo(x + 2.0, y + 4.0)
                    lineTo(x + 2.0, y + 7.5)
                    lineTo(x + 5.5, y + 7.5)
                }
                g2.draw(head)
            } finally {
                g2.dispose()
            }
        }
    }

    fun refresh(size: Int = 12): Icon = object : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = c?.foreground ?: iconColor()
                g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val arc = Arc2D.Double(x + 2.0, y + 2.0, size - 4.0, size - 4.0, 45.0, 270.0, Arc2D.OPEN)
                g2.draw(arc)
                val head = Path2D.Double().apply {
                    moveTo(x + size - 2.5, y + 2.0)
                    lineTo(x + size - 2.5, y + 5.5)
                    lineTo(x + size - 6.0, y + 5.5)
                }
                g2.draw(head)
            } finally {
                g2.dispose()
            }
        }
    }

    fun folder(size: Int = 13): Icon = object : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = DiffThemeManager.palette.modifiedBorder
                g2.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val path = Path2D.Double().apply {
                    moveTo(x + 1.5, y + 3.0)
                    lineTo(x + 5.5, y + 3.0)
                    lineTo(x + 7.0, y + 4.5)
                    lineTo(x + size - 1.5, y + 4.5)
                    lineTo(x + size - 1.5, y + size - 2.0)
                    lineTo(x + 1.5, y + size - 2.0)
                    closePath()
                }
                g2.draw(path)
            } finally {
                g2.dispose()
            }
        }
    }

    fun closeTab(size: Int = 10): Icon = object : Icon {
        override fun getIconWidth(): Int = size
        override fun getIconHeight(): Int = size
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2.color = c?.foreground ?: iconColor()
                g2.stroke = BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                val pad = 2.0
                val path = Path2D.Double().apply {
                    moveTo(x + pad, y + pad)
                    lineTo(x + size - pad, y + size - pad)
                    moveTo(x + size - pad, y + pad)
                    lineTo(x + pad, y + size - pad)
                }
                g2.draw(path)
            } finally {
                g2.dispose()
            }
        }
    }

    /**
     * Paints a crisp double-chevron `>>` or `<<` inside the editor gutter chunk button.
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
        g2.color = color
        g2.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val cy = y + height / 2.0
        val cx = x + width / 2.0
        val dy = 3.2
        val dx = 2.6
        val gap = 2.2
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
