package org.ijdiff.ui.compose.syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.ijdiff.ui.compose.theme.ComposeDiffColorPalette

/**
 * Pure-Kotlin syntax and inline-diff highlighter producing Compose [AnnotatedString]s per line.
 * Overlays inline word/character diff highlight ranges underneath syntax token colors.
 */
object ComposeSyntaxHighlighter {

    data class InlineHighlightRange(
        val startCol: Int,
        val endCol: Int,
        val backgroundColor: Color
    )

    private val KEYWORDS = setOf(
        "package", "import", "class", "interface", "object", "fun", "val", "var", "return",
        "if", "else", "when", "for", "while", "do", "try", "catch", "finally", "throw",
        "private", "protected", "public", "internal", "open", "override", "abstract",
        "sealed", "data", "enum", "companion", "suspend", "inline", "reified", "const",
        "true", "false", "null", "this", "super", "is", "in", "as", "by", "where",
        "void", "int", "long", "double", "float", "boolean", "char", "byte", "short",
        "static", "final", "extends", "implements", "new", "switch", "case", "default",
        "def", "from", "async", "await", "let", "mut", "fn", "pub", "struct", "impl", "use"
    )

    private val TOKEN_REGEX = Regex(
        """(//.*$|#.*$)|("(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*')|(@[A-Za-z_][A-Za-z0-9_]*)|\b([0-9]+(?:\.[0-9]+)?[Lfd]?)\b|\b([A-Za-z_][A-Za-z0-9_]*)\b"""
    )

    fun highlightLine(
        lineText: String,
        palette: ComposeDiffColorPalette,
        inlineRanges: List<InlineHighlightRange> = emptyList()
    ): AnnotatedString = buildAnnotatedString {
        append(lineText)
        if (lineText.isEmpty()) return@buildAnnotatedString

        // 1. Apply inline word/character diff background spans
        for (range in inlineRanges) {
            val s = range.startCol.coerceIn(0, lineText.length)
            val e = range.endCol.coerceIn(0, lineText.length)
            if (s < e) {
                addStyle(SpanStyle(background = range.backgroundColor), s, e)
            }
        }

        // 2. Apply syntax foreground spans
        val keywordColor = if (palette.isDark) Color(0xFFCF8E6D) else Color(0xFF0033B3)
        val stringColor = if (palette.isDark) Color(0xFF6AAB73) else Color(0xFF067D17)
        val numberColor = if (palette.isDark) Color(0xFF2AACB8) else Color(0xFF1750EB)
        val commentColor = if (palette.isDark) Color(0xFF7A7E85) else Color(0xFF8C8C8C)
        val annotationColor = if (palette.isDark) Color(0xFFB3AE60) else Color(0xFF9E880D)

        for (match in TOKEN_REGEX.findAll(lineText)) {
            val s = match.range.first
            val e = match.range.last + 1
            when {
                match.groups[1] != null -> {
                    addStyle(SpanStyle(color = commentColor), s, e)
                }
                match.groups[2] != null -> {
                    addStyle(SpanStyle(color = stringColor), s, e)
                }
                match.groups[3] != null -> {
                    addStyle(SpanStyle(color = annotationColor), s, e)
                }
                match.groups[4] != null -> {
                    addStyle(SpanStyle(color = numberColor), s, e)
                }
                match.groups[5] != null -> {
                    val word = match.value
                    if (word in KEYWORDS) {
                        addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.SemiBold), s, e)
                    }
                }
            }
        }
    }
}
