// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

class IntLCS {
    private final int[] myFirst;
    private final int[] mySecond;
    private final int myStart1;
    private final int myStart2;
    private final int myCount1;
    private final int myCount2;
    private final BitSet myChanges1;
    private final BitSet myChanges2;

    IntLCS(int[] first, int[] second) {
        this(first, second, 0, first.length, 0, second.length, new BitSet(first.length), new BitSet(second.length));
    }

    IntLCS(
        int[] first,
        int[] second,
        int start1,
        int count1,
        int start2,
        int count2,
        BitSet changes1,
        BitSet changes2
    ) {
        myFirst = first;
        mySecond = second;
        myStart1 = start1;
        myStart2 = start2;
        myCount1 = count1;
        myCount2 = count2;
        myChanges1 = changes1;
        myChanges2 = changes2;
    }

    public void execute() throws FilesTooBigForDiffException {
        execute(true);
    }

    public void execute(boolean throwException) throws FilesTooBigForDiffException {
        int start1 = myStart1;
        int count1 = myCount1;
        int start2 = myStart2;
        int count2 = myCount2;

        while (count1 > 0 && count2 > 0 && myFirst[start1] == mySecond[start2]) {
            start1++;
            start2++;
            count1--;
            count2--;
        }
        while (count1 > 0 && count2 > 0 && myFirst[start1 + count1 - 1] == mySecond[start2 + count2 - 1]) {
            count1--;
            count2--;
        }

        if (count1 == 0 && count2 == 0) return;
        if (count1 == 0) {
            myChanges2.set(start2, start2 + count2);
            return;
        }
        if (count2 == 0) {
            myChanges1.set(start1, start1 + count1);
            return;
        }

        int max = count1 + count2;
        if (throwException && max > 2 * DiffConfig.DELTA_THRESHOLD_SIZE) {
            throw new FilesTooBigForDiffException();
        }

        int offset = max + 1;
        int[] v = new int[2 * max + 3];
        List<int[]> trace = new ArrayList<>(Math.min(max + 1, 1024));

        int foundD = -1;
        outer:
        for (int d = 0; d <= max; d++) {
            int[] vSnapshot = new int[2 * d + 1];
            for (int k = -d; k <= d; k += 2) {
                int kIdx = offset + k;
                int x;
                if (k == -d || (k != d && v[kIdx - 1] < v[kIdx + 1])) {
                    x = v[kIdx + 1];
                } else {
                    x = v[kIdx - 1] + 1;
                }
                int y = x - k;
                while (x < count1 && y < count2 && myFirst[start1 + x] == mySecond[start2 + y]) {
                    x++;
                    y++;
                }
                v[kIdx] = x;
                vSnapshot[k + d] = x;
                if (x >= count1 && y >= count2) {
                    trace.add(vSnapshot);
                    foundD = d;
                    break outer;
                }
            }
            trace.add(vSnapshot);
        }

        if (foundD < 0) {
            myChanges1.set(start1, start1 + count1);
            myChanges2.set(start2, start2 + count2);
            return;
        }

        // Backtrack through the saved v snapshots to mark exact insertions and deletions
        int x = count1;
        int y = count2;
        for (int d = foundD; d > 0; d--) {
            int k = x - y;
            int[] vPrev = trace.get(d - 1);
            int prevD = d - 1;
            boolean down;
            if (k == -d) {
                down = true;
            } else if (k == d) {
                down = false;
            } else {
                int vLeft = vPrev[(k - 1) + prevD];
                int vRight = vPrev[(k + 1) + prevD];
                down = vLeft < vRight;
            }

            int prevK = down ? k + 1 : k - 1;
            int prevX = vPrev[prevK + prevD];
            int prevY = prevX - prevK;

            while (x > prevX && y > prevY) {
                x--;
                y--;
            }

            if (down) {
                // Insertion in second at y - 1
                y--;
                myChanges2.set(start2 + y);
            } else {
                // Deletion in first at x - 1
                x--;
                myChanges1.set(start1 + x);
            }
        }
    }

    public BitSet[] getChanges() {
        return new BitSet[]{myChanges1, myChanges2};
    }
}
