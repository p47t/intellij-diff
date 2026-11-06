// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.fragments;

import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface LineFragment extends DiffFragment {
    int getStartLine1();

    int getEndLine1();

    int getStartLine2();

    int getEndLine2();

    @Nullable
    List<DiffFragment> getInnerFragments();
}
