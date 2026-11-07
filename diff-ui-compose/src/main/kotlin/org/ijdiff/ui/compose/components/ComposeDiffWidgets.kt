package org.ijdiff.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.ijdiff.ui.compose.syntax.ComposeSyntaxHighlighter
import org.ijdiff.ui.compose.theme.AppTheme

enum class VectorIconKind {
    ARROW_UP,
    ARROW_DOWN,
    UNDO,
    REFRESH,
    FOLDER,
    CLOSE_X,
    CHEVRON_RIGHT,
    CHEVRON_LEFT,
    CARET_DOWN
}

/**
 * Sealed component variant hierarchy for toolbar & dialog buttons (`kmp-compose-design-system`).
 */
sealed interface ButtonVariant {
    data object ToolbarDefault : ButtonVariant
    data object ToolbarPrimary : ButtonVariant
    data class ToolbarToggle(val active: Boolean) : ButtonVariant
}

@Composable
fun ComposeVectorIcon(
    kind: VectorIconKind,
    tint: Color = AppTheme.colors.editorFg,
    modifier: Modifier = Modifier.size(AppTheme.spacing.iconSize)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)
        val strokeWidth = (minDim * 0.13f).coerceAtLeast(1.5f)
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            VectorIconKind.ARROW_UP -> {
                val cx = w * 0.5f
                val top = h * 0.15f
                val bottom = h * 0.85f
                val wingX = w * 0.30f
                val wingY = h * 0.45f
                val path = Path().apply {
                    moveTo(cx, bottom)
                    lineTo(cx, top)
                    moveTo(cx - wingX, wingY)
                    lineTo(cx, top)
                    lineTo(cx + wingX, wingY)
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.ARROW_DOWN -> {
                val cx = w * 0.5f
                val top = h * 0.15f
                val bottom = h * 0.85f
                val wingX = w * 0.30f
                val wingY = h * 0.55f
                val path = Path().apply {
                    moveTo(cx, top)
                    lineTo(cx, bottom)
                    moveTo(cx - wingX, wingY)
                    lineTo(cx, bottom)
                    lineTo(cx + wingX, wingY)
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.UNDO -> {
                val pad = minDim * 0.16f
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 230f,
                    useCenter = false,
                    topLeft = Offset(pad, pad * 1.25f),
                    size = Size(w - pad * 2f, h - pad * 2f),
                    style = stroke
                )
                val headSize = minDim * 0.32f
                val headTop = pad * 1.35f
                val head = Path().apply {
                    moveTo(pad, headTop)
                    lineTo(pad, headTop + headSize)
                    lineTo(pad + headSize, headTop + headSize)
                }
                drawPath(head, color = tint, style = stroke)
            }
            VectorIconKind.REFRESH -> {
                val pad = minDim * 0.16f
                drawArc(
                    color = tint,
                    startAngle = -45f,
                    sweepAngle = -270f,
                    useCenter = false,
                    topLeft = Offset(pad, pad),
                    size = Size(w - pad * 2f, h - pad * 2f),
                    style = stroke
                )
                val headSize = minDim * 0.32f
                val rx = w - pad * 1.2f
                val head = Path().apply {
                    moveTo(rx, pad)
                    lineTo(rx, pad + headSize)
                    lineTo(rx - headSize, pad + headSize)
                }
                drawPath(head, color = tint, style = stroke)
            }
            VectorIconKind.FOLDER -> {
                val padX = w * 0.10f
                val topTabY = h * 0.20f
                val bodyTopY = h * 0.34f
                val bottomY = h * 0.84f
                val path = Path().apply {
                    moveTo(padX, topTabY)
                    lineTo(w * 0.42f, topTabY)
                    lineTo(w * 0.54f, bodyTopY)
                    lineTo(w - padX, bodyTopY)
                    lineTo(w - padX, bottomY)
                    lineTo(padX, bottomY)
                    close()
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.CLOSE_X -> {
                val pad = minDim * 0.20f
                val path = Path().apply {
                    moveTo(pad, pad)
                    lineTo(w - pad, h - pad)
                    moveTo(w - pad, pad)
                    lineTo(pad, h - pad)
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.CHEVRON_RIGHT -> {
                val cy = h * 0.5f
                val cx = w * 0.5f
                val dy = h * 0.30f
                val dx = w * 0.20f
                val gap = w * 0.15f
                val path = Path().apply {
                    moveTo(cx - gap - dx, cy - dy)
                    lineTo(cx - gap, cy)
                    lineTo(cx - gap - dx, cy + dy)
                    moveTo(cx + gap - dx, cy - dy)
                    lineTo(cx + gap, cy)
                    lineTo(cx + gap - dx, cy + dy)
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.CHEVRON_LEFT -> {
                val cy = h * 0.5f
                val cx = w * 0.5f
                val dy = h * 0.30f
                val dx = w * 0.20f
                val gap = w * 0.15f
                val path = Path().apply {
                    moveTo(cx - gap + dx, cy - dy)
                    lineTo(cx - gap, cy)
                    lineTo(cx - gap + dx, cy + dy)
                    moveTo(cx + gap + dx, cy - dy)
                    lineTo(cx + gap, cy)
                    lineTo(cx + gap + dx, cy + dy)
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.CARET_DOWN -> {
                val cx = w * 0.5f
                val cy = h * 0.5f
                val dx = w * 0.32f
                val dy = h * 0.18f
                val path = Path().apply {
                    moveTo(cx - dx, cy - dy)
                    lineTo(cx, cy + dy)
                    lineTo(cx + dx, cy - dy)
                }
                drawPath(path, color = tint, style = stroke)
            }
        }
    }
}

/**
 * Pure CMP foundation text primitive with zero `androidx.compose.material` dependencies.
 * Automatically scales Nerd Font Private Use Area (PUA) icons with a full-size Nerd Font family.
 */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    val mergedStyle = style.merge(
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textAlign = textAlign
        )
    )
    val overflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip
    if (ComposeSyntaxHighlighter.containsNerdFontGlyphs(text)) {
        val themeState = AppTheme.state
        val baseSp = if (mergedStyle.fontSize != TextUnit.Unspecified) {
            mergedStyle.fontSize.value
        } else {
            themeState.uiFontSizePt.toFloat()
        }
        val annotated = remember(text, themeState.nerdFontFamily, baseSp) {
            ComposeSyntaxHighlighter.applyNerdFontSpans(
                text = text,
                nerdFontFamily = themeState.nerdFontFamily,
                baseFontSizeSp = baseSp
            )
        }
        BasicText(
            text = annotated,
            modifier = modifier,
            style = mergedStyle,
            softWrap = softWrap,
            maxLines = maxLines,
            overflow = overflow
        )
    } else {
        BasicText(
            text = text,
            modifier = modifier,
            style = mergedStyle,
            softWrap = softWrap,
            maxLines = maxLines,
            overflow = overflow
        )
    }
}

@Composable
fun AppText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = AppTheme.typography.code,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    val mergedStyle = style.merge(
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontFamily = fontFamily,
            fontWeight = fontWeight
        )
    )
    BasicText(
        text = text,
        modifier = modifier,
        style = mergedStyle,
        softWrap = softWrap,
        maxLines = maxLines
    )
}

/**
 * Flat UI button component supporting leading icons, hover states, and custom slot content without 3D/boxed borders.
 */
@Composable
fun AppButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.ToolbarDefault,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val palette = AppTheme.colors
    val spacing = AppTheme.spacing
    val shape = AppTheme.shapes.subtle
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isActive = (variant as? ButtonVariant.ToolbarToggle)?.active == true
    val isPrimary = variant is ButtonVariant.ToolbarPrimary

    val bgColor = when {
        !enabled -> Color.Transparent
        isPrimary -> if (isHovered) palette.accentBlue.copy(alpha = 0.88f) else palette.accentBlue
        isActive -> palette.accentBlue.copy(alpha = if (palette.isDark) 0.28f else 0.18f)
        isHovered -> palette.buttonBg
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(spacing.controlHeight)
            .clip(shape)
            .background(bgColor)
            .hoverable(interactionSource = interactionSource, enabled = enabled)
            .let { mod ->
                if (enabled) mod.pointerHoverIcon(PointerIcon.Hand) else mod
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = spacing.md)
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(5.dp))
        }
        content()
    }
}

/**
 * Data + variant convenience overload of [AppButton] for flat toolbar controls.
 */
@Composable
fun AppButton(
    text: String,
    icon: VectorIconKind? = null,
    variant: ButtonVariant = ButtonVariant.ToolbarDefault,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = AppTheme.colors
    val typography = AppTheme.typography
    val isActive = (variant as? ButtonVariant.ToolbarToggle)?.active == true
    val isPrimary = variant is ButtonVariant.ToolbarPrimary
    val fgColor = when {
        !enabled -> palette.gutterFg.copy(alpha = 0.55f)
        isPrimary -> Color.White
        isActive -> if (palette.isDark) Color(0xFF88B4FF) else palette.accentBlue
        else -> palette.editorFg
    }
    val isBold = isPrimary || isActive

    val iconSize = AppTheme.spacing.iconSize
    AppButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        leadingIcon = if (icon != null && text.isNotEmpty()) {
            { ComposeVectorIcon(kind = icon, tint = fgColor, modifier = Modifier.size(iconSize)) }
        } else null
    ) {
        if (icon != null && text.isEmpty()) {
            ComposeVectorIcon(kind = icon, tint = fgColor, modifier = Modifier.size(iconSize))
        }
        if (text.isNotEmpty()) {
            AppText(
                text = text,
                style = if (isBold) typography.bodySmallBold else typography.bodySmall,
                color = fgColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ToolbarActionButton(
    text: String,
    icon: VectorIconKind? = null,
    enabled: Boolean = true,
    bold: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val variant = when {
        active -> ButtonVariant.ToolbarToggle(active = true)
        bold -> ButtonVariant.ToolbarPrimary
        else -> ButtonVariant.ToolbarDefault
    }
    AppButton(
        text = text,
        icon = icon,
        variant = variant,
        enabled = enabled,
        onClick = onClick
    )
}

/**
 * Subtle vertical divider separating functional clusters in toolbars.
 */
@Composable
fun AppToolbarSeparator() {
    val palette = AppTheme.colors
    val spacing = AppTheme.spacing
    Spacer(Modifier.width(spacing.xs))
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(16.dp)
            .background(palette.gutterBorder)
    )
    Spacer(Modifier.width(spacing.xs))
}

/**
 * Flat 1dp horizontal hairline divider between UI sections.
 */
@Composable
fun AppHorizontalDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppTheme.colors.gutterBorder)
    )
}

/**
 * Flat desktop-native Select/Dropdown built on `androidx.compose.ui.window.Popup` with zero Material dependencies.
 */
@Composable
fun <T> AppSelect(
    selected: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = AppTheme.colors
    val spacing = AppTheme.spacing
    val shape = AppTheme.shapes.subtle
    val typography = AppTheme.typography
    var expanded by remember { mutableStateOf(false) }
    val triggerInteraction = remember { MutableInteractionSource() }
    val isHovered by triggerInteraction.collectIsHoveredAsState()
    val density = LocalDensity.current
    val popupOffsetPx = with(density) { IntOffset(0, (spacing.controlHeight + spacing.xxs).roundToPx()) }

    val triggerBg = when {
        expanded -> palette.accentBlue.copy(alpha = if (palette.isDark) 0.25f else 0.16f)
        isHovered -> palette.buttonBg
        else -> palette.editorBg.copy(alpha = if (palette.isDark) 0.45f else 0.70f)
    }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(spacing.controlHeight)
                .clip(shape)
                .background(triggerBg)
                .hoverable(interactionSource = triggerInteraction)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = triggerInteraction,
                    indication = null,
                    onClick = { expanded = !expanded }
                )
                .padding(horizontal = spacing.md)
        ) {
            AppText(
                text = labelFor(selected),
                style = typography.bodySmall,
                maxLines = 1
            )
            Spacer(Modifier.width(spacing.sm))
            ComposeVectorIcon(
                kind = VectorIconKind.CARET_DOWN,
                tint = palette.gutterFg,
                modifier = Modifier.size(spacing.smallIconSize)
            )
        }

        if (expanded) {
            Popup(
                alignment = Alignment.TopStart,
                offset = popupOffsetPx,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = 140.dp)
                        .clip(shape)
                        .background(palette.editorBg)
                        .border(1.dp, palette.gutterBorder, shape)
                        .padding(vertical = spacing.xxs)
                ) {
                    for (option in options) {
                        val isCurrent = option == selected
                        val itemInteraction = remember { MutableInteractionSource() }
                        val itemHovered by itemInteraction.collectIsHoveredAsState()
                        val itemBg = when {
                            isCurrent -> palette.accentBlue.copy(alpha = if (palette.isDark) 0.30f else 0.18f)
                            itemHovered -> palette.buttonBg
                            else -> Color.Transparent
                        }
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(itemBg)
                                .hoverable(interactionSource = itemInteraction)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(
                                    interactionSource = itemInteraction,
                                    indication = null
                                ) {
                                    expanded = false
                                    onSelected(option)
                                }
                                .padding(horizontal = spacing.lg, vertical = spacing.xs + 1.dp)
                        ) {
                            AppText(
                                text = labelFor(option),
                                style = if (isCurrent) typography.bodySmallBold else typography.bodySmall,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> ToolbarDropdownSelector(
    selected: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit
) = AppSelect(
    selected = selected,
    options = options,
    labelFor = labelFor,
    onSelected = onSelected
)

/**
 * Standardized flat desktop modal dialog overlay (`AppDialog`) with header title, body slot, and footer action bar.
 */
@Composable
fun AppDialog(
    title: String,
    onDismissRequest: () -> Unit,
    width: Dp = 460.dp,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val palette = AppTheme.colors
    val spacing = AppTheme.spacing
    val shapes = AppTheme.shapes
    val typography = AppTheme.typography
    val backdropInteraction = remember { MutableInteractionSource() }
    val surfaceInteraction = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = backdropInteraction,
                indication = null,
                onClick = onDismissRequest
            )
    ) {
        Column(
            modifier = Modifier
                .width(width)
                .clip(shapes.modal)
                .background(palette.appBg)
                .border(1.dp, palette.gutterBorder, shapes.modal)
                .clickable(
                    interactionSource = surfaceInteraction,
                    indication = null,
                    onClick = {}
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xxl, vertical = spacing.lg)
            ) {
                AppText(
                    text = title,
                    style = typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(spacing.iconSize + 8.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onDismissRequest)
                ) {
                    ComposeVectorIcon(
                        kind = VectorIconKind.CLOSE_X,
                        tint = palette.gutterFg,
                        modifier = Modifier.size(spacing.smallIconSize)
                    )
                }
            }
            AppHorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xxl, vertical = spacing.xl),
                content = content
            )
            AppHorizontalDivider()
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xxl, vertical = spacing.lg),
                content = actions
            )
        }
    }
}
