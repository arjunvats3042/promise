package app.promise.android.notifications

data class PromiseNotificationData(
    val reminderId: String,
    val identityKey: String,
    val entityType: String, // COMMITMENT, GOAL, SYSTEM
    val entityId: String,
    val eventType: String,
    val title: String,
    val body: String,
    val deepLink: String,
    val channelId: String = NotificationChannels.CHANNEL_COMMITMENT_REMINDERS,
    val priority: String = "normal",
) {
    val notificationId: Int
        get() = computeNotificationId(identityKey)

    companion object {
        fun computeNotificationId(identityKey: String): Int {
            if (identityKey.isEmpty()) return 1001
            var hash = 0x811c9dc5.toInt()
            for (b in identityKey.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (b.toInt() and 0xff)
                hash *= 0x01000193
            }
            val positiveHash = hash and 0x7fffffff
            return if (positiveHash == 0) 1001 else positiveHash
        }
    }
}
