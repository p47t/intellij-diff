// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Reindexer {
    private final int[][] myOldIndices = new int[2][];
    private final int[] myOriginalLengths = new int[]{-1, -1};
    private final int[] myDiscardedLengths = new int[]{-1, -1};

    public int[][] discardUnique(Object[] ints1, Object[] ints2) {
        myOriginalLengths[0] = ints1.length;
        myOriginalLengths[1] = ints2.length;

        Set<Object> set2 = new HashSet<>(ints2.length * 4 / 3 + 1);
        set2.addAll(Arrays.asList(ints2));

        // Assign a single canonical integer ID to each shared element present in both arrays
        Map<Object, Integer> sharedIds = new HashMap<>(Math.min(ints1.length, ints2.length) * 4 / 3 + 1);
        int nextId = 0;
        for (Object o : ints1) {
            if (set2.contains(o) && !sharedIds.containsKey(o)) {
                sharedIds.put(o, nextId++);
            }
        }

        int[] discarded1 = discard(ints1, sharedIds, 0);
        int[] discarded2 = discard(ints2, sharedIds, 1);
        return new int[][]{discarded1, discarded2};
    }

    public void idInit(int length1, int length2) {
        myOriginalLengths[0] = length1;
        myOriginalLengths[1] = length2;
        myDiscardedLengths[0] = length1;
        myDiscardedLengths[1] = length2;
        for (int j = 0; j < 2; j++) {
            int originalLength = myOriginalLengths[j];
            myOldIndices[j] = new int[originalLength];
            for (int i = 0; i < originalLength; i++) {
                myOldIndices[j][i] = i;
            }
        }
    }

    public int restoreIndex(int index, int array) {
        return myOldIndices[array][index];
    }

    private int[] discard(Object[] array, Map<Object, Integer> sharedIds, int arrayIndex) {
        int[] oldIndices = new int[array.length];
        int[] discarded = new int[array.length];
        int k = 0;
        for (int i = 0; i < array.length; i++) {
            Integer id = sharedIds.get(array[i]);
            if (id != null) {
                oldIndices[k] = i;
                discarded[k] = id;
                k++;
            }
        }
        myOldIndices[arrayIndex] = Arrays.copyOf(oldIndices, k);
        myDiscardedLengths[arrayIndex] = k;
        return Arrays.copyOf(discarded, k);
    }

    public void reindex(BitSet[] discardedChanges, BitSet[] changes) {
        BitSet changes1;
        BitSet changes2;
        if (myDiscardedLengths[0] == myOriginalLengths[0] && myDiscardedLengths[1] == myOriginalLengths[1]) {
            changes1 = discardedChanges[0];
            changes2 = discardedChanges[1];
        } else {
            changes1 = new BitSet(myOriginalLengths[0]);
            changes1.set(0, myOriginalLengths[0]);
            for (int i = 0; i < myDiscardedLengths[0]; i++) {
                if (!discardedChanges[0].get(i)) {
                    int x = myOldIndices[0][i];
                    changes1.clear(x);
                }
            }
            changes2 = new BitSet(myOriginalLengths[1]);
            changes2.set(0, myOriginalLengths[1]);
            for (int i = 0; i < myDiscardedLengths[1]; i++) {
                if (!discardedChanges[1].get(i)) {
                    int y = myOldIndices[1][i];
                    changes2.clear(y);
                }
            }
        }
        changes[0].or(changes1);
        changes[1].or(changes2);
    }
}
