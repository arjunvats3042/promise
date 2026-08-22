package app.promise.android.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationPayloadParserTest {

    @Test
    fun `parse empty map returns null`() {
        val parsed = NotificationPayloadParser.parse(emptyMap())
        assertNull(parsed)
    }

    @Test
    fun `parse commitment due now routes to commitment alerts channel with high priority`() {
        val data = mapOf(
            "reminder_id" to "rem_123",
            "identity_key" to "u1:COMMITMENT:c1:commitment.due_now:2026-08-22",
            "entity_type" to "COMMITMENT",
            "entity_id" to "c1",
            "event_type" to "commitment.due_now",
            "title" to "Due now: Submit report",
            "body" to "Submit report is due right now.",
            "deep_link" to "promise://commitment/c1",
        )

        val parsed = NotificationPayloadParser.parse(data)
        assertNotNull(parsed)
        assertEquals("rem_123", parsed!!.reminderId)
        assertEquals("u1:COMMITMENT:c1:commitment.due_now:2026-08-22", parsed.identityKey)
        assertEquals("COMMITMENT", parsed.entityType)
        assertEquals("c1", parsed.entityId)
        assertEquals(NotificationChannels.CHANNEL_COMMITMENT_ALERTS, parsed.channelId)
        assertEquals("high", parsed.priority)
        assertEquals("promise://commitment/c1", parsed.deepLink)
    }

    @Test
    fun `parse commitment due soon routes to commitment reminders channel with normal priority`() {
        val data = mapOf(
            "reminder_id" to "rem_456",
            "identity_key" to "u1:COMMITMENT:c2:commitment.due_soon:2026-08-22",
            "entity_type" to "COMMITMENT",
            "entity_id" to "c2",
            "event_type" to "commitment.due_soon",
            "title" to "Upcoming promise",
            "body" to "Dentist appointment at 3 PM",
        )

        val parsed = NotificationPayloadParser.parse(data)
        assertNotNull(parsed)
        assertEquals(NotificationChannels.CHANNEL_COMMITMENT_REMINDERS, parsed!!.channelId)
        assertEquals("normal", parsed.priority)
    }

    @Test
    fun `parse goal streak protection routes to goal reminders channel`() {
        val data = mapOf(
            "reminder_id" to "rem_789",
            "identity_key" to "u1:GOAL:g1:goal.streak_protection:2026-08-22",
            "entity_type" to "GOAL",
            "entity_id" to "g1",
            "event_type" to "goal.streak_protection",
            "title" to "Protect your streak",
            "body" to "Check in for Daily Meditation today",
            "deep_link" to "promise://goal/g1",
        )

        val parsed = NotificationPayloadParser.parse(data)
        assertNotNull(parsed)
        assertEquals(NotificationChannels.CHANNEL_GOAL_REMINDERS, parsed!!.channelId)
        assertEquals("normal", parsed.priority)
        assertEquals("promise://goal/g1", parsed.deepLink)
    }

    @Test
    fun `parse security event routes to system channel with high priority`() {
        val data = mapOf(
            "reminder_id" to "rem_sec",
            "identity_key" to "u1:SYSTEM:auth:auth.new_device_login:now",
            "entity_type" to "SYSTEM",
            "entity_id" to "auth",
            "event_type" to "auth.new_device_login",
            "title" to "New login detected",
            "body" to "Logged in from Pixel 9",
        )

        val parsed = NotificationPayloadParser.parse(data)
        assertNotNull(parsed)
        assertEquals(NotificationChannels.CHANNEL_SYSTEM, parsed!!.channelId)
        assertEquals("high", parsed.priority)
    }

    @Test
    fun `fnv1a hash generates stable positive notification ids`() {
        val key1 = "u1:COMMITMENT:c1:commitment.due_now:2026-08-22"
        val key2 = "u1:COMMITMENT:c2:commitment.due_now:2026-08-22"

        val id1a = PromiseNotificationData.computeNotificationId(key1)
        val id1b = PromiseNotificationData.computeNotificationId(key1)
        val id2 = PromiseNotificationData.computeNotificationId(key2)

        assertEquals(id1a, id1b)
        org.junit.Assert.assertNotEquals(id1a, id2)
        org.junit.Assert.assertTrue(id1a > 0)
        org.junit.Assert.assertTrue(id2 > 0)
    }
}
