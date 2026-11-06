// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.util.CancellationChecker;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public abstract class ComparisonManager {
    @NotNull
    public static ComparisonManager getInstance() {
        return ComparisonManagerImpl.getInstanceImpl();
    }

    @NotNull
    public abstract List<LineFragment> compareLines(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException;

    @NotNull
    public abstract List<LineFragment> compareLinesInner(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException;

    @NotNull
    public abstract List<LineFragment> compareLinesInner(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull List<LineFragment> lineFragments,
        @NotNull ComparisonPolicy policy,
        @NotNull InnerFragmentsPolicy fragmentsPolicy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException;

    @NotNull
    public abstract List<DiffFragment> compareWords(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException;

    @NotNull
    public abstract List<DiffFragment> compareChars(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException;

    public abstract boolean isEquals(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy
    );

    @NotNull
    public abstract List<LineFragment> squash(@NotNull List<LineFragment> oldFragments);

    @NotNull
    public abstract List<LineFragment> processBlocks(
        @NotNull List<LineFragment> oldFragments,
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        boolean squash,
        boolean trim
    );
}
