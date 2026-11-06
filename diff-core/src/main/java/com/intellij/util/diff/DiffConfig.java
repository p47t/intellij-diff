// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

public final class DiffConfig {
    public static final boolean USE_PATIENCE_ALG = false;
    public static final boolean USE_GREEDY_MERGE_MAGIC_RESOLVE = false;
    public static final int DELTA_THRESHOLD_SIZE = 20000;
    public static final int MAX_BAD_LINES = 3;
    public static final int UNIMPORTANT_LINE_CHAR_COUNT = 3;

    private DiffConfig() {
    }
}
