// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments;

public interface DiffFragment {
    int getStartOffset1();

    int getEndOffset1();

    int getStartOffset2();

    int getEndOffset2();
}
