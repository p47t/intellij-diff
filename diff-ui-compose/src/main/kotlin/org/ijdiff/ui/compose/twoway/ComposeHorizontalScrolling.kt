package org.ijdiff.ui.compose.twoway

import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ijdiff.ui.compose.syntax.ComposeSyntaxHighlighter
import org.ijdiff.ui.compose.theme.LocalComposeThemeState

@Composable
internal fun SynchronizeHorizontalScrolling(leftHorizontalScroll: ScrollState, rightHorizontalScroll: ScrollState) {
    // Track the positions after clamping so a shorter pane cannot pull the source back.
    LaunchedEffect(leftHorizontalScroll, rightHorizontalScroll) {
        var lastLeft = leftHorizontalScroll.value
        var lastRight = rightHorizontalScroll.value
        snapshotFlow { leftHorizontalScroll.value to rightHorizontalScroll.value }.collect { (left, right) ->
            if (left != lastLeft) {
                rightHorizontalScroll.scrollTo(left)
            } else if (right != lastRight) {
                leftHorizontalScroll.scrollTo(right)
            }
            lastLeft = leftHorizontalScroll.value
            lastRight = rightHorizontalScroll.value
        }
    }
}

/** Measure all lines, not just visible rows, so vertical scrolling never changes the X range. */
@Composable
internal fun rememberCodeWidth(lines: List<String>): Dp {
    val theme = LocalComposeThemeState.current
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(fontFamily = theme.editorFontFamily, fontSize = theme.editorFontSizePt.sp)
    val widthPx = remember(lines, style, theme.nerdFontFamily, measurer) {
        lines.maxOfOrNull { line ->
            val annotated = ComposeSyntaxHighlighter.highlightLine(
                lineText = line.replace("\t", "    "),
                palette = theme.palette,
                nerdFontFamily = theme.nerdFontFamily,
                baseFontSizeSp = theme.editorFontSizePt.toFloat()
            )
            measurer.measure(annotated, style = style, softWrap = false, maxLines = 1).size.width
        } ?: 0
    }
    return with(density) { widthPx.toDp() } + 16.dp
}

@Composable
internal fun CodeHorizontalScrollbar(state: ScrollState) {
    val palette = LocalComposeThemeState.current.palette
    if (state.maxValue == 0 || state.maxValue == Int.MAX_VALUE) {
        Spacer(Modifier.fillMaxWidth().height(10.dp))
        return
    }
    HorizontalScrollbar(
        adapter = rememberScrollbarAdapter(state),
        modifier = Modifier.fillMaxWidth().height(10.dp),
        style = ScrollbarStyle(
            minimalHeight = 24.dp,
            thickness = 8.dp,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            hoverDurationMillis = 150,
            unhoverColor = palette.gutterFg.copy(alpha = 0.45f),
            hoverColor = palette.gutterFg.copy(alpha = 0.8f)
        )
    )
}

/** Handle horizontal touchpad events and Shift+wheel before the vertical list sees them. */
internal fun Modifier.horizontalWheelScroll(state: ScrollState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type != PointerEventType.Scroll) continue
            val delta = event.changes.fold(Offset.Zero) { sum, change -> sum + change.scrollDelta }
            val horizontal = if (event.keyboardModifiers.isShiftPressed && delta.x == 0f) delta.y else delta.x
            if (horizontal != 0f) {
                // Desktop wheel deltas are in line units, including fractional touchpad deltas.
                state.dispatchRawDelta(horizontal * 16.dp.toPx())
                event.changes.forEach { it.consume() }
            }
        }
    }
}
