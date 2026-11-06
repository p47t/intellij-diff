// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.comparison.iterables.Range;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.DiffFragmentImpl;
import com.intellij.diff.util.CancellationChecker;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class ByCharRt {
    private ByCharRt() {
    }

    @NotNull
    public static List<DiffFragment> compare(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) {
        indicator.checkCanceled();
        FairDiffIterable iterable = switch (policy) {
            case DEFAULT -> compareDefault(text1, text2, indicator);
            case TRIM_WHITESPACES -> compareTrimWhitespaces(text1, text2, indicator);
            case IGNORE_WHITESPACES -> compareIgnoreWhitespaces(text1, text2, indicator);
        };

        List<DiffFragment> fragments = new ArrayList<>();
        for (Range range : iterable.iterateChanges()) {
            if (!range.isEmpty()) {
                fragments.add(new DiffFragmentImpl(range.start1, range.end1, range.start2, range.end2));
            }
        }
        return fragments;
    }

    @NotNull
    public static FairDiffIterable compareDefault(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull CancellationChecker indicator
    ) {
        indicator.checkCanceled();
        Character[] chars1 = toCharacterArray(text1);
        Character[] chars2 = toCharacterArray(text2);
        try {
            Diff.Change change = Diff.buildChanges(chars1, chars2);
            return DiffIterableUtil.create(change, text1.length(), text2.length());
        } catch (FilesTooBigForDiffException e) {
            throw new DiffTooBigException();
        }
    }

    @NotNull
    private static FairDiffIterable compareTrimWhitespaces(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull CancellationChecker indicator
    ) {
        int start1 = TrimUtil.trimStart(text1, 0, text1.length());
        int end1 = TrimUtil.trimEnd(text1, start1, text1.length());
        int start2 = TrimUtil.trimStart(text2, 0, text2.length());
        int end2 = TrimUtil.trimEnd(text2, start2, text2.length());

        FairDiffIterable inner = compareDefault(text1.subSequence(start1, end1), text2.subSequence(start2, end2), indicator);
        DiffIterableUtil.ChangeBuilder builder = new DiffIterableUtil.ChangeBuilder(text1.length(), text2.length());
        builder.markEqual(0, 0, start1, start2);
        for (Range range : inner.iterateUnchanged()) {
            builder.markEqual(start1 + range.start1, start2 + range.start2, start1 + range.end1, start2 + range.end2);
        }
        builder.markEqual(end1, end2, text1.length(), text2.length());
        return builder.finish();
    }

    @NotNull
    private static FairDiffIterable compareIgnoreWhitespaces(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull CancellationChecker indicator
    ) {
        indicator.checkCanceled();
        List<Integer> offsets1 = new ArrayList<>();
        List<Character> chars1 = new ArrayList<>();
        for (int i = 0; i < text1.length(); i++) {
            char c = text1.charAt(i);
            if (!TrimUtil.isWhiteSpace(c)) {
                offsets1.add(i);
                chars1.add(c);
            }
        }
        List<Integer> offsets2 = new ArrayList<>();
        List<Character> chars2 = new ArrayList<>();
        for (int i = 0; i < text2.length(); i++) {
            char c = text2.charAt(i);
            if (!TrimUtil.isWhiteSpace(c)) {
                offsets2.add(i);
                chars2.add(c);
            }
        }
        try {
            Diff.Change change = Diff.buildChanges(chars1.toArray(new Character[0]), chars2.toArray(new Character[0]));
            FairDiffIterable nonSpace = DiffIterableUtil.create(change, chars1.size(), chars2.size());

            DiffIterableUtil.ChangeBuilder builder = new DiffIterableUtil.ChangeBuilder(text1.length(), text2.length());
            int s1 = offsets1.isEmpty() ? text1.length() : offsets1.get(0);
            int s2 = offsets2.isEmpty() ? text2.length() : offsets2.get(0);
            builder.markEqual(0, 0, s1, s2);

            for (Range range : nonSpace.iterateUnchanged()) {
                int count = range.end1 - range.start1;
                for (int i = 0; i < count; i++) {
                    int idx1 = range.start1 + i;
                    int idx2 = range.start2 + i;
                    int off1 = offsets1.get(idx1);
                    int off2 = offsets2.get(idx2);
                    int next1 = (idx1 + 1 < offsets1.size()) ? offsets1.get(idx1 + 1) : text1.length();
                    int next2 = (idx2 + 1 < offsets2.size()) ? offsets2.get(idx2 + 1) : text2.length();
                    if (i < count - 1) {
                        builder.markEqual(off1, off2, next1, next2);
                    } else {
                        builder.markEqual(off1, off2, off1 + 1, off2 + 1);
                    }
                }
            }
            if (!offsets1.isEmpty() && !offsets2.isEmpty()) {
                int last1 = offsets1.get(offsets1.size() - 1) + 1;
                int last2 = offsets2.get(offsets2.size() - 1) + 1;
                builder.markEqual(last1, last2, text1.length(), text2.length());
            }
            return builder.finish();
        } catch (FilesTooBigForDiffException e) {
            throw new DiffTooBigException();
        }
    }

    private static Character @NotNull [] toCharacterArray(@NotNull CharSequence text) {
        Character[] result = new Character[text.length()];
        for (int i = 0; i < text.length(); i++) {
            result[i] = text.charAt(i);
        }
        return result;
    }
}
