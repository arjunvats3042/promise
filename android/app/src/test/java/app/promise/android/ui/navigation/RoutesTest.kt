package app.promise.android.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutesTest {
    @Test
    fun graphsAndTabsExist() {
        assertEquals(AuthGraphRoute, AuthGraphRoute)
        assertEquals(SessionRestoreRoute, SessionRestoreRoute)
        assertEquals(LoginRoute, LoginRoute)
        assertEquals(RegisterRoute, RegisterRoute)
        assertEquals(MainGraphRoute, MainGraphRoute)
        assertEquals(HomeRoute, HomeRoute)
        assertEquals(CommitmentsRoute, CommitmentsRoute)
        assertEquals(GoalsRoute, GoalsRoute)
        assertEquals(ProfileRoute, ProfileRoute)
    }

    @Test
    fun deepLinkIdsCarryResourceIds() {
        assertEquals("c1", CommitmentRoute("c1").commitmentId)
        assertEquals("g1", GoalRoute("g1").goalId)
    }
}
