package app.promise.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationActionTest {

    @Test
    fun `test payload parser normalizes entityType to uppercase and trims strings`() {
        val payload = mapOf(
            "reminder_id" to " rem_123 ",
            "entity_type" to " goal ",
            "entity_id" to " goal_abc ",
            "event_type" to " goal.reminder ",
            "title" to " Daily Habit ",
            "body" to "Time to meditate",
        )

        val parsed = NotificationPayloadParser.parse(payload)
        assertNotNull(parsed)
        assertEquals("rem_123", parsed!!.reminderId)
        assertEquals("GOAL", parsed.entityType)
        assertEquals("goal_abc", parsed.entityId)
        assertEquals("goal.reminder", parsed.eventType)
        assertEquals("Daily Habit", parsed.title)
    }

    @Test
    fun `test computeNotificationId stays within safe integer range to prevent overflow`() {
        val testKeys = listOf(
            "GOAL:g123",
            "COMMITMENT:c456",
            "SYSTEM:security_alert",
            "DIGEST:weekly_digest_2026_08_30",
            "VERY_LONG_KEY_WITH_SPECIAL_CHARS_!@#$%^&*()_+~`",
            "",
        )

        for (key in testKeys) {
            val id = PromiseNotificationData.computeNotificationId(key)
            assertTrue("ID $id should be >= 1000", id >= 1000)
            assertTrue("ID $id should be <= 100_000_999", id <= 100_000_999)

            // Ensure multiplying by 10 and adding offsets up to 10 never overflows 32-bit Int
            val requestCode1 = id * 10 + 1
            val requestCode2 = id * 10 + 2
            val requestCode3 = id * 10 + 3
            val requestCode4 = id * 10 + 4
            assertTrue("Request code should be positive", requestCode1 > 0)
            assertTrue("Request code should be positive", requestCode2 > 0)
            assertTrue("Request code should be positive", requestCode3 > 0)
            assertTrue("Request code should be positive", requestCode4 > 0)
        }
    }

    @Test
    fun `test computeNotificationId produces deterministic positive values`() {
        val key = "COMMITMENT:c_abc_999"
        val id1 = PromiseNotificationData.computeNotificationId(key)
        val id2 = PromiseNotificationData.computeNotificationId(key)
        assertEquals(id1, id2)
        assertTrue(id1 > 0)
    }
}
