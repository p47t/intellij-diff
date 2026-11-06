# Standalone IntelliJ Diff Viewer (`ijdiff`) — Architecture & Implementation Plan

## 1. Goals & Architectural Strategy

The goal is to separate IntelliJ IDEA's diff and folder comparison viewer from `intellij-community` into a fast, lightweight, standalone desktop and CLI application (`ijdiff`) without pulling in the heavyweight IntelliJ Platform IDE container (`Application`, `Project`, `VirtualFileSystem`, `PsiManager`, `EditorImpl`).

We execute this in **two architectural phases** built around a strict boundary between the diff engine/session state and the UI toolkit:

- **Phase 1 (Extracted Engine + Swing/FlatLaf + Lightweight Editor Adapter)**:
  Vendor the Apache-2.0 diff core from `intellij-community` (`platform/util/diff` and key geometry/fragment algorithms from `platform/diff-impl`), expose it through a UI-agnostic reactive model layer (`:diff-model`), and build the Phase 1 desktop UI (`:diff-ui-swing`) using **Swing + FlatLaf (IntelliJ Light / Darcula themes)** and **RSyntaxTextArea** wrapped behind a thin `DiffEditor` / `DiffDocument` adapter.
- **Phase 2 (Migration to Compose Multiplatform Desktop + JetBrains Jewel)**:
  Introduce `:diff-ui-compose` built with **Compose Multiplatform for Desktop** and **JetBrains Jewel** (the official IntelliJ New UI theme for Compose), binding directly to `:diff-core` and `:diff-model` so the Swing UI can be swapped out without rewriting any diff, patching, or directory synchronization logic.

---

## 2. Multi-Module Gradle Project Structure

```text
intellij-diff/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/
│   └── libs.versions.toml          # Version catalog (Kotlin, Coroutines, FlatLaf, RSyntaxTextArea, Clikt, JUnit 5)
├── diff-core/                      # Pure diff engine & fragment builders (zero UI dependencies)
│   ├── UPSTREAM.md                 # Tracks intellij-community commit SHA & Apache-2.0 attribution
│   └── src/main/
│       ├── java/com/intellij/diff/ # Vendored & cleaned platform/util/diff sources
│       └── kotlin/org/ijdiff/core/ # Idiomatic Kotlin facade, UnifiedFragmentBuilder, LineNumberConvertor
├── diff-model/                     # Toolkit-independent view-models & document/directory abstractions
│   └── src/main/kotlin/org/ijdiff/model/
│       ├── document/               # DiffDocument, LineOffsets, UndoManager, ExplicitSaveController
│       ├── twoway/                 # TwoWayDiffSession, UnifiedDiffSession, ChunkActionController
│       └── dirdiff/                # DirDiffSession, DirDiffScanner, DirEntryNode, FileSyncExecutor
├── diff-ui-swing/                  # Phase 1 Swing UI (FlatLaf + RSyntaxTextArea + IntelliJ DiffSplitter)
│   └── src/main/kotlin/org/ijdiff/ui/swing/
│       ├── editor/                 # SwingDiffEditorAdapter (RSyntaxTextArea), GutterActionRenderer
│       ├── splitter/               # DiffDividerPainter (ported from DiffDividerDrawUtil bezier curves)
│       ├── twoway/                 # TwoWayDiffPanel (Side-by-Side & Unified toggle), DiffOverviewScrollbar
│       ├── dirdiff/                # DirDiffMasterDetailPanel, DirDiffTable, FilterToolbar
│       └── shell/                  # MainWindow, TabbedSessionHost, FilePickerBar, UnsavedChangesDialog
├── diff-ui-compose/                # Phase 2 UI target (Compose Desktop + JetBrains Jewel)
└── app/                            # CLI entry point (`ijdiff`), git difftool integration, jpackage config
    └── src/main/kotlin/org/ijdiff/app/
        └── Main.kt
```

---

## 3. Detailed Component Breakdown

### 3.1 `:diff-core` — Vendoring & Cleaning `intellij-community` Diff Engine
Extract the Apache-2.0 source files from `intellij-community`:
1. **Source Packages from `platform/util/diff`**:
   - `com.intellij.util.diff.*`: `Diff` (Myers), `PatienceIntLCS`, `UniqueLCS`, `Reindexer`, `DiffConfig`.
   - `com.intellij.diff.comparison.*`: `ComparisonManager`, `ComparisonManagerImpl`, `ComparisonPolicy` (`DEFAULT`, `TRIM_WHITESPACES`, `IGNORE_WHITESPACES`), `ByLine`, `ByWord`, `ByChar`, `ChunkOptimizer`, `TrimUtil`, `iterables.FairDiffIterable`.
   - `com.intellij.diff.fragments.*`: `DiffFragment`, `DiffFragmentImpl`, `LineFragment`, `LineFragmentImpl`.
   - `com.intellij.diff.tools.util.text.*`: `LineOffsets`, `LineOffsetsImpl`, `TwosideTextDiffProvider`.
2. **Selected Algorithms from `platform/diff-impl`**:
   - `LineNumberConvertor` (maps left-to-right line numbers for synchronized scrolling and unified diff line numbering).
   - `UnifiedFragmentBuilder` (builds a single virtual combined document and highlight ranges from two texts + `List<LineFragment>`).
   - Geometry math from `DiffDividerDrawUtil` (calculates cubic bezier control points connecting `[startLine1, endLine1)` on the left to `[startLine2, endLine2)` on the right).
3. **Decoupling Shims**:
   - Replace `com.intellij.openapi.progress.ProgressIndicator` with a lightweight `CancellationChecker` (`fun checkCanceled()`) backed by `kotlinx.coroutines.currentCoroutineContext().ensureActive()`.
   - Replace `com.intellij.openapi.util.TextRange` and `IntPair` with minimal value/data classes in `:diff-core` so zero `intellij-platform` JARs are needed.

### 3.2 `:diff-model` — UI-Agnostic Diff & Directory Sessions
1. **`DiffDocument` & Explicit Save Model**:
   - Wraps a mutable text buffer, line-ending metadata (`LF`, `CRLF`, `CR`), charset, file path, `isDirty: StateFlow<Boolean>`, and undo/redo stack.
   - Supports **explicit save** (`save()`, `reloadFromDisk()`) rather than silent auto-save, emitting dirty state changes so the tab header shows `*` and closing/switching prompts the user.
2. **`TwoWayDiffSession`**:
   - Holds `leftDoc: DiffDocument`, `rightDoc: DiffDocument`, `comparisonPolicy: StateFlow<ComparisonPolicy>`, `highlightPolicy: StateFlow<HighlightPolicy>` (Do Not Highlight, By Lines, By Words, By Split Words), and `viewMode: StateFlow<ViewMode>` (`SIDE_BY_SIDE`, `UNIFIED`).
   - Runs diff calculation asynchronously on `Dispatchers.Default` (with a 150ms debounce when typing freehand, and immediate recalculation when applying a gutter chunk action).
   - Exposes `fragments: StateFlow<List<LineFragment>>`, navigation helpers (`nextDifference()`, `prevDifference()`), and chunk mutation methods:
     - `replaceChunk(fragment, direction: LeftToRight | RightToLeft)` (`>>` / `<<`)
     - `appendChunk(fragment, direction: LeftToRight | RightToLeft)` (`Ctrl` / modifier click on `>>` / `<<`)
3. **`DirDiffSession` (Folder Comparison & Sync)**:
   - Takes `leftRoot: Path` and `rightRoot: Path`.
   - Asynchronous recursive directory walker producing paired relative paths with status: `ADDED` (only in right), `DELETED` (only in left), `MODIFIED` (in both, different), `EQUAL` (in both, identical).
   - Configurable comparison criteria: **Compare by Content (SHA-256 / byte stream)**, **Size only**, or **Size & Timestamp**.
   - Filtering via status toggles (Show New / Show Deleted / Show Modified / Show Equal) and glob patterns (e.g., `!*.class;!node_modules/**`).
   - Synchronization actions per row or selection: `COPY_TO_RIGHT`, `COPY_TO_LEFT`, `DELETE_LEFT`, `DELETE_RIGHT`, `SKIP`, with batch execution (`Synchronize Selected` / `Synchronize All`) and confirmation dialog.
   - Selecting any file pair in `DirDiffSession` updates an embedded child `TwoWayDiffSession` for the bottom detail pane.

### 3.3 `:diff-ui-swing` — Phase 1 Desktop UI
1. **Theme & Look-and-Feel**:
   - Uses **FlatLaf** (`FlatIntelliJLaf` and `FlatDarculaLaf`) with IntelliJ diff color palettes (`DIFF_INSERTED`, `DIFF_DELETED`, `DIFF_MODIFIED`, `DIFF_CONFLICT`) for both light and dark themes.
2. **`SwingDiffEditorAdapter` (wrapping `RSyntaxTextArea`)**:
   - Configures syntax highlighting automatically from file extension.
   - Custom background painter rendering full-width line-range backgrounds for inserted, deleted, and modified blocks, plus deeper-tinted inline boxes for word/character-level `DiffFragment` changes.
   - Custom gutter component rendering line numbers and interactive `>>` / `<<` action icons on the first line of each `LineFragment`.
3. **Three-Part `DiffSplitter` & Synchronized Scrolling**:
   - Left Editor | Center 36px Divider Component | Right Editor.
   - Center divider uses `DiffDividerDrawUtil`'s cubic bezier curve polygons to link corresponding `LineFragment` blocks across vertical scroll positions.
   - Synchronized scrolling listener: translates the visible center line through `LineNumberConvertor` and aligns the opposite pane.
4. **Unified Diff View**:
   - Single read-only `RSyntaxTextArea` backed by `UnifiedFragmentBuilder`, featuring a dual-column gutter (`Left Line #` | `Right Line #`) and red/green line + inline word highlights.
5. **Master-Detail `DirDiffMasterDetailPanel`**:
   - Top pane: toolbar (Compare Mode, Status filters, Glob filter field, Sync actions) + two-sided table (`Left Name | Size | Time | [Action Arrow] | Right Name | Size | Time`).
   - Bottom pane: embedded `TwoWayDiffPanel` showing the live 2-way diff of the currently selected row (with unsaved-change protection before switching rows).

### 3.4 `:app` — CLI, Multi-Tab Shell, and Git Integration
1. **CLI Modes (`ijdiff`)**:
   - `ijdiff` (no args): Opens the multi-tab window with a New Comparison dialog / drag-and-drop empty state.
   - `ijdiff <file1> <file2>`: Opens directly into a 2-way file diff tab.
   - `ijdiff <dir1> <dir2>`: Opens directly into a Master-Detail Directory Diff tab (compatible with `git difftool --dir-diff`).
   - Flags: `--unified`, `--ignore-whitespace`, `--theme=dark|light`, `--wait`.

---

## 4. Phase 1 → Phase 2 Migration Strategy (Moving to Compose Desktop + Jewel)

| Layer | Phase 1 (`:diff-ui-swing`) | Phase 2 (`:diff-ui-compose`) |
| :--- | :--- | :--- |
| **App Chrome & Tabs** | FlatLaf `JFrame` + `JTabbedPane` | Jewel `IntUiTheme` Window + Jewel `TabStrip` |
| **Toolbar & Filters** | Swing `JToolBar` + FlatLaf icons | Jewel `IconActionButton`, `Dropdown`, `TextField` |
| **Directory Diff Table** | `JTable` with custom cell renderers | Compose `LazyColumn` / Jewel `SelectableLazyColumn` |
| **Center Bezier Divider** | `JComponent.paintComponent(Graphics2D)` | Compose `Canvas { drawPath(bezierPath, ...) }` |
| **Code Diff Panes** | `RSyntaxTextArea` + custom Highlighter/Gutter | Stage 2a: Embed `SwingDiffEditorAdapter` via `SwingPanel` inside Jewel shell<br/>Stage 2b: Pure Compose `BasicTextField2` / Jewel Code Editor canvas with `TextMeasurer` |

---

## 5. Execution Milestones

- **Milestone 1**: Project Scaffold & `:diff-core` Extraction (Myers, Patience, `ByLine`, `ByWord`, `LineNumberConvertor`, `UnifiedFragmentBuilder`, bezier geometry, JUnit 5 suite)
- **Milestone 2**: `:diff-model` (`DiffDocument`, `TwoWayDiffSession`, `UnifiedDiffSession`, `DirDiffSession`, chunk patching & folder sync tests)
- **Milestone 3**: `:diff-ui-swing` — 2-Way Side-by-Side & Unified Diff Viewer (`SwingDiffEditorAdapter`, `DiffSplitter`, gutter actions, explicit Save & Undo/Redo)
- **Milestone 4**: `:diff-ui-swing` — Directory Diff Master-Detail View (`DirDiffMasterDetailPanel`, filters, sync actions, embedded diff preview)
- **Milestone 5**: `:app` — CLI, Multi-Tab Window, & Native Packaging (`jlink` / `jpackage`)
- **Milestone 6**: Phase 2 Preparation (`:diff-ui-compose` + JetBrains Jewel prototype bound to `:diff-model`)
