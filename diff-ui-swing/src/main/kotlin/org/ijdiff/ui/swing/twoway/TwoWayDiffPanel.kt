package org.ijdiff.ui.swing.twoway

import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.fragments.LineFragment
import org.ijdiff.model.twoway.ChunkDirection
import org.ijdiff.model.twoway.DiffViewMode
import org.ijdiff.model.twoway.HighlightPolicy
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.ui.swing.editor.DiffEditorPane
import org.ijdiff.ui.swing.editor.EditorSide
import org.ijdiff.ui.swing.splitter.DiffDividerComponent
import org.ijdiff.ui.swing.splitter.DiffOverviewBar
import org.ijdiff.ui.swing.theme.DiffIcons
import org.ijdiff.ui.swing.theme.DiffThemeManager
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GridLayout
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JToolBar
import javax.swing.KeyStroke
import javax.swing.Timer
import kotlin.math.max

/**
 * Full 2-Way Diff Viewer component (Side-by-Side + Unified views) with synchronized scrolling,
 * curved Bezier splitter, chunk actions (`>>` / `<<`), Undo/Redo, and explicit Save (`Ctrl+S`).
 */
class TwoWayDiffPanel(
    val session: TwoWayDiffSession,
    private val onDirtyStateChanged: () -> Unit = {}
) : JPanel(BorderLayout()) {

    private var syncingScroll = false

    val leftEditorPane = DiffEditorPane(
        side = EditorSide.LEFT,
        document = session.leftDocument,
        canTargetAcceptChunks = { !session.rightDocument.isReadOnly },
        onChunkAction = { fragment, append ->
            if (append) {
                session.appendChunk(fragment, ChunkDirection.LEFT_TO_RIGHT)
            } else {
                session.replaceChunk(fragment, ChunkDirection.LEFT_TO_RIGHT)
            }
            refreshUI()
        },
        onDocumentEdited = { scheduleDebouncedRecompute() }
    )

    val rightEditorPane = DiffEditorPane(
        side = EditorSide.RIGHT,
        document = session.rightDocument,
        canTargetAcceptChunks = { !session.leftDocument.isReadOnly },
        onChunkAction = { fragment, append ->
            if (append) {
                session.appendChunk(fragment, ChunkDirection.RIGHT_TO_LEFT)
            } else {
                session.replaceChunk(fragment, ChunkDirection.RIGHT_TO_LEFT)
            }
            refreshUI()
        },
        onDocumentEdited = { scheduleDebouncedRecompute() }
    )

    private val dividerComponent = DiffDividerComponent(
        leftPane = leftEditorPane,
        rightPane = rightEditorPane,
        onFragmentClicked = { idx ->
            session.selectFragment(idx)?.let { scrollToFragment(it) }
            refreshUI()
        }
    )

    private val overviewBar = DiffOverviewBar { targetLine ->
        leftEditorPane.scrollToLine(targetLine)
    }

    private val unifiedPane = UnifiedDiffPane(
        fileTitle = session.rightDocument.title.ifBlank { session.leftDocument.title }
    )

    private val cardLayout = CardLayout()
    private val centerCards = JPanel(cardLayout)

    private val leftHeaderLabel = JLabel()
    private val rightHeaderLabel = JLabel()
    private val statusCountLabel = JLabel()
    private val saveButton = JButton("Save (Ctrl+S)")
    private val undoLeftBtn = JButton("Undo L", DiffIcons.undo())
    private val undoRightBtn = JButton("Undo R", DiffIcons.undo())

    private val debounceTimer = Timer(140) {
        session.recomputeNow()
        refreshUI()
    }.apply { isRepeats = false }

    init {
        add(createToolbar(), BorderLayout.NORTH)

        val sideBySideContainer = createSideBySideContainer()
        centerCards.add(sideBySideContainer, DiffViewMode.SIDE_BY_SIDE.name)
        centerCards.add(unifiedPane, DiffViewMode.UNIFIED.name)
        add(centerCards, BorderLayout.CENTER)

        setupSynchronizedScrolling()
        setupKeyboardShortcuts()
        refreshUI()

        // Scroll to first difference initially if present
        session.state.value.fragments.firstOrNull()?.let { first ->
            javax.swing.SwingUtilities.invokeLater { scrollToFragment(first) }
        }
    }

    fun applyTheme() {
        val boldUi = DiffThemeManager.uiBoldFont()
        saveButton.font = boldUi
        statusCountLabel.font = boldUi
        leftHeaderLabel.font = boldUi
        rightHeaderLabel.font = boldUi
        leftEditorPane.applyTheme()
        rightEditorPane.applyTheme()
        unifiedPane.applyTheme()
        dividerComponent.repaint()
        overviewBar.repaint()
        revalidate()
        repaint()
    }

    fun refreshUI() {
        val st = session.state.value
        leftEditorPane.syncFromDocumentIfChanged()
        rightEditorPane.syncFromDocumentIfChanged()

        leftEditorPane.updateFragments(st.fragments, st.currentFragmentIndex)
        rightEditorPane.updateFragments(st.fragments, st.currentFragmentIndex)
        dividerComponent.updateState(st.fragments, st.currentFragmentIndex)
        overviewBar.updateState(
            st.fragments,
            max(session.leftDocument.lineCount, session.rightDocument.lineCount)
        )
        unifiedPane.updateUnifiedResult(st.unifiedResult)

        cardLayout.show(centerCards, session.viewMode.value.name)

        // Update file headers & dirty badges
        val leftDirty = if (session.leftDocument.isDirty.value) " *" else ""
        val leftRo = if (session.leftDocument.isReadOnly) " [Read-Only]" else ""
        leftHeaderLabel.text = "${session.leftDocument.title}$leftDirty  (${session.leftDocument.lineSeparator.name})$leftRo"

        val rightDirty = if (session.rightDocument.isDirty.value) " *" else ""
        val rightRo = if (session.rightDocument.isReadOnly) " [Read-Only]" else ""
        rightHeaderLabel.text = "${session.rightDocument.title}$rightDirty  (${session.rightDocument.lineSeparator.name})$rightRo"

        val totalDiffs = st.fragments.size
        statusCountLabel.text = if (totalDiffs == 0) {
            "Contents are identical"
        } else {
            val cur = (st.currentFragmentIndex + 1).coerceIn(1, totalDiffs)
            "Difference $cur of $totalDiffs"
        }

        val anyDirty = session.hasUnsavedChanges()
        saveButton.isEnabled = anyDirty
        saveButton.text = if (anyDirty) "Save *" else "Save"
        undoLeftBtn.isEnabled = session.leftDocument.canUndo.value
        undoRightBtn.isEnabled = session.rightDocument.canUndo.value

        onDirtyStateChanged()
    }

    /**
     * Prompts the user if either document has unsaved changes. Returns `true` if safe to proceed.
     */
    fun confirmCloseOrSwitch(parent: Component = this): Boolean {
        if (!session.hasUnsavedChanges()) return true
        val dirtyNames = buildList {
            if (session.leftDocument.isDirty.value) add(session.leftDocument.title)
            if (session.rightDocument.isDirty.value) add(session.rightDocument.title)
        }.joinToString("\n- ", prefix = "- ")

        val choice = JOptionPane.showConfirmDialog(
            parent,
            "The following files have unsaved changes:\n$dirtyNames\n\nSave changes before continuing?",
            "Unsaved Changes",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE
        )
        return when (choice) {
            JOptionPane.YES_OPTION -> {
                session.saveAll()
                refreshUI()
                true
            }
            JOptionPane.NO_OPTION -> true
            else -> false
        }
    }

    private fun scheduleDebouncedRecompute() {
        debounceTimer.restart()
        onDirtyStateChanged()
    }

    private fun createToolbar(): JToolBar {
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        toolbar.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)

        val prevBtn = JButton("Prev", DiffIcons.arrowUp()).apply {
            toolTipText = "Previous Difference (Shift+F7)"
            addActionListener {
                session.prevDifference()?.let { scrollToFragment(it) }
                refreshUI()
            }
        }
        val nextBtn = JButton("Next", DiffIcons.arrowDown()).apply {
            toolTipText = "Next Difference (F7)"
            addActionListener {
                session.nextDifference()?.let { scrollToFragment(it) }
                refreshUI()
            }
        }

        val viewModeCombo = JComboBox(DiffViewMode.entries.toTypedArray()).apply {
            selectedItem = session.viewMode.value
            maximumSize = Dimension(190, 28)
            addActionListener {
                val selected = selectedItem as? DiffViewMode ?: return@addActionListener
                session.setViewMode(selected)
                refreshUI()
            }
        }

        val policyCombo = JComboBox(ComparisonPolicy.entries.toTypedArray()).apply {
            selectedItem = session.comparisonPolicy.value
            maximumSize = Dimension(180, 28)
            toolTipText = "Whitespace Comparison Policy"
            addActionListener {
                val selected = selectedItem as? ComparisonPolicy ?: return@addActionListener
                session.setComparisonPolicy(selected)
                refreshUI()
            }
        }

        val highlightCombo = JComboBox(HighlightPolicy.entries.toTypedArray()).apply {
            selectedItem = session.highlightPolicy.value
            maximumSize = Dimension(180, 28)
            toolTipText = "Inline Highlight Granularity"
            addActionListener {
                val selected = selectedItem as? HighlightPolicy ?: return@addActionListener
                session.setHighlightPolicy(selected)
                refreshUI()
            }
        }

        undoLeftBtn.apply {
            toolTipText = "Undo Left Document"
            addActionListener {
                if (session.leftDocument.undo()) {
                    session.recomputeNow()
                    refreshUI()
                }
            }
        }

        undoRightBtn.apply {
            toolTipText = "Undo Right Document"
            addActionListener {
                if (session.rightDocument.undo()) {
                    session.recomputeNow()
                    refreshUI()
                }
            }
        }

        val boldUi = DiffThemeManager.uiBoldFont()
        saveButton.apply {
            toolTipText = "Explicitly Save Modified Files to Disk (Ctrl+S)"
            font = boldUi
            addActionListener {
                session.saveAll()
                refreshUI()
            }
        }

        statusCountLabel.font = boldUi

        toolbar.add(prevBtn)
        toolbar.add(Box.createHorizontalStrut(4))
        toolbar.add(nextBtn)
        toolbar.addSeparator(Dimension(10, 20))
        toolbar.add(viewModeCombo)
        toolbar.add(Box.createHorizontalStrut(6))
        toolbar.add(policyCombo)
        toolbar.add(Box.createHorizontalStrut(6))
        toolbar.add(highlightCombo)
        toolbar.addSeparator(Dimension(10, 20))
        toolbar.add(undoLeftBtn)
        toolbar.add(Box.createHorizontalStrut(4))
        toolbar.add(undoRightBtn)
        toolbar.add(Box.createHorizontalStrut(6))
        toolbar.add(saveButton)
        toolbar.add(Box.createHorizontalGlue())
        toolbar.add(statusCountLabel)

        return toolbar
    }

    private fun createSideBySideContainer(): JPanel {
        val container = JPanel(BorderLayout())

        // File path header strip
        val boldUi = DiffThemeManager.uiBoldFont()
        val headerGrid = JPanel(GridLayout(1, 2, 36, 0)).apply {
            border = BorderFactory.createEmptyBorder(4, 10, 4, 24)
            leftHeaderLabel.font = boldUi
            rightHeaderLabel.font = boldUi
            add(leftHeaderLabel)
            add(rightHeaderLabel)
        }
        container.add(headerGrid, BorderLayout.NORTH)

        // 3-column layout: Left Editor (weight 0.5) | Bezier Divider (fixed 36px) | Right Editor (weight 0.5) | OverviewBar (fixed 14px)
        val gridPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.BOTH
            gridy = 0
            weighty = 1.0
        }

        gbc.gridx = 0
        gbc.weightx = 0.5
        gridPanel.add(leftEditorPane, gbc)

        gbc.gridx = 1
        gbc.weightx = 0.0
        gridPanel.add(dividerComponent, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.5
        gridPanel.add(rightEditorPane, gbc)

        gbc.gridx = 3
        gbc.weightx = 0.0
        gridPanel.add(overviewBar, gbc)

        container.add(gridPanel, BorderLayout.CENTER)
        return container
    }

    private fun setupSynchronizedScrolling() {
        leftEditorPane.scrollPane.viewport.addChangeListener {
            dividerComponent.repaint()
            if (syncingScroll) return@addChangeListener
            syncingScroll = true
            try {
                val lineHeight = leftEditorPane.textArea.lineHeight.coerceAtLeast(16)
                val leftPos = leftEditorPane.scrollPane.viewport.viewPosition
                val anchorLine1 = leftPos.y / lineHeight
                val subLinePixelOffset = leftPos.y % lineHeight
                val anchorLine2 = session.state.value.lineConvertor.convertApproximate1(anchorLine1)
                val targetY = (anchorLine2 * lineHeight + subLinePixelOffset).coerceAtLeast(0)
                val rightViewport = rightEditorPane.scrollPane.viewport
                val maxRightY = (rightEditorPane.textArea.height - rightViewport.extentSize.height).coerceAtLeast(0)
                rightViewport.viewPosition = java.awt.Point(rightViewport.viewPosition.x, targetY.coerceAtMost(maxRightY))
            } finally {
                syncingScroll = false
            }
        }

        rightEditorPane.scrollPane.viewport.addChangeListener {
            dividerComponent.repaint()
            if (syncingScroll) return@addChangeListener
            syncingScroll = true
            try {
                val lineHeight = rightEditorPane.textArea.lineHeight.coerceAtLeast(16)
                val rightPos = rightEditorPane.scrollPane.viewport.viewPosition
                val anchorLine2 = rightPos.y / lineHeight
                val subLinePixelOffset = rightPos.y % lineHeight
                val anchorLine1 = session.state.value.lineConvertor.convertApproximateInv1(anchorLine2)
                val targetY = (anchorLine1 * lineHeight + subLinePixelOffset).coerceAtLeast(0)
                val leftViewport = leftEditorPane.scrollPane.viewport
                val maxLeftY = (leftEditorPane.textArea.height - leftViewport.extentSize.height).coerceAtLeast(0)
                leftViewport.viewPosition = java.awt.Point(leftViewport.viewPosition.x, targetY.coerceAtMost(maxLeftY))
            } finally {
                syncingScroll = false
            }
        }
    }

    private fun setupKeyboardShortcuts() {
        val menuMask = runCatching { Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx }
            .getOrDefault(KeyEvent.CTRL_DOWN_MASK)
        val inputMap = getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)

        // Ctrl+S / Cmd+S -> Explicit Save
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, menuMask), "saveDiff")
        actionMap.put("saveDiff", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                session.saveAll()
                refreshUI()
            }
        })

        // F7 -> Next Difference
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "nextDiff")
        actionMap.put("nextDiff", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                session.nextDifference()?.let { scrollToFragment(it) }
                refreshUI()
            }
        })

        // Shift+F7 -> Previous Difference
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F7, KeyEvent.SHIFT_DOWN_MASK), "prevDiff")
        actionMap.put("prevDiff", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                session.prevDifference()?.let { scrollToFragment(it) }
                refreshUI()
            }
        })
    }

    fun scrollToFragment(fragment: LineFragment) {
        if (session.viewMode.value == DiffViewMode.UNIFIED) {
            val idx = session.state.value.fragments.indexOf(fragment)
            if (idx >= 0) unifiedPane.scrollToUnifiedBlock(idx)
        } else {
            leftEditorPane.scrollToLine(fragment.startLine1)
        }
    }
}
