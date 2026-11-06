package org.ijdiff.ui.compose.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import org.ijdiff.core.TextDiffType
import org.ijdiff.model.config.IjDiffConfig
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface
import androidx.compose.ui.text.platform.Typeface as ComposeTypeface

enum class ComposeAppTheme(val isDark: Boolean, val displayName: String) {
    DARK(true, "Darcula (Dark)"),
    LIGHT(false, "IntelliJ Light")
}

data class ComposeDiffColorPalette(
    val isDark: Boolean,
    val appBg: Color,
    val toolbarBg: Color,
    val editorBg: Color,
    val editorFg: Color,
    val gutterBg: Color,
    val gutterFg: Color,
    val gutterBorder: Color,
    val dividerBg: Color,
    val insertedLineBg: Color,
    val insertedWordBg: Color,
    val insertedBorder: Color,
    val deletedLineBg: Color,
    val deletedWordBg: Color,
    val deletedBorder: Color,
    val modifiedLineBg: Color,
    val modifiedWordBg: Color,
    val modifiedBorder: Color,
    val activeFragmentBorder: Color,
    val buttonBg: Color,
    val buttonBorder: Color,
    val accentBlue: Color
) {
    fun getLineBg(type: TextDiffType): Color = when (type) {
        TextDiffType.INSERTED -> insertedLineBg
        TextDiffType.DELETED -> deletedLineBg
        TextDiffType.MODIFIED, TextDiffType.CONFLICT -> modifiedLineBg
    }

    fun getWordBg(type: TextDiffType): Color = when (type) {
        TextDiffType.INSERTED -> insertedWordBg
        TextDiffType.DELETED -> deletedWordBg
        TextDiffType.MODIFIED, TextDiffType.CONFLICT -> modifiedWordBg
    }

    fun getBorderColor(type: TextDiffType): Color = when (type) {
        TextDiffType.INSERTED -> insertedBorder
        TextDiffType.DELETED -> deletedBorder
        TextDiffType.MODIFIED, TextDiffType.CONFLICT -> modifiedBorder
    }

    companion object {
        val DARK = ComposeDiffColorPalette(
            isDark = true,
            appBg = Color(0xFF2B2D30),
            toolbarBg = Color(0xFF3C3F41),
            editorBg = Color(0xFF1E1F22),
            editorFg = Color(0xFFBCBEC4),
            gutterBg = Color(0xFF2B2D30),
            gutterFg = Color(0xFF6F737A),
            gutterBorder = Color(0xFF393B40),
            dividerBg = Color(0xFF26282C),
            insertedLineBg = Color(0xFF233B29),
            insertedWordBg = Color(0xFF33633E),
            insertedBorder = Color(0xFF437A4F),
            deletedLineBg = Color(0xFF402929),
            deletedWordBg = Color(0xFF6E3838),
            deletedBorder = Color(0xFF8C4646),
            modifiedLineBg = Color(0xFF24344D),
            modifiedWordBg = Color(0xFF345280),
            modifiedBorder = Color(0xFF466DA6),
            activeFragmentBorder = Color(0xFF548AF7),
            buttonBg = Color(0xFF4C5052),
            buttonBorder = Color(0xFF5E6060),
            accentBlue = Color(0xFF3574F0)
        )

        val LIGHT = ComposeDiffColorPalette(
            isDark = false,
            appBg = Color(0xFFF2F2F2),
            toolbarBg = Color(0xFFEBECF0),
            editorBg = Color(0xFFFFFFFF),
            editorFg = Color(0xFF080808),
            gutterBg = Color(0xFFF7F8FA),
            gutterFg = Color(0xFF8C8C8C),
            gutterBorder = Color(0xFFEBECF0),
            dividerBg = Color(0xFFF2F3F5),
            insertedLineBg = Color(0xFFE6F6E6),
            insertedWordBg = Color(0xFFA6E2A6),
            insertedBorder = Color(0xFF7EC47E),
            deletedLineBg = Color(0xFFFCE8E8),
            deletedWordBg = Color(0xFFF5B8B8),
            deletedBorder = Color(0xFFE08B8B),
            modifiedLineBg = Color(0xFFE8F0FE),
            modifiedWordBg = Color(0xFFB4D2FC),
            modifiedBorder = Color(0xFF8AB4F8),
            activeFragmentBorder = Color(0xFF3574F0),
            buttonBg = Color(0xFFFFFFFF),
            buttonBorder = Color(0xFFC4C4C4),
            accentBlue = Color(0xFF3574F0)
        )
    }
}

class ComposeThemeState(
    initialTheme: ComposeAppTheme = ComposeAppTheme.DARK,
    initialConfig: IjDiffConfig = IjDiffConfig.DEFAULT
) {
    var theme by mutableStateOf(initialTheme)
    var editorFontFamilyName by mutableStateOf(initialConfig.fontFamily ?: "JetBrains Mono")
    var editorFontSizePt by mutableStateOf(initialConfig.fontSize)
    var uiFontFamilyName by mutableStateOf(initialConfig.uiFontFamily ?: "Inter")
    var uiFontSizePt by mutableStateOf(initialConfig.uiFontSize)

    val palette: ComposeDiffColorPalette
        get() = if (theme.isDark) ComposeDiffColorPalette.DARK else ComposeDiffColorPalette.LIGHT

    val editorFontFamily: FontFamily
        get() = resolveFontFamily(editorFontFamilyName, fallback = FontFamily.Monospace)

    val uiFontFamily: FontFamily
        get() = resolveFontFamily(uiFontFamilyName, fallback = FontFamily.SansSerif)

    fun applyConfig(config: IjDiffConfig) {
        config.fontFamily?.takeIf { it.isNotBlank() }?.let { editorFontFamilyName = it }
        editorFontSizePt = config.fontSize.coerceIn(IjDiffConfig.MIN_FONT_SIZE, IjDiffConfig.MAX_FONT_SIZE)
        config.uiFontFamily?.takeIf { it.isNotBlank() }?.let { uiFontFamilyName = it }
        uiFontSizePt = config.uiFontSize.coerceIn(IjDiffConfig.MIN_FONT_SIZE, IjDiffConfig.MAX_FONT_SIZE)
        when (config.theme?.lowercase()) {
            "light" -> theme = ComposeAppTheme.LIGHT
            "dark", "darcula" -> theme = ComposeAppTheme.DARK
        }
    }

    companion object {
        private val fontCache = mutableMapOf<String, FontFamily>()

        fun resolveFontFamily(familyName: String, fallback: FontFamily): FontFamily {
            val trimmed = familyName.trim()
            if (trimmed.isEmpty()) return fallback
            return fontCache.getOrPut(trimmed) {
                runCatching {
                    val skiaTypeface: Typeface? = FontMgr.default.matchFamilyStyle(trimmed, FontStyle.NORMAL)
                    if (skiaTypeface != null) {
                        FontFamily(ComposeTypeface(skiaTypeface))
                    } else {
                        fallback
                    }
                }.getOrDefault(fallback)
            }
        }
    }
}

val LocalComposeThemeState = staticCompositionLocalOf { ComposeThemeState() }

@Composable
fun ProvideComposeDiffTheme(
    themeState: ComposeThemeState,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalComposeThemeState provides themeState) {
        content()
    }
}
