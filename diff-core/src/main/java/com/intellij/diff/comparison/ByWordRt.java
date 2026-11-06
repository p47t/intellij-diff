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
import java.util.Objects;

public final class ByWordRt {
    private ByWordRt() {
    }

    @NotNull
    public static List<DiffFragment> compare(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) {
        indicator.checkCanceled();

        List<InlineChunk> words1 = getInlineChunks(text1);
        List<InlineChunk> words2 = getInlineChunks(text2);

        return compare(text1, words1, text2, words2, policy, indicator);
    }

    @NotNull
    public static List<DiffFragment> compare(
        @NotNull CharSequence text1,
        @NotNull List<InlineChunk> words1,
        @NotNull CharSequence text2,
        @NotNull List<InlineChunk> words2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) {
        FairDiffIterable wordChanges = diff(words1, words2, indicator);

        DiffIterableUtil.ChangeBuilder chBuilder = new DiffIterableUtil.ChangeBuilder(text1.length(), text2.length());

        int count1 = words1.size();
        int count2 = words2.size();

        // Check leading whitespace before the first token
        int startOffset1 = count1 > 0 ? words1.get(0).getOffset1() : text1.length();
        int startOffset2 = count2 > 0 ? words2.get(0).getOffset1() : text2.length();
        matchSpaces(text1, 0, startOffset1, text2, 0, startOffset2, policy, chBuilder);

        for (Range range : wordChanges.iterateUnchanged()) {
            int count = range.end1 - range.start1;
            if (count == 0) continue;

            // Match trailing spaces before the first unchanged word of this block
            int prevEnd1 = range.start1 > 0 ? words1.get(range.start1 - 1).getOffset2() : 0;
            int prevEnd2 = range.start2 > 0 ? words2.get(range.start2 - 1).getOffset2() : 0;
            int firstStart1 = words1.get(range.start1).getOffset1();
            int firstStart2 = words2.get(range.start2).getOffset1();
            if (range.start1 > 0 || range.start2 > 0) {
                matchSpaces(text1, prevEnd1, firstStart1, text2, prevEnd2, firstStart2, policy, chBuilder);
            }

            for (int i = 0; i < count; i++) {
                int idx1 = range.start1 + i;
                int idx2 = range.start2 + i;
                InlineChunk w1 = words1.get(idx1);
                InlineChunk w2 = words2.get(idx2);
                chBuilder.markEqual(w1.getOffset1(), w2.getOffset1(), w1.getOffset2(), w2.getOffset2());

                int nextStart1 = (idx1 + 1 < count1) ? words1.get(idx1 + 1).getOffset1() : text1.length();
                int nextStart2 = (idx2 + 1 < count2) ? words2.get(idx2 + 1).getOffset1() : text2.length();
                matchSpaces(text1, w1.getOffset2(), nextStart1, text2, w2.getOffset2(), nextStart2, policy, chBuilder);
            }
        }

        FairDiffIterable charIterable = chBuilder.finish();
        List<DiffFragment> fragments = new ArrayList<>();
        for (Range range : charIterable.iterateChanges()) {
            if (!range.isEmpty()) {
                fragments.add(new DiffFragmentImpl(range.start1, range.end1, range.start2, range.end2));
            }
        }
        return fragments;
    }

    private static void matchSpaces(
        @NotNull CharSequence text1, int start1, int end1,
        @NotNull CharSequence text2, int start2, int end2,
        @NotNull ComparisonPolicy policy,
        @NotNull DiffIterableUtil.ChangeBuilder builder
    ) {
        start1 = Math.max(start1, builder.getIndex1());
        start2 = Math.max(start2, builder.getIndex2());
        if (start1 > end1 || start2 > end2) return;

        switch (policy) {
            case DEFAULT -> {
                int len1 = end1 - start1;
                int len2 = end2 - start2;
                int prefix = 0;
                while (prefix < len1 && prefix < len2 && text1.charAt(start1 + prefix) == text2.charAt(start2 + prefix)) {
                    prefix++;
                }
                int suffix = 0;
                while (suffix < len1 - prefix && suffix < len2 - prefix &&
                       text1.charAt(end1 - 1 - suffix) == text2.charAt(end2 - 1 - suffix)) {
                    suffix++;
                }
                if (prefix > 0) {
                    builder.markEqual(start1, start2, start1 + prefix, start2 + prefix);
                }
                if (suffix > 0) {
                    builder.markEqual(end1 - suffix, end2 - suffix, end1, end2);
                }
            }
            case TRIM_WHITESPACES -> {
                // If at start or end of line/text, ignore whitespace differences
                boolean atEdge = (start1 == 0 && start2 == 0) || (end1 == text1.length() && end2 == text2.length());
                if (atEdge) {
                    builder.markEqual(start1, start2, end1, end2);
                } else {
                    matchSpaces(text1, start1, end1, text2, start2, end2, ComparisonPolicy.DEFAULT, builder);
                }
            }
            case IGNORE_WHITESPACES -> builder.markEqual(start1, start2, end1, end2);
        }
    }

    @NotNull
    private static FairDiffIterable diff(
        @NotNull List<InlineChunk> words1,
        @NotNull List<InlineChunk> words2,
        @NotNull CancellationChecker indicator
    ) {
        try {
            indicator.checkCanceled();
            InlineChunk[] arr1 = words1.toArray(new InlineChunk[0]);
            InlineChunk[] arr2 = words2.toArray(new InlineChunk[0]);
            Diff.Change change = Diff.buildChanges(arr1, arr2);
            return DiffIterableUtil.create(change, words1.size(), words2.size());
        } catch (FilesTooBigForDiffException e) {
            throw new DiffTooBigException();
        }
    }

    @NotNull
    public static List<InlineChunk> getInlineChunks(@NotNull CharSequence text) {
        List<InlineChunk> chunks = new ArrayList<>();
        int len = text.length();
        int offset = 0;
        while (offset < len) {
            char ch = text.charAt(offset);
            if (TrimUtil.isAlpha(ch)) {
                int startOffset = offset;
                int h = 0;
                while (offset < len) {
                    char c = text.charAt(offset);
                    if (!TrimUtil.isAlpha(c)) break;
                    h = 31 * h + c;
                    offset++;
                }
                chunks.add(new WordChunk(text, startOffset, offset, h));
            } else if (ch == '\n') {
                chunks.add(new NewlineChunk(offset));
                offset++;
            } else if (TrimUtil.isPunctuation(ch)) {
                chunks.add(new WordChunk(text, offset, offset + 1, ch));
                offset++;
            } else {
                offset++;
            }
        }
        return chunks;
    }

    public interface InlineChunk {
        int getOffset1();

        int getOffset2();
    }

    public static final class WordChunk implements InlineChunk {
        @NotNull
        private final CharSequence myText;
        private final int myOffset1;
        private final int myOffset2;
        private final int myHash;

        public WordChunk(@NotNull CharSequence text, int offset1, int offset2, int hash) {
            myText = text;
            myOffset1 = offset1;
            myOffset2 = offset2;
            myHash = hash;
        }

        @NotNull
        public CharSequence getContent() {
            return myText.subSequence(myOffset1, myOffset2);
        }

        @Override
        public int getOffset1() {
            return myOffset1;
        }

        @Override
        public int getOffset2() {
            return myOffset2;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WordChunk word)) return false;
            if (myHash != word.myHash) return false;
            return TrimUtil.isEqualsDefault(getContent(), word.getContent());
        }

        @Override
        public int hashCode() {
            return myHash;
        }
    }

    public static final class NewlineChunk implements InlineChunk {
        private final int myOffset1;
        private final int myOffset2;

        public NewlineChunk(int offset) {
            myOffset1 = offset;
            myOffset2 = offset + 1;
        }

        @Override
        public int getOffset1() {
            return myOffset1;
        }

        @Override
        public int getOffset2() {
            return myOffset2;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof NewlineChunk;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode('\n');
        }
    }
}
