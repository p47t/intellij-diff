// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.comparison.iterables.Range;
import com.intellij.diff.util.CancellationChecker;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.DiffConfig;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.diff.PatienceIntLCS;
import com.intellij.util.diff.Reindexer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class ByLineRt {
    private ByLineRt() {
    }

    @NotNull
    public static FairDiffIterable compare(
        @NotNull List<? extends CharSequence> lines1,
        @NotNull List<? extends CharSequence> lines2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) {
        indicator.checkCanceled();
        List<Line> iwLines1 = convertMode(lines1, ComparisonPolicy.IGNORE_WHITESPACES);
        List<Line> iwLines2 = convertMode(lines2, ComparisonPolicy.IGNORE_WHITESPACES);

        FairDiffIterable iwChanges = diff(iwLines1, iwLines2, indicator);
        iwChanges = optimizeLineChunks(lines1, lines2, iwLines1, iwLines2, iwChanges, indicator);

        if (policy == ComparisonPolicy.IGNORE_WHITESPACES) {
            return iwChanges;
        }

        List<Line> targetLines1 = convertMode(lines1, policy);
        List<Line> targetLines2 = convertMode(lines2, policy);

        return correctChangesSecondStep(targetLines1, targetLines2, iwChanges);
    }

    @NotNull
    private static FairDiffIterable correctChangesSecondStep(
        @NotNull List<Line> lines1,
        @NotNull List<Line> lines2,
        @NotNull FairDiffIterable changes
    ) {
        DiffIterableUtil.ChangeBuilder builder = new DiffIterableUtil.ChangeBuilder(lines1.size(), lines2.size());
        for (Range range : changes.iterateUnchanged()) {
            int count = range.end1 - range.start1;
            for (int i = 0; i < count; i++) {
                int index1 = range.start1 + i;
                int index2 = range.start2 + i;
                if (lines1.get(index1).equals(lines2.get(index2))) {
                    builder.markEqual(index1, index2);
                }
            }
        }
        return builder.finish();
    }

    @NotNull
    private static FairDiffIterable diff(
        @NotNull List<Line> lines1,
        @NotNull List<Line> lines2,
        @NotNull CancellationChecker indicator
    ) {
        indicator.checkCanceled();
        if (UsePatience.shouldUse(lines1, lines2)) {
            return diffPatience(lines1, lines2, indicator);
        }
        return diffSimple(lines1, lines2, indicator);
    }

    @NotNull
    private static FairDiffIterable diffSimple(
        @NotNull List<Line> lines1,
        @NotNull List<Line> lines2,
        @NotNull CancellationChecker indicator
    ) {
        try {
            indicator.checkCanceled();
            Line[] arr1 = lines1.toArray(new Line[0]);
            Line[] arr2 = lines2.toArray(new Line[0]);
            Diff.Change change = Diff.buildChanges(arr1, arr2);
            return DiffIterableUtil.create(change, lines1.size(), lines2.size());
        } catch (FilesTooBigForDiffException e) {
            throw new DiffTooBigException();
        }
    }

    @NotNull
    private static FairDiffIterable diffPatience(
        @NotNull List<Line> lines1,
        @NotNull List<Line> lines2,
        @NotNull CancellationChecker indicator
    ) {
        try {
            indicator.checkCanceled();
            Line[] arr1 = lines1.toArray(new Line[0]);
            Line[] arr2 = lines2.toArray(new Line[0]);
            Reindexer reindexer = new Reindexer();
            int[][] discarded = reindexer.discardUnique(arr1, arr2);

            PatienceIntLCS patienceIntLCS = new PatienceIntLCS(discarded[0], discarded[1]);
            patienceIntLCS.execute();

            BitSet[] changes = new BitSet[]{new BitSet(arr1.length), new BitSet(arr2.length)};
            reindexer.reindex(patienceIntLCS.getChanges(), changes);

            Diff.ChangeBuilder builder = new Diff.ChangeBuilder(0);
            builder.process(changes[0], changes[1], arr1.length, arr2.length);
            return DiffIterableUtil.create(builder.getFirstChange(), lines1.size(), lines2.size());
        } catch (FilesTooBigForDiffException e) {
            throw new DiffTooBigException();
        }
    }

    @NotNull
    private static FairDiffIterable optimizeLineChunks(
        @NotNull List<? extends CharSequence> rawLines1,
        @NotNull List<? extends CharSequence> rawLines2,
        @NotNull List<Line> iwLines1,
        @NotNull List<Line> iwLines2,
        @NotNull FairDiffIterable iterable,
        @NotNull CancellationChecker indicator
    ) {
        // Shift adjacent equal boundary lines to align chunks on blank lines or brace boundaries
        List<Range> unchanged = new ArrayList<>();
        for (Range r : iterable.iterateUnchanged()) {
            unchanged.add(r);
        }
        if (unchanged.isEmpty()) return iterable;

        for (int i = 0; i < unchanged.size(); i++) {
            indicator.checkCanceled();
            Range current = unchanged.get(i);
            int nextStart1 = (i + 1 < unchanged.size()) ? unchanged.get(i + 1).start1 : rawLines1.size();
            int nextStart2 = (i + 1 < unchanged.size()) ? unchanged.get(i + 1).start2 : rawLines2.size();

            // Check if the trailing line of `current` matches the trailing line before `next`
            int shift = 0;
            while (current.end1 - shift > current.start1 &&
                   nextStart1 - 1 - shift >= current.end1 &&
                   nextStart2 - 1 - shift >= current.end2) {
                Line trailingLine = iwLines1.get(current.end1 - 1 - shift);
                if (trailingLine.getNonSpaceChars() > DiffConfig.UNIMPORTANT_LINE_CHAR_COUNT) break;
                if (trailingLine.equals(iwLines1.get(nextStart1 - 1 - shift)) &&
                    trailingLine.equals(iwLines2.get(nextStart2 - 1 - shift))) {
                    shift++;
                } else {
                    break;
                }
            }
            if (shift > 0 && current.end1 - shift > current.start1) {
                unchanged.set(i, new Range(current.start1, current.end1 - shift, current.start2, current.end2 - shift));
                if (i + 1 < unchanged.size()) {
                    Range next = unchanged.get(i + 1);
                    unchanged.set(i + 1, new Range(next.start1 - shift, next.end1, next.start2 - shift, next.end2));
                }
            }
        }
        return DiffIterableUtil.createUnchanged(unchanged, rawLines1.size(), rawLines2.size());
    }

    @NotNull
    private static List<Line> convertMode(@NotNull List<? extends CharSequence> lines, @NotNull ComparisonPolicy policy) {
        List<Line> result = new ArrayList<>(lines.size());
        for (CharSequence line : lines) {
            result.add(new Line(line, policy));
        }
        return result;
    }

    private static final class UsePatience {
        static boolean shouldUse(@NotNull List<Line> lines1, @NotNull List<Line> lines2) {
            return DiffConfig.USE_PATIENCE_ALG || (lines1.size() + lines2.size() > 64);
        }
    }

    private static final class Line {
        @NotNull
        private final CharSequence myText;
        @NotNull
        private final ComparisonPolicy myPolicy;
        private final int myHash;
        private final int myNonSpaceChars;

        Line(@NotNull CharSequence text, @NotNull ComparisonPolicy policy) {
            myText = text;
            myPolicy = policy;
            myHash = switch (policy) {
                case DEFAULT -> text.toString().hashCode();
                case TRIM_WHITESPACES -> TrimUtil.trim(text).toString().hashCode();
                case IGNORE_WHITESPACES -> TrimUtil.hashCodeIgnoreWhitespaces(text);
            };
            myNonSpaceChars = countNonSpaceChars(text);
        }

        private static int countNonSpaceChars(@NotNull CharSequence text) {
            int count = 0;
            for (int i = 0; i < text.length(); i++) {
                if (!TrimUtil.isWhiteSpace(text.charAt(i))) count++;
            }
            return count;
        }

        int getNonSpaceChars() {
            return myNonSpaceChars;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Line line)) return false;
            if (myHash != line.myHash) return false;
            return TrimUtil.isEquals(myText, line.myText, myPolicy);
        }

        @Override
        public int hashCode() {
            return myHash;
        }

        @Override
        public String toString() {
            return myText.toString();
        }
    }
}
