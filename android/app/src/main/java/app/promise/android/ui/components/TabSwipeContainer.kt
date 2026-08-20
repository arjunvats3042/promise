package app.promise.android.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import app.promise.android.ui.navigation.TabSwipeClassifier
import app.promise.android.ui.navigation.TabSwipeConfig
import app.promise.android.ui.navigation.TabSwipeDecision
import app.promise.android.ui.navigation.TabSwipeDirection
import app.promise.android.ui.navigation.TabSwipePointer

@Composable
fun TabSwipeContainer(
    currentIndex: Int,
    tabCount: Int,
    enabled: Boolean,
    onSwipe: (TabSwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    modalBlocking: Boolean = false,
    textFieldOwnsFocus: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val edgeExclusionPx = with(density) { 24.dp.toPx() }
    val commitDistancePx = with(density) { 64.dp.toPx() }
    val commitVelocity = with(density) { 650.dp.toPx() }
    val config = remember(viewConfiguration, commitDistancePx, commitVelocity, edgeExclusionPx) {
        TabSwipeConfig(
            touchSlopPx = viewConfiguration.touchSlop,
            horizontalDominanceRatio = 1.5f,
            commitDistancePx = commitDistancePx,
            commitVelocityPxPerSec = commitVelocity,
            edgeExclusionPx = edgeExclusionPx,
        )
    }
    var lockedHorizontal by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.pointerInput(
            enabled,
            currentIndex,
            tabCount,
            modalBlocking,
            textFieldOwnsFocus,
            config,
        ) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                lockedHorizontal = false
                val start = down.position
                val width = size.width.toFloat()
                val downInEdge = start.x <= config.edgeExclusionPx ||
                    start.x >= width - config.edgeExclusionPx
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var committed = false
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    val velocity = tracker.calculateVelocity().x
                    val delta = change.positionChange()
                    val childConsumedHorizontal = change.isConsumed &&
                        kotlin.math.abs(delta.x) > kotlin.math.abs(delta.y)
                    val pointer = TabSwipePointer(
                        startX = start.x,
                        startY = start.y,
                        x = change.position.x,
                        y = change.position.y,
                        velocityX = velocity,
                        downInExcludedEdge = downInEdge,
                        childConsumedHorizontal = childConsumedHorizontal,
                        textFieldOwnsFocus = textFieldOwnsFocus,
                        modalBlocking = modalBlocking,
                    )
                    val decision = TabSwipeClassifier.decide(
                        currentIndex = currentIndex,
                        tabCount = tabCount,
                        pointer = pointer,
                        lockedHorizontal = lockedHorizontal,
                        config = config,
                    )
                    when (decision) {
                        TabSwipeDecision.Ignore -> Unit
                        TabSwipeDecision.Abort -> break
                        TabSwipeDecision.Track -> {
                            lockedHorizontal = true
                            change.consume()
                        }
                        is TabSwipeDecision.Commit -> {
                            if (!committed) {
                                committed = true
                                change.consume()
                                onSwipe(decision.direction)
                            }
                        }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
        content = content,
    )
}
