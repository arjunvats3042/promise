package app.promise.android.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TabDestinationsTest {
    @Test
    fun fourTabsWithHomeStart() {
        assertEquals(4, TabDestinations.items.size)
        assertEquals("Home", TabDestinations.items[0].label)
        assertEquals("Commitments", TabDestinations.items[1].label)
        assertEquals("Goals", TabDestinations.items[2].label)
        assertEquals("Profile", TabDestinations.items[3].label)
        assertEquals(HomeRoute, TabDestinations.startRoute)
    }
}
