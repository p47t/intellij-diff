// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

import java.util.BitSet;

public class PatienceIntLCS {
    private final int[] myFirst;
    private final int[] mySecond;
    private final BitSet myChanges1;
    private final BitSet myChanges2;

    public PatienceIntLCS(int[] first, int[] second) {
        this(first, second, new BitSet(first.length), new BitSet(second.length));
    }

    public PatienceIntLCS(int[] first, int[] second, BitSet changes1, BitSet changes2) {
        myFirst = first;
        mySecond = second;
        myChanges1 = changes1;
        myChanges2 = changes2;
    }

    public void execute() throws FilesTooBigForDiffException {
        execute(false);
    }

    public void execute(boolean failOnSmallReduction) throws FilesTooBigForDiffException {
        execute(0, myFirst.length, 0, mySecond.length, failOnSmallReduction ? 2 : -1);
    }

    private void execute(int start1, int count1, int start2, int count2, int thresholdCheckCounter)
        throws FilesTooBigForDiffException {
        if (count1 == 0 && count2 == 0) return;
        if (count1 == 0) {
            myChanges2.set(start2, start2 + count2);
            return;
        }
        if (count2 == 0) {
            myChanges1.set(start1, start1 + count1);
            return;
        }

        int prefix = 0;
        while (prefix < count1 && prefix < count2 && myFirst[start1 + prefix] == mySecond[start2 + prefix]) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < count1 - prefix && suffix < count2 - prefix &&
               myFirst[start1 + count1 - 1 - suffix] == mySecond[start2 + count2 - 1 - suffix]) {
            suffix++;
        }

        start1 += prefix;
        start2 += prefix;
        count1 -= prefix + suffix;
        count2 -= prefix + suffix;

        if (count1 == 0 || count2 == 0) {
            if (count1 == 0 && count2 > 0) myChanges2.set(start2, start2 + count2);
            if (count2 == 0 && count1 > 0) myChanges1.set(start1, start1 + count1);
            return;
        }

        if (thresholdCheckCounter == 0) {
            checkReduction(count1, count2);
        }
        thresholdCheckCounter = Math.max(-1, thresholdCheckCounter - 1);

        UniqueLCS uniqueLCS = new UniqueLCS(myFirst, mySecond, start1, count1, start2, count2);
        int[][] matching = uniqueLCS.execute();

        if (matching == null) {
            if (thresholdCheckCounter >= 0) checkReduction(count1, count2);
            IntLCS intLCS = new IntLCS(myFirst, mySecond, start1, count1, start2, count2, myChanges1, myChanges2);
            intLCS.execute();
        } else {
            int s1 = matching[0][0];
            int s2 = matching[1][0];
            if (s1 > 0 || s2 > 0) {
                execute(start1, s1, start2, s2, thresholdCheckCounter);
            }
            for (int i = 1; i < matching[0].length; i++) {
                int prev1 = matching[0][i - 1];
                int prev2 = matching[1][i - 1];
                int cur1 = matching[0][i];
                int cur2 = matching[1][i];
                int c1 = cur1 - prev1 - 1;
                int c2 = cur2 - prev2 - 1;
                if (c1 > 0 || c2 > 0) {
                    execute(start1 + prev1 + 1, c1, start2 + prev2 + 1, c2, thresholdCheckCounter);
                }
            }
            int lastIdx = matching[0].length - 1;
            int last1 = matching[0][lastIdx];
            int last2 = matching[1][lastIdx];
            if (last1 < count1 - 1 || last2 < count2 - 1) {
                execute(
                    start1 + last1 + 1,
                    count1 - last1 - 1,
                    start2 + last2 + 1,
                    count2 - last2 - 1,
                    thresholdCheckCounter
                );
            }
        }
    }

    private void checkReduction(int count1, int count2) throws FilesTooBigForDiffException {
        if (count1 * 2 < myFirst.length) return;
        if (count2 * 2 < mySecond.length) return;
        if ((long) count1 + count2 > 2L * DiffConfig.DELTA_THRESHOLD_SIZE) {
            throw new FilesTooBigForDiffException();
        }
    }

    public BitSet[] getChanges() {
        return new BitSet[]{myChanges1, myChanges2};
    }
}
