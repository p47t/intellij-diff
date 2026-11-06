# `ijdiff` — Standalone IntelliJ Diff & Folder Comparison Viewer

`ijdiff` extracts the diff and directory comparison engine from open-source **IntelliJ IDEA Community Edition** (`intellij-community`) into a fast, standalone desktop and CLI application without the heavyweight IntelliJ Platform IDE container.

## Features

- **2-Way Side-by-Side Text Diff**:
  - Authentic IntelliJ diff engine (`Myers` + `PatienceIntLCS` + `ByLine` + `ByWord` + `ByChar`).
  - Multi-tier highlighting: full-width line block backgrounds + deeper-tinted inline word/character changes.
  - Smooth cubic **Bezier curve splitter** (`DiffDividerGeometry` / `DiffDividerDrawUtil`) connecting corresponding line blocks across scroll positions.
  - Synchronized scrolling powered by `LineNumberConvertor`.
  - Interactive gutter chunk buttons (`»` / `«`) to replace or append (`Ctrl+Click`) changes between files.
  - Full freehand editing with Undo/Redo and **Explicit Save (`Ctrl+S`)** with unsaved-change protection.
- **2-Way Unified (Single-Pane) Text Diff**:
  - Combined virtual document (`UnifiedFragmentBuilder`) with dual left/right line-number columns and inline word diff highlights.
- **Master-Detail Directory Comparison (`DirDiff`)**:
  - Recursive folder comparison by **Content**, **Size**, or **Size & Timestamp**.
  - Status filters (`* Modified`, `- Left Only`, `+ Right Only`, `= Equal`) and Glob filters (e.g., `*.kt` or `!*.class;!build/**`).
  - Interactive synchronization (`Copy Left → Right`, `Copy Right ← Left`, `Delete`, `Skip`) with **Sync Selected** and **Sync All**.
  - Embedded bottom 2-way diff preview for the currently selected file pair.
- **IntelliJ Light & Darcula Themes**:
  - Powered by FlatLaf (`FlatDarculaLaf` / `FlatIntelliJLaf`) and syntax highlighting via `RSyntaxTextArea`.

## Quick Start

### Run via CLI Wrapper
```bash
# Compare two files (Side-by-Side)
./ijdiff path/to/Left.kt path/to/Right.kt

# Compare two files in Unified mode, ignoring whitespace
./ijdiff --unified --whitespace=ignore path/to/Left.kt path/to/Right.kt

# Compare and synchronize two directories
./ijdiff path/to/dirA path/to/dirB

# Launch standalone multi-tab GUI (with Scratch Diff pad & file/folder pickers)
./ijdiff
```

### Launch with Custom Config or Font
`ijdiff` reads font face, font size, and theme settings from `./ijdiff.conf` or `~/.config/ijdiff/ijdiff.conf` (or via `--config <path>`):

```ini
# ijdiff.conf
font.family = Ioskeley Mono
font.size = 14
theme = dark
```

You can also override the font face or size from the CLI or via the **Font: …** button in the top toolbar:
```bash
./ijdiff --font-family="Ioskeley Mono" --font-size=15 path/to/Left.kt path/to/Right.kt
```

### Use as `git difftool` (including `git difftool --dir-diff`)
```ini
[diff]
    tool = ijdiff
[difftool "ijdiff"]
    cmd = /home/patrick/code/intellij-diff/ijdiff "$LOCAL" "$REMOTE"
[difftool]
    prompt = false
```

### Build Standalone Distribution & Fat-JAR
```bash
./gradlew test :app:installDist :app:fatJar
```
- Launcher binary: `app/build/install/ijdiff/bin/ijdiff`
- Standalone Fat-JAR: `app/build/libs/ijdiff-standalone-0.1.0-SNAPSHOT-all.jar`

## Module Architecture

| Module | Responsibility |
| :--- | :--- |
| [`:diff-core`](diff-core) | Vendored & decoupled Apache-2.0 `intellij.platform.util.diff` (`Diff`, `PatienceIntLCS`, `ComparisonManagerImpl`, `ByLineRt`, `ByWordRt`, `ByCharRt`) + `LineNumberConvertor`, `UnifiedFragmentBuilder`, and `DiffDividerGeometry`. Zero UI dependencies. |
| [`:diff-model`](diff-model) | UI-agnostic reactive session models (`DiffDocument`, `TwoWayDiffSession`, `DirDiffSession`) with undo/redo, explicit save, chunk patching, and folder synchronization. |
| [`:diff-ui-swing`](diff-ui-swing) | Phase 1 desktop UI (`TwoWayDiffPanel`, `DiffEditorPane`, `DiffDividerComponent`, `UnifiedDiffPane`, `DirDiffMasterDetailPanel`, `MainWindow`). |
| [`:diff-ui-compose`](diff-ui-compose) | Phase 2 UI presentation bridge for Compose Multiplatform Desktop + JetBrains Jewel, consuming `:diff-model` and `:diff-core` with zero Swing dependencies. |
| [`:app`](app) | CLI entry point (`IjDiffCli`) and packaging tasks. |
