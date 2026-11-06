// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.diff.comparison.DiffTooBigException;

@FunctionalInterface
public interface CancellationChecker {
    CancellationChecker EMPTY = () -> {};

    void checkCanceled() throws DiffTooBigException;
}
