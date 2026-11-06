# AGENTS.md — Developer & AI Agent Guide for `ijdiff`

`ijdiff` is a standalone, fast desktop and CLI diff & directory comparison viewer extracted from open-source **IntelliJ IDEA Community Edition** (`intellij-community`), built without the heavyweight IntelliJ Platform IDE container.

---

## 1. Project Architecture & Modules

The project is a multi-module Kotlin/Java Gradle build (`JVM Toolchain 17`):

| Module | Package Namespace | Responsibility |
| :--- | :--- | :--- |
| [`:diff-core`](diff-core) | `com.intellij.util.diff`, `com.intellij.diff.*`, `org.ijdiff.core` | Vendored & decoupled Apache-2.0 IntelliJ diff engine (`Diff`, `IntLCS`, `PatienceIntLCS`, `UniqueLCS`, `Reindexer`, `ComparisonManagerImpl`, `ByLineRt`, `ByWordRt`, `ByCharRt`) plus `LineOffsets`, `LineNumberConvertor`, `UnifiedFragmentBuilder`, `DiffDividerGeometry`, and `DiffSampleCatalog`. **Zero UI dependencies.** |
| [`:diff-model`](diff-model) | `org.ijdiff.model.*` | UI-agnostic reactive state & session controllers (`DiffDocument`, `TwoWayDiffSession`, `DirDiffSession`, `IjDiffConfig`). Manages line-ending-preserving explicit Save (`Ctrl+S`), Undo/Redo, chunk patching (`replaceChunk` / `appendChunk`), directory scanning/filtering/synchronization, and TOML configuration. **Zero UI dependencies.** |
| [`:diff-ui-swing`](diff-ui-swing) | `org.ijdiff.ui.swing.*` | Phase 1 Desktop UI powered by Swing, FlatLaf (`FlatDarculaLaf` / `FlatIntelliJLaf`), and `RSyntaxTextArea` (`TwoWayDiffPanel`, `DiffEditorPane`, `DiffDividerComponent`, `UnifiedDiffPane`, `DirDiffMasterDetailPanel`, `MainWindow`, `DiffThemeManager`, `DiffIcons`). |
| [`:diff-ui-compose`](diff-ui-compose) | `org.ijdiff.ui.compose.*` | Phase 2 Compose Multiplatform Desktop UI (`ComposeMainWindow`, `ComposeTwoWayDiffView`, `ComposeDirDiffView`, `ComposeSyntaxHighlighter`, `ComposeThemeState`, `ComposeDiffWidgets`, `ComposeDiffPresentation`), consuming `:diff-model` and `:diff-core`. |
| [`:app`](app) | `org.ijdiff.app` | Clikt-based CLI entry point (`Main.kt` / `IjDiffCli`) and distribution/fat-JAR packaging tasks. |
| [`samples/`](samples) | N/A | Automated test corpus containing 12 file diff pairs (`samples/files/01-*` .. `12-*`) and a multi-directory microservice tree (`samples/directories/dir-01-microservice-release`). |

---

## 2. Build, Test, and Run Commands

Java 17 (`temurin-17.0.20+101`) is configured via [`.mise.toml`](.mise.toml).

```bash
# Run all unit, corpus, and offscreen UI rendering tests across all modules
./gradlew test

# Build the launcher distribution and standalone fat-JAR
./gradlew :app:installDist :app:fatJar

# Launch via the root wrapper script (auto-builds if needed)
./ijdiff --help
./ijdiff samples/files/01-kotlin-refactoring/left.kt samples/files/01-kotlin-refactoring/right.kt
./ijdiff --load-samples
```

- **Sample Render Snapshots**: Running `./gradlew :diff-ui-swing:test` executes `SampleCorpusSwingRenderTest`, which renders all 12 file samples (Side-by-Side and Unified) and the directory comparison sample offscreen and writes 25 PNG snapshots to `diff-ui-swing/build/reports/sample-renders/`.

---

## 3. Configuration (`ijdiff.toml`) & CLI Alignment

Configuration uses **TOML** ([`ijdiff.toml`](ijdiff.toml) in the working directory or `~/.config/ijdiff/config.toml`) and is parsed/serialized by [`IjDiffConfig`](diff-model/src/main/kotlin/org/ijdiff/model/config/IjDiffConfig.kt).

- **1-to-1 Naming Rule**: Every configuration key in `ijdiff.toml` must map 1-to-1 to a CLI option in [`IjDiffCli`](app/src/main/kotlin/org/ijdiff/app/Main.kt):
  - `ui` (`"swing"` | `"compose"`) $\leftrightarrow$ `--ui`
  - `theme` (`"dark"` | `"light"`) $\leftrightarrow$ `--theme`
  - `unified` (`true` | `false`) $\leftrightarrow$ `-u`, `--unified` / `--no-unified`
  - `whitespace` (`"default"` | `"trim"` | `"ignore"`) $\leftrightarrow$ `-w`, `--whitespace`
  - `[font] family` $\leftrightarrow$ `--font-family`
  - `[font] size` $\leftrightarrow$ `--font-size`
  - `[ui.font] family` $\leftrightarrow$ `--ui-font-family`
  - `[ui.font] size` $\leftrightarrow$ `--ui-font-size`

---

## 4. UI & Rendering Engineering Guidelines

When modifying `:diff-ui-swing`, preserve the following architectural invariants:

1. **No Unicode Symbol Dependencies in UI Chrome**:
   - Do **not** use special Unicode symbol characters (`↶`, `⟳`, `📁`, `✕`, `⊘`, `↔`, `»`, `«`) in Swing button labels, tab headers, or table cells, as they render as missing-glyph tofu boxes (`□`) when a custom or system font lacks symbol coverage.
   - Always use resolution-independent Java2D vector icons from [`DiffIcons`](diff-ui-swing/src/main/kotlin/org/ijdiff/ui/swing/theme/DiffIcons.kt) paired with clean ASCII text.
2. **Composite Font Fallback (`StyleContext`)**:
   - Never instantiate fonts with raw `Font(family, style, size)` or `font.deriveFont(...)` when applying editor or UI fonts; doing so strips Java2D's `CompositeFont` fallback slots on Linux/macOS/Windows.
   - Always use [`DiffThemeManager.createCompositeFont(family, style, size)`](diff-ui-swing/src/main/kotlin/org/ijdiff/ui/swing/theme/DiffThemeManager.kt) (which delegates to `StyleContext.getDefaultStyleContext().getFont(...)`) and update token styles in [`SyntaxStyleResolver.applyPaletteToSyntaxArea`](diff-ui-swing/src/main/kotlin/org/ijdiff/ui/swing/editor/SyntaxStyleResolver.kt).
3. **`RSyntaxTextArea` Diff Highlight Layering**:
   - `RTextAreaBase` uses an internal `ColorBackgroundPainterStrategy` that fills `getVisibleRect()` with `textArea.background` at the start of `super.paintComponent(g)`.
   - Keep `textArea.isOpaque = false` and `textArea.background = Color(0, 0, 0, 0)` in `SyntaxStyleResolver.applyPaletteToSyntaxArea`, and paint `palette.editorBg` + line/word diff highlights in `paintComponent(g)` **before** calling `super.paintComponent(g2)`.
4. **Headless / Offscreen Rendering Compatibility**:
   - `RSyntaxTextArea` subclasses in `DiffEditorPane` and `UnifiedDiffPane` override `getGraphics()` with a fallback `1x1` `BufferedImage` graphics context so `RSyntaxTextArea.getGraphics2D()` never throws an NPE when rendered offscreen in tests before being attached to a native window peer.
5. **Explicit Save Only (`Ctrl+S`)**:
   - Editing in `DiffEditorPane` or applying chunks (`>>` / `<<`) mutates the in-memory `DiffDocument` and marks `isDirty = true`. Never auto-write changes to disk; disk writes happen only on explicit `DiffDocument.save()` / `TwoWayDiffSession.saveAll()` (`Ctrl+S`) or user confirmation on tab/window close.

---

## 5. Version Control

This repository uses **Jujutsu (`jj`)** backed by Git:
- Inspect working-copy status with `jj status` and history with `jj log`.
- Commit changes using `jj commit -m "<conventional commit message>"`.
