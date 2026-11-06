package org.ijdiff.ui.swing.dirdiff

import org.ijdiff.model.dirdiff.DirCompareMode
import org.ijdiff.model.dirdiff.DirDiffEntry
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.dirdiff.DirDiffStatus
import org.ijdiff.model.dirdiff.DirSyncAction
import org.ijdiff.ui.swing.theme.DiffIcons
import org.ijdiff.ui.swing.theme.DiffThemeManager
import org.ijdiff.ui.swing.twoway.TwoWayDiffPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Master-Detail Directory Comparison & Synchronization Viewer (`DirDiffViewer`).
 * Top pane: Filter/Sync toolbar + Two-sided file comparison table.
 * Bottom pane: Embedded [TwoWayDiffPanel] live-previewing and editing the selected file pair.
 */
class DirDiffMasterDetailPanel(
    val session: DirDiffSession
) : JPanel(BorderLayout()) {

    private val tableModel = DirDiffTableModel()
    private val table = JTable(tableModel)
    private val detailContainer = JPanel(BorderLayout())
    private var currentDiffPanel: TwoWayDiffPanel? = null
    private var suppressSelectionListener = false

    private val summaryLabel = JLabel()
    private val splitPane: JSplitPane

    init {
        session.scanSync()

        add(createToolbar(), BorderLayout.NORTH)
        configureTable()

        val tableScroll = JScrollPane(table).apply {
            border = BorderFactory.createEmptyBorder()
            preferredSize = Dimension(900, 260)
        }

        detailContainer.preferredSize = Dimension(900, 440)
        splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailContainer).apply {
            resizeWeight = 0.36
            isContinuousLayout = true
        }
        add(splitPane, BorderLayout.CENTER)

        refreshTableAndDetail()
    }

    fun applyTheme() {
        val baseUiFont = DiffThemeManager.uiFont
        val boldUiFont = DiffThemeManager.uiBoldFont()
        table.font = baseUiFont
        table.tableHeader?.font = boldUiFont
        table.rowHeight = (baseUiFont.size + 11).coerceAtLeast(22)
        summaryLabel.font = boldUiFont
        table.revalidate()
        table.repaint()
        currentDiffPanel?.applyTheme()
        revalidate()
        repaint()
    }

    fun confirmClose(parent: Component = this): Boolean {
        return currentDiffPanel?.confirmCloseOrSwitch(parent) ?: true
    }

    private fun createToolbar(): JToolBar {
        val toolbar = JToolBar()
        toolbar.isFloatable = false
        toolbar.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)

        val refreshBtn = JButton("Rescan", DiffIcons.refresh()).apply {
            toolTipText = "Rescan Left and Right Directories"
            addActionListener {
                if (currentDiffPanel?.confirmCloseOrSwitch(this@DirDiffMasterDetailPanel) == false) {
                    return@addActionListener
                }
                session.scanSync()
                refreshTableAndDetail()
            }
        }

        val compareCombo = JComboBox(DirCompareMode.entries.toTypedArray()).apply {
            selectedItem = session.compareMode.value
            maximumSize = Dimension(170, 28)
            toolTipText = "File Comparison Mode"
            addActionListener {
                val mode = selectedItem as? DirCompareMode ?: return@addActionListener
                session.setCompareMode(mode)
                refreshTableAndDetail()
            }
        }

        // Status filter toggle buttons
        fun createStatusToggle(label: String, status: DirDiffStatus): JToggleButton {
            return JToggleButton(label, status in session.statusFilter.value).apply {
                toolTipText = status.displayName
                addActionListener {
                    session.toggleStatusFilter(status)
                    refreshTableAndDetail()
                }
            }
        }

        val btnModified = createStatusToggle("* Modified", DirDiffStatus.MODIFIED)
        val btnDeleted = createStatusToggle("- Left Only", DirDiffStatus.DELETED)
        val btnAdded = createStatusToggle("+ Right Only", DirDiffStatus.ADDED)
        val btnEqual = createStatusToggle("= Equal", DirDiffStatus.EQUAL)

        val globField = JTextField(session.globFilter.value, 14).apply {
            maximumSize = Dimension(180, 28)
            toolTipText = "Glob filter (e.g., *.kt or !*.class;!build/**)"
            document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = onFilterChanged()
                override fun removeUpdate(e: DocumentEvent) = onFilterChanged()
                override fun changedUpdate(e: DocumentEvent) = onFilterChanged()
                private fun onFilterChanged() {
                    session.setGlobFilter(text)
                    refreshTableAndDetail()
                }
            })
        }

        val syncSelectedBtn = JButton("Sync Selected").apply {
            toolTipText = "Execute Sync Action on Selected Row(s)"
            addActionListener {
                val entries = getSelectedEntries()
                if (entries.isEmpty()) return@addActionListener
                executeSynchronization(entries)
            }
        }

        val boldUiFont = DiffThemeManager.uiBoldFont()
        val syncAllBtn = JButton("Sync All").apply {
            toolTipText = "Execute Sync Action on All Filtered Rows"
            font = boldUiFont
            addActionListener {
                val actionable = session.filteredEntries.value.filter { it.syncAction != DirSyncAction.SKIP }
                if (actionable.isEmpty()) return@addActionListener
                executeSynchronization(actionable)
            }
        }

        summaryLabel.font = boldUiFont

        toolbar.add(refreshBtn)
        toolbar.add(Box.createHorizontalStrut(6))
        toolbar.add(compareCombo)
        toolbar.addSeparator(Dimension(10, 20))
        toolbar.add(btnModified)
        toolbar.add(Box.createHorizontalStrut(2))
        toolbar.add(btnDeleted)
        toolbar.add(Box.createHorizontalStrut(2))
        toolbar.add(btnAdded)
        toolbar.add(Box.createHorizontalStrut(2))
        toolbar.add(btnEqual)
        toolbar.addSeparator(Dimension(10, 20))
        toolbar.add(JLabel("Filter: "))
        toolbar.add(globField)
        toolbar.addSeparator(Dimension(10, 20))
        toolbar.add(syncSelectedBtn)
        toolbar.add(Box.createHorizontalStrut(4))
        toolbar.add(syncAllBtn)
        toolbar.add(Box.createHorizontalGlue())
        toolbar.add(summaryLabel)

        return toolbar
    }

    private fun configureTable() {
        val baseUiFont = DiffThemeManager.uiFont
        table.rowHeight = (baseUiFont.size + 11).coerceAtLeast(22)
        table.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        table.font = baseUiFont
        table.tableHeader?.font = DiffThemeManager.uiBoldFont()
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 1)

        // Column widths: 0=Left Path, 1=Left Size, 2=Status/Sync Action, 3=Right Path, 4=Right Size
        table.columnModel.getColumn(0).preferredWidth = 340
        table.columnModel.getColumn(1).preferredWidth = 90
        table.columnModel.getColumn(1).maxWidth = 120
        table.columnModel.getColumn(2).preferredWidth = 90
        table.columnModel.getColumn(2).maxWidth = 110
        table.columnModel.getColumn(3).preferredWidth = 340
        table.columnModel.getColumn(4).preferredWidth = 90
        table.columnModel.getColumn(4).maxWidth = 120

        val cellRenderer = DirDiffCellRenderer()
        for (i in 0 until table.columnCount) {
            table.columnModel.getColumn(i).cellRenderer = cellRenderer
        }

        // Clicking column 2 cycles the sync action for that row
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                val col = table.columnAtPoint(e.point)
                if (row in tableModel.entries.indices && col == 2) {
                    val entry = tableModel.entries[row]
                    val nextAction = cycleActionFor(entry)
                    session.setSyncAction(entry.relativePath, nextAction)
                    tableModel.entries = session.filteredEntries.value
                    tableModel.fireTableRowsUpdated(row, row)
                }
            }
        })

        table.selectionModel.addListSelectionListener { e ->
            if (e.valueIsAdjusting || suppressSelectionListener) return@addListSelectionListener
            val row = table.selectedRow
            val nextEntry = if (row in tableModel.entries.indices) tableModel.entries[row] else null
            if (nextEntry?.relativePath == session.selectedEntry.value?.relativePath) return@addListSelectionListener

            if (currentDiffPanel?.confirmCloseOrSwitch(this) == false) {
                return@addListSelectionListener
            }
            session.selectEntry(nextEntry)
            updateDetailPane()
        }
    }

    private fun cycleActionFor(entry: DirDiffEntry): DirSyncAction {
        val order = when (entry.status) {
            DirDiffStatus.DELETED -> listOf(DirSyncAction.COPY_TO_RIGHT, DirSyncAction.DELETE_LEFT, DirSyncAction.SKIP)
            DirDiffStatus.ADDED -> listOf(DirSyncAction.COPY_TO_LEFT, DirSyncAction.DELETE_RIGHT, DirSyncAction.SKIP)
            DirDiffStatus.MODIFIED -> listOf(DirSyncAction.COPY_TO_RIGHT, DirSyncAction.COPY_TO_LEFT, DirSyncAction.SKIP)
            DirDiffStatus.EQUAL -> listOf(DirSyncAction.SKIP)
        }
        val idx = order.indexOf(entry.syncAction)
        return order[(idx + 1) % order.size]
    }

    private fun getSelectedEntries(): List<DirDiffEntry> {
        return table.selectedRows
            .filter { it in tableModel.entries.indices }
            .map { tableModel.entries[it] }
    }

    private fun executeSynchronization(entries: List<DirDiffEntry>) {
        val actionable = entries.filter { it.syncAction != DirSyncAction.SKIP }
        if (actionable.isEmpty()) return

        val confirm = JOptionPane.showConfirmDialog(
            this,
            "Synchronize ${actionable.size} file(s)?",
            "Confirm Directory Synchronization",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )
        if (confirm != JOptionPane.YES_OPTION) return

        val result = session.synchronizeEntries(actionable)
        refreshTableAndDetail()
        if (result.errors.isNotEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Synchronization completed with errors:\n${result.errors.joinToString("\n")}",
                "Sync Errors",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun refreshTableAndDetail() {
        suppressSelectionListener = true
        try {
            val entries = session.filteredEntries.value
            tableModel.entries = entries
            tableModel.fireTableDataChanged()

            val all = session.allEntries.value
            val modCount = all.count { it.status == DirDiffStatus.MODIFIED }
            val delCount = all.count { it.status == DirDiffStatus.DELETED }
            val addCount = all.count { it.status == DirDiffStatus.ADDED }
            summaryLabel.text = "${entries.size} shown (Modified: $modCount, Left Only: $delCount, Right Only: $addCount)"

            val selectedRel = session.selectedEntry.value?.relativePath
            val idx = entries.indexOfFirst { it.relativePath == selectedRel }.takeIf { it >= 0 }
                ?: entries.indexOfFirst { !it.isBinary }.takeIf { it >= 0 }
                ?: if (entries.isNotEmpty()) 0 else -1
            if (idx >= 0) {
                table.setRowSelectionInterval(idx, idx)
                session.selectEntry(entries[idx])
            } else {
                session.selectEntry(null)
            }
        } finally {
            suppressSelectionListener = false
        }
        updateDetailPane()
    }

    private fun updateDetailPane() {
        detailContainer.removeAll()
        val preview = session.previewSession.value
        val selected = session.selectedEntry.value
        if (preview != null) {
            val panel = TwoWayDiffPanel(preview)
            currentDiffPanel = panel
            detailContainer.add(panel, BorderLayout.CENTER)
        } else {
            currentDiffPanel = null
            val msg = when {
                selected == null -> "Select a file pair above to view its diff"
                selected.isBinary -> "Binary file (${selected.relativePath}) - cannot display text diff"
                else -> "No preview available"
            }
            val emptyLabel = JLabel(msg, SwingConstants.CENTER).apply {
                font = DiffThemeManager.uiItalicFont()
            }
            detailContainer.add(emptyLabel, BorderLayout.CENTER)
        }
        detailContainer.revalidate()
        detailContainer.repaint()
    }

    private class DirDiffTableModel : AbstractTableModel() {
        var entries: List<DirDiffEntry> = emptyList()

        private val columns = arrayOf(
            "Left File",
            "Left Size",
            "Action",
            "Right File",
            "Right Size"
        )

        override fun getRowCount(): Int = entries.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val entry = entries[rowIndex]
            return when (columnIndex) {
                0 -> if (entry.leftFile != null) entry.relativePath else ""
                1 -> entry.leftFile?.size?.let { formatSize(it) } ?: ""
                2 -> "${entry.status.symbol}  ${entry.syncAction.symbol}"
                3 -> if (entry.rightFile != null) entry.relativePath else ""
                4 -> entry.rightFile?.size?.let { formatSize(it) } ?: ""
                else -> ""
            }
        }

        private fun formatSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            return "%.1f MB".format(mb)
        }
    }

    private inner class DirDiffCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column) as JLabel
            val palette = DiffThemeManager.palette
            val entry = tableModel.entries.getOrNull(row)

            comp.horizontalAlignment = when (column) {
                1, 4 -> SwingConstants.RIGHT
                2 -> SwingConstants.CENTER
                else -> SwingConstants.LEFT
            }
            comp.border = BorderFactory.createEmptyBorder(0, 8, 0, 8)

            if (!isSelected && entry != null) {
                comp.background = when (entry.status) {
                    DirDiffStatus.ADDED -> palette.insertedLineBg
                    DirDiffStatus.DELETED -> palette.deletedLineBg
                    DirDiffStatus.MODIFIED -> palette.modifiedLineBg
                    DirDiffStatus.EQUAL -> palette.editorBg
                }
                comp.foreground = palette.editorFg
            }
            return comp
        }
    }
}
