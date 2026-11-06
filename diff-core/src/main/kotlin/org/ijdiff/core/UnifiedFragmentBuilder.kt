// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.ijdiff.core

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.util.TextRange

/**
 * Builds a single unified (1-pane) document and highlight metadata from two documents and their [LineFragment]s.
 * Ported from `com.intellij.diff.tools.simple.UnifiedFragmentBuilder`.
 */
class UnifiedFragmentBuilder(
    private val text1: CharSequence,
    private val text2: CharSequence,
    private val lineOffsets1: LineOffsets = LineOffsetsImpl.create(text1),
    private val lineOffsets2: LineOffsets = LineOffsetsImpl.create(text2),
    private val fragments: List<LineFragment>
) {
    data class UnifiedBlock(
        val startLine: Int,
        val endLine: Int,
        val deletedRangeLines: IntRange,
        val insertedRangeLines: IntRange,
        val originalFragment: LineFragment,
        val innerHighlights: List<InlineHighlight>
    )

    data class InlineHighlight(
        val range: TextRange,
        val type: TextDiffType
    )

    data class LineNumberMapping(
        val unifiedLine: Int,
        val leftLine: Int,  // -1 if inserted from right only
        val rightLine: Int, // -1 if deleted from left only
        val type: TextDiffType?
    )

    data class UnifiedDiffResult(
        val text: String,
        val lineOffsets: LineOffsets,
        val blocks: List<UnifiedBlock>,
        val lineMappings: List<LineNumberMapping>
    )

    fun exec(): UnifiedDiffResult {
        val sb = StringBuilder(text1.length + text2.length)
        val blocks = mutableListOf<UnifiedBlock>()
        val mappings = mutableListOf<LineNumberMapping>()

        var lastLine1 = 0
        var lastLine2 = 0
        var currentUnifiedLine = 0

        fun appendLinesFrom1(startLine: Int, endLine: Int, alsoRightStart: Int?, diffType: TextDiffType?) {
            for (line in startLine until endLine) {
                if (currentUnifiedLine > 0) sb.append('\n')
                val s = lineOffsets1.getLineStart(line)
                val e = lineOffsets1.getLineEnd(line, false)
                sb.append(text1, s, e)
                val rightLine = if (alsoRightStart != null) alsoRightStart + (line - startLine) else -1
                mappings.add(LineNumberMapping(currentUnifiedLine, line, rightLine, diffType))
                currentUnifiedLine++
            }
        }

        fun appendLinesFrom2(startLine: Int, endLine: Int, diffType: TextDiffType?) {
            for (line in startLine until endLine) {
                if (currentUnifiedLine > 0) sb.append('\n')
                val s = lineOffsets2.getLineStart(line)
                val e = lineOffsets2.getLineEnd(line, false)
                sb.append(text2, s, e)
                mappings.add(LineNumberMapping(currentUnifiedLine, -1, line, diffType))
                currentUnifiedLine++
            }
        }

        for (fragment in fragments) {
            // Unchanged lines before fragment
            if (lastLine1 < fragment.startLine1) {
                appendLinesFrom1(lastLine1, fragment.startLine1, lastLine2, null)
            }

            val blockStartLine = currentUnifiedLine
            val deletedStartOffset = if (currentUnifiedLine > 0) sb.length + 1 else 0
            val deletedStartLine = currentUnifiedLine
            if (fragment.startLine1 < fragment.endLine1) {
                appendLinesFrom1(fragment.startLine1, fragment.endLine1, null, TextDiffType.DELETED)
            }
            val deletedEndLine = currentUnifiedLine

            val insertedStartOffset = if (currentUnifiedLine > 0) sb.length + 1 else 0
            val insertedStartLine = currentUnifiedLine
            if (fragment.startLine2 < fragment.endLine2) {
                appendLinesFrom2(fragment.startLine2, fragment.endLine2, TextDiffType.INSERTED)
            }
            val insertedEndLine = currentUnifiedLine

            val innerHighlights = mutableListOf<InlineHighlight>()
            fragment.innerFragments?.forEach { inner ->
                val len1 = inner.endOffset1 - inner.startOffset1
                val len2 = inner.endOffset2 - inner.startOffset2
                if (len1 > 0 && fragment.startLine1 < fragment.endLine1) {
                    val s = (deletedStartOffset + inner.startOffset1).coerceAtMost(sb.length)
                    val e = (deletedStartOffset + inner.endOffset1).coerceAtMost(sb.length)
                    if (s < e) {
                        innerHighlights.add(InlineHighlight(TextRange(s, e), TextDiffType.DELETED))
                    }
                }
                if (len2 > 0 && fragment.startLine2 < fragment.endLine2) {
                    val s = (insertedStartOffset + inner.startOffset2).coerceAtMost(sb.length)
                    val e = (insertedStartOffset + inner.endOffset2).coerceAtMost(sb.length)
                    if (s < e) {
                        innerHighlights.add(InlineHighlight(TextRange(s, e), TextDiffType.INSERTED))
                    }
                }
            }

            blocks.add(
                UnifiedBlock(
                    startLine = blockStartLine,
                    endLine = currentUnifiedLine,
                    deletedRangeLines = deletedStartLine until deletedEndLine,
                    insertedRangeLines = insertedStartLine until insertedEndLine,
                    originalFragment = fragment,
                    innerHighlights = innerHighlights
                )
            )

            lastLine1 = fragment.endLine1
            lastLine2 = fragment.endLine2
        }

        if (lastLine1 < lineOffsets1.lineCount) {
            appendLinesFrom1(lastLine1, lineOffsets1.lineCount, lastLine2, null)
        }

        val combinedText = sb.toString()
        return UnifiedDiffResult(
            text = combinedText,
            lineOffsets = LineOffsetsImpl.create(combinedText),
            blocks = blocks,
            lineMappings = mappings
        )
    }
}
