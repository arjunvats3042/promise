package app.promise.android.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabTransitionTest {
    @Test
    fun indexOfMapsTabRoutes() {
        assertEquals(0, TabTransition.indexOf(HomeRoute))
        assertEquals(1, TabTransition.indexOf(CommitmentsRoute))
        assertEquals(2, TabTransition.indexOf(GoalsRoute))
        assertEquals(3, TabTransition.indexOf(ProfileRoute))
        assertNull(TabTransition.indexOf(LoginRoute))
        assertNull(TabTransition.indexOf(CommitmentRoute("x")))
    }

    @Test
    fun enterSlideSignFollowsDirection() {
        assertEquals(1, TabTransition.enterSlideSign(0, 1))
        assertEquals(-1, TabTransition.enterSlideSign(2, 1))
        assertEquals(1, TabTransition.enterSlideSign(1, 1))
    }

    @Test
    fun exitSlideSignIsOppositeOfEnter() {
        assertEquals(-1, TabTransition.exitSlideSign(0, 1))
        assertEquals(1, TabTransition.exitSlideSign(2, 1))
    }
}
