package app.promise.android.notifications

object NotificationPayloadParser {

    fun parse(data: Map<String, String>): PromiseNotificationData? {
        val reminderId = data["reminder_id"] ?: ""
        val identityKey = data["identity_key"] ?: ""
        val entityType = data["entity_type"] ?: "COMMITMENT"
        val entityId = data["entity_id"] ?: ""
        val eventType = data["event_type"] ?: ""
        val title = data["title"] ?: "Promise"
        val body = data["body"] ?: ""
        val deepLink = data["deep_link"] ?: "promise://home"

        if (reminderId.isEmpty() && identityKey.isEmpty() && entityId.isEmpty()) {
            return null
        }

        val channelId = when (eventType) {
            "commitment.due_now" -> NotificationChannels.CHANNEL_COMMITMENT_ALERTS
            "commitment.due_soon", "commitment.overdue", "commitment.snooze_expired" -> NotificationChannels.CHANNEL_COMMITMENT_REMINDERS
            "goal.today_practice", "goal.streak_protection", "goal.at_risk_consistency", "goal.checkin_reminder", "goal.chat.message_created" -> NotificationChannels.CHANNEL_GOAL_REMINDERS
            "auth.new_device_login" -> NotificationChannels.CHANNEL_SYSTEM
            else -> {
                if (entityType == "GOAL") NotificationChannels.CHANNEL_GOAL_REMINDERS
                else NotificationChannels.CHANNEL_COMMITMENT_REMINDERS
            }
        }

        val priority = if (channelId == NotificationChannels.CHANNEL_COMMITMENT_ALERTS || channelId == NotificationChannels.CHANNEL_SYSTEM) {
            "high"
        } else {
            "normal"
        }

        return PromiseNotificationData(
            reminderId = reminderId,
            identityKey = if (identityKey.isNotEmpty()) identityKey else "$reminderId:$entityType:$entityId:$eventType",
            entityType = entityType,
            entityId = entityId,
            eventType = eventType,
            title = title,
            body = body,
            deepLink = deepLink,
            channelId = channelId,
            priority = priority,
        )
    }
}
