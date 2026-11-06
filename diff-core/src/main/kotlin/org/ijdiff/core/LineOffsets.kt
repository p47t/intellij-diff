// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.ijdiff.core

import java.util.Arrays

interface LineOffsets {
    val lineCount: Int
    val textLength: Int

    fun getLineStart(line: Int): Int
    fun getLineEnd(line: Int): Int
    fun getLineEnd(line: Int, includeNewline: Boolean): Int
    fun getLineNumber(offset: Int): Int
}

class LineOffsetsImpl private constructor(
    private val lineEnds: IntArray,
    override val textLength: Int
) : LineOffsets {

    override val lineCount: Int
        get() = lineEnds.size

    override fun getLineStart(line: Int): Int {
        checkLineIndex(line)
        if (line == 0) return 0
        return lineEnds[line - 1] + 1
    }

    override fun getLineEnd(line: Int): Int = getLineEnd(line, false)

    override fun getLineEnd(line: Int, includeNewline: Boolean): Int {
        checkLineIndex(line)
        return lineEnds[line] + if (includeNewline && line != lineEnds.size - 1) 1 else 0
    }

    override fun getLineNumber(offset: Int): Int {
        if (offset < 0 || offset > textLength) {
            throw IndexOutOfBoundsException("Wrong offset: $offset. Available text length: $textLength")
        }
        if (offset == 0) return 0
        if (offset == textLength) return lineCount - 1

        val bsResult = Arrays.binarySearch(lineEnds, offset)
        return if (bsResult >= 0) bsResult else -bsResult - 1
    }

    private fun checkLineIndex(index: Int) {
        if (index < 0 || index >= lineCount) {
            throw IndexOutOfBoundsException("Wrong line: $index. Available lines count: $lineCount")
        }
    }

    companion object {
        @JvmStatic
        fun create(text: CharSequence): LineOffsets {
            val ends = IntArrayList()
            val len = text.length
            for (i in 0 until len) {
                if (text[i] == '\n') {
                    ends.add(i)
                }
            }
            ends.add(len)
            return LineOffsetsImpl(ends.toArray(), len)
        }
    }

    private class IntArrayList(capacity: Int = 16) {
        private var data = IntArray(capacity)
        private var size = 0

        fun add(element: Int) {
            if (size == data.size) {
                data = data.copyOf(data.size * 2)
            }
            data[size++] = element
        }

        fun toArray(): IntArray = data.copyOf(size)
    }
}
