package org.ijdiff.model.config

import com.intellij.diff.comparison.ComparisonPolicy
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Application configuration model backed by TOML (`ijdiff.toml` / `~/.config/ijdiff/config.toml`).
 *
 * Canonical TOML format (1-to-1 with CLI flags):
 * ```toml
 * theme = "dark"
 * unified = false
 * whitespace = "default"
 *
 * [font]
 * family = "JetBrains Mono"
 * size = 13
 *
 * [ui.font]
 * family = "Inter"
 * size = 13
 * ```
 */
data class IjDiffConfig(
    val fontFamily: String? = null,
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val uiFontFamily: String? = null,
    val uiFontSize: Int = DEFAULT_UI_FONT_SIZE,
    val theme: String? = null,
    val unified: Boolean = false,
    val whitespace: String = "default",
    val sourcePath: Path? = null
) {
    fun resolveComparisonPolicy(): ComparisonPolicy = when (whitespace.lowercase()) {
        "trim", "trim_whitespaces" -> ComparisonPolicy.TRIM_WHITESPACES
        "ignore", "ignore_whitespaces" -> ComparisonPolicy.IGNORE_WHITESPACES
        else -> ComparisonPolicy.DEFAULT
    }

    companion object {
        const val DEFAULT_FONT_SIZE = 13
        const val DEFAULT_UI_FONT_SIZE = 13
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 48

        val DEFAULT = IjDiffConfig()

        /**
         * Resolves the configuration file path using standard priority:
         * 1. Explicit path if provided (`--config <path>`)
         * 2. `./ijdiff.toml` or `./.ijdiff.toml` in the working directory
         * 3. `$XDG_CONFIG_HOME/ijdiff/config.toml` (or `~/.config/ijdiff/config.toml`)
         * 4. `$XDG_CONFIG_HOME/ijdiff/ijdiff.toml` or `~/.ijdiff.toml`
         */
        fun resolveConfigPath(explicitPath: Path? = null, workingDir: Path = Path.of("")): Path {
            if (explicitPath != null) return explicitPath

            val localCandidates = listOf(
                workingDir.resolve("ijdiff.toml"),
                workingDir.resolve(".ijdiff.toml")
            )
            localCandidates.firstOrNull { it.exists() && it.isRegularFile() }?.let { return it }

            val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home", "."), ".config")

            val userCandidates = listOf(
                xdgConfigHome.resolve("ijdiff").resolve("config.toml"),
                xdgConfigHome.resolve("ijdiff").resolve("ijdiff.toml"),
                Path.of(System.getProperty("user.home", "."), ".ijdiff.toml")
            )
            userCandidates.firstOrNull { it.exists() && it.isRegularFile() }?.let { return it }

            // Default location for saving new user config
            return xdgConfigHome.resolve("ijdiff").resolve("config.toml")
        }

        /**
         * Loads TOML configuration from [path] if it exists, or returns [DEFAULT] with [sourcePath] set to [path].
         */
        fun load(path: Path = resolveConfigPath()): IjDiffConfig {
            if (!path.exists() || !path.isRegularFile()) {
                return DEFAULT.copy(sourcePath = path)
            }
            val raw = Files.readString(path)
            return parse(raw, sourcePath = path)
        }

        /**
         * Parses TOML configuration content, supporting `[font]` and `[ui.font]` tables as well as
         * dotted keys (`font.family`, `font.size`, `ui.font.family`, `ui.font.size`) and top-level keys.
         */
        fun parse(content: String, sourcePath: Path? = null): IjDiffConfig {
            val entries = parseTomlEntries(content)

            fun lookup(vararg keys: String): String? {
                for (k in keys) {
                    val v = entries[k]?.takeIf { it.isNotEmpty() }
                    if (v != null) return v
                }
                return null
            }

            val family = lookup(
                "font.family",
                "font.face",
                "editor.font.family",
                "editor.font.face",
                "font_family",
                "font_face"
            )
            val rawSize = lookup(
                "font.size",
                "editor.font.size",
                "font_size"
            )?.toIntOrNull()
            val size = (rawSize ?: DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)

            val uiFamily = lookup(
                "ui.font.family",
                "ui.font.face",
                "ui_font.family",
                "ui_font.face",
                "ui.font_family",
                "ui.font_face",
                "font.ui_family",
                "font.ui_face",
                "ui_font_family"
            )
            val rawUiSize = lookup(
                "ui.font.size",
                "ui_font.size",
                "ui.font_size",
                "font.ui_size",
                "ui_font_size"
            )?.toIntOrNull()
            val uiSize = (rawUiSize ?: DEFAULT_UI_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)

            val theme = lookup("theme", "ui.theme", "appearance.theme")
            val unified = lookup("unified", "diff.unified")?.toBooleanStrictOrNull() ?: false
            val whitespace = lookup("whitespace", "diff.whitespace")?.lowercase() ?: "default"

            return IjDiffConfig(
                fontFamily = family,
                fontSize = size,
                uiFontFamily = uiFamily,
                uiFontSize = uiSize,
                theme = theme,
                unified = unified,
                whitespace = whitespace,
                sourcePath = sourcePath
            )
        }

        /**
         * Serializes [config] as TOML and writes it to [targetPath].
         */
        fun save(config: IjDiffConfig, targetPath: Path = config.sourcePath ?: resolveConfigPath()): Path {
            targetPath.parent?.let { Files.createDirectories(it) }
            val content = toTomlString(config)
            Files.writeString(targetPath, content)
            return targetPath
        }

        fun toTomlString(config: IjDiffConfig): String = buildString {
            appendLine("# ijdiff configuration file (TOML)")
            if (!config.theme.isNullOrBlank()) {
                appendLine("theme = ${quoteTomlString(config.theme)}")
            } else {
                appendLine("theme = \"dark\"")
            }
            appendLine("unified = ${config.unified}")
            appendLine("whitespace = ${quoteTomlString(config.whitespace)}")
            appendLine()
            appendLine("[font]")
            if (!config.fontFamily.isNullOrBlank()) {
                appendLine("family = ${quoteTomlString(config.fontFamily)}")
            } else {
                appendLine("# family = \"JetBrains Mono\"")
            }
            appendLine("size = ${config.fontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)}")
            appendLine()
            appendLine("[ui.font]")
            if (!config.uiFontFamily.isNullOrBlank()) {
                appendLine("family = ${quoteTomlString(config.uiFontFamily)}")
            } else {
                appendLine("# family = \"Inter\"")
            }
            appendLine("size = ${config.uiFontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)}")
        }

        private fun quoteTomlString(value: String): String {
            val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
            return "\"$escaped\""
        }

        /**
         * Lightweight TOML parser handling `[section]` headers, dotted keys, basic/literal strings,
         * integers/booleans, and `#` comments outside quotes.
         */
        private fun parseTomlEntries(toml: String): Map<String, String> {
            val map = linkedMapOf<String, String>()
            var currentTable = ""

            for (rawLine in toml.lineSequence()) {
                val line = stripTomlComment(rawLine).trim()
                if (line.isEmpty()) continue

                if (line.startsWith("[") && line.endsWith("]")) {
                    currentTable = line.removeSurrounding("[", "]").trim()
                    continue
                }

                val eqIndex = findTopLevelEquals(line)
                if (eqIndex <= 0) continue

                val rawKey = line.substring(0, eqIndex).trim()
                val rawValue = line.substring(eqIndex + 1).trim()
                if (rawKey.isEmpty() || rawValue.isEmpty()) continue

                val normalizedKey = rawKey
                    .split('.')
                    .joinToString(".") { it.trim().removeSurrounding("\"").removeSurrounding("'") }

                val fullKey = if (currentTable.isEmpty()) {
                    normalizedKey
                } else {
                    "$currentTable.$normalizedKey"
                }

                map[fullKey] = unquoteTomlValue(rawValue)
            }
            return map
        }

        private fun stripTomlComment(line: String): String {
            var inDoubleQuote = false
            var inSingleQuote = false
            var escaped = false

            for (i in line.indices) {
                val c = line[i]
                if (escaped) {
                    escaped = false
                    continue
                }
                if (c == '\\' && inDoubleQuote) {
                    escaped = true
                    continue
                }
                if (c == '"' && !inSingleQuote) {
                    inDoubleQuote = !inDoubleQuote
                } else if (c == '\'' && !inDoubleQuote) {
                    inSingleQuote = !inSingleQuote
                } else if (c == '#' && !inDoubleQuote && !inSingleQuote) {
                    return line.substring(0, i)
                }
            }
            return line
        }

        private fun findTopLevelEquals(line: String): Int {
            var inDoubleQuote = false
            var inSingleQuote = false
            for (i in line.indices) {
                when (val c = line[i]) {
                    '"' -> if (!inSingleQuote) inDoubleQuote = !inDoubleQuote
                    '\'' -> if (!inDoubleQuote) inSingleQuote = !inSingleQuote
                    '=' -> if (!inDoubleQuote && !inSingleQuote) return i
                }
            }
            return -1
        }

        private fun unquoteTomlValue(raw: String): String {
            if (raw.length >= 2 && raw.startsWith('"') && raw.endsWith('"')) {
                return raw.substring(1, raw.length - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim()
            }
            if (raw.length >= 2 && raw.startsWith('\'') && raw.endsWith('\'')) {
                return raw.substring(1, raw.length - 1).trim()
            }
            return raw.trim()
        }
    }
}
