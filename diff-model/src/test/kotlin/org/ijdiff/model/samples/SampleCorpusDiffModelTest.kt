package org.ijdiff.model.samples

import org.ijdiff.core.samples.DiffSampleCatalog
import org.ijdiff.core.samples.DirectoryDiffSampleSpec
import org.ijdiff.core.samples.FileDiffSampleSpec
import org.ijdiff.model.dirdiff.DirDiffSession
import org.ijdiff.model.dirdiff.DirDiffStatus
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.ChunkDirection
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Stream
import kotlin.io.path.isRegularFile

class SampleCorpusDiffModelTest {

    private val samplesRoot = DiffSampleCatalog.locateSamplesRoot()

    companion object {
        @JvmStatic
        fun fileSampleSpecs(): Stream<FileDiffSampleSpec> = DiffSampleCatalog.FILE_SAMPLES.stream()

        @JvmStatic
        fun dirSampleSpecs(): Stream<DirectoryDiffSampleSpec> = DiffSampleCatalog.DIRECTORY_SAMPLES.stream()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fileSampleSpecs")
    fun `Applying all chunks Left-to-Right and Right-to-Left converges documents and Undo restores original state`(
        spec: FileDiffSampleSpec
    ) {
        val leftPath = spec.resolveLeftPath(samplesRoot)
        val rightPath = spec.resolveRightPath(samplesRoot)

        // 1. Test Left -> Right convergence + full Undo restoration
        val leftDoc1 = DiffDocument.fromFile(leftPath)
        val rightDoc1 = DiffDocument.fromFile(rightPath)
        val originalRightText = rightDoc1.text.value

        val session1 = TwoWayDiffSession(leftDoc1, rightDoc1)
        var safetyCounter = 0
        while (session1.state.value.fragments.isNotEmpty() && safetyCounter < 100) {
            val firstFragment = session1.state.value.fragments.first()
            session1.replaceChunk(firstFragment, ChunkDirection.LEFT_TO_RIGHT)
            safetyCounter++
        }
        assertTrue(session1.state.value.fragments.isEmpty(), "Sample ${spec.id}: chunks did not converge L->R")
        assertEquals(leftDoc1.text.value, rightDoc1.text.value)

        // Undo all steps on rightDoc1 and verify it matches originalRightText and clears dirty state
        while (rightDoc1.canUndo.value) {
            rightDoc1.undo()
        }
        session1.recomputeNow()
        assertEquals(originalRightText, rightDoc1.text.value)
        assertFalse(rightDoc1.isDirty.value)

        // 2. Test Right -> Left convergence + full Undo restoration
        val leftDoc2 = DiffDocument.fromFile(leftPath)
        val rightDoc2 = DiffDocument.fromFile(rightPath)
        val originalLeftText = leftDoc2.text.value

        val session2 = TwoWayDiffSession(leftDoc2, rightDoc2)
        safetyCounter = 0
        while (session2.state.value.fragments.isNotEmpty() && safetyCounter < 100) {
            val firstFragment = session2.state.value.fragments.first()
            session2.replaceChunk(firstFragment, ChunkDirection.RIGHT_TO_LEFT)
            safetyCounter++
        }
        assertTrue(session2.state.value.fragments.isEmpty(), "Sample ${spec.id}: chunks did not converge R->L")
        assertEquals(rightDoc2.text.value, leftDoc2.text.value)

        while (leftDoc2.canUndo.value) {
            leftDoc2.undo()
        }
        session2.recomputeNow()
        assertEquals(originalLeftText, leftDoc2.text.value)
        assertFalse(leftDoc2.isDirty.value)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("dirSampleSpecs")
    fun `Directory sample scans expected statuses, detects binary files, filters globs, and synchronizes cleanly`(
        spec: DirectoryDiffSampleSpec,
        @TempDir tempDir: Path
    ) {
        val leftCopy = tempDir.resolve("left")
        val rightCopy = tempDir.resolve("right")
        copyTree(spec.resolveLeftRoot(samplesRoot), leftCopy)
        copyTree(spec.resolveRightRoot(samplesRoot), rightCopy)

        val dirSession = DirDiffSession(leftCopy, rightCopy)
        dirSession.scanSync()

        val all = dirSession.allEntries.value
        assertEquals(spec.expectedTotalEntries, all.size)
        assertEquals(spec.expectedModifiedCount, all.count { it.status == DirDiffStatus.MODIFIED })
        assertEquals(spec.expectedDeletedCount, all.count { it.status == DirDiffStatus.DELETED })
        assertEquals(spec.expectedAddedCount, all.count { it.status == DirDiffStatus.ADDED })
        assertEquals(spec.expectedEqualCount, all.count { it.status == DirDiffStatus.EQUAL })

        // Verify binary file detection
        val detectedBinaries = all.filter { it.isBinary }.map { it.relativePath }.toSet()
        assertEquals(spec.binaryRelativePaths, detectedBinaries)

        // Verify glob exclusion filter (!*.class;!*.tmp) hides build/Server.class and scratch.tmp
        dirSession.setGlobFilter("!*.class;!*.tmp")
        val filteredPaths = dirSession.filteredEntries.value.map { it.relativePath }.toSet()
        assertFalse("build/Server.class" in filteredPaths)
        assertFalse("scratch.tmp" in filteredPaths)

        // Clear glob filter and synchronize all non-equal entries
        dirSession.setGlobFilter("")
        val summary = dirSession.synchronizeEntries(dirSession.filteredEntries.value)
        assertTrue(summary.errors.isEmpty(), "Unexpected sync errors: ${summary.errors}")

        // After synchronization, all entries should now be EQUAL (so 0 non-equal entries in default filteredEntries)
        assertEquals(0, dirSession.filteredEntries.value.size)
        assertTrue(dirSession.allEntries.value.all { it.status == DirDiffStatus.EQUAL })
    }

    private fun copyTree(sourceRoot: Path, targetRoot: Path) {
        Files.walk(sourceRoot).use { stream ->
            stream.forEach { src ->
                val rel = sourceRoot.relativize(src)
                val dst = targetRoot.resolve(rel)
                if (src.isRegularFile()) {
                    dst.parent?.let { Files.createDirectories(it) }
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                } else {
                    Files.createDirectories(dst)
                }
            }
        }
    }
}
