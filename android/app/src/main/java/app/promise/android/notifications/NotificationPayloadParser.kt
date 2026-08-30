package app.promise.android.notifications

object NotificationPayloadParser {

    fun parse(data: Map<String, String>): PromiseNotificationData? {
        val reminderId = (data["reminder_id"] ?: "").trim()
        val identityKey = (data["identity_key"] ?: "").trim()
        val entityType = (data["entity_type"] ?: "COMMITMENT").trim().uppercase()
        val entityId = (data["entity_id"] ?: "").trim()
        val eventType = (data["event_type"] ?: "").trim()
        val title = (data["title"] ?: "Promise").trim()
        val body = (data["body"] ?: "").trim()
        val deepLink = (data["deep_link"] ?: "promise://home").trim()
        val senderName = (data["sender_name"] ?: "").trim()
        val streakCount = data["streak_count"]?.toIntOrNull() ?: 0
        val subtitle = (data["subtitle"] ?: "").trim()

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

