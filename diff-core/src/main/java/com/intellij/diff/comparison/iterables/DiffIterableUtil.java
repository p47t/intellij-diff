// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import com.intellij.util.diff.Diff;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public final class DiffIterableUtil {
    private DiffIterableUtil() {
    }

    @NotNull
    public static FairDiffIterable fair(@NotNull DiffIterable iterable) {
        if (iterable instanceof FairDiffIterable fair) return fair;
        return new FairDiffIterableWrapper(iterable);
    }

    @NotNull
    public static FairDiffIterable create(@Nullable Diff.Change change, int length1, int length2) {
        List<Range> ranges = new ArrayList<>();
        Diff.Change current = change;
        while (current != null) {
            ranges.add(new Range(
                current.line0,
                current.line0 + current.deleted,
                current.line1,
                current.line1 + current.inserted
            ));
            current = current.link;
        }
        return create(ranges, length1, length2);
    }

    @NotNull
    public static FairDiffIterable create(@NotNull List<Range> changes, int length1, int length2) {
        return new RangesDiffIterable(changes, length1, length2);
    }

    @NotNull
    public static FairDiffIterable createUnchanged(@NotNull List<Range> unchanged, int length1, int length2) {
        List<Range> changes = new ArrayList<>();
        int last1 = 0;
        int last2 = 0;
        for (Range range : unchanged) {
            if (last1 < range.start1 || last2 < range.start2) {
                changes.add(new Range(last1, range.start1, last2, range.start2));
            }
            last1 = range.end1;
            last2 = range.end2;
        }
        if (last1 < length1 || last2 < length2) {
            changes.add(new Range(last1, length1, last2, length2));
        }
        return new RangesDiffIterable(changes, length1, length2);
    }

    public abstract static class ChangeBuilderBase {
        private final int myLength1;
        private final int myLength2;
        private int myIndex1 = 0;
        private int myIndex2 = 0;

        public ChangeBuilderBase(int length1, int length2) {
            myLength1 = length1;
            myLength2 = length2;
        }

        public int getIndex1() {
            return myIndex1;
        }

        public int getIndex2() {
            return myIndex2;
        }

        public int getLength1() {
            return myLength1;
        }

        public int getLength2() {
            return myLength2;
        }

        public void markEqual(int index1, int index2) {
            markEqual(index1, index2, 1);
        }

        public void markEqual(int index1, int index2, int count) {
            markEqual(index1, index2, index1 + count, index2 + count);
        }

        public void markEqual(int index1, int index2, int end1, int end2) {
            if (index1 == end1 && index2 == end2) return;
            if (myIndex1 < index1 || myIndex2 < index2) {
                addChange(myIndex1, myIndex2, index1, index2);
            }
            myIndex1 = end1;
            myIndex2 = end2;
        }

        protected void flush() {
            if (myIndex1 < myLength1 || myIndex2 < myLength2) {
                addChange(myIndex1, myIndex2, myLength1, myLength2);
                myIndex1 = myLength1;
                myIndex2 = myLength2;
            }
        }

        protected abstract void addChange(int start1, int start2, int end1, int end2);
    }

    public static class ChangeBuilder extends ChangeBuilderBase {
        private final List<Range> myChanges = new ArrayList<>();

        public ChangeBuilder(int length1, int length2) {
            super(length1, length2);
        }

        @Override
        protected void addChange(int start1, int start2, int end1, int end2) {
            myChanges.add(new Range(start1, end1, start2, end2));
        }

        @NotNull
        public FairDiffIterable finish() {
            flush();
            return create(myChanges, getLength1(), getLength2());
        }
    }

    private static class FairDiffIterableWrapper implements FairDiffIterable {
        private final DiffIterable myDelegate;

        FairDiffIterableWrapper(DiffIterable delegate) {
            myDelegate = delegate;
        }

        @Override
        public int getLength1() {
            return myDelegate.getLength1();
        }

        @Override
        public int getLength2() {
            return myDelegate.getLength2();
        }

        @Override
        @NotNull
        public Iterator<Range> changes() {
            return myDelegate.changes();
        }

        @Override
        @NotNull
        public Iterator<Range> unchanged() {
            return myDelegate.unchanged();
        }
    }

    private static class RangesDiffIterable implements FairDiffIterable {
        private final List<Range> myChanges;
        private final int myLength1;
        private final int myLength2;

        RangesDiffIterable(@NotNull List<Range> changes, int length1, int length2) {
            myChanges = Collections.unmodifiableList(changes);
            myLength1 = length1;
            myLength2 = length2;
        }

        @Override
        public int getLength1() {
            return myLength1;
        }

        @Override
        public int getLength2() {
            return myLength2;
        }

        @Override
        @NotNull
        public Iterator<Range> changes() {
            return myChanges.iterator();
        }

        @Override
        @NotNull
        public Iterator<Range> unchanged() {
            return new UnchangedIterator(myChanges.iterator(), myLength1, myLength2);
        }
    }

    private static class UnchangedIterator implements Iterator<Range> {
        private final Iterator<Range> myChanges;
        private final int myLength1;
        private final int myLength2;
        private int myLastIndex1 = 0;
        private int myLastIndex2 = 0;
        private Range myNextUnchanged;

        UnchangedIterator(Iterator<Range> changes, int length1, int length2) {
            myChanges = changes;
            myLength1 = length1;
            myLength2 = length2;
            advance();
        }

        private void advance() {
            myNextUnchanged = null;
            while (myChanges.hasNext()) {
                Range change = myChanges.next();
                int start1 = myLastIndex1;
                int start2 = myLastIndex2;
                myLastIndex1 = change.end1;
                myLastIndex2 = change.end2;
                if (start1 < change.start1 || start2 < change.start2) {
                    myNextUnchanged = new Range(start1, change.start1, start2, change.start2);
                    return;
                }
            }
            if (myLastIndex1 < myLength1 || myLastIndex2 < myLength2) {
                myNextUnchanged = new Range(myLastIndex1, myLength1, myLastIndex2, myLength2);
                myLastIndex1 = myLength1;
                myLastIndex2 = myLength2;
            }
        }

        @Override
        public boolean hasNext() {
            return myNextUnchanged != null;
        }

        @Override
        public Range next() {
            if (myNextUnchanged == null) throw new NoSuchElementException();
            Range result = myNextUnchanged;
            advance();
            return result;
        }
    }
}
