// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.diff;

public class FilesTooBigForDiffException extends Exception {
    public FilesTooBigForDiffException() {
        super("File is too big to be compared");
    }
}
