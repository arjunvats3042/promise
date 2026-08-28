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
        val senderName = data["sender_name"] ?: ""
        val streakCount = data["streak_count"]?.toIntOrNull() ?: 0
        val subtitle = data["subtitle"] ?: ""

        if (reminderId.isEmpty() && identityKey.isEmpty() && entityId.isEmpty() && title.isEmpty()) {
            return null
        }

        val channelId = data["channel_id"] ?: when {
            eventType == "commitment.due_now" -> NotificationChannels.CHANNEL_COMMITMENT_ALERTS
            eventType == "goal.chat.message_created" || eventType.startsWith("goal.participant.") -> NotificationChannels.CHANNEL_COMMUNITY
            eventType == "digest.weekly" || entityType == "DIGEST" -> NotificationChannels.CHANNEL_SYSTEM
            eventType.startsWith("security.") || eventType.startsWith("auth.") || entityType == "SECURITY" || entityType == "SYSTEM" -> NotificationChannels.CHANNEL_SYSTEM
            eventType.startsWith("goal.") || entityType == "GOAL" -> NotificationChannels.CHANNEL_GOAL_REMINDERS
            else -> NotificationChannels.CHANNEL_COMMITMENT_REMINDERS
        }

        val priority = data["priority"] ?: if (
            eventType == "commitment.due_now" ||
            eventType.startsWith("security.") ||
            eventType.startsWith("auth.") ||
            entityType == "SECURITY"
        ) {
            "high"
        } else {
            "normal"
        }

        return PromiseNotificationData(
            reminderId = reminderId,
            identityKey = if (identityKey.isNotEmpty()) identityKey else "$entityType:$entityId:$eventType",
            entityType = entityType,
            entityId = entityId,
            eventType = eventType,
            title = title,
            body = body,
            deepLink = deepLink,
            channelId = channelId,
            priority = priority,
            senderName = senderName,
            streakCount = streakCount,
            subtitle = subtitle,
        )
    }
}

