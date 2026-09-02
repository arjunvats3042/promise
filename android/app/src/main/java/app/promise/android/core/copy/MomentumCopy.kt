package app.promise.android.core.copy

data class MomentumCardContent(
    val headline: String,
    val subtitle: String,
)

/**
 * Pure generator for Daily Momentum Hero Card messaging that adapts
 * to completion tiers and situational contexts.
 */
object MomentumCopy {

    fun forProgress(
        completedCount: Int,
        totalCount: Int,
    ): MomentumCardContent {
        if (totalCount == 0) {
            return MomentumCardContent(
                headline = "Clear slate for today",
                subtitle = "Speak a thought or tap + to plan what matters most today.",
            )
        }

        val remaining = totalCount - completedCount

        return when {
            completedCount == totalCount -> {
                MomentumCardContent(
                    headline = "You kept every promise today ✨",
                    subtitle = "Everything on your list is done. Enjoy the rest of your day.",
                )
            }
            remaining == 1 -> {
                MomentumCardContent(
                    headline = "Just 1 left for today",
                    subtitle = "Almost done. Finish this last one to keep your streak going.",
                )
            }
            completedCount == 0 -> {
                MomentumCardContent(
                    headline = "$totalCount things planned today",
                    subtitle = "Pick one to start with and build your flow.",
                )
            }
            else -> {
                MomentumCardContent(
                    headline = "$completedCount of $totalCount completed",
                    subtitle = "$remaining more to go. Keep moving at your own calm pace.",
                )
            }
        }
    }
}
