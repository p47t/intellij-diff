// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison.iterables;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public interface DiffIterable {
    int getLength1();

    int getLength2();

    @NotNull
    Iterator<Range> changes();

    @NotNull
    Iterator<Range> unchanged();

    @NotNull
    default Iterable<Range> iterateChanges() {
        return this::changes;
    }

    @NotNull
    default Iterable<Range> iterateUnchanged() {
        return this::unchanged;
    }

    @NotNull
    default List<Range> changesList() {
        List<Range> list = new ArrayList<>();
        Iterator<Range> it = changes();
        while (it.hasNext()) {
            list.add(it.next());
        }
        return list;
    }
}
