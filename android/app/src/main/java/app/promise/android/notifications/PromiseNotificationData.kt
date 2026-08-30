package app.promise.android.notifications

data class PromiseNotificationData(
    val reminderId: String,
    val identityKey: String,
    val entityType: String, // COMMITMENT, GOAL, SYSTEM, DIGEST
    val entityId: String,
    val eventType: String,
    val title: String,
    val body: String,
    val deepLink: String,
    val channelId: String = NotificationChannels.CHANNEL_COMMITMENT_REMINDERS,
    val priority: String = "normal",
    val senderName: String = "",
    val streakCount: Int = 0,
    val subtitle: String = "",
) {
    val notificationId: Int
        get() = computeNotificationId(
            if (entityId.isNotBlank() && entityType.isNotBlank()) {
                "${entityType.uppercase()}:$entityId"
            } else if (identityKey.isNotBlank()) {
                identityKey
            } else {
                "$eventType:$title"
            }
        )

    companion object {
        fun computeNotificationId(key: String): Int {
            if (key.isEmpty()) return 1001
            var hash = 0x811c9dc5.toInt()
            for (b in key.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (b.toInt() and 0xff)
                hash *= 0x01000193
            }
            val positiveHash = hash and 0x7fffffff
            return if (positiveHash == 0) 1001 else (positiveHash % 100_000_000) + 1000
        }
    }
}

