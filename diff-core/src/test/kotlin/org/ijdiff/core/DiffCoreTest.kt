package org.ijdiff.core

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.InnerFragmentsPolicy
import com.intellij.diff.util.CancellationChecker
import com.intellij.util.diff.Diff
import com.intellij.util.diff.PatienceIntLCS
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiffCoreTest {

    private val comparisonManager = ComparisonManager.getInstance()

    @Test
    fun `Myers Diff detects insertions, deletions, and modifications`() {
        val before = "alpha\nbeta\ngamma\ndelta"
        val after = "alpha\nbeta_modified\ngamma\nepsilon\ndelta"

        val change = Diff.buildChanges(before, after)
        assertNotNull(change)
        val changes = change!!.toList()
        assertEquals(2, changes.size)

        // First change: line 1 modified ("beta" -> "beta_modified")
        assertEquals(1, changes[0].line0)
        assertEquals(1, changes[0].line1)
        assertEquals(1, changes[0].deleted)
        assertEquals(1, changes[0].inserted)

        // Second change: "epsilon" inserted at line 3 in after
        assertEquals(3, changes[1].line0)
        assertEquals(3, changes[1].line1)
        assertEquals(0, changes[1].deleted)
        assertEquals(1, changes[1].inserted)

        // Line translation
        assertEquals(0, Diff.translateLine(change, 0))
        assertEquals(-1, Diff.translateLine(change, 1, false))
        assertEquals(1, Diff.translateLine(change, 1, true))
        assertEquals(2, Diff.translateLine(change, 2))
        assertEquals(4, Diff.translateLine(change, 3))
    }

    @Test
    fun `PatienceIntLCS aligns unique anchor lines properly`() {
        val first = intArrayOf(1, 2, 10, 2, 3, 99)
        val second = intArrayOf(1, 2, 3, 2, 10, 99)

        val patience = PatienceIntLCS(first, second)
        patience.execute()
        val changes = patience.changes
        // Anchor lines 1 (index 0) and 99 (index 5) must be unchanged
        assertTrue(!changes[0].get(0))
        assertTrue(!changes[1].get(0))
        assertTrue(!changes[0].get(5))
        assertTrue(!changes[1].get(5))
    }

    @Test
    fun `ComparisonManager compareLinesInner computes line and inline word fragments`() {
        val text1 = """
            fun greet(name: String): String {
                return "Hello, " + name + "!"
            }
        """.trimIndent()

        val text2 = """
            fun greet(userName: String): String {
                return "Hi, " + userName + "!"
            }
            fun farewell() {}
        """.trimIndent()

        val fragments = comparisonManager.compareLinesInner(
            text1,
            text2,
            ComparisonPolicy.DEFAULT,
            CancellationChecker.EMPTY
        )

        assertEquals(2, fragments.size)

        // First fragment: lines 0..2 modified with inline word fragments ("name" -> "userName", "Hello" -> "Hi")
        val modFrag = fragments[0]
        assertEquals(0, modFrag.startLine1)
        assertEquals(2, modFrag.endLine1)
        assertEquals(0, modFrag.startLine2)
        assertEquals(2, modFrag.endLine2)
        assertEquals(TextDiffType.MODIFIED, modFrag.getDiffType())
        assertNotNull(modFrag.innerFragments)
        assertTrue(modFrag.innerFragments!!.size >= 3)

        // Second fragment: line 3 inserted ("fun farewell() {}")
        val insFrag = fragments[1]
        assertEquals(3, insFrag.startLine1)
        assertEquals(3, insFrag.endLine1)
        assertEquals(3, insFrag.startLine2)
        assertEquals(4, insFrag.endLine2)
        assertEquals(TextDiffType.INSERTED, insFrag.getDiffType())
        assertNull(insFrag.innerFragments)
    }

    @Test
    fun `ComparisonPolicy handles TRIM_WHITESPACES and IGNORE_WHITESPACES`() {
        val text1 = "  int x = 1 + 2;\n  return x;\n"
        val text2 = "int x = 1 + 2;   \n    return   x;\n"

        val defaultDiff = comparisonManager.compareLines(
            text1, text2, ComparisonPolicy.DEFAULT, CancellationChecker.EMPTY
        )
        assertEquals(1, defaultDiff.size) // Both lines differ in DEFAULT mode

        val trimDiff = comparisonManager.compareLines(
            text1, text2, ComparisonPolicy.TRIM_WHITESPACES, CancellationChecker.EMPTY
        )
        // Line 0 only differs in leading/trailing spaces, so only line 1 ("return   x;") differs in TRIM mode
        assertEquals(1, trimDiff.size)
        assertEquals(1, trimDiff[0].startLine1)
        assertEquals(2, trimDiff[0].endLine1)

        val ignoreDiff = comparisonManager.compareLines(
            text1, text2, ComparisonPolicy.IGNORE_WHITESPACES, CancellationChecker.EMPTY
        )
        assertTrue(ignoreDiff.isEmpty())
    }

    @Test
    fun `LineNumberConvertor maps exact and approximate lines for synchronized scrolling`() {
        val text1 = "L0\nL1_del1\nL2_del2\nL3_same\nL4_same"
        val text2 = "L0\nR1_ins\nL3_same\nL4_same"
        val fragments = comparisonManager.compareLines(
            text1, text2, ComparisonPolicy.DEFAULT, CancellationChecker.EMPTY
        )
        val convertor = LineNumberConvertor.fromLineFragments(fragments, 5, 4)

        // Line 0 is unchanged
        assertEquals(0, convertor.convert1(0))
        assertEquals(0, convertor.convertInv1(0))

        // Lines 1..2 on left were replaced by line 1 on right
        assertEquals(-1, convertor.convert1(1))
        assertEquals(1, convertor.convertApproximate1(1))
        assertEquals(1, convertor.convertApproximate1(2))
        assertEquals(2, convertor.convertApproximate1(3))

        // Line 3 on left == Line 2 on right
        assertEquals(2, convertor.convert1(3))
        assertEquals(3, convertor.convertInv1(2))

        // Scrolling UP by small pixel offsets across the asymmetric diff block is strictly monotonic
        val lineHeight = 20f
        var prevRightY = convertor.convertApproximatePixel1(3 * lineHeight, lineHeight)
        assertEquals(2 * lineHeight, prevRightY)
        for (leftPx in (3 * 20 - 1) downTo 0) {
            val curRightY = convertor.convertApproximatePixel1(leftPx.toFloat(), lineHeight)
            assertTrue(
                curRightY <= prevRightY,
                "Expected monotonic upward scroll at leftPx=$leftPx: curRightY=$curRightY, prevRightY=$prevRightY"
            )
            prevRightY = curRightY
        }
        // Directly above line 3 (e.g. leftPx = 58, 2px scroll up), rightY must already move up into line 1 (< 40f)
        assertTrue(convertor.convertApproximatePixel1(58f, lineHeight) < 2 * lineHeight)
    }

    @Test
    fun `UnifiedFragmentBuilder constructs merged text, line mappings, and inline highlights`() {
        val text1 = "line1\noldValue = 10\nline3"
        val text2 = "line1\nnewValue = 10\nline3\nline4"

        val fragments = comparisonManager.compareLinesInner(
            text1, text2, ComparisonPolicy.DEFAULT, CancellationChecker.EMPTY
        )
        val result = UnifiedFragmentBuilder(
            text1 = text1,
            text2 = text2,
            fragments = fragments
        ).exec()

        val expectedLines = listOf(
            "line1",
            "oldValue = 10",
            "newValue = 10",
            "line3",
            "line4"
        )
        assertEquals(expectedLines.joinToString("\n"), result.text)
        assertEquals(5, result.lineMappings.size)

        // Line 0: unchanged (left 0, right 0)
        assertEquals(0, result.lineMappings[0].leftLine)
        assertEquals(0, result.lineMappings[0].rightLine)
        assertNull(result.lineMappings[0].type)

        // Line 1: deleted from left (left 1, right -1)
        assertEquals(1, result.lineMappings[1].leftLine)
        assertEquals(-1, result.lineMappings[1].rightLine)
        assertEquals(TextDiffType.DELETED, result.lineMappings[1].type)

        // Line 2: inserted from right (left -1, right 1)
        assertEquals(-1, result.lineMappings[2].leftLine)
        assertEquals(1, result.lineMappings[2].rightLine)
        assertEquals(TextDiffType.INSERTED, result.lineMappings[2].type)

        // Verify inline word highlights were mapped into the unified document
        assertEquals(2, result.blocks.size)
        assertEquals(2, result.blocks[0].innerHighlights.size)
        val delHighlight = result.blocks[0].innerHighlights.first { it.type == TextDiffType.DELETED }
        val insHighlight = result.blocks[0].innerHighlights.first { it.type == TextDiffType.INSERTED }
        assertEquals("oldValue", delHighlight.range.subSequence(result.text).toString())
        assertEquals("newValue", insHighlight.range.subSequence(result.text).toString())
    }

    @Test
    fun `DiffDividerGeometry creates smooth bezier polygon`() {
        val poly = DiffDividerGeometry.createPolygon(
            width = 36f,
            startY1 = 100f,
            endY1 = 140f,
            startY2 = 160f,
            endY2 = 180f,
            type = TextDiffType.MODIFIED
        )
        assertEquals(0f, poly.topCurve.startX)
        assertEquals(100f, poly.topCurve.startY)
        assertEquals(36f, poly.topCurve.endX)
        assertEquals(160f, poly.topCurve.endY)
        assertEquals(TextDiffType.MODIFIED, poly.type)
    }
}
