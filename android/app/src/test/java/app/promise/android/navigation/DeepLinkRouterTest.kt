package app.promise.android.navigation

import app.promise.android.ui.navigation.DeepLinkDestination
import app.promise.android.ui.navigation.DeepLinkRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkRouterTest {

    private val router = DeepLinkRouter()

    @Test
    fun `test routeEntity with VOICE type`() {
        val routed = router.routeEntity(entityType = "VOICE", entityId = "")
        assertTrue(routed)
        val dest = router.consumeLatest()
        assertEquals(DeepLinkDestination.VoiceCapture, dest)
    }

    @Test
    fun `test routeEntity with VOICE_CAPTURE type`() {
        val routed = router.routeEntity(entityType = "VOICE_CAPTURE", entityId = "")
        assertTrue(routed)
        val dest = router.consumeLatest()
        assertEquals(DeepLinkDestination.VoiceCapture, dest)
    }

    @Test
    fun `test routeEntity with standard entities`() {
        router.routeEntity(entityType = "COMMITMENT", entityId = "c123")
        assertEquals(DeepLinkDestination.Commitment("c123"), router.consumeLatest())

        router.routeEntity(entityType = "GOAL", entityId = "g456")
        assertEquals(DeepLinkDestination.Goal("g456"), router.consumeLatest())

        router.routeEntity(entityType = "GOAL", entityId = "g456", eventType = "goal.chat.message_created")
        assertEquals(DeepLinkDestination.GoalChat("g456"), router.consumeLatest())

        router.routeEntity(entityType = "DIGEST", entityId = "")
        assertEquals(DeepLinkDestination.Home, router.consumeLatest())
    }

    @Test
    fun `test clearReplay clears latest destination`() {
        router.routeEntity(entityType = "VOICE", entityId = "")
        assertEquals(DeepLinkDestination.VoiceCapture, router.consumeLatest())
        router.clearReplay()
        assertNull(router.consumeLatest())
    }
}
