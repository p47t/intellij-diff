// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import org.jetbrains.annotations.NotNull;

public final class TextRange {
    public static final TextRange EMPTY_RANGE = new TextRange(0, 0);

    private final int myStartOffset;
    private final int myEndOffset;

    public TextRange(int startOffset, int endOffset) {
        if (startOffset > endOffset) {
            throw new IllegalArgumentException("Invalid range: [" + startOffset + ", " + endOffset + ")");
        }
        myStartOffset = startOffset;
        myEndOffset = endOffset;
    }

    public int getStartOffset() {
        return myStartOffset;
    }

    public int getEndOffset() {
        return myEndOffset;
    }

    public int getLength() {
        return myEndOffset - myStartOffset;
    }

    public boolean isEmpty() {
        return myStartOffset >= myEndOffset;
    }

    @NotNull
    public CharSequence subSequence(@NotNull CharSequence str) {
        return str.subSequence(myStartOffset, myEndOffset);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TextRange that)) return false;
        return myStartOffset == that.myStartOffset && myEndOffset == that.myEndOffset;
    }

    @Override
    public int hashCode() {
        return 31 * myStartOffset + myEndOffset;
    }

    @Override
    public String toString() {
        return "(" + myStartOffset + "," + myEndOffset + ")";
    }
}
