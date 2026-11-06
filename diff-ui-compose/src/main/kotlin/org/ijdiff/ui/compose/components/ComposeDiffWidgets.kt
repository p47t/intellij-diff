package org.ijdiff.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ijdiff.ui.compose.theme.LocalComposeThemeState

enum class VectorIconKind {
    ARROW_UP,
    ARROW_DOWN,
    UNDO,
    REFRESH,
    FOLDER,
    CLOSE_X,
    CHEVRON_RIGHT,
    CHEVRON_LEFT
}

@Composable
fun ComposeVectorIcon(
    kind: VectorIconKind,
    tint: Color = LocalComposeThemeState.current.palette.editorFg,
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
    val theme = LocalComposeThemeState.current
    val palette = theme.palette
    val shape = RoundedCornerShape(4.dp)
    val bgColor = when {
        active -> palette.accentBlue.copy(alpha = 0.28f)
        enabled -> palette.buttonBg
        else -> palette.toolbarBg
    }
    val borderColor = if (active) palette.accentBlue else palette.buttonBorder
    val fgColor = if (enabled) palette.editorFg else palette.gutterFg

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .height(26.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        if (icon != null) {
            ComposeVectorIcon(kind = icon, tint = fgColor, modifier = Modifier.size(12.dp))
            if (text.isNotEmpty()) {
                Spacer(Modifier.width(5.dp))
            }
        }
        if (text.isNotEmpty()) {
            Text(
                text = text,
                color = fgColor,
                fontFamily = theme.uiFontFamily,
                fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
fun <T> ToolbarDropdownSelector(
    selected: T,
    options: List<T>,
    labelFor: (T) -> String,
    onSelected: (T) -> Unit
) {
    val theme = LocalComposeThemeState.current
    val palette = theme.palette
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(26.dp)
                .clip(shape)
                .background(palette.buttonBg)
                .border(1.dp, palette.buttonBorder, shape)
                .clickable { expanded = true }
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = labelFor(selected),
                color = palette.editorFg,
                fontFamily = theme.uiFontFamily,
                fontSize = (theme.uiFontSizePt - 1).coerceAtLeast(10).sp,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "v",
                color = palette.gutterFg,
                fontFamily = theme.uiFontFamily,
                fontSize = 10.sp
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(palette.toolbarBg)
        ) {
            for (option in options) {
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onSelected(option)
                    }
                ) {
                    Text(
                        text = labelFor(option),
                        color = palette.editorFg,
                        fontFamily = theme.uiFontFamily,
                        fontSize = theme.uiFontSizePt.sp
                    )
                }
            }
        }
    }
}
