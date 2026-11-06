package org.ijdiff.ui.swing.twoway

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.ijdiff.core.TextDiffType
import org.ijdiff.core.UnifiedFragmentBuilder
import org.ijdiff.ui.swing.editor.SyntaxStyleResolver
import org.ijdiff.ui.swing.theme.DiffThemeManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.math.max

/**
 * Single-pane Unified Diff viewer backed by [UnifiedFragmentBuilder.UnifiedDiffResult].
 * Renders dual left/right line-number columns in the gutter alongside red/green line blocks and inline word highlights.
 */
class UnifiedDiffPane(
    fileTitle: String
) : JPanel(BorderLayout()) {

    private var unifiedResult: UnifiedFragmentBuilder.UnifiedDiffResult? = null

    val textArea: RSyntaxTextArea = object : RSyntaxTextArea() {
        private val fallbackGraphicsImage = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB)

        init {
            isOpaque = false
        }

        override fun getGraphics(): Graphics {
            return super.getGraphics() ?: fallbackGraphicsImage.createGraphics()
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                val palette = DiffThemeManager.palette
                g2.color = palette.editorBg
                g2.fillRect(0, 0, width, height)
                paintUnifiedHighlights(g2)
                super.paintComponent(g2)
            } finally {
                g2.dispose()
            }
        }
    }

    private val gutter = UnifiedGutterComponent()
    val scrollPane = JScrollPane(textArea)

    init {
        textArea.font = DiffThemeManager.editorFont
        textArea.tabSize = 4
        textArea.tabsEmulated = true
        textArea.isEditable = false
        textArea.syntaxEditingStyle = SyntaxStyleResolver.resolveSyntaxStyle(fileTitle)
        textArea.isCodeFoldingEnabled = false
        textArea.antiAliasingEnabled = true

        applyTheme()

        scrollPane.setRowHeaderView(gutter)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.verticalScrollBar.unitIncrement = 16
        scrollPane.viewport.addChangeListener { gutter.repaint() }

        add(scrollPane, BorderLayout.CENTER)
    }

    fun applyTheme() {
        val palette = DiffThemeManager.palette
        SyntaxStyleResolver.applyPaletteToSyntaxArea(textArea, palette)
        scrollPane.viewport.background = palette.editorBg
        gutter.revalidate()
        gutter.repaint()
        textArea.repaint()
    }

    fun updateUnifiedResult(result: UnifiedFragmentBuilder.UnifiedDiffResult) {
        unifiedResult = result
        if (textArea.text != result.text) {
            textArea.text = result.text
            textArea.caretPosition = 0
        }
        gutter.revalidate()
        gutter.repaint()
        textArea.repaint()
    }

    fun scrollToUnifiedBlock(blockIndex: Int) {
        val result = unifiedResult ?: return
        if (blockIndex !in result.blocks.indices) return
        val line = result.blocks[blockIndex].startLine
        val lineHeight = textArea.lineHeight.coerceAtLeast(16)
        val viewportHeight = scrollPane.viewport.extentSize.height
        val targetY = (line * lineHeight - viewportHeight / 3).coerceAtLeast(0)
        val maxScroll = (textArea.height - viewportHeight).coerceAtLeast(0)
        scrollPane.viewport.viewPosition = java.awt.Point(0, targetY.coerceAtMost(maxScroll))
    }

    private fun paintUnifiedHighlights(g2: Graphics2D) {
        val result = unifiedResult ?: return
        val palette = DiffThemeManager.palette
        val lineHeight = textArea.lineHeight.coerceAtLeast(16)
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        for (block in result.blocks) {
            if (!block.deletedRangeLines.isEmpty()) {
                val y1 = block.deletedRangeLines.first * lineHeight
                val y2 = (block.deletedRangeLines.last + 1) * lineHeight
                g2.color = palette.deletedLineBg
                g2.fillRect(0, y1, textArea.width, y2 - y1)
            }
            if (!block.insertedRangeLines.isEmpty()) {
                val y1 = block.insertedRangeLines.first * lineHeight
                val y2 = (block.insertedRangeLines.last + 1) * lineHeight
                g2.color = palette.insertedLineBg
                g2.fillRect(0, y1, textArea.width, y2 - y1)
            }

            for (inline in block.innerHighlights) {
                val s = inline.range.startOffset.coerceIn(0, textArea.document.length)
                val e = inline.range.endOffset.coerceIn(0, textArea.document.length)
                if (s < e) {
                    g2.color = palette.getWordBg(inline.type)
                    runCatching {
                        val r1 = textArea.modelToView2D(s).bounds
                        val r2 = textArea.modelToView2D(e).bounds
                        if (r1.y == r2.y) {
                            g2.fillRoundRect(r1.x, r1.y, (r2.x - r1.x).coerceAtLeast(4), lineHeight, 4, 4)
                        }
                    }
                }
            }
        }
    }

    private inner class UnifiedGutterComponent : JComponent() {
        override fun getPreferredSize(): Dimension {
            val digits = max(3, textArea.lineCount.toString().length)
            val fm = getFontMetrics(DiffThemeManager.editorFont)
            val colWidth = digits * fm.charWidth('9') + 12
            return Dimension(colWidth * 2 + 16, textArea.preferredSize.height)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                val palette = DiffThemeManager.palette
                val clip = g2.clipBounds ?: Rectangle(0, 0, width, height)

                g2.color = palette.gutterBg
                g2.fillRect(clip.x, clip.y, clip.width, clip.height)

                val result = unifiedResult ?: return
                val lineHeight = textArea.lineHeight.coerceAtLeast(16)
                g2.font = DiffThemeManager.editorFont
                val fm = g2.fontMetrics
                val colWidth = (width - 16) / 2

                val firstLine = (clip.y / lineHeight).coerceAtLeast(0)
                val lastLine = ((clip.y + clip.height) / lineHeight + 1).coerceAtMost(result.lineMappings.lastIndex)

                for (uLine in firstLine..lastLine) {
                    val mapping = result.lineMappings[uLine]
                    val yTop = uLine * lineHeight
                    if (mapping.type != null) {
                        g2.color = palette.getLineBg(mapping.type!!)
                        g2.fillRect(0, yTop, width, lineHeight)
                    }

                    val textY = yTop + (lineHeight + fm.ascent - fm.descent) / 2
                    g2.color = palette.gutterFg

                    if (mapping.leftLine >= 0) {
                        val s = (mapping.leftLine + 1).toString()
                        g2.drawString(s, 4 + (colWidth - fm.stringWidth(s)).coerceAtLeast(0), textY)
                    }
                    if (mapping.rightLine >= 0) {
                        val s = (mapping.rightLine + 1).toString()
                        g2.drawString(s, 8 + colWidth + (colWidth - fm.stringWidth(s)).coerceAtLeast(0), textY)
                    }
                    if (mapping.type != null) {
                        val sign = if (mapping.type == TextDiffType.DELETED) "-" else "+"
                        g2.color = palette.getBorderColor(mapping.type!!).darker()
                        g2.drawString(sign, width - 11, textY)
                    }
                }

                g2.color = palette.gutterBorder
                g2.drawLine(colWidth + 6, clip.y, colWidth + 6, clip.y + clip.height)
                g2.drawLine(width - 1, clip.y, width - 1, clip.y + clip.height)
            } finally {
                g2.dispose()
            }
        }
    }
}
