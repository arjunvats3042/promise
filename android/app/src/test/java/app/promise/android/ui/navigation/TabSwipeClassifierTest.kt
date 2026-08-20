package app.promise.android.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSwipeClassifierTest {
    private val config = TabSwipeConfig(
        touchSlopPx = 10f,
        horizontalDominanceRatio = 1.5f,
        commitDistancePx = 64f,
        commitVelocityPxPerSec = 650f,
        edgeExclusionPx = 24f,
    )

    @Test
    fun ignoresSmallMovementInsideSlop() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 0,
            tabCount = 4,
            pointer = pointer(dx = 5f, dy = 2f),
            lockedHorizontal = false,
            config = config,
        )
        assertEquals(TabSwipeDecision.Ignore, decision)
    }

    @Test
    fun ignoresVerticalDominantDrag() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 1,
            tabCount = 4,
            pointer = pointer(dx = 20f, dy = 40f),
            lockedHorizontal = false,
            config = config,
        )
        assertEquals(TabSwipeDecision.Ignore, decision)
    }

    @Test
    fun tracksHorizontalIntentBeforeCommit() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 1,
            tabCount = 4,
            pointer = pointer(dx = -40f, dy = 5f),
            lockedHorizontal = false,
            config = config,
        )
        assertEquals(TabSwipeDecision.Track, decision)
    }

    @Test
    fun commitsNextOnDistance() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 1,
            tabCount = 4,
            pointer = pointer(dx = -70f, dy = 4f),
            lockedHorizontal = true,
            config = config,
        )
        assertEquals(TabSwipeDecision.Commit(TabSwipeDirection.Next), decision)
    }

    @Test
    fun commitsPreviousOnVelocity() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 2,
            tabCount = 4,
            pointer = pointer(dx = 20f, dy = 2f, velocityX = 800f),
            lockedHorizontal = true,
            config = config,
        )
        assertEquals(TabSwipeDecision.Commit(TabSwipeDirection.Previous), decision)
    }

    @Test
    fun ignoresSwipePastFirstTab() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 0,
            tabCount = 4,
            pointer = pointer(dx = 80f, dy = 0f),
            lockedHorizontal = true,
            config = config,
        )
        assertEquals(TabSwipeDecision.Ignore, decision)
    }

    @Test
    fun ignoresSwipePastLastTab() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 3,
            tabCount = 4,
            pointer = pointer(dx = -80f, dy = 0f),
            lockedHorizontal = true,
            config = config,
        )
        assertEquals(TabSwipeDecision.Ignore, decision)
    }

    @Test
    fun ignoresSystemEdgeDown() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 1,
            tabCount = 4,
            pointer = pointer(dx = -80f, dy = 0f, downInExcludedEdge = true),
            lockedHorizontal = false,
            config = config,
        )
        assertEquals(TabSwipeDecision.Ignore, decision)
    }

    @Test
    fun ignoresWhenModalBlocking() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 1,
            tabCount = 4,
            pointer = pointer(dx = -80f, dy = 0f, modalBlocking = true),
            lockedHorizontal = false,
            config = config,
        )
        assertEquals(TabSwipeDecision.Ignore, decision)
    }

    @Test
    fun ignoresWhenTextFieldOwnsFocus() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 1,
            tabCount = 4,
            pointer = pointer(dx = -80f, dy = 0f, textFieldOwnsFocus = true),
            lockedHorizontal = false,
            config = config,
        )
        assertEquals(TabSwipeDecision.Ignore, decision)
    }

    @Test
    fun abortsWhenChildConsumedHorizontal() {
        val decision = TabSwipeClassifier.decide(
            currentIndex = 1,
            tabCount = 4,
            pointer = pointer(dx = -80f, dy = 0f, childConsumedHorizontal = true),
            lockedHorizontal = true,
            config = config,
        )
        assertEquals(TabSwipeDecision.Abort, decision)
    }

    @Test
    fun adjacentIndexOneTabOnly() {
        assertEquals(2, TabSwipeClassifier.adjacentIndex(1, TabSwipeDirection.Next, 4))
        assertEquals(0, TabSwipeClassifier.adjacentIndex(1, TabSwipeDirection.Previous, 4))
        assertNull(TabSwipeClassifier.adjacentIndex(0, TabSwipeDirection.Previous, 4))
        assertNull(TabSwipeClassifier.adjacentIndex(3, TabSwipeDirection.Next, 4))
    }

    @Test
    fun locksAfterHorizontalIntentWins() {
        val pre = TabSwipeClassifier.decide(
            currentIndex = 1,
            tabCount = 4,
            pointer = pointer(dx = -20f, dy = 5f),
            lockedHorizontal = false,
            config = config,
        )
        assertTrue(pre is TabSwipeDecision.Track || pre is TabSwipeDecision.Ignore)
        val locked = TabSwipeClassifier.decide(
            currentIndex = 1,
            tabCount = 4,
            pointer = pointer(dx = -70f, dy = 50f),
            lockedHorizontal = true,
            config = config,
        )
        assertEquals(TabSwipeDecision.Commit(TabSwipeDirection.Next), locked)
    }

    private fun pointer(
        dx: Float,
        dy: Float,
        velocityX: Float = 0f,
        downInExcludedEdge: Boolean = false,
        childConsumedHorizontal: Boolean = false,
        textFieldOwnsFocus: Boolean = false,
        modalBlocking: Boolean = false,
    ): TabSwipePointer {
        return TabSwipePointer(
            startX = 200f,
            startY = 400f,
            x = 200f + dx,
            y = 400f + dy,
            velocityX = velocityX,
            downInExcludedEdge = downInExcludedEdge,
            childConsumedHorizontal = childConsumedHorizontal,
            textFieldOwnsFocus = textFieldOwnsFocus,
            modalBlocking = modalBlocking,
        )
    }
}
