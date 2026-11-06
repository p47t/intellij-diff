// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import java.util.Objects;

public final class Range {
    public final int start1;
    public final int end1;
    public final int start2;
    public final int end2;

    public Range(int start1, int end1, int start2, int end2) {
        if (start1 > end1 || start2 > end2) {
            throw new IllegalArgumentException("Invalid Range: [" + start1 + ", " + end1 + ") - [" + start2 + ", " + end2 + ")");
        }
        this.start1 = start1;
        this.end1 = end1;
        this.start2 = start2;
        this.end2 = end2;
    }

    public boolean isEmpty() {
        return start1 == end1 && start2 == end2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Range range)) return false;
        return start1 == range.start1 && end1 == range.end1 && start2 == range.start2 && end2 == range.end2;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start1, end1, start2, end2);
    }

    @Override
    public String toString() {
        return "[" + start1 + ", " + end1 + ") - [" + start2 + ", " + end2 + ")";
    }
}
