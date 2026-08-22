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

    @Test
    fun deepLinkPatternsMatchExpectedSchemes() {
        val homeUri = java.net.URI("promise://home")
        assertEquals("promise", homeUri.scheme)
        assertEquals("home", homeUri.host)

        val commitmentUri = java.net.URI("promise://commitment/c-12345")
        assertEquals("promise", commitmentUri.scheme)
        assertEquals("commitment", commitmentUri.host)
        assertEquals("/c-12345", commitmentUri.path)
        assertEquals("c-12345", commitmentUri.path.substringAfterLast("/"))

        val goalUri = java.net.URI("promise://goal/g-67890")
        assertEquals("promise", goalUri.scheme)
        assertEquals("goal", goalUri.host)
        assertEquals("/g-67890", goalUri.path)
        assertEquals("g-67890", goalUri.path.substringAfterLast("/"))
    }
}
