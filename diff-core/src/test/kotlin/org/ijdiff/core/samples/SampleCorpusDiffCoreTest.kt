package org.ijdiff.core.samples

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.InnerFragmentsPolicy
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.util.CancellationChecker
import org.ijdiff.core.LineNumberConvertor
import org.ijdiff.core.LineOffsetsImpl
import org.ijdiff.core.UnifiedFragmentBuilder
import org.ijdiff.core.getDiffType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.util.stream.Stream

class SampleCorpusDiffCoreTest {

    private val comparisonManager = ComparisonManager.getInstance()
    private val samplesRoot = DiffSampleCatalog.locateSamplesRoot()

    companion object {
        @JvmStatic
        fun fileSampleSpecs(): Stream<FileDiffSampleSpec> = DiffSampleCatalog.FILE_SAMPLES.stream()
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fileSampleSpecs")
    fun `Sample satisfies fragment bounds, offset monotonicity, exact patch reconstruction, and unified builder invariants`(
        spec: FileDiffSampleSpec
    ) {
        val leftRaw = Files.readString(spec.resolveLeftPath(samplesRoot))
        val rightRaw = Files.readString(spec.resolveRightPath(samplesRoot))
        val leftText = leftRaw.replace("\r\n", "\n").replace('\r', '\n')
        val rightText = rightRaw.replace("\r\n", "\n").replace('\r', '\n')

        val fragments = comparisonManager.compareLinesInner(
            leftText,
            rightText,
            ComparisonPolicy.DEFAULT,
            CancellationChecker.EMPTY
        )

        assertTrue(
            fragments.size in spec.minFragmentsDefault..spec.maxFragmentsDefault,
            "Sample ${spec.id}: expected fragment count in ${spec.minFragmentsDefault}..${spec.maxFragmentsDefault}, got ${fragments.size}"
        )

        if (spec.expectedDiffTypes.isNotEmpty()) {
            val actualTypes = fragments.map { it.getDiffType() }.toSet()
            assertTrue(
                actualTypes.all { it in spec.expectedDiffTypes },
                "Sample ${spec.id}: unexpected diff types $actualTypes (allowed: ${spec.expectedDiffTypes})"
            )
        }

        // Verify monotonic non-overlapping line and character offsets + inner DiffFragment bounds
        verifyFragmentInvariants(leftText, rightText, fragments)

        // Verify exact reconstruction: patching leftText with all fragments produces rightText
        val reconstructedRight = applyFragmentsByOffset(leftText, rightText, fragments)
        assertEquals(rightText, reconstructedRight, "Sample ${spec.id}: reconstructing rightText from leftText + fragments failed")

        // Verify IGNORE_WHITESPACES policy expectation if specified
        if (spec.expectedFragmentsIgnoreWhitespace != null) {
            val iwFragments = comparisonManager.compareLines(
                leftText,
                rightText,
                ComparisonPolicy.IGNORE_WHITESPACES,
                CancellationChecker.EMPTY
            )
            assertEquals(
                spec.expectedFragmentsIgnoreWhitespace,
                iwFragments.size,
                "Sample ${spec.id}: unexpected IGNORE_WHITESPACES fragment count"
            )
        }

        // Verify BY_CHAR inner fragments also obey strict offset invariants
        val charFragments = comparisonManager.compareLinesInner(
            leftText,
            rightText,
            comparisonManager.compareLines(leftText, rightText, ComparisonPolicy.DEFAULT, CancellationChecker.EMPTY),
            ComparisonPolicy.DEFAULT,
            InnerFragmentsPolicy.CHARS,
            CancellationChecker.EMPTY
        )
        verifyFragmentInvariants(leftText, rightText, charFragments)

        // Verify UnifiedFragmentBuilder & LineNumberConvertor invariants
        val offsets1 = LineOffsetsImpl.create(leftText)
        val offsets2 = LineOffsetsImpl.create(rightText)
        val convertor = LineNumberConvertor.fromLineFragments(fragments, offsets1.lineCount, offsets2.lineCount)
        for (line in 0 until offsets1.lineCount) {
            val approx = convertor.convertApproximate1(line)
            assertTrue(approx >= 0, "Approximate line conversion must be non-negative")
        }

        val unified = UnifiedFragmentBuilder(
            text1 = leftText,
            text2 = rightText,
            lineOffsets1 = offsets1,
            lineOffsets2 = offsets2,
            fragments = fragments
        ).exec()

        assertEquals(unified.blocks.size, fragments.size)
        for (block in unified.blocks) {
            for (hl in block.innerHighlights) {
                assertTrue(
                    hl.range.startOffset >= 0 && hl.range.endOffset <= unified.text.length,
                    "Unified inline highlight out of bounds: ${hl.range} vs length ${unified.text.length}"
                )
            }
        }
    }

    private fun verifyFragmentInvariants(leftText: String, rightText: String, fragments: List<LineFragment>) {
        var prevEndLine1 = 0
        var prevEndLine2 = 0
        var prevEndOffset1 = 0
        var prevEndOffset2 = 0

        for (f in fragments) {
            assertTrue(f.startLine1 >= prevEndLine1)
            assertTrue(f.startLine2 >= prevEndLine2)
            assertTrue(f.startOffset1 >= prevEndOffset1)
            assertTrue(f.startOffset2 >= prevEndOffset2)
            assertTrue(f.endOffset1 <= leftText.length)
            assertTrue(f.endOffset2 <= rightText.length)

            val len1 = f.endOffset1 - f.startOffset1
            val len2 = f.endOffset2 - f.startOffset2

            f.innerFragments?.let { inners ->
                var prevInner1 = 0
                var prevInner2 = 0
                for (df in inners) {
                    assertTrue(df.startOffset1 >= prevInner1)
                    assertTrue(df.startOffset2 >= prevInner2)
                    assertTrue(df.endOffset1 <= len1)
                    assertTrue(df.endOffset2 <= len2)
                    prevInner1 = df.endOffset1
                    prevInner2 = df.endOffset2
                }
            }

            prevEndLine1 = f.endLine1
            prevEndLine2 = f.endLine2
            prevEndOffset1 = f.endOffset1
            prevEndOffset2 = f.endOffset2
        }
    }

    private fun applyFragmentsByOffset(leftText: String, rightText: String, fragments: List<LineFragment>): String {
        val offsets1 = LineOffsetsImpl.create(leftText)
        val offsets2 = LineOffsetsImpl.create(rightText)
        val leftLines = (0 until offsets1.lineCount).map {
            leftText.substring(offsets1.getLineStart(it), offsets1.getLineEnd(it, false))
        }
        val rightLines = (0 until offsets2.lineCount).map {
            rightText.substring(offsets2.getLineStart(it), offsets2.getLineEnd(it, false))
        }

        val outLines = mutableListOf<String>()
        var lastLine1 = 0
        for (f in fragments) {
            if (lastLine1 < f.startLine1) {
                outLines.addAll(leftLines.subList(lastLine1, f.startLine1))
            }
            if (f.startLine2 < f.endLine2) {
                outLines.addAll(rightLines.subList(f.startLine2, f.endLine2))
            }
            lastLine1 = f.endLine1
        }
        if (lastLine1 < leftLines.size) {
            outLines.addAll(leftLines.subList(lastLine1, leftLines.size))
        }
        return outLines.joinToString("\n")
    }
}
