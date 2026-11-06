package org.ijdiff.model.config

import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * Application configuration model supporting font face (`font.family` / `font.face`),
 * font size (`font.size`), and optional UI defaults (`theme`).
 */
data class IjDiffConfig(
    val fontFamily: String? = null,
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val theme: String? = null,
    val sourcePath: Path? = null
) {
    companion object {
        const val DEFAULT_FONT_SIZE = 13
        const val MIN_FONT_SIZE = 8
        const val MAX_FONT_SIZE = 48

        val DEFAULT = IjDiffConfig()

        /**
         * Resolves the default configuration file path using standard priority:
         * 1. Explicit path if provided
         * 2. `./ijdiff.conf` or `./.ijdiff.conf` in the working directory
         * 3. `$XDG_CONFIG_HOME/ijdiff/ijdiff.conf` (or `~/.config/ijdiff/ijdiff.conf`)
         * 4. `~/.ijdiff.conf`
         */
        fun resolveConfigPath(explicitPath: Path? = null, workingDir: Path = Path.of("")): Path {
            if (explicitPath != null) return explicitPath

            val localCandidates = listOf(
                workingDir.resolve("ijdiff.conf"),
                workingDir.resolve(".ijdiff.conf")
            )
            localCandidates.firstOrNull { it.exists() && it.isRegularFile() }?.let { return it }

            val xdgConfigHome = System.getenv("XDG_CONFIG_HOME")?.takeIf { it.isNotBlank() }
                ?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home", "."), ".config")

            val userCandidates = listOf(
                xdgConfigHome.resolve("ijdiff").resolve("ijdiff.conf"),
                xdgConfigHome.resolve("ijdiff").resolve("config.properties"),
                Path.of(System.getProperty("user.home", "."), ".ijdiff.conf")
            )
            userCandidates.firstOrNull { it.exists() && it.isRegularFile() }?.let { return it }

            // Default location for saving new user config
            return xdgConfigHome.resolve("ijdiff").resolve("ijdiff.conf")
        }

        /**
         * Loads configuration from [path] if it exists, or returns [DEFAULT] with [sourcePath] set to [path].
         */
        fun load(path: Path = resolveConfigPath()): IjDiffConfig {
            if (!path.exists() || !path.isRegularFile()) {
                return DEFAULT.copy(sourcePath = path)
            }
            val raw = Files.readString(path)
            return parse(raw, sourcePath = path)
        }

        /**
         * Parses configuration content (supports `font.family`, `font.face`, `editor.font.family`,
         * `font.size`, `editor.font.size`, and `theme`).
         */
        fun parse(content: String, sourcePath: Path? = null): IjDiffConfig {
            val props = Properties()
            props.load(StringReader(content))

            fun getClean(vararg keys: String): String? {
                for (k in keys) {
                    val v = props.getProperty(k)?.trim()?.removeSurrounding("\"")?.removeSurrounding("'")?.trim()
                    if (!v.isNullOrEmpty()) return v
                }
                return null
            }

            val family = getClean("font.family", "font.face", "editor.font.family", "editor.font.face", "font_family", "font_face")
            val rawSize = getClean("font.size", "editor.font.size", "font_size")?.toIntOrNull()
            val size = (rawSize ?: DEFAULT_FONT_SIZE).coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
            val theme = getClean("theme", "ui.theme")

            return IjDiffConfig(
                fontFamily = family,
                fontSize = size,
                theme = theme,
                sourcePath = sourcePath
            )
        }

        /**
         * Saves [config] to [targetPath] (or `config.sourcePath` / default user config path).
         */
        fun save(config: IjDiffConfig, targetPath: Path = config.sourcePath ?: resolveConfigPath()): Path {
            targetPath.parent?.let { Files.createDirectories(it) }
            val content = buildString {
                appendLine("# ijdiff configuration file")
                if (!config.fontFamily.isNullOrBlank()) {
                    appendLine("font.family = ${config.fontFamily}")
                } else {
                    appendLine("# font.family = JetBrains Mono")
                }
                appendLine("font.size = ${config.fontSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)}")
                if (!config.theme.isNullOrBlank()) {
                    appendLine("theme = ${config.theme}")
                }
            }
            Files.writeString(targetPath, content)
            return targetPath
        }
    }
}
