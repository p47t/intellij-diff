// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.ijdiff.core

import com.intellij.diff.fragments.LineFragment
import kotlin.math.min

/**
 * Maps line numbers between two documents (or between a Unified document and Left/Right documents),
 * supporting both exact mapping (unchanged lines) and approximate interpolation (for synchronized scrolling).
 */
class LineNumberConvertor private constructor(
    private val fragments: List<Chunk>
) {
    private data class Chunk(
        val start1: Int,
        val start2: Int,
        val length1: Int,
        val length2: Int,
        val exact: Boolean
    )

    /**
     * Exact conversion from left line to right line. Returns -1 if the line is modified/deleted.
     */
    fun convert1(line1: Int): Int {
        for (chunk in fragments) {
            if (line1 < chunk.start1) return -1
            if (line1 < chunk.start1 + chunk.length1) {
                return if (chunk.exact && chunk.length1 == chunk.length2) {
                    chunk.start2 + (line1 - chunk.start1)
                } else {
                    -1
                }
            }
        }
        return -1
    }

    /**
     * Exact conversion from right line to left line. Returns -1 if the line is modified/inserted.
     */
    fun convertInv1(line2: Int): Int {
        for (chunk in fragments) {
            if (line2 < chunk.start2) return -1
            if (line2 < chunk.start2 + chunk.length2) {
                return if (chunk.exact && chunk.length1 == chunk.length2) {
                    chunk.start1 + (line2 - chunk.start2)
                } else {
                    -1
                }
            }
        }
        return -1
    }

    /**
     * Approximate conversion from left line to right line (used for synchronized scrolling).
     */
    fun convertApproximate1(line1: Int): Int {
        if (line1 <= 0) return 0
        for (chunk in fragments) {
            if (line1 < chunk.start1) return chunk.start2
            if (line1 < chunk.start1 + chunk.length1) {
                if (chunk.length1 == chunk.length2) {
                    return chunk.start2 + (line1 - chunk.start1)
                }
                return chunk.start2 + ((line1 - chunk.start1) * chunk.length2) / chunk.length1
            }
        }
        val last = fragments.lastOrNull() ?: return line1
        return last.start2 + last.length2 + (line1 - (last.start1 + last.length1))
    }

    /**
     * Approximate conversion from right line to left line (used for synchronized scrolling).
     */
    fun convertApproximateInv1(line2: Int): Int {
        if (line2 <= 0) return 0
        for (chunk in fragments) {
            if (line2 < chunk.start2) return chunk.start1
            if (line2 < chunk.start2 + chunk.length2) {
                if (chunk.length1 == chunk.length2) {
                    return chunk.start1 + (line2 - chunk.start2)
                }
                return chunk.start1 + ((line2 - chunk.start2) * chunk.length1) / chunk.length2
            }
        }
        val last = fragments.lastOrNull() ?: return line2
        return last.start1 + last.length1 + (line2 - (last.start2 + last.length2))
    }

    /**
     * Smooth, strictly monotonic pixel-level scroll conversion from left pane (`scrollY1`) to right pane (`scrollY2`).
     * Prevents sub-line offset wrap-around when scrolling up by small deltas across asymmetric diff blocks.
     */
    fun convertApproximatePixel1(scrollY1: Float, lineHeightPx: Float): Float {
        if (scrollY1 <= 0f || lineHeightPx <= 0f) return 0f
        for (chunk in fragments) {
            val startY1 = chunk.start1 * lineHeightPx
            val endY1 = (chunk.start1 + chunk.length1) * lineHeightPx
            val startY2 = chunk.start2 * lineHeightPx
            val endY2 = (chunk.start2 + chunk.length2) * lineHeightPx
            if (scrollY1 < startY1) return startY2
            if (scrollY1 < endY1) {
                if (chunk.length1 == chunk.length2) {
                    return startY2 + (scrollY1 - startY1)
                }
                val fraction = (scrollY1 - startY1) / (endY1 - startY1)
                return startY2 + fraction * (endY2 - startY2)
            }
        }
        val last = fragments.lastOrNull() ?: return scrollY1
        val lastEndY1 = (last.start1 + last.length1) * lineHeightPx
        val lastEndY2 = (last.start2 + last.length2) * lineHeightPx
        return lastEndY2 + (scrollY1 - lastEndY1)
    }

    /**
     * Smooth, strictly monotonic pixel-level scroll conversion from right pane (`scrollY2`) to left pane (`scrollY1`).
     * Prevents sub-line offset wrap-around when scrolling up by small deltas across asymmetric diff blocks.
     */
    fun convertApproximatePixelInv1(scrollY2: Float, lineHeightPx: Float): Float {
        if (scrollY2 <= 0f || lineHeightPx <= 0f) return 0f
        for (chunk in fragments) {
            val startY1 = chunk.start1 * lineHeightPx
            val endY1 = (chunk.start1 + chunk.length1) * lineHeightPx
            val startY2 = chunk.start2 * lineHeightPx
            val endY2 = (chunk.start2 + chunk.length2) * lineHeightPx
            if (scrollY2 < startY2) return startY1
            if (scrollY2 < endY2) {
                if (chunk.length1 == chunk.length2) {
                    return startY1 + (scrollY2 - startY2)
                }
                val fraction = (scrollY2 - startY2) / (endY2 - startY2)
                return startY1 + fraction * (endY1 - startY1)
            }
        }
        val last = fragments.lastOrNull() ?: return scrollY2
        val lastEndY1 = (last.start1 + last.length1) * lineHeightPx
        val lastEndY2 = (last.start2 + last.length2) * lineHeightPx
        return lastEndY1 + (scrollY2 - lastEndY2)
    }

    class Builder {
        private val chunks = mutableListOf<Chunk>()

        fun put(start1: Int, start2: Int, length1: Int, length2: Int, exact: Boolean = false) {
            if (length1 == 0 && length2 == 0) return
            chunks.add(Chunk(start1, start2, length1, length2, exact))
        }

        fun build(): LineNumberConvertor = LineNumberConvertor(chunks.toList())
    }

    companion object {
        @JvmStatic
        fun fromLineFragments(
            lineFragments: List<LineFragment>,
            lineCount1: Int,
            lineCount2: Int
        ): LineNumberConvertor {
            val builder = Builder()
            var last1 = 0
            var last2 = 0
            for (fragment in lineFragments) {
                if (last1 < fragment.startLine1 || last2 < fragment.startLine2) {
                    builder.put(
                        last1,
                        last2,
                        fragment.startLine1 - last1,
                        fragment.startLine2 - last2,
                        exact = true
                    )
                }
                builder.put(
                    fragment.startLine1,
                    fragment.startLine2,
                    fragment.endLine1 - fragment.startLine1,
                    fragment.endLine2 - fragment.startLine2,
                    exact = false
                )
                last1 = fragment.endLine1
                last2 = fragment.endLine2
            }
            if (last1 < lineCount1 || last2 < lineCount2) {
                builder.put(last1, last2, lineCount1 - last1, lineCount2 - last2, exact = true)
            }
            return builder.build()
        }
    }
}
