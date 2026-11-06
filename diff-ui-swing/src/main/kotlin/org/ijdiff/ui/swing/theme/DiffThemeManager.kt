package org.ijdiff.ui.swing.theme

import com.formdev.flatlaf.FlatDarculaLaf
import com.formdev.flatlaf.FlatIntelliJLaf
import com.formdev.flatlaf.FlatLaf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ijdiff.core.TextDiffType
import org.ijdiff.model.config.IjDiffConfig
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.plaf.FontUIResource
import javax.swing.text.StyleContext

enum class AppTheme(val isDark: Boolean, val displayName: String) {
    LIGHT(false, "IntelliJ Light"),
    DARK(true, "Darcula (Dark)")
}

data class FontSpec(
    val family: String,
    val size: Int
)

data class DiffColorPalette(
    val isDark: Boolean,
    val editorBg: Color,
    val editorFg: Color,
    val caretColor: Color,
    val currentLineBg: Color,
    val gutterBg: Color,
    val gutterFg: Color,
    val gutterDimFg: Color,
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
    val activeFragmentBorder: Color
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
}

object DiffThemeManager {
    val LIGHT_PALETTE = DiffColorPalette(
        isDark = false,
        editorBg = Color(0xFFFFFF),
        editorFg = Color(0x080808),
        caretColor = Color(0x000000),
        currentLineBg = Color(0xF5F8FE),
        gutterBg = Color(0xF7F8FA),
        gutterFg = Color(0x8C8C8C),
        gutterDimFg = Color(0xB8B8B8),
        gutterBorder = Color(0xEBECF0),
        dividerBg = Color(0xF2F3F5),
        // Authentic IntelliJ Light Diff Colors
        insertedLineBg = Color(0xE6F6E6),
        insertedWordBg = Color(0xA6E2A6),
        insertedBorder = Color(0x7EC47E),
        deletedLineBg = Color(0xFCE8E8),
        deletedWordBg = Color(0xF5B8B8),
        deletedBorder = Color(0xE08B8B),
        modifiedLineBg = Color(0xE8F0FE),
        modifiedWordBg = Color(0xB4D2FC),
        modifiedBorder = Color(0x8AB4F8),
        activeFragmentBorder = Color(0x3574F0)
    )

    val DARK_PALETTE = DiffColorPalette(
        isDark = true,
        editorBg = Color(0x1E1F22),
        editorFg = Color(0xBCBEC4),
        caretColor = Color(0xCED0D6),
        currentLineBg = Color(0x26282E),
        gutterBg = Color(0x2B2D30),
        gutterFg = Color(0x6F737A),
        gutterDimFg = Color(0x4E5157),
        gutterBorder = Color(0x393B40),
        dividerBg = Color(0x26282C),
        // Authentic IntelliJ Darcula Diff Colors
        insertedLineBg = Color(0x233B29),
        insertedWordBg = Color(0x33633E),
        insertedBorder = Color(0x437A4F),
        deletedLineBg = Color(0x402929),
        deletedWordBg = Color(0x6E3838),
        deletedBorder = Color(0x8C4646),
        modifiedLineBg = Color(0x24344D),
        modifiedWordBg = Color(0x345280),
        modifiedBorder = Color(0x466DA6),
        activeFragmentBorder = Color(0x548AF7)
    )

    private val availableFontFamilies: Set<String> by lazy {
        runCatching {
            GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
        }.getOrDefault(emptySet())
    }

    private val defaultFontFamily: String by lazy {
        val preferred = listOf("JetBrains Mono", "Ioskeley Mono", "Fira Code", "Consolas", Font.MONOSPACED)
        preferred.firstOrNull { it in availableFontFamilies } ?: Font.MONOSPACED
    }

    private val defaultUiFontFamily: String by lazy {
        val preferred = listOf("Inter", "Segoe UI", "SF Pro Text", "Ubuntu", "Noto Sans", "Cantarell", Font.SANS_SERIF)
        preferred.firstOrNull { it in availableFontFamilies } ?: Font.SANS_SERIF
    }

    private val _currentTheme = MutableStateFlow(AppTheme.DARK)
    val currentTheme: StateFlow<AppTheme> = _currentTheme.asStateFlow()

    private val _fontSpec = MutableStateFlow(
        FontSpec(family = defaultFontFamily, size = IjDiffConfig.DEFAULT_FONT_SIZE)
    )
    val fontSpec: StateFlow<FontSpec> = _fontSpec.asStateFlow()

    private val _uiFontSpec = MutableStateFlow(
        FontSpec(family = defaultUiFontFamily, size = IjDiffConfig.DEFAULT_UI_FONT_SIZE)
    )
    val uiFontSpec: StateFlow<FontSpec> = _uiFontSpec.asStateFlow()

    @Volatile
    var editorFont: Font = createCompositeFont(defaultFontFamily, Font.PLAIN, IjDiffConfig.DEFAULT_FONT_SIZE)
        private set

    @Volatile
    var uiFont: Font = createCompositeFont(defaultUiFontFamily, Font.PLAIN, IjDiffConfig.DEFAULT_UI_FONT_SIZE)
        private set

    val palette: DiffColorPalette
        get() = if (_currentTheme.value.isDark) DARK_PALETTE else LIGHT_PALETTE

    fun getAvailableFontFamilies(): List<String> = availableFontFamilies.sorted()

    /**
     * Creates a composite [Font] via [StyleContext] so OS font fallback (for CJK, symbols, etc.)
     * is preserved on Linux/macOS/Windows instead of rendering missing glyphs as boxes (`□`).
     */
    fun createCompositeFont(family: String, style: Int = Font.PLAIN, size: Int): Font {
        return StyleContext.getDefaultStyleContext().getFont(family, style, size)
    }

    fun uiBoldFont(sizeOffset: Int = 0): Font {
        val spec = _uiFontSpec.value
        return createCompositeFont(spec.family, Font.BOLD, (spec.size + sizeOffset).coerceAtLeast(8))
    }

    fun uiItalicFont(sizeOffset: Int = 0): Font {
        val spec = _uiFontSpec.value
        return createCompositeFont(spec.family, Font.ITALIC, (spec.size + sizeOffset).coerceAtLeast(8))
    }

    fun initialize(theme: AppTheme = AppTheme.DARK, config: IjDiffConfig? = null) {
        if (config != null) {
            setFont(config.fontFamily, config.fontSize)
            setUiFont(config.uiFontFamily, config.uiFontSize, updateLaf = false)
            val resolvedTheme = when (config.theme?.lowercase()) {
                "light" -> AppTheme.LIGHT
                "dark", "darcula" -> AppTheme.DARK
                else -> theme
            }
            _currentTheme.value = resolvedTheme
        } else {
            _currentTheme.value = theme
        }
        setupLaf(_currentTheme.value)
    }

    fun applyConfig(config: IjDiffConfig) {
        setFont(config.fontFamily, config.fontSize)
        setUiFont(config.uiFontFamily, config.uiFontSize)
        when (config.theme?.lowercase()) {
            "light" -> setTheme(AppTheme.LIGHT)
            "dark", "darcula" -> setTheme(AppTheme.DARK)
        }
    }

    fun setFont(family: String?, size: Int?) {
        val resolvedFamily = family?.trim()?.takeIf { it.isNotEmpty() } ?: _fontSpec.value.family
        val resolvedSize = (size ?: _fontSpec.value.size).coerceIn(IjDiffConfig.MIN_FONT_SIZE, IjDiffConfig.MAX_FONT_SIZE)
        val newSpec = FontSpec(family = resolvedFamily, size = resolvedSize)
        _fontSpec.value = newSpec
        editorFont = createCompositeFont(newSpec.family, Font.PLAIN, newSpec.size)
    }

    fun setUiFont(family: String?, size: Int?, updateLaf: Boolean = true) {
        val resolvedFamily = family?.trim()?.takeIf { it.isNotEmpty() } ?: _uiFontSpec.value.family
        val resolvedSize = (size ?: _uiFontSpec.value.size).coerceIn(IjDiffConfig.MIN_FONT_SIZE, IjDiffConfig.MAX_FONT_SIZE)
        val newSpec = FontSpec(family = resolvedFamily, size = resolvedSize)
        _uiFontSpec.value = newSpec
        uiFont = createCompositeFont(newSpec.family, Font.PLAIN, newSpec.size)
        runCatching {
            val resource = uiFont as? FontUIResource ?: FontUIResource(uiFont)
            UIManager.put("defaultFont", resource)
            if (updateLaf) {
                if (SwingUtilities.isEventDispatchThread()) {
                    FlatLaf.updateUI()
                } else {
                    SwingUtilities.invokeLater { FlatLaf.updateUI() }
                }
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        if (_currentTheme.value == theme) return
        _currentTheme.value = theme
        setupLaf(theme)
        runCatching {
            if (SwingUtilities.isEventDispatchThread()) {
                FlatLaf.updateUI()
            } else {
                SwingUtilities.invokeLater { FlatLaf.updateUI() }
            }
        }
    }

    private fun setupLaf(theme: AppTheme) {
        runCatching {
            val resource = uiFont as? FontUIResource ?: FontUIResource(uiFont)
            UIManager.put("defaultFont", resource)
            if (theme.isDark) {
                FlatDarculaLaf.setup()
            } else {
                FlatIntelliJLaf.setup()
            }
            UIManager.put("defaultFont", resource)
        }
    }
}
