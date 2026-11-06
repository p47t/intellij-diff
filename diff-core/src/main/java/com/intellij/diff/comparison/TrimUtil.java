// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import org.jetbrains.annotations.NotNull;

public final class TrimUtil {
    private TrimUtil() {
    }

    public static boolean isWhiteSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n';
    }

    public static boolean isPunctuation(char c) {
        if (c == '_') return false;
        return (c >= 33 && c <= 47) ||
               (c >= 58 && c <= 64) ||
               (c >= 91 && c <= 96) ||
               (c >= 123 && c <= 126);
    }

    public static boolean isAlpha(char c) {
        return !isWhiteSpace(c) && !isPunctuation(c);
    }

    public static int trimStart(@NotNull CharSequence text, int start, int end) {
        while (start < end && isWhiteSpace(text.charAt(start))) {
            start++;
        }
        return start;
    }

    public static int trimEnd(@NotNull CharSequence text, int start, int end) {
        while (end > start && isWhiteSpace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    @NotNull
    public static CharSequence trim(@NotNull CharSequence text) {
        int start = trimStart(text, 0, text.length());
        int end = trimEnd(text, start, text.length());
        return text.subSequence(start, end);
    }

    public static boolean isEquals(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull ComparisonPolicy policy) {
        return switch (policy) {
            case DEFAULT -> isEqualsDefault(text1, text2);
            case TRIM_WHITESPACES -> isEqualsTrimWhitespaces(text1, text2);
            case IGNORE_WHITESPACES -> isEqualsIgnoreWhitespaces(text1, text2);
        };
    }

    public static boolean isEqualsDefault(@NotNull CharSequence text1, @NotNull CharSequence text2) {
        return CharSequence.compare(text1, text2) == 0;
    }

    public static boolean isEqualsTrimWhitespaces(@NotNull CharSequence text1, @NotNull CharSequence text2) {
        int start1 = trimStart(text1, 0, text1.length());
        int end1 = trimEnd(text1, start1, text1.length());
        int start2 = trimStart(text2, 0, text2.length());
        int end2 = trimEnd(text2, start2, text2.length());
        return isEqualsSubSequence(text1, start1, end1, text2, start2, end2);
    }

    public static boolean isEqualsIgnoreWhitespaces(@NotNull CharSequence text1, @NotNull CharSequence text2) {
        return isEqualsIgnoreWhitespaces(text1, 0, text1.length(), text2, 0, text2.length());
    }

    public static boolean isEqualsIgnoreWhitespaces(
        @NotNull CharSequence text1, int start1, int end1,
        @NotNull CharSequence text2, int start2, int end2
    ) {
        int i1 = start1;
        int i2 = start2;
        while (i1 < end1 && i2 < end2) {
            char c1 = text1.charAt(i1);
            if (isWhiteSpace(c1)) {
                i1++;
                continue;
            }
            char c2 = text2.charAt(i2);
            if (isWhiteSpace(c2)) {
                i2++;
                continue;
            }
            if (c1 != c2) return false;
            i1++;
            i2++;
        }
        while (i1 < end1) {
            if (!isWhiteSpace(text1.charAt(i1))) return false;
            i1++;
        }
        while (i2 < end2) {
            if (!isWhiteSpace(text2.charAt(i2))) return false;
            i2++;
        }
        return true;
    }

    public static int hashCodeIgnoreWhitespaces(@NotNull CharSequence text) {
        int h = 0;
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (!isWhiteSpace(c)) {
                h = 31 * h + c;
            }
        }
        return h;
    }

    private static boolean isEqualsSubSequence(
        @NotNull CharSequence text1, int start1, int end1,
        @NotNull CharSequence text2, int start2, int end2
    ) {
        int len = end1 - start1;
        if (len != end2 - start2) return false;
        for (int i = 0; i < len; i++) {
            if (text1.charAt(start1 + i) != text2.charAt(start2 + i)) return false;
        }
        return true;
    }
}
