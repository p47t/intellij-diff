package org.ijdiff.ui.swing.samples

import org.ijdiff.core.samples.DiffSampleCatalog
import org.ijdiff.core.samples.DirectoryDiffSampleSpec
import org.ijdiff.core.samples.FileDiffSampleSpec
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.DiffViewMode
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.ui.swing.dirdiff.DirDiffMasterDetailPanel
import org.ijdiff.ui.swing.theme.AppTheme
import org.ijdiff.ui.swing.theme.DiffThemeManager
import org.ijdiff.ui.swing.twoway.TwoWayDiffPanel
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Container
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import javax.imageio.ImageIO
import javax.swing.JComponent

class SampleCorpusSwingRenderTest {

    private val samplesRoot = DiffSampleCatalog.locateSamplesRoot()
    private val outputDir: Path = Path.of("build/reports/sample-renders").also {
        Files.createDirectories(it)
    }

    companion object {
        @JvmStatic
        fun fileSampleSpecs(): Stream<FileDiffSampleSpec> = DiffSampleCatalog.FILE_SAMPLES.stream()

        @JvmStatic
        fun dirSampleSpecs(): Stream<DirectoryDiffSampleSpec> = DiffSampleCatalog.DIRECTORY_SAMPLES.stream()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fileSampleSpecs")
    fun `Renders file diff sample in Side-by-Side and Unified views and exports PNG snapshots`(
        spec: FileDiffSampleSpec
    ) {
        DiffThemeManager.initialize(AppTheme.DARK)
        val leftDoc = DiffDocument.fromFile(spec.resolveLeftPath(samplesRoot))
        val rightDoc = DiffDocument.fromFile(spec.resolveRightPath(samplesRoot))
        val session = TwoWayDiffSession(leftDoc, rightDoc)
        val panel = TwoWayDiffPanel(session)

        // 1. Render Side-by-Side (Darcula)
        val sbsImg = renderOffscreen(panel, 1280, 720)
        val sbsFile = outputDir.resolve("${spec.id}-side-by-side.png")
        ImageIO.write(sbsImg, "png", sbsFile.toFile())
        assertTrue(Files.size(sbsFile) > 1000L)

        // 2. Render Unified (Darcula)
        session.setViewMode(DiffViewMode.UNIFIED)
        panel.refreshUI()
        val unifiedImg = renderOffscreen(panel, 1280, 720)
        val unifiedFile = outputDir.resolve("${spec.id}-unified.png")
        ImageIO.write(unifiedImg, "png", unifiedFile.toFile())
        assertTrue(Files.size(unifiedFile) > 1000L)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dirSampleSpecs")
    fun `Renders directory diff master-detail sample and exports PNG snapshot`(
        spec: DirectoryDiffSampleSpec
    ) {
        DiffThemeManager.initialize(AppTheme.DARK)
        val session = DirDiffSession(
            leftRoot = spec.resolveLeftRoot(samplesRoot),
            rightRoot = spec.resolveRightRoot(samplesRoot)
        )
        val panel = DirDiffMasterDetailPanel(session)

        val img = renderOffscreen(panel, 1280, 820)
        val outFile = outputDir.resolve("${spec.id}-master-detail.png")
        ImageIO.write(img, "png", outFile.toFile())
        assertTrue(Files.size(outFile) > 1000L)
    }

    private fun renderOffscreen(component: JComponent, width: Int, height: Int): BufferedImage {
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
        if (comp is Container) {
            for (child in comp.components) {
                layoutRecursively(child)
            }
        }
    }
}
