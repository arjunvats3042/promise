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
                subtitle = "Dictate promises with your voice or capture open thoughts.",
            )
        }

        val remaining = totalCount - completedCount

        return when {
            completedCount == totalCount -> {
                MomentumCardContent(
                    headline = "All promises fulfilled!",
                    subtitle = "Great follow-through today. Your consistency is compounding.",
                )
            }
            remaining == 1 -> {
                MomentumCardContent(
                    headline = "1 final action remaining",
                    subtitle = "Almost across the finish line. Keep your daily streak alive.",
                )
            }
            completedCount == 0 -> {
                MomentumCardContent(
                    headline = "$totalCount actions planned today",
                    subtitle = "Start with your highest-priority commitment to set the pace.",
                )
            }
            else -> {
                MomentumCardContent(
                    headline = "$completedCount of $totalCount completed",
                    subtitle = "$remaining actions remaining. Stay in steady, calm momentum.",
                )
            }
        }
    }
}
