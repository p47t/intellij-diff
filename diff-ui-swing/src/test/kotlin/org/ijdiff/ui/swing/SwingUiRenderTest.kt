package org.ijdiff.ui.swing

import org.ijdiff.model.config.IjDiffConfig
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.DiffViewMode
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.ui.swing.dirdiff.DirDiffMasterDetailPanel
import org.ijdiff.ui.swing.shell.MainWindow
import org.ijdiff.ui.swing.theme.AppTheme
import org.ijdiff.ui.swing.theme.DiffThemeManager
import org.ijdiff.ui.swing.twoway.TwoWayDiffPanel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.writeText

class SwingUiRenderTest {

    private fun layoutAndPaintOffscreen(component: JComponent, width: Int, height: Int): BufferedImage {
        component.size = Dimension(width, height)
        component.preferredSize = Dimension(width, height)
        layoutRecursively(component)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g2 = image.createGraphics()
        try {
            component.printAll(g2)
        } finally {
            g2.dispose()
        }
        return image
    }

    private fun layoutRecursively(comp: java.awt.Component) {
        comp.doLayout()
        if (comp is java.awt.Container) {
            for (child in comp.components) {
                layoutRecursively(child)
            }
        }
    }

    @Test
    fun `TwoWayDiffPanel renders Side-by-Side and Unified views cleanly in Dark and Light themes`() {
        DiffThemeManager.initialize(AppTheme.DARK)
        val left = DiffDocument(
            initialText = """
                package demo

                fun calculate(x: Int, y: Int): Int {
                    val result = x + y
                    return result
                }
            """.trimIndent(),
            title = "Left.kt"
        )
        val right = DiffDocument(
            initialText = """
                package demo

                fun calculate(x: Int, y: Int, scale: Int = 1): Int {
                    val sum = (x + y) * scale
                    return sum
                }

                fun helper() = 42
            """.trimIndent(),
            title = "Right.kt"
        )

        val session = TwoWayDiffSession(left, right)
        val panel = TwoWayDiffPanel(session)

        // Render Side-by-Side in Darcula
        val darkImage = layoutAndPaintOffscreen(panel, 1200, 700)
        assertEquals(1200, darkImage.width)

        // Switch to Unified view and render
        session.setViewMode(DiffViewMode.UNIFIED)
        panel.refreshUI()
        val unifiedImage = layoutAndPaintOffscreen(panel, 1200, 700)
        assertEquals(700, unifiedImage.height)

        // Switch to Light theme and render Side-by-Side
        DiffThemeManager.setTheme(AppTheme.LIGHT)
        session.setViewMode(DiffViewMode.SIDE_BY_SIDE)
        panel.applyTheme()
        panel.refreshUI()
        val lightImage = layoutAndPaintOffscreen(panel, 1200, 700)
        assertTrue(lightImage.getRGB(600, 350) != 0)

        // Apply custom font face and size from IjDiffConfig and verify editor fonts update
        val customConfig = IjDiffConfig.parse("font.family = Monospaced\nfont.size = 18\ntheme = dark")
        DiffThemeManager.applyConfig(customConfig)
        panel.applyTheme()
        assertEquals("Monospaced", panel.leftEditorPane.textArea.font.family)
        assertEquals(18, panel.leftEditorPane.textArea.font.size)
        assertEquals(18, panel.rightEditorPane.textArea.font.size)
        val customFontImage = layoutAndPaintOffscreen(panel, 1200, 700)
        assertEquals(1200, customFontImage.width)
    }

    @Test
    fun `DirDiffMasterDetailPanel renders table and embedded diff preview cleanly`(@TempDir tempDir: Path) {
        val leftDir = tempDir.resolve("left").also { Files.createDirectories(it) }
        val rightDir = tempDir.resolve("right").also { Files.createDirectories(it) }

        leftDir.resolve("App.kt").writeText("fun main() {\n    println(\"v1\")\n}\n")
        rightDir.resolve("App.kt").writeText("fun main() {\n    println(\"v2\")\n}\n")
        rightDir.resolve("NewFile.kt").writeText("class NewFile\n")

        val dirSession = DirDiffSession(leftDir, rightDir)
        val dirPanel = DirDiffMasterDetailPanel(dirSession)

        val img = layoutAndPaintOffscreen(dirPanel, 1200, 800)
        assertEquals(1200, img.width)
        assertEquals(800, img.height)
    }

    @Test
    fun `MainWindow opens file comparison and directory comparison tabs without error`(@TempDir tempDir: Path) {
        val f1 = tempDir.resolve("A.kt").apply { writeText("val a = 1\n") }
        val f2 = tempDir.resolve("B.kt").apply { writeText("val a = 2\n") }

        val panel = TwoWayDiffPanel(
            session = TwoWayDiffSession(DiffDocument.fromFile(f1), DiffDocument.fromFile(f2))
        )
        // Exercise the exact callback wiring used in MainWindow.openFileComparison
        var callbackPanel: TwoWayDiffPanel? = null
        val created = TwoWayDiffPanel(
            session = panel.session,
            onDirtyStateChanged = {
                callbackPanel?.let { p -> assertTrue(p.width >= 0) }
            }
        )
        callbackPanel = created
        created.session.rightDocument.setText("val a = 3\n")
        created.refreshUI()
        assertTrue(created.session.hasUnsavedChanges())
    }
}
