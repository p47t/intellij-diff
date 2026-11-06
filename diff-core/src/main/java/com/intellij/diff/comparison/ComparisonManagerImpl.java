// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.comparison.iterables.Range;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.diff.fragments.LineFragmentImpl;
import com.intellij.diff.util.CancellationChecker;
import com.intellij.diff.util.TextRange;
import org.ijdiff.core.LineOffsets;
import org.ijdiff.core.LineOffsetsImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ComparisonManagerImpl extends ComparisonManager {
    private static final ComparisonManagerImpl INSTANCE = new ComparisonManagerImpl();

    @NotNull
    public static ComparisonManagerImpl getInstanceImpl() {
        return INSTANCE;
    }

    @Override
    @NotNull
    public List<LineFragment> compareLines(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException {
        LineOffsets lineOffsets1 = LineOffsetsImpl.create(text1);
        LineOffsets lineOffsets2 = LineOffsetsImpl.create(text2);
        return compareLines(text1, text2, lineOffsets1, lineOffsets2, policy, indicator);
    }

    @NotNull
    public List<LineFragment> compareLines(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull LineOffsets lineOffsets1,
        @NotNull LineOffsets lineOffsets2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException {
        List<CharSequence> lines1 = getLineContents(text1, lineOffsets1);
        List<CharSequence> lines2 = getLineContents(text2, lineOffsets2);

        FairDiffIterable lineChanges = ByLineRt.compare(lines1, lines2, policy, indicator);
        return convertIntoLineFragments(lineOffsets1, lineOffsets2, lineChanges);
    }

    @Override
    @NotNull
    public List<LineFragment> compareLinesInner(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException {
        List<LineFragment> lineFragments = compareLines(text1, text2, policy, indicator);
        return compareLinesInner(text1, text2, lineFragments, policy, InnerFragmentsPolicy.WORDS, indicator);
    }

    @Override
    @NotNull
    public List<LineFragment> compareLinesInner(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull List<LineFragment> lineFragments,
        @NotNull ComparisonPolicy policy,
        @NotNull InnerFragmentsPolicy fragmentsPolicy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException {
        if (fragmentsPolicy == InnerFragmentsPolicy.NONE) return lineFragments;

        List<LineFragment> result = new ArrayList<>(lineFragments.size());
        int tooBigChunksCount = 0;

        for (LineFragment fragment : lineFragments) {
            indicator.checkCanceled();
            if (fragment.getInnerFragments() != null) {
                result.add(fragment);
                continue;
            }
            // Pure insertion or pure deletion of lines has no inline word diff
            if (fragment.getStartLine1() == fragment.getEndLine1() ||
                fragment.getStartLine2() == fragment.getEndLine2()) {
                result.add(fragment);
                continue;
            }

            if (tooBigChunksCount >= 3) {
                result.add(fragment);
                continue;
            }

            CharSequence subSequence1 = text1.subSequence(fragment.getStartOffset1(), fragment.getEndOffset1());
            CharSequence subSequence2 = text2.subSequence(fragment.getStartOffset2(), fragment.getEndOffset2());

            try {
                List<DiffFragment> innerFragments = switch (fragmentsPolicy) {
                    case WORDS -> ByWordRt.compare(subSequence1, subSequence2, policy, indicator);
                    case CHARS -> ByCharRt.compare(subSequence1, subSequence2, policy, indicator);
                    case NONE -> Collections.emptyList();
                };
                result.add(new LineFragmentImpl(fragment, innerFragments));
            } catch (DiffTooBigException e) {
                result.add(fragment);
                tooBigChunksCount++;
            }
        }
        return result;
    }

    @Override
    @NotNull
    public List<DiffFragment> compareWords(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException {
        return ByWordRt.compare(text1, text2, policy, indicator);
    }

    @Override
    @NotNull
    public List<DiffFragment> compareChars(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        @NotNull CancellationChecker indicator
    ) throws DiffTooBigException {
        return ByCharRt.compare(text1, text2, policy, indicator);
    }

    @Override
    public boolean isEquals(
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy
    ) {
        return TrimUtil.isEquals(text1, text2, policy);
    }

    @NotNull
    public static List<LineFragment> convertIntoLineFragments(
        @NotNull LineOffsets lineOffsets1,
        @NotNull LineOffsets lineOffsets2,
        @NotNull FairDiffIterable changes
    ) {
        List<LineFragment> fragments = new ArrayList<>();
        for (Range ch : changes.iterateChanges()) {
            if (ch.isEmpty()) continue;
            TextRange range1 = getOffsets(lineOffsets1, ch.start1, ch.end1);
            TextRange range2 = getOffsets(lineOffsets2, ch.start2, ch.end2);
            fragments.add(new LineFragmentImpl(
                ch.start1, ch.end1, ch.start2, ch.end2,
                range1.getStartOffset(), range1.getEndOffset(),
                range2.getStartOffset(), range2.getEndOffset()
            ));
        }
        return fragments;
    }

    @NotNull
    private static TextRange getOffsets(@NotNull LineOffsets lineOffsets, int startLine, int endLine) {
        if (startLine == endLine) {
            int offset = startLine < lineOffsets.getLineCount()
                ? lineOffsets.getLineStart(startLine)
                : lineOffsets.getTextLength();
            return new TextRange(offset, offset);
        } else {
            int startOffset = lineOffsets.getLineStart(startLine);
            int endOffset = lineOffsets.getLineEnd(endLine - 1, true);
            return new TextRange(startOffset, endOffset);
        }
    }

    @NotNull
    public static List<CharSequence> getLineContents(@NotNull CharSequence text, @NotNull LineOffsets lineOffsets) {
        int lineCount = lineOffsets.getLineCount();
        List<CharSequence> lines = new ArrayList<>(lineCount);
        for (int i = 0; i < lineCount; i++) {
            lines.add(text.subSequence(lineOffsets.getLineStart(i), lineOffsets.getLineEnd(i, false)));
        }
        return lines;
    }

    @Override
    @NotNull
    public List<LineFragment> squash(@NotNull List<LineFragment> oldFragments) {
        if (oldFragments.isEmpty()) return oldFragments;
        List<LineFragment> newFragments = new ArrayList<>(oldFragments.size());
        LineFragment previous = null;
        for (LineFragment fragment : oldFragments) {
            if (previous != null &&
                previous.getEndLine1() == fragment.getStartLine1() &&
                previous.getEndLine2() == fragment.getStartLine2()) {
                List<DiffFragment> combinedInner = null;
                if (previous.getInnerFragments() != null && fragment.getInnerFragments() != null) {
                    combinedInner = new ArrayList<>(previous.getInnerFragments());
                    int shift1 = fragment.getStartOffset1() - previous.getStartOffset1();
                    int shift2 = fragment.getStartOffset2() - previous.getStartOffset2();
                    for (DiffFragment df : fragment.getInnerFragments()) {
                        combinedInner.add(new com.intellij.diff.fragments.DiffFragmentImpl(
                            df.getStartOffset1() + shift1,
                            df.getEndOffset1() + shift1,
                            df.getStartOffset2() + shift2,
                            df.getEndOffset2() + shift2
                        ));
                    }
                }
                previous = new LineFragmentImpl(
                    previous.getStartLine1(), fragment.getEndLine1(),
                    previous.getStartLine2(), fragment.getEndLine2(),
                    previous.getStartOffset1(), fragment.getEndOffset1(),
                    previous.getStartOffset2(), fragment.getEndOffset2(),
                    combinedInner
                );
                newFragments.set(newFragments.size() - 1, previous);
            } else {
                newFragments.add(fragment);
                previous = fragment;
            }
        }
        return newFragments;
    }

    @Override
    @NotNull
    public List<LineFragment> processBlocks(
        @NotNull List<LineFragment> oldFragments,
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy,
        boolean squash,
        boolean trim
    ) {
        List<LineFragment> result = oldFragments;
        if (squash) {
            result = squash(result);
        }
        if (trim) {
            result = trimIgnoredBlocks(result, text1, text2, policy);
        }
        return result;
    }

    @NotNull
    private List<LineFragment> trimIgnoredBlocks(
        @NotNull List<LineFragment> oldFragments,
        @NotNull CharSequence text1,
        @NotNull CharSequence text2,
        @NotNull ComparisonPolicy policy
    ) {
        if (policy == ComparisonPolicy.DEFAULT) return oldFragments;
        LineOffsets offsets1 = LineOffsetsImpl.create(text1);
        LineOffsets offsets2 = LineOffsetsImpl.create(text2);
        List<LineFragment> result = new ArrayList<>(oldFragments.size());
        for (LineFragment f : oldFragments) {
            int s1 = f.getStartLine1();
            int e1 = f.getEndLine1();
            int s2 = f.getStartLine2();
            int e2 = f.getEndLine2();

            while (s1 < e1 && s2 < e2) {
                CharSequence l1 = text1.subSequence(offsets1.getLineStart(s1), offsets1.getLineEnd(s1, false));
                CharSequence l2 = text2.subSequence(offsets2.getLineStart(s2), offsets2.getLineEnd(s2, false));
                if (TrimUtil.isEquals(l1, l2, policy)) {
                    s1++;
                    s2++;
                } else {
                    break;
                }
            }
            while (s1 < e1 && s2 < e2) {
                CharSequence l1 = text1.subSequence(offsets1.getLineStart(e1 - 1), offsets1.getLineEnd(e1 - 1, false));
                CharSequence l2 = text2.subSequence(offsets2.getLineStart(e2 - 1), offsets2.getLineEnd(e2 - 1, false));
                if (TrimUtil.isEquals(l1, l2, policy)) {
                    e1--;
                    e2--;
                } else {
                    break;
                }
            }
            if (s1 < e1 || s2 < e2) {
                if (s1 == f.getStartLine1() && e1 == f.getEndLine1() && s2 == f.getStartLine2() && e2 == f.getEndLine2()) {
                    result.add(f);
                } else {
                    TextRange r1 = getOffsets(offsets1, s1, e1);
                    TextRange r2 = getOffsets(offsets2, s2, e2);
                    result.add(new LineFragmentImpl(
                        s1, e1, s2, e2,
                        r1.getStartOffset(), r1.getEndOffset(),
                        r2.getStartOffset(), r2.getEndOffset()
                    ));
                }
            }
        }
        return result;
    }
}
