# Upstream Attribution (`:diff-core`)

This module contains extracted and decoupled source code from **JetBrains IntelliJ IDEA Community Edition** (`JetBrains/intellij-community`), licensed under the **Apache License, Version 2.0**.

## Upstream Modules Extracted
- `platform/util/diff` (`intellij.platform.util.diff`)
  - `com.intellij.util.diff.*` (`Diff`, `Reindexer`, `UniqueLCS`, `PatienceIntLCS`, `IntLCS`, `DiffConfig`, `FilesTooBigForDiffException`)
  - `com.intellij.diff.comparison.*` (`ComparisonPolicy`, `ComparisonManager`, `ComparisonManagerImpl`, `ByLine`, `ByWord`, `ByChar`, `TrimUtil`, `iterables.*`)
  - `com.intellij.diff.fragments.*` (`DiffFragment`, `DiffFragmentImpl`, `LineFragment`, `LineFragmentImpl`)
- `platform/diff-impl` (`intellij.platform.diff.impl`)
  - `LineNumberConvertor` (`org.ijdiff.core.LineNumberConvertor`)
  - `UnifiedFragmentBuilder` (`org.ijdiff.core.UnifiedFragmentBuilder`)
  - `DiffDividerDrawUtil` bezier curve geometry (`org.ijdiff.core.DiffDividerGeometry`)

## Modifications for Standalone Execution
- Removed dependencies on `intellij.platform.core` (`ApplicationManager`, `ProgressIndicator`, `Document`, `Editor`) by introducing `com.intellij.diff.util.CancellationChecker`, `com.intellij.diff.util.TextRange`, and `org.ijdiff.core.LineOffsets`.
