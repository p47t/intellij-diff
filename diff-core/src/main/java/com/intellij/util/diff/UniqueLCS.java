// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class UniqueLCS {
    private final int[] myFirst;
    private final int[] mySecond;
    private final int myStart1;
    private final int myStart2;
    private final int myCount1;
    private final int myCount2;

    public UniqueLCS(int[] first, int[] second) {
        this(first, second, 0, first.length, 0, second.length);
    }

    public UniqueLCS(int[] first, int[] second, int start1, int count1, int start2, int count2) {
        myFirst = first;
        mySecond = second;
        myStart1 = start1;
        myStart2 = start2;
        myCount1 = count1;
        myCount2 = count2;
    }

    public int[][] execute() {
        Map<Integer, Integer> map = new HashMap<>(myCount1 + myCount2);
        for (int i = 0; i < myCount1; i++) {
            int val = myFirst[myStart1 + i];
            Integer existing = map.get(val);
            if (existing == null) {
                map.put(val, i + 1);
            } else if (existing > 0) {
                map.put(val, -1);
            }
        }

        int[] match = new int[myCount1];
        Arrays.fill(match, -1);
        for (int j = 0; j < myCount2; j++) {
            int val = mySecond[myStart2 + j];
            Integer idx1Plus1 = map.get(val);
            if (idx1Plus1 != null && idx1Plus1 > 0) {
                int idx1 = idx1Plus1 - 1;
                if (match[idx1] == -1) {
                    match[idx1] = j;
                } else {
                    match[idx1] = -2;
                    map.put(val, -1);
                }
            }
        }

        int uniqueCount = 0;
        for (int i = 0; i < myCount1; i++) {
            if (match[i] >= 0) {
                uniqueCount++;
            } else {
                match[i] = -1;
            }
        }
        if (uniqueCount == 0) return null;

        // Longest Increasing Subsequence (patience sorting)
        int[] tailIndices1 = new int[uniqueCount];
        int[] prevIndex1 = new int[myCount1];
        Arrays.fill(prevIndex1, -1);
        int length = 0;

        for (int i = 0; i < myCount1; i++) {
            int j = match[i];
            if (j < 0) continue;

            int lo = 0;
            int hi = length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (match[tailIndices1[mid]] < j) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            if (lo > 0) {
                prevIndex1[i] = tailIndices1[lo - 1];
            }
            tailIndices1[lo] = i;
            if (lo == length) {
                length++;
            }
        }

        int[][] result = new int[2][length];
        int curr = tailIndices1[length - 1];
        for (int k = length - 1; k >= 0; k--) {
            result[0][k] = curr;
            result[1][k] = match[curr];
            curr = prevIndex1[curr];
        }
        return result;
    }
}
