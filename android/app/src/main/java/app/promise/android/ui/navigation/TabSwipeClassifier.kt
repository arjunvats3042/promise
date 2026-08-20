package app.promise.android.ui.navigation

/**
 * Pure adjacent-tab swipe intent classifier.
 * Unit-tested in isolation before any Compose integration.
 */
enum class TabSwipeDirection {
    Previous,
    Next,
}

sealed interface TabSwipeDecision {
    data object Ignore : TabSwipeDecision
    data object Track : TabSwipeDecision
    data class Commit(val direction: TabSwipeDirection) : TabSwipeDecision
    data object Abort : TabSwipeDecision
}

data class TabSwipeConfig(
    val touchSlopPx: Float = 24f,
    val horizontalDominanceRatio: Float = 1.5f,
    val commitDistancePx: Float = 192f,
    val commitVelocityPxPerSec: Float = 1950f,
    val edgeExclusionPx: Float = 72f,
)

data class TabSwipePointer(
    val startX: Float,
    val startY: Float,
    val x: Float,
    val y: Float,
    val velocityX: Float = 0f,
    val downInExcludedEdge: Boolean = false,
    val childConsumedHorizontal: Boolean = false,
    val textFieldOwnsFocus: Boolean = false,
    val modalBlocking: Boolean = false,
)

object TabSwipeClassifier {
    fun decide(
        currentIndex: Int,
        tabCount: Int,
        pointer: TabSwipePointer,
        lockedHorizontal: Boolean,
        config: TabSwipeConfig = TabSwipeConfig(),
    ): TabSwipeDecision {
        if (tabCount <= 1) return TabSwipeDecision.Ignore
        if (pointer.downInExcludedEdge) return TabSwipeDecision.Ignore
        if (pointer.modalBlocking) return TabSwipeDecision.Ignore
        if (pointer.textFieldOwnsFocus) return TabSwipeDecision.Ignore
        if (pointer.childConsumedHorizontal) return TabSwipeDecision.Abort

        val dx = pointer.x - pointer.startX
        val dy = pointer.y - pointer.startY
        val absDx = kotlin.math.abs(dx)
        val absDy = kotlin.math.abs(dy)

        if (!lockedHorizontal) {
            if (absDx < config.touchSlopPx && absDy < config.touchSlopPx) {
                return TabSwipeDecision.Ignore
            }
            if (absDx < config.touchSlopPx) {
                return TabSwipeDecision.Ignore
            }
            if (absDx < absDy * config.horizontalDominanceRatio) {
                return TabSwipeDecision.Ignore
            }
        }

        val direction = if (dx < 0f) TabSwipeDirection.Next else TabSwipeDirection.Previous
        val nextIndex = when (direction) {
            TabSwipeDirection.Next -> currentIndex + 1
            TabSwipeDirection.Previous -> currentIndex - 1
        }
        if (nextIndex !in 0 until tabCount) {
            return TabSwipeDecision.Ignore
        }

        val distanceCommit = absDx >= config.commitDistancePx
        val velocityCommit = kotlin.math.abs(pointer.velocityX) >= config.commitVelocityPxPerSec &&
            ((pointer.velocityX < 0f && direction == TabSwipeDirection.Next) ||
                (pointer.velocityX > 0f && direction == TabSwipeDirection.Previous))

        return if (distanceCommit || velocityCommit) {
            TabSwipeDecision.Commit(direction)
        } else {
            TabSwipeDecision.Track
        }
    }

    fun adjacentIndex(currentIndex: Int, direction: TabSwipeDirection, tabCount: Int): Int? {
        val next = when (direction) {
            TabSwipeDirection.Next -> currentIndex + 1
            TabSwipeDirection.Previous -> currentIndex - 1
        }
        return next.takeIf { it in 0 until tabCount }
    }
}
