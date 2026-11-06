// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.ijdiff.core

import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.fragments.LineFragment

enum class TextDiffType {
    INSERTED,
    DELETED,
    MODIFIED,
    CONFLICT
}

fun LineFragment.getDiffType(): TextDiffType {
    val len1 = endLine1 - startLine1
    val len2 = endLine2 - startLine2
    return when {
        len1 == 0 -> TextDiffType.INSERTED
        len2 == 0 -> TextDiffType.DELETED
        else -> TextDiffType.MODIFIED
    }
}

fun DiffFragment.getDiffType(): TextDiffType {
    val len1 = endOffset1 - startOffset1
    val len2 = endOffset2 - startOffset2
    return when {
        len1 == 0 -> TextDiffType.INSERTED
        len2 == 0 -> TextDiffType.DELETED
        else -> TextDiffType.MODIFIED
    }
}
