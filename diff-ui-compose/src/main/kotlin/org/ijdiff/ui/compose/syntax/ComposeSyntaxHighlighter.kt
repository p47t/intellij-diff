package org.ijdiff.ui.compose.syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.ijdiff.ui.compose.theme.ComposeDiffColorPalette

/**
 * Pure-Kotlin syntax and inline-diff highlighter producing Compose [AnnotatedString]s per line.
 * Overlays inline word/character diff highlight ranges underneath syntax token colors,
 * and scales Nerd Font Private Use Area (PUA) icons with a full-size Nerd Font family.
 */
object ComposeSyntaxHighlighter {

    /**
     * Scale multiplier applied to Nerd Font PUA glyphs so icons match the visual line height
     * rather than appearing shrunken inside a narrow monospace cell.
     */
    const val NERD_FONT_SCALE_FACTOR = 1.28f

    data class InlineHighlightRange(
        val startCol: Int,
        val endCol: Int,
        val backgroundColor: Color
    )

    fun isNerdFontCodePoint(cp: Int): Boolean =
        cp in 0xE000..0xF8FF ||
            cp in 0xF0000..0xFFFFD ||
            cp in 0x100000..0x10FFFD ||
            cp in 0x23FB..0x23FE ||
            cp == 0x2665 || cp == 0x26A1 || cp == 0x2B58

    fun containsNerdFontGlyphs(text: String): Boolean {
        var i = 0
        val len = text.length
        while (i < len) {
            val cp = Character.codePointAt(text, i)
            if (isNerdFontCodePoint(cp)) return true
            i += Character.charCount(cp)
        }
        return false
    }

    fun applyNerdFontSpans(
        text: String,
        nerdFontFamily: FontFamily,
        baseFontSizeSp: Float
    ): AnnotatedString = buildAnnotatedString {
        append(text)
        addNerdFontSpansToBuilder(this, text, nerdFontFamily, baseFontSizeSp)
    }

    private fun addNerdFontSpansToBuilder(
        builder: AnnotatedString.Builder,
        text: String,
        nerdFontFamily: FontFamily?,
        baseFontSizeSp: Float
    ) {
        val scaledSp = (baseFontSizeSp * NERD_FONT_SCALE_FACTOR).sp
        val style = SpanStyle(
            fontFamily = nerdFontFamily,
            fontSize = scaledSp
        )
        var i = 0
        val len = text.length
        while (i < len) {
            val cp = Character.codePointAt(text, i)
            val count = Character.charCount(cp)
            if (isNerdFontCodePoint(cp)) {
                builder.addStyle(style, i, i + count)
            }
            i += count
        }
    }

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
        inlineRanges: List<InlineHighlightRange> = emptyList(),
        nerdFontFamily: FontFamily? = null,
        baseFontSizeSp: Float = 13f
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

        // 3. Apply full-size Nerd Font family & scaled fontSize to any PUA icon codepoints
        if (containsNerdFontGlyphs(lineText)) {
            addNerdFontSpansToBuilder(this, lineText, nerdFontFamily, baseFontSizeSp)
        }
    }
}
