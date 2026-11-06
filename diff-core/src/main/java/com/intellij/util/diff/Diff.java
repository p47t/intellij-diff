// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class Diff {
    private Diff() {
    }

    @Nullable
    public static Change buildChanges(@NotNull CharSequence before, @NotNull CharSequence after)
        throws FilesTooBigForDiffException {
        return buildChanges(splitLines(before), splitLines(after));
    }

    @NotNull
    public static String[] splitLines(@NotNull CharSequence s) {
        if (s.isEmpty()) return new String[]{""};
        List<String> lines = new ArrayList<>();
        int start = 0;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            if (ch == '\n') {
                int end = (i > start && s.charAt(i - 1) == '\r') ? i - 1 : i;
                lines.add(s.subSequence(start, end).toString());
                start = i + 1;
            }
        }
        if (start < len) {
            lines.add(s.subSequence(start, len).toString());
        }
        return lines.toArray(new String[0]);
    }

    @Nullable
    public static <T> Change buildChanges(T @NotNull [] objects1, T @NotNull [] objects2)
        throws FilesTooBigForDiffException {
        int startShift = getStartShift(objects1, objects2);
        int endCut = getEndCut(objects1, objects2, startShift);

        int trimmedLen1 = objects1.length - startShift - endCut;
        int trimmedLen2 = objects2.length - startShift - endCut;
        if (trimmedLen1 == 0 && trimmedLen2 == 0) {
            return null;
        }
        if (trimmedLen1 == 0 || trimmedLen2 == 0) {
            return new Change(startShift, startShift, trimmedLen1, trimmedLen2, null);
        }

        Object[] trimmed1 = new Object[trimmedLen1];
        Object[] trimmed2 = new Object[trimmedLen2];
        System.arraycopy(objects1, startShift, trimmed1, 0, trimmedLen1);
        System.arraycopy(objects2, startShift, trimmed2, 0, trimmedLen2);

        Reindexer reindexer = new Reindexer();
        int[][] discarded = reindexer.discardUnique(trimmed1, trimmed2);

        IntLCS intLCS = new IntLCS(discarded[0], discarded[1]);
        intLCS.execute();

        BitSet[] changes = new BitSet[]{new BitSet(trimmed1.length), new BitSet(trimmed2.length)};
        reindexer.reindex(intLCS.getChanges(), changes);

        ChangeBuilder builder = new ChangeBuilder(startShift);
        builder.process(changes[0], changes[1], trimmed1.length, trimmed2.length);
        return builder.getFirstChange();
    }

    private static <T> int getStartShift(T @NotNull [] o1, T @NotNull [] o2) {
        int size = Math.min(o1.length, o2.length);
        for (int i = 0; i < size; i++) {
            if (!Objects.equals(o1[i], o2[i])) return i;
        }
        return size;
    }

    private static <T> int getEndCut(T @NotNull [] o1, T @NotNull [] o2, int startShift) {
        int size = Math.min(o1.length, o2.length) - startShift;
        for (int i = 0; i < size; i++) {
            if (!Objects.equals(o1[o1.length - i - 1], o2[o2.length - i - 1])) return i;
        }
        return size;
    }

    public static int translateLine(@NotNull CharSequence before, @NotNull CharSequence after, int line, boolean approximate)
        throws FilesTooBigForDiffException {
        String[] strings1 = splitLines(before);
        String[] strings2 = splitLines(after);
        if (approximate) {
            line = Math.min(line, strings1.length - 1);
        }
        Change change = buildChanges(strings1, strings2);
        return translateLine(change, line, approximate);
    }

    public static int translateLine(@Nullable Change change, int line) {
        return translateLine(change, line, false);
    }

    public static int translateLine(@Nullable Change change, int line, boolean approximate) {
        if (line < 0) return -1;
        int result = line;
        Change currentChange = change;
        while (currentChange != null) {
            if (line < currentChange.line0) break;
            if (line >= currentChange.line0 + currentChange.deleted) {
                result += currentChange.inserted - currentChange.deleted;
            } else {
                return approximate ? currentChange.line1 : -1;
            }
            currentChange = currentChange.link;
        }
        return result;
    }

    public static class Change {
        public final int inserted;
        public final int deleted;
        public final int line0;
        public final int line1;
        @Nullable
        public Change link;

        public Change(int line0, int line1, int deleted, int inserted, @Nullable Change link) {
            this.inserted = inserted;
            this.deleted = deleted;
            this.line0 = line0;
            this.line1 = line1;
            this.link = link;
        }

        @NotNull
        public List<Change> toList() {
            List<Change> result = new ArrayList<>();
            Change current = this;
            while (current != null) {
                result.add(current);
                current = current.link;
            }
            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Change change)) return false;
            return inserted == change.inserted &&
                   deleted == change.deleted &&
                   line0 == change.line0 &&
                   line1 == change.line1 &&
                   Objects.equals(link, change.link);
        }

        @Override
        public int hashCode() {
            return Objects.hash(inserted, deleted, line0, line1, link);
        }

        @Override
        public String toString() {
            return "change[inserted=" + inserted + ", deleted=" + deleted + ", line0=" + line0 + ", line1=" + line1 + "]";
        }
    }

    public static class ChangeBuilder {
        private int myIndex1;
        private int myIndex2;
        private Change myFirstChange;
        private Change myLastChange;

        public ChangeBuilder(int startShift) {
            myIndex1 = startShift;
            myIndex2 = startShift;
        }

        public void process(@NotNull BitSet changes1, @NotNull BitSet changes2, int len1, int len2) {
            int i = 0;
            int j = 0;
            while (i < len1 || j < len2) {
                if ((i < len1 && changes1.get(i)) || (j < len2 && changes2.get(j))) {
                    int start1 = i;
                    int start2 = j;
                    while (i < len1 && changes1.get(i)) i++;
                    while (j < len2 && changes2.get(j)) j++;
                    addChange(myIndex1 + start1, myIndex2 + start2, i - start1, j - start2);
                } else {
                    i++;
                    j++;
                }
            }
        }

        protected void addChange(int line0, int line1, int deleted, int inserted) {
            Change change = new Change(line0, line1, deleted, inserted, null);
            if (myLastChange != null) {
                myLastChange.link = change;
            } else {
                myFirstChange = change;
            }
            myLastChange = change;
        }

        @Nullable
        public Change getFirstChange() {
            return myFirstChange;
        }
    }
}
