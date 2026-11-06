package org.ijdiff.ui.swing.shell

import com.intellij.diff.comparison.ComparisonPolicy
import org.ijdiff.core.samples.DiffSampleCatalog
import org.ijdiff.model.config.IjDiffConfig
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.DiffViewMode
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.ui.swing.dirdiff.DirDiffMasterDetailPanel
import org.ijdiff.ui.swing.theme.AppTheme
import org.ijdiff.ui.swing.theme.DiffThemeManager
import org.ijdiff.ui.swing.twoway.TwoWayDiffPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.io.File
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.JToolBar
import javax.swing.SpinnerNumberModel
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Standalone multi-tabbed desktop window for `ijdiff`.
 * Supports file comparison tabs, directory comparison tabs, blank scratch diff tabs,
 * drag-and-drop of file/folder pairs, and Light/Darcula theme switching.
 */
class MainWindow(
    private val defaultPolicy: ComparisonPolicy = ComparisonPolicy.DEFAULT,
    private val defaultViewMode: DiffViewMode = DiffViewMode.SIDE_BY_SIDE,
    private val configPath: Path = IjDiffConfig.resolveConfigPath()
) : JFrame("IntelliJ Diff Viewer (ijdiff)") {

    private val tabbedPane = JTabbedPane()
    private val fontStatusButton = JButton()

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        preferredSize = Dimension(1380, 860)
        minimumSize = Dimension(880, 560)

        contentPane.layout = BorderLayout()
        contentPane.add(createTopBar(), BorderLayout.NORTH)
        contentPane.add(tabbedPane, BorderLayout.CENTER)

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                if (confirmAllTabsCanClose()) {
                    dispose()
                }
            }
        })

        installDragAndDrop()
        pack()
        setLocationRelativeTo(null)
    }

    fun openComparison(leftPath: Path, rightPath: Path) {
        if (leftPath.isDirectory() && rightPath.isDirectory()) {
            openDirectoryComparison(leftPath, rightPath)
        } else {
            openFileComparison(leftPath, rightPath)
        }
    }

    fun openFileComparison(
        leftPath: Path,
        rightPath: Path,
        policy: ComparisonPolicy = defaultPolicy,
        viewMode: DiffViewMode = defaultViewMode
    ) {
        val leftDoc = DiffDocument.fromFile(leftPath)
        val rightDoc = DiffDocument.fromFile(rightPath)
        val session = TwoWayDiffSession(
            leftDocument = leftDoc,
            rightDocument = rightDoc,
            initialComparisonPolicy = policy,
            initialViewMode = viewMode
        )
        val titleBase = "${leftPath.name} ↔ ${rightPath.name}"
        var panel: TwoWayDiffPanel? = null
        val createdPanel = TwoWayDiffPanel(
            session = session,
            onDirtyStateChanged = {
                panel?.let { p ->
                    updateTabTitle(p, if (session.hasUnsavedChanges()) "$titleBase *" else titleBase)
                }
            }
        )
        panel = createdPanel
        addCloseableTab(titleBase, createdPanel)
    }

    fun openDirectoryComparison(leftDir: Path, rightDir: Path) {
        val session = DirDiffSession(leftRoot = leftDir, rightRoot = rightDir)
        val panel = DirDiffMasterDetailPanel(session)
        val title = "📁 ${leftDir.name} ↔ ${rightDir.name}"
        addCloseableTab(title, panel)
    }

    fun openBlankScratchDiff(
        leftInitial: String = "",
        rightInitial: String = ""
    ) {
        val leftDoc = DiffDocument(initialText = leftInitial, title = "Left Scratch Buffer")
        val rightDoc = DiffDocument(initialText = rightInitial, title = "Right Scratch Buffer")
        val session = TwoWayDiffSession(
            leftDocument = leftDoc,
            rightDocument = rightDoc,
            initialComparisonPolicy = defaultPolicy,
            initialViewMode = defaultViewMode
        )
        val panel = TwoWayDiffPanel(session)
        addCloseableTab("Scratch Diff", panel)
    }

    fun loadAllBuiltInSamples() {
        runCatching {
            val root = DiffSampleCatalog.locateSamplesRoot()
            for (spec in DiffSampleCatalog.FILE_SAMPLES) {
                openFileComparison(
                    leftPath = spec.resolveLeftPath(root),
                    rightPath = spec.resolveRightPath(root)
                )
            }
            for (dirSpec in DiffSampleCatalog.DIRECTORY_SAMPLES) {
                openDirectoryComparison(
                    leftDir = dirSpec.resolveLeftRoot(root),
                    rightDir = dirSpec.resolveRightRoot(root)
                )
            }
        }.onFailure { err ->
            JOptionPane.showMessageDialog(
                this,
                "Could not load sample corpus: ${err.message}",
                "Sample Corpus Error",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    private fun createTopBar(): JToolBar {
        val bar = JToolBar()
        bar.isFloatable = false
        bar.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)

        val compareFilesBtn = JButton("Compare Files…").apply {
            addActionListener { showCompareDialog(directories = false) }
        }
        val compareDirsBtn = JButton("Compare Folders…").apply {
            addActionListener { showCompareDialog(directories = true) }
        }
        val scratchBtn = JButton("+ Scratch Diff").apply {
            toolTipText = "Open an editable two-way scratch pad for comparing pasted snippets"
            addActionListener { openBlankScratchDiff() }
        }
        val loadSamplesBtn = JButton("Load Sample Suite").apply {
            toolTipText = "Open all 12 file diff samples and the directory diff sample in tabs"
            addActionListener { loadAllBuiltInSamples() }
        }

        val themeCombo = JComboBox(AppTheme.entries.toTypedArray()).apply {
            selectedItem = DiffThemeManager.currentTheme.value
            maximumSize = Dimension(160, 28)
            addActionListener {
                val selected = selectedItem as? AppTheme ?: return@addActionListener
                DiffThemeManager.setTheme(selected)
                refreshAllTabThemes()
            }
        }

        updateFontButtonLabel()
        fontStatusButton.apply {
            toolTipText = "Configure editor font face and size (saved to $configPath)"
            addActionListener { showFontConfigDialog() }
        }

        bar.add(compareFilesBtn)
        bar.add(Box.createHorizontalStrut(6))
        bar.add(compareDirsBtn)
        bar.add(Box.createHorizontalStrut(6))
        bar.add(scratchBtn)
        bar.add(Box.createHorizontalStrut(6))
        bar.add(loadSamplesBtn)
        bar.add(Box.createHorizontalGlue())
        bar.add(fontStatusButton)
        bar.add(Box.createHorizontalStrut(8))
        bar.add(JLabel("Theme: "))
        bar.add(themeCombo)
        return bar
    }

    private fun updateFontButtonLabel() {
        val spec = DiffThemeManager.fontSpec.value
        fontStatusButton.text = "Font: ${spec.family} (${spec.size}pt)"
    }

    private fun showFontConfigDialog() {
        val currentSpec = DiffThemeManager.fontSpec.value
        val families = DiffThemeManager.getAvailableFontFamilies().ifEmpty {
            listOf("JetBrains Mono", "Ioskeley Mono", "Fira Code", "Monospaced")
        }
        val familyCombo = JComboBox(families.toTypedArray()).apply {
            isEditable = true
            selectedItem = currentSpec.family
        }
        val sizeSpinner = JSpinner(
            SpinnerNumberModel(
                currentSpec.size,
                IjDiffConfig.MIN_FONT_SIZE,
                IjDiffConfig.MAX_FONT_SIZE,
                1
            )
        )

        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(6, 6, 6, 6)
            fill = GridBagConstraints.HORIZONTAL
        }
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("Font Face ([font] family):"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(familyCombo, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        panel.add(JLabel("Font Size ([font] size):"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(sizeSpinner, gbc)

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2
        panel.add(JLabel("TOML Config File: $configPath"), gbc)

        val options = arrayOf("Apply & Save to Config", "Apply Only", "Cancel")
        val choice = JOptionPane.showOptionDialog(
            this,
            panel,
            "Editor Font & Configuration",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE,
            null,
            options,
            options[0]
        )
        if (choice == 0 || choice == 1) {
            val newFamily = familyCombo.selectedItem?.toString()?.trim()?.takeUnless { it.isEmpty() } ?: currentSpec.family
            val newSize = (sizeSpinner.value as? Number)?.toInt() ?: currentSpec.size
            DiffThemeManager.setFont(newFamily, newSize)
            updateFontButtonLabel()
            refreshAllTabThemes()
            if (choice == 0) {
                val existing = IjDiffConfig.load(configPath)
                val updatedConfig = existing.copy(
                    fontFamily = newFamily,
                    fontSize = newSize,
                    theme = if (DiffThemeManager.currentTheme.value.isDark) "dark" else "light",
                    sourcePath = configPath
                )
                runCatching { IjDiffConfig.save(updatedConfig, configPath) }
            }
        }
    }

    private fun showCompareDialog(directories: Boolean) {
        val dialogPanel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
        }

        val leftField = JTextField(32)
        val rightField = JTextField(32)

        fun browseInto(field: JTextField) {
            val chooser = JFileChooser().apply {
                fileSelectionMode = if (directories) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
                if (field.text.isNotBlank()) {
                    selectedFile = File(field.text)
                }
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                field.text = chooser.selectedFile.absolutePath
            }
        }

        val leftBrowse = JButton("Browse…").apply { addActionListener { browseInto(leftField) } }
        val rightBrowse = JButton("Browse…").apply { addActionListener { browseInto(rightField) } }

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        dialogPanel.add(JLabel(if (directories) "Left Folder:" else "Left File:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        dialogPanel.add(leftField, gbc)
        gbc.gridx = 2; gbc.weightx = 0.0
        dialogPanel.add(leftBrowse, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        dialogPanel.add(JLabel(if (directories) "Right Folder:" else "Right File:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        dialogPanel.add(rightField, gbc)
        gbc.gridx = 2; gbc.weightx = 0.0
        dialogPanel.add(rightBrowse, gbc)

        val result = JOptionPane.showConfirmDialog(
            this,
            dialogPanel,
            if (directories) "Select Folders to Compare" else "Select Files to Compare",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )
        if (result == JOptionPane.OK_OPTION) {
            val p1 = Path.of(leftField.text.trim())
            val p2 = Path.of(rightField.text.trim())
            if (!p1.exists() || !p2.exists()) {
                JOptionPane.showMessageDialog(
                    this,
                    "Both paths must exist:\n• $p1\n• $p2",
                    "Path Not Found",
                    JOptionPane.ERROR_MESSAGE
                )
                return
            }
            openComparison(p1, p2)
        }
    }

    private fun addCloseableTab(title: String, component: Component) {
        tabbedPane.addTab(title, component)
        val idx = tabbedPane.indexOfComponent(component)
        tabbedPane.setTabComponentAt(idx, createTabHeader(title, component))
        tabbedPane.selectedComponent = component
    }

    private fun updateTabTitle(component: Component, newTitle: String) {
        val idx = tabbedPane.indexOfComponent(component)
        if (idx >= 0) {
            tabbedPane.setTitleAt(idx, newTitle)
            tabbedPane.setTabComponentAt(idx, createTabHeader(newTitle, component))
        }
    }

    private fun createTabHeader(title: String, component: Component): JPanel {
        val header = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        val label = JLabel(title).apply { font = font.deriveFont(Font.PLAIN, 12f) }
        val closeBtn = JButton("×").apply {
            margin = Insets(0, 4, 0, 4)
            isBorderPainted = false
            isContentAreaFilled = false
            toolTipText = "Close Tab"
            addActionListener {
                if (confirmTabCanClose(component)) {
                    tabbedPane.remove(component)
                }
            }
        }
        header.add(label)
        header.add(closeBtn)
        return header
    }

    private fun confirmTabCanClose(component: Component): Boolean {
        return when (component) {
            is TwoWayDiffPanel -> component.confirmCloseOrSwitch(this)
            is DirDiffMasterDetailPanel -> component.confirmClose(this)
            else -> true
        }
    }

    private fun confirmAllTabsCanClose(): Boolean {
        for (i in 0 until tabbedPane.tabCount) {
            val comp = tabbedPane.getComponentAt(i)
            if (!confirmTabCanClose(comp)) return false
        }
        return true
    }

    private fun refreshAllTabThemes() {
        for (i in 0 until tabbedPane.tabCount) {
            when (val comp = tabbedPane.getComponentAt(i)) {
                is TwoWayDiffPanel -> comp.applyTheme()
                is DirDiffMasterDetailPanel -> comp.applyTheme()
            }
        }
    }

    private fun installDragAndDrop() {
        dropTarget = DropTarget(this, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
            override fun drop(dtde: DropTargetDropEvent) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val transferable = dtde.transferable
                    if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        @Suppress("UNCHECKED_CAST")
                        val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                        if (files.size >= 2) {
                            openComparison(files[0].toPath(), files[1].toPath())
                            dtde.dropComplete(true)
                            return
                        }
                    }
                    dtde.dropComplete(false)
                } catch (_: Exception) {
                    dtde.dropComplete(false)
                }
            }
        })
    }
}
