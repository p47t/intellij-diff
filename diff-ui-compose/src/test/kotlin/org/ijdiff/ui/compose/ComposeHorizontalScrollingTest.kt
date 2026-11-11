package org.ijdiff.ui.compose

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.unit.dp
import androidx.compose.ui.use
import org.ijdiff.model.document.DiffDocument
import org.ijdiff.model.twoway.TwoWayDiffSession
import org.ijdiff.ui.compose.theme.ComposeThemeState
import org.ijdiff.ui.compose.theme.ProvideComposeDiffTheme
import org.ijdiff.ui.compose.twoway.ComposeDiffSidePane
import org.ijdiff.ui.compose.twoway.ComposeUnifiedDiffPane
import org.ijdiff.ui.compose.twoway.SynchronizeHorizontalScrolling
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComposeHorizontalScrollingTest {
    @Test
    fun `long line range survives short visible rows and sync clamps without bouncing`() {
        val left = ScrollState(0)
        val right = ScrollState(0)
        val leftList = LazyListState()
        val rightList = LazyListState()
        val leftDoc = DiffDocument("x".repeat(500) + "\n" + (1..100).joinToString("\n") { "short" })
        val rightDoc = DiffDocument("y".repeat(200) + "\n" + (1..100).joinToString("\n") { "short" })
        val theme = ComposeThemeState()
        ImageComposeScene(width = 1000, height = 300).use { scene ->
            scene.setContent {
                ProvideComposeDiffTheme(theme) {
                    SynchronizeHorizontalScrolling(left, right)
                    Row(Modifier.fillMaxSize()) {
                        ComposeDiffSidePane(true, leftDoc, emptyList(), -1, leftList, left, 24.dp,
                            false, false, {}, {}, Modifier.weight(1f).fillMaxHeight())
                        ComposeDiffSidePane(false, rightDoc, emptyList(), -1, rightList, right, 24.dp,
                            false, false, {}, {}, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            fun settle() { repeat(4) { scene.render() } }
            settle()
            assertTrue(left.maxValue > right.maxValue)
            assertTrue(right.maxValue > 200)
            val maxLeft = left.maxValue
            // Send real desktop input through the pane, including fractional touchpad deltas.
            scene.sendPointerEvent(
                eventType = PointerEventType.Scroll, position = Offset(200f, 50f),
                scrollDelta = Offset(0.5f, 0f)
            )
            settle()
            assertTrue(left.value > 0)
            assertEquals(left.value, right.value)
            val beforeShiftWheel = left.value
            scene.sendPointerEvent(
                eventType = PointerEventType.Scroll, position = Offset(200f, 50f),
                scrollDelta = Offset(0f, 1f),
                keyboardModifiers = PointerKeyboardModifiers(isShiftPressed = true)
            )
            settle()
            assertTrue(left.value > beforeShiftWheel)
            assertEquals(0, leftList.firstVisibleItemScrollOffset)
            left.dispatchRawDelta(-left.value.toFloat())
            settle()
            left.dispatchRawDelta(200f)
            settle()
            assertEquals(200, right.value)
            right.dispatchRawDelta(-100f)
            settle()
            assertEquals(100, left.value)
            left.dispatchRawDelta(maxLeft.toFloat())
            settle()
            assertEquals(maxLeft, left.value)
            assertEquals(right.maxValue, right.value)
            leftList.dispatchRawDelta(1000f)
            rightList.dispatchRawDelta(1000f)
            settle()
            assertTrue(leftList.firstVisibleItemIndex > 0)
            assertEquals(maxLeft, left.maxValue)
            assertEquals(maxLeft, left.value)
        }
    }

    @Test
    fun `unified long lines overflow and short documents have no horizontal range`() {
        for (length in listOf(3, 500)) {
            val session = TwoWayDiffSession(DiffDocument("a".repeat(length)), DiffDocument("b".repeat(length)))
            val scroll = ScrollState(0)
            val list = LazyListState()
            val theme = ComposeThemeState()
            ImageComposeScene(width = 600, height = 300).use { scene ->
                scene.setContent {
                    ProvideComposeDiffTheme(theme) {
                        ComposeUnifiedDiffPane(session.state.value.unifiedResult, list, 24.dp, scroll)
                    }
                }
                repeat(3) { scene.render() }
                if (length == 3) assertEquals(0, scroll.maxValue)
                else {
                    assertTrue(scroll.maxValue > 0)
                    scroll.dispatchRawDelta(200f)
                    scene.render()
                    assertEquals(200, scroll.value)
                }
            }
        }
    }
}
