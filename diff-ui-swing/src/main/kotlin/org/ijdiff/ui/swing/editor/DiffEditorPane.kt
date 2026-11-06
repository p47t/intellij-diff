package org.ijdiff.ui.swing.editor

import com.intellij.diff.fragments.LineFragment
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.ijdiff.core.TextDiffType
import org.ijdiff.core.getDiffType
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.ui.swing.theme.DiffIcons
import org.ijdiff.ui.swing.theme.DiffThemeManager
import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.math.max

enum class EditorSide {
    LEFT,
    RIGHT
}

/**
 * Lightweight Swing code editor pane backed by [RSyntaxTextArea] with multi-tier diff background painting
 * (full-width line blocks + inline word highlights) and an interactive gutter with line numbers and `>>` / `<<` buttons.
 */
class DiffEditorPane(
    val side: EditorSide,
    val document: DiffDocument,
    private val canTargetAcceptChunks: () -> Boolean,
    private val onChunkAction: (fragment: LineFragment, append: Boolean) -> Unit,
    private val onDocumentEdited: () -> Unit
) : JPanel(BorderLayout()) {

    private var fragments: List<LineFragment> = emptyList()
    private var activeFragmentIndex: Int = -1
    private var suppressDocumentEvents = false

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
                paintDiffHighlights(g2)
                super.paintComponent(g2)
            } finally {
                g2.dispose()
            }
        }
    }

    private val gutterComponent = DiffGutterComponent()
    val scrollPane: JScrollPane = JScrollPane(textArea)

    init {
        textArea.font = DiffThemeManager.editorFont
        textArea.tabSize = 4
        textArea.tabsEmulated = true
        textArea.isEditable = !document.isReadOnly
        textArea.syntaxEditingStyle = SyntaxStyleResolver.resolveSyntaxStyle(document.title)
        textArea.isCodeFoldingEnabled = false
        textArea.antiAliasingEnabled = true

        suppressDocumentEvents = true
        textArea.text = document.text.value
        textArea.caretPosition = 0
        suppressDocumentEvents = false

        applyTheme()

        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = handleUserEdit()
            override fun removeUpdate(e: DocumentEvent) = handleUserEdit()
            override fun changedUpdate(e: DocumentEvent) = Unit
        })

        scrollPane.setRowHeaderView(gutterComponent)
        scrollPane.border = BorderFactory.createEmptyBorder()
        scrollPane.verticalScrollBar.unitIncrement = 16
        scrollPane.viewport.addChangeListener {
            gutterComponent.repaint()
        }

        add(scrollPane, BorderLayout.CENTER)
    }

    fun applyTheme() {
        val palette = DiffThemeManager.palette
        SyntaxStyleResolver.applyPaletteToSyntaxArea(textArea, palette)
        scrollPane.viewport.background = palette.editorBg
        gutterComponent.revalidate()
        gutterComponent.repaint()
        textArea.repaint()
    }

    fun syncFromDocumentIfChanged() {
        val modelText = document.text.value
        if (textArea.text != modelText) {
            val prevCaret = textArea.caretPosition
            suppressDocumentEvents = true
            try {
                textArea.text = modelText
                textArea.caretPosition = prevCaret.coerceIn(0, modelText.length)
            } finally {
                suppressDocumentEvents = false
            }
            gutterComponent.revalidate()
            gutterComponent.repaint()
        }
    }

    fun updateFragments(newFragments: List<LineFragment>, currentFragmentIdx: Int) {
        fragments = newFragments
        activeFragmentIndex = currentFragmentIdx
        gutterComponent.revalidate()
        gutterComponent.repaint()
        textArea.repaint()
    }

    /**
     * Computes the `[startY, endY]` pixel coordinates in the scrollPane viewport space for `[startLine, endLine)`.
     */
    fun getViewportYRange(startLine: Int, endLine: Int): Pair<Float, Float> {
        val lineHeight = textArea.lineHeight.coerceAtLeast(16)
        val scrollY = scrollPane.viewport.viewPosition.y
        val topY = (startLine * lineHeight - scrollY).toFloat()
        val bottomY = (endLine * lineHeight - scrollY).toFloat()
        return topY to bottomY
    }

    fun scrollToLine(line: Int) {
        val lineHeight = textArea.lineHeight.coerceAtLeast(16)
        val viewportHeight = scrollPane.viewport.extentSize.height
        val targetY = (line * lineHeight - viewportHeight / 3).coerceAtLeast(0)
        val maxScroll = (textArea.height - viewportHeight).coerceAtLeast(0)
        val currentPos = scrollPane.viewport.viewPosition
        scrollPane.viewport.viewPosition = java.awt.Point(currentPos.x, targetY.coerceAtMost(maxScroll))
    }

    private fun handleUserEdit() {
        if (suppressDocumentEvents) return
        document.setText(textArea.text, recordUndo = true)
        gutterComponent.revalidate()
        gutterComponent.repaint()
        onDocumentEdited()
    }

    private fun paintDiffHighlights(g2: Graphics2D) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val palette = DiffThemeManager.palette
        val lineHeight = textArea.lineHeight.coerceAtLeast(16)
        val clip: Rectangle = g2.clipBounds ?: Rectangle(0, 0, textArea.width, textArea.height)

        for ((index, fragment) in fragments.withIndex()) {
            val startLine = if (side == EditorSide.LEFT) fragment.startLine1 else fragment.startLine2
            val endLine = if (side == EditorSide.LEFT) fragment.endLine1 else fragment.endLine2
            val diffType = fragment.getDiffType()
            val y1 = startLine * lineHeight
            val y2 = endLine * lineHeight

            if (y2 < clip.y - lineHeight || y1 > clip.y + clip.height + lineHeight) continue

            if (startLine == endLine) {
                // Empty range on this side (pure insertion on the other side): draw a 2px marker line
                g2.color = palette.getBorderColor(diffType)
                val lineY = y1.coerceAtLeast(1)
                g2.fillRect(0, lineY - 1, textArea.width, 2)
            } else {
                // Full-width line block background
                g2.color = palette.getLineBg(diffType)
                g2.fillRect(0, y1, textArea.width, y2 - y1)

                // Paint inline word/character DiffFragment highlights
                val inner = fragment.innerFragments
                if (inner != null) {
                    val baseOffset = if (side == EditorSide.LEFT) fragment.startOffset1 else fragment.startOffset2
                    g2.color = palette.getWordBg(diffType)
                    for (df in inner) {
                        val relStart = if (side == EditorSide.LEFT) df.startOffset1 else df.startOffset2
                        val relEnd = if (side == EditorSide.LEFT) df.endOffset1 else df.endOffset2
                        val absStart = (baseOffset + relStart).coerceIn(0, textArea.document.length)
                        val absEnd = (baseOffset + relEnd).coerceIn(0, textArea.document.length)
                        if (absStart < absEnd) {
                            runCatching {
                                val r1 = textArea.modelToView2D(absStart).bounds
                                val r2 = textArea.modelToView2D(absEnd).bounds
                                if (r1.y == r2.y) {
                                    val w = (r2.x - r1.x).coerceAtLeast(4)
                                    g2.fillRoundRect(r1.x, r1.y, w, lineHeight, 4, 4)
                                } else {
                                    // Multi-line inline fragment
                                    g2.fillRect(r1.x, r1.y, textArea.width - r1.x, lineHeight)
                                    var midY = r1.y + lineHeight
                                    while (midY < r2.y) {
                                        g2.fillRect(0, midY, textArea.width, lineHeight)
                                        midY += lineHeight
                                    }
                                    if (r2.x > 0) {
                                        g2.fillRect(0, r2.y, r2.x, lineHeight)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Active fragment outline
            if (index == activeFragmentIndex) {
                g2.color = palette.activeFragmentBorder
                g2.stroke = BasicStroke(1.5f)
                val top = y1.coerceAtLeast(0)
                val bottom = max(y1 + 2, y2) - 1
                g2.drawLine(0, top, textArea.width, top)
                g2.drawLine(0, bottom, textArea.width, bottom)
            }
        }
    }

    /**
     * Custom Gutter rendering 1-based line numbers + interactive `>>` / `<<` chunk apply/append buttons.
     */
    private inner class DiffGutterComponent : JComponent() {
        private var hoveredFragmentIndex: Int = -1
        private val actionColumnWidth = 22

        init {
            val mouseHandler = object : MouseAdapter() {
                override fun mouseMoved(e: MouseEvent) {
                    val idx = findFragmentButtonAt(e.x, e.y)
                    if (idx != hoveredFragmentIndex) {
                        hoveredFragmentIndex = idx
                        cursor = if (idx >= 0) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
                        toolTipText = if (idx >= 0) {
                            val dirText = if (side == EditorSide.LEFT) "Accept Left -> Right" else "Accept Right <- Left"
                            "$dirText (Ctrl+Click to Append)"
                        } else {
                            null
                        }
                        repaint()
                    }
                }

                override fun mouseExited(e: MouseEvent) {
                    if (hoveredFragmentIndex != -1) {
                        hoveredFragmentIndex = -1
                        cursor = Cursor.getDefaultCursor()
                        repaint()
                    }
                }

                override fun mouseClicked(e: MouseEvent) {
                    val idx = findFragmentButtonAt(e.x, e.y)
                    if (idx in fragments.indices && canTargetAcceptChunks()) {
                        val append = e.isControlDown || e.isMetaDown || e.isShiftDown
                        onChunkAction(fragments[idx], append)
                    }
                }
            }
            addMouseMotionListener(mouseHandler)
            addMouseListener(mouseHandler)
        }

        override fun getPreferredSize(): Dimension {
            val digits = max(3, textArea.lineCount.toString().length)
            val fm = getFontMetrics(DiffThemeManager.editorFont)
            val numWidth = digits * fm.charWidth('9') + 16
            return Dimension(numWidth + actionColumnWidth, textArea.preferredSize.height)
        }

        private fun findFragmentButtonAt(x: Int, y: Int): Int {
            if (!canTargetAcceptChunks()) return -1
            val w = width
            val btnStartX = if (side == EditorSide.LEFT) w - actionColumnWidth else 0
            val btnEndX = btnStartX + actionColumnWidth
            if (x !in btnStartX..btnEndX) return -1

            val lineHeight = textArea.lineHeight.coerceAtLeast(16)
            for ((index, fragment) in fragments.withIndex()) {
                val startLine = if (side == EditorSide.LEFT) fragment.startLine1 else fragment.startLine2
                val endLine = if (side == EditorSide.LEFT) fragment.endLine1 else fragment.endLine2
                if (startLine == endLine) continue // Pure deletion on this side has its button on the other side
                val btnY = startLine * lineHeight
                if (y in btnY..(btnY + lineHeight)) {
                    return index
                }
            }
            return -1
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                val palette = DiffThemeManager.palette
                val clip = g2.clipBounds ?: Rectangle(0, 0, width, height)

                g2.color = palette.gutterBg
                g2.fillRect(clip.x, clip.y, clip.width, clip.height)

                val lineHeight = textArea.lineHeight.coerceAtLeast(16)
                // Paint fragment gutter strips and >> / << buttons
                for ((index, fragment) in fragments.withIndex()) {
                    val startLine = if (side == EditorSide.LEFT) fragment.startLine1 else fragment.startLine2
                    val endLine = if (side == EditorSide.LEFT) fragment.endLine1 else fragment.endLine2
                    val diffType = fragment.getDiffType()
                    val y1 = startLine * lineHeight
                    val y2 = endLine * lineHeight

                    if (startLine < endLine) {
                        g2.color = palette.getLineBg(diffType)
                        g2.fillRect(0, y1, width, y2 - y1)
                    } else {
                        g2.color = palette.getBorderColor(diffType)
                        g2.fillRect(0, y1.coerceAtLeast(1) - 1, width, 2)
                    }

                    // Draw >> or << vector chevron button on the first line of non-empty chunk if target is editable
                    if (startLine < endLine && canTargetAcceptChunks()) {
                        val btnX = if (side == EditorSide.LEFT) width - actionColumnWidth else 2
                        val isHovered = (index == hoveredFragmentIndex)
                        if (isHovered) {
                            g2.color = palette.getWordBg(diffType)
                            g2.fillRoundRect(btnX, y1 + 1, actionColumnWidth - 2, lineHeight - 2, 4, 4)
                        }
                        val arrowColor = if (isHovered) palette.editorFg else palette.getBorderColor(diffType).darker()
                        DiffIcons.paintDoubleChevron(
                            g2 = g2,
                            x = btnX,
                            y = y1 + 1,
                            width = actionColumnWidth - 2,
                            height = lineHeight - 2,
                            pointRight = (side == EditorSide.LEFT),
                            color = arrowColor
                        )
                    }
                }

                // Paint 1-based line numbers
                g2.font = DiffThemeManager.editorFont
                val fm = g2.fontMetrics
                val firstLine = (clip.y / lineHeight).coerceAtLeast(0)
                val lastLine = ((clip.y + clip.height) / lineHeight + 1).coerceAtMost(textArea.lineCount - 1)
                val numAreaStartX = if (side == EditorSide.LEFT) 4 else actionColumnWidth + 2
                val numAreaWidth = width - actionColumnWidth - 8

                g2.color = palette.gutterFg
                for (line in firstLine..lastLine) {
                    val numStr = (line + 1).toString()
                    val y = line * lineHeight + (lineHeight + fm.ascent - fm.descent) / 2
                    val x = numAreaStartX + (numAreaWidth - fm.stringWidth(numStr)).coerceAtLeast(0)
                    g2.drawString(numStr, x, y)
                }

                // Border line
                g2.color = palette.gutterBorder
                if (side == EditorSide.LEFT) {
                    g2.drawLine(width - 1, clip.y, width - 1, clip.y + clip.height)
                } else {
                    g2.drawLine(0, clip.y, 0, clip.y + clip.height)
                }
            } finally {
                g2.dispose()
            }
        }
    }
}
