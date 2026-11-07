package org.ijdiff.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
    modifier: Modifier = Modifier.size(12.dp)
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (kind) {
            VectorIconKind.ARROW_UP -> {
                val cx = w / 2f
                val path = Path().apply {
                    moveTo(cx, h - 2f)
                    lineTo(cx, 2f)
                    moveTo(cx - 3.5f, 5.5f)
                    lineTo(cx, 2f)
                    lineTo(cx + 3.5f, 5.5f)
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.ARROW_DOWN -> {
                val cx = w / 2f
                val path = Path().apply {
                    moveTo(cx, 2f)
                    lineTo(cx, h - 2f)
                    moveTo(cx - 3.5f, h - 5.5f)
                    lineTo(cx, h - 2f)
                    lineTo(cx + 3.5f, h - 5.5f)
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.UNDO -> {
                drawArc(
                    color = tint,
                    startAngle = 180f,
                    sweepAngle = 230f,
                    useCenter = false,
                    topLeft = Offset(2f, 3f),
                    size = Size(w - 4f, h - 4f),
                    style = stroke
                )
                val head = Path().apply {
                    moveTo(2f, 4f)
                    lineTo(2f, 8f)
                    lineTo(6f, 8f)
                }
                drawPath(head, color = tint, style = stroke)
            }
            VectorIconKind.REFRESH -> {
                drawArc(
                    color = tint,
                    startAngle = -45f,
                    sweepAngle = -270f,
                    useCenter = false,
                    topLeft = Offset(2f, 2f),
                    size = Size(w - 4f, h - 4f),
                    style = stroke
                )
                val head = Path().apply {
                    moveTo(w - 2.5f, 2f)
                    lineTo(w - 2.5f, 6f)
                    lineTo(w - 6.5f, 6f)
                }
                drawPath(head, color = tint, style = stroke)
            }
            VectorIconKind.FOLDER -> {
                val path = Path().apply {
                    moveTo(1.5f, 3f)
                    lineTo(w * 0.42f, 3f)
                    lineTo(w * 0.54f, 4.8f)
                    lineTo(w - 1.5f, 4.8f)
                    lineTo(w - 1.5f, h - 2f)
                    lineTo(1.5f, h - 2f)
                    close()
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.CLOSE_X -> {
                val pad = 2.2f
                val path = Path().apply {
                    moveTo(pad, pad)
                    lineTo(w - pad, h - pad)
                    moveTo(w - pad, pad)
                    lineTo(pad, h - pad)
                }
                drawPath(path, color = tint, style = stroke)
            }
            VectorIconKind.CHEVRON_RIGHT -> {
                val cy = h / 2f
                val cx = w / 2f
                val dy = 3.2f
                val dx = 2.5f
                val gap = 2.0f
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
                val cy = h / 2f
                val cx = w / 2f
                val dy = 3.2f
                val dx = 2.5f
                val gap = 2.0f
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
                val cx = w / 2f
                val cy = h / 2f
                val path = Path().apply {
                    moveTo(cx - 3.2f, cy - 1.5f)
                    lineTo(cx, cy + 2.0f)
                    lineTo(cx + 3.2f, cy - 1.5f)
                }
                drawPath(path, color = tint, style = stroke)
            }
        }
    }
}

/**
 * Pure CMP foundation text primitive with zero `androidx.compose.material` dependencies.
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
    BasicText(
        text = text,
        modifier = modifier,
        style = mergedStyle,
        softWrap = softWrap,
        maxLines = maxLines,
        overflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip
    )
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
 * Variant-based button component supporting leading icons and custom slot content.
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
    val isActive = (variant as? ButtonVariant.ToolbarToggle)?.active == true
    val isPrimary = variant is ButtonVariant.ToolbarPrimary

    val bgColor = when {
        isActive -> palette.accentBlue.copy(alpha = 0.28f)
        isPrimary && enabled -> palette.accentBlue.copy(alpha = if (palette.isDark) 0.24f else 0.16f)
        enabled -> palette.buttonBg
        else -> palette.toolbarBg
    }
    val borderColor = when {
        isActive -> palette.accentBlue
        isPrimary && enabled -> palette.accentBlue
        else -> palette.buttonBorder
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .height(spacing.controlHeight)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .let { mod ->
                if (enabled) mod.pointerHoverIcon(PointerIcon.Hand) else mod
            }
            .clickable(enabled = enabled, onClick = onClick)
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
 * Data + variant convenience overload of [AppButton] for toolbar controls.
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
    val fgColor = if (enabled) palette.editorFg else palette.gutterFg
    val isBold = variant is ButtonVariant.ToolbarPrimary

    AppButton(
        onClick = onClick,
        modifier = modifier,
        variant = variant,
        enabled = enabled,
        leadingIcon = if (icon != null && text.isNotEmpty()) {
            { ComposeVectorIcon(kind = icon, tint = fgColor, modifier = Modifier.size(12.dp)) }
        } else null
    ) {
        if (icon != null && text.isEmpty()) {
            ComposeVectorIcon(kind = icon, tint = fgColor, modifier = Modifier.size(12.dp))
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
    Spacer(Modifier.width(spacing.sm))
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(palette.gutterBorder)
    )
    Spacer(Modifier.width(spacing.sm))
}

/**
 * Desktop-native Select/Dropdown built on `androidx.compose.ui.window.Popup` with zero Material dependencies.
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
    val density = LocalDensity.current
    val popupOffsetPx = with(density) { IntOffset(0, (spacing.controlHeight + spacing.xxs).roundToPx()) }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(spacing.controlHeight)
                .clip(shape)
                .background(palette.buttonBg)
                .border(1.dp, if (expanded) palette.accentBlue else palette.buttonBorder, shape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable { expanded = !expanded }
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
                modifier = Modifier.size(10.dp)
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
                        .background(palette.toolbarBg)
                        .border(1.dp, palette.buttonBorder, shape)
                        .padding(vertical = spacing.xs)
                ) {
                    for (option in options) {
                        val isCurrent = option == selected
                        val itemBg = if (isCurrent) {
                            palette.accentBlue.copy(alpha = if (palette.isDark) 0.32f else 0.20f)
                        } else {
                            Color.Transparent
                        }
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(itemBg)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
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
 * Standardized desktop modal dialog overlay (`AppDialog`) with header title, body slot, and footer action bar.
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
                .background(palette.toolbarBg)
                .border(1.dp, palette.buttonBorder, shapes.modal)
                .clickable(
                    interactionSource = surfaceInteraction,
                    indication = null,
                    onClick = {}
                )
                .padding(spacing.xxl)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                AppText(
                    text = title,
                    style = typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(18.dp)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onDismissRequest)
                ) {
                    ComposeVectorIcon(
                        kind = VectorIconKind.CLOSE_X,
                        tint = palette.gutterFg,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
            Spacer(Modifier.height(spacing.lg + 2.dp))
            content()
            Spacer(Modifier.height(spacing.xl))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                content = actions
            )
        }
    }
}
