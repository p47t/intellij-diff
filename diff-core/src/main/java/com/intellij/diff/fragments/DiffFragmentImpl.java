// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments;

import java.util.Objects;

public class DiffFragmentImpl implements DiffFragment {
    private final int myStartOffset1;
    private final int myEndOffset1;
    private final int myStartOffset2;
    private final int myEndOffset2;

    public DiffFragmentImpl(int startOffset1, int endOffset1, int startOffset2, int endOffset2) {
        if (startOffset1 == endOffset1 && startOffset2 == endOffset2) {
            throw new IllegalArgumentException("DiffFragmentImpl should not be empty");
        }
        if (startOffset1 > endOffset1 || startOffset2 > endOffset2) {
            throw new IllegalArgumentException(
                "Invalid DiffFragmentImpl: [" + startOffset1 + ", " + endOffset1 + ") - [" + startOffset2 + ", " + endOffset2 + ")"
            );
        }
        myStartOffset1 = startOffset1;
        myEndOffset1 = endOffset1;
        myStartOffset2 = startOffset2;
        myEndOffset2 = endOffset2;
    }

    @Override
    public int getStartOffset1() {
        return myStartOffset1;
    }

    @Override
    public int getEndOffset1() {
        return myEndOffset1;
    }

    @Override
    public int getStartOffset2() {
        return myStartOffset2;
    }

    @Override
    public int getEndOffset2() {
        return myEndOffset2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiffFragmentImpl fragment)) return false;
        return myStartOffset1 == fragment.myStartOffset1 &&
               myEndOffset1 == fragment.myEndOffset1 &&
               myStartOffset2 == fragment.myStartOffset2 &&
               myEndOffset2 == fragment.myEndOffset2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(myStartOffset1, myEndOffset1, myStartOffset2, myEndOffset2);
    }

    @Override
    public String toString() {
        return "DiffFragmentImpl [" + myStartOffset1 + ", " + myEndOffset1 + ") - [" + myStartOffset2 + ", " + myEndOffset2 + ")";
    }
}
