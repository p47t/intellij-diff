package org.ijdiff.ui.swing.editor

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rsyntaxtextarea.Token
import org.ijdiff.ui.swing.theme.DiffColorPalette
import org.ijdiff.ui.swing.theme.DiffThemeManager
import java.awt.Color

object SyntaxStyleResolver {
    fun resolveSyntaxStyle(fileNameOrPath: String): String {
        val lower = fileNameOrPath.lowercase()
        val ext = lower.substringAfterLast('.', "")
        return when (ext) {
            "kt", "kts" -> SyntaxConstants.SYNTAX_STYLE_KOTLIN
            "java" -> SyntaxConstants.SYNTAX_STYLE_JAVA
            "py" -> SyntaxConstants.SYNTAX_STYLE_PYTHON
            "js", "mjs", "cjs" -> SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT
            "ts", "tsx", "jsx" -> SyntaxConstants.SYNTAX_STYLE_TYPESCRIPT
            "rs" -> SyntaxConstants.SYNTAX_STYLE_RUST
            "go" -> SyntaxConstants.SYNTAX_STYLE_GO
            "c", "h" -> SyntaxConstants.SYNTAX_STYLE_C
            "cpp", "cc", "cxx", "hpp" -> SyntaxConstants.SYNTAX_STYLE_CPLUSPLUS
            "cs" -> SyntaxConstants.SYNTAX_STYLE_CSHARP
            "json" -> SyntaxConstants.SYNTAX_STYLE_JSON
            "yaml", "yml" -> SyntaxConstants.SYNTAX_STYLE_YAML
            "xml", "svg", "pom" -> SyntaxConstants.SYNTAX_STYLE_XML
            "html", "htm" -> SyntaxConstants.SYNTAX_STYLE_HTML
            "css", "scss", "less" -> SyntaxConstants.SYNTAX_STYLE_CSS
            "md", "markdown" -> SyntaxConstants.SYNTAX_STYLE_MARKDOWN
            "sh", "bash", "zsh" -> SyntaxConstants.SYNTAX_STYLE_UNIX_SHELL
            "sql" -> SyntaxConstants.SYNTAX_STYLE_SQL
            "properties", "ini", "toml", "conf" -> SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE
            "gradle" -> SyntaxConstants.SYNTAX_STYLE_GROOVY
            else -> SyntaxConstants.SYNTAX_STYLE_NONE
        }
    }

    fun applyPaletteToSyntaxArea(textArea: RSyntaxTextArea, palette: DiffColorPalette) {
        val baseFont = DiffThemeManager.editorFont
        textArea.font = baseFont
        textArea.background = Color(0, 0, 0, 0)
        textArea.foreground = palette.editorFg
        textArea.caretColor = palette.caretColor
        textArea.currentLineHighlightColor = palette.currentLineBg
        textArea.highlightCurrentLine = false

        val scheme = textArea.syntaxScheme
        if (palette.isDark) {
            scheme.getStyle(Token.RESERVED_WORD)?.foreground = Color(0xCF8E6D)
            scheme.getStyle(Token.RESERVED_WORD_2)?.foreground = Color(0xCF8E6D)
            scheme.getStyle(Token.DATA_TYPE)?.foreground = Color(0xCF8E6D)
            scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE)?.foreground = Color(0x6AAB73)
            scheme.getStyle(Token.LITERAL_CHAR)?.foreground = Color(0x6AAB73)
            scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT)?.foreground = Color(0x2AACB8)
            scheme.getStyle(Token.LITERAL_NUMBER_FLOAT)?.foreground = Color(0x2AACB8)
            scheme.getStyle(Token.LITERAL_NUMBER_HEXADECIMAL)?.foreground = Color(0x2AACB8)
            scheme.getStyle(Token.COMMENT_EOL)?.foreground = Color(0x7A7E85)
            scheme.getStyle(Token.COMMENT_MULTILINE)?.foreground = Color(0x7A7E85)
            scheme.getStyle(Token.COMMENT_DOCUMENTATION)?.foreground = Color(0x5F826B)
            scheme.getStyle(Token.ANNOTATION)?.foreground = Color(0xB3AE60)
            scheme.getStyle(Token.FUNCTION)?.foreground = Color(0x56A8F5)
            scheme.getStyle(Token.IDENTIFIER)?.foreground = palette.editorFg
            scheme.getStyle(Token.OPERATOR)?.foreground = palette.editorFg
            scheme.getStyle(Token.SEPARATOR)?.foreground = palette.editorFg
        } else {
            scheme.getStyle(Token.RESERVED_WORD)?.foreground = Color(0x0033B3)
            scheme.getStyle(Token.RESERVED_WORD_2)?.foreground = Color(0x0033B3)
            scheme.getStyle(Token.DATA_TYPE)?.foreground = Color(0x0033B3)
            scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE)?.foreground = Color(0x067D17)
            scheme.getStyle(Token.LITERAL_CHAR)?.foreground = Color(0x067D17)
            scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT)?.foreground = Color(0x1750EB)
            scheme.getStyle(Token.LITERAL_NUMBER_FLOAT)?.foreground = Color(0x1750EB)
            scheme.getStyle(Token.LITERAL_NUMBER_HEXADECIMAL)?.foreground = Color(0x1750EB)
            scheme.getStyle(Token.COMMENT_EOL)?.foreground = Color(0x8C8C8C)
            scheme.getStyle(Token.COMMENT_MULTILINE)?.foreground = Color(0x8C8C8C)
            scheme.getStyle(Token.COMMENT_DOCUMENTATION)?.foreground = Color(0x8C8C8C)
            scheme.getStyle(Token.ANNOTATION)?.foreground = Color(0x9E880D)
            scheme.getStyle(Token.FUNCTION)?.foreground = Color(0x00627A)
            scheme.getStyle(Token.IDENTIFIER)?.foreground = palette.editorFg
            scheme.getStyle(Token.OPERATOR)?.foreground = palette.editorFg
            scheme.getStyle(Token.SEPARATOR)?.foreground = palette.editorFg
        }
        // Ensure no token paints an opaque background over our diff highlight layer
        // and synchronize all token fonts with the configured editorFont face & size using composite fonts
        val spec = DiffThemeManager.fontSpec.value
        for (i in 0 until scheme.styleCount) {
            val style = scheme.getStyle(i) ?: continue
            style.background = null
            style.font = if (style.font != null && style.font.style != java.awt.Font.PLAIN) {
                DiffThemeManager.createCompositeFont(spec.family, style.font.style, spec.size)
            } else {
                baseFont
            }
        }
    }
}
