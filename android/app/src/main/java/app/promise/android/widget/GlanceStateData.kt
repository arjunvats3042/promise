package app.promise.android.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class WidgetItemType {
    COMMITMENT,
    GOAL,
}

@Serializable
data class WidgetCommitmentItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val isCompleted: Boolean = false,
    val isOverdue: Boolean = false,
    val dueTimeFormatted: String = "",
    val streakCount: Int = 0,
    val type: WidgetItemType = WidgetItemType.COMMITMENT,
)

@Serializable
data class PromiseWidgetData(
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val streakCount: Int = 0,
    val goalCompletionPercent: Int = 0,
    val items: List<WidgetCommitmentItem> = emptyList(),
    val expandedItemId: String? = null,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
) {
    val progressPercent: Int
        get() = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 0

    val progressSummaryString: String
        get() = "$progressPercent% ($completedCount/$totalCount Commitments & Goals)"

    val consistencyLevel: String
        get() = when {
            progressPercent >= 80 -> "High"
            progressPercent >= 50 -> "Medium"
            else -> "Getting Started"
        }

    val topPendingItem: WidgetCommitmentItem?
        get() = items.firstOrNull { !it.isCompleted } ?: items.firstOrNull()

    fun toJson(): String = jsonInstance.encodeToString(this)

    companion object {
        private val jsonInstance = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(jsonStr: String?): PromiseWidgetData {
            if (jsonStr.isNullOrBlank()) return emptyData()
            return try {
                jsonInstance.decodeFromString(jsonStr)
            } catch (_: Throwable) {
                emptyData()
            }
        }

        fun emptyData(): PromiseWidgetData {
            return PromiseWidgetData(
                completedCount = 0,
                totalCount = 0,
                streakCount = 0,
                goalCompletionPercent = 0,
                items = emptyList(),
            )
        }

        fun sampleData(): PromiseWidgetData {
            val sampleItems = listOf(
                WidgetCommitmentItem(
                    id = "sample_1",
                    title = "Morning Yoga (20m)",
                    subtitle = "Daily Practice",
                    isCompleted = true,
                    dueTimeFormatted = "07:15 AM",
                    streakCount = 12,
                    type = WidgetItemType.GOAL,
                ),
                WidgetCommitmentItem(
                    id = "sample_2",
                    title = "Read 15 Pages",
                    subtitle = "Book Goal",
                    isCompleted = true,
                    dueTimeFormatted = "08:30 AM",
                    streakCount = 12,
                    type = WidgetItemType.GOAL,
                ),
                WidgetCommitmentItem(
                    id = "sample_3",
                    title = "Water Intake (2L)",
                    subtitle = "Daily Habit",
                    isCompleted = false,
                    dueTimeFormatted = "10:00 PM",
                    streakCount = 5,
                    type = WidgetItemType.COMMITMENT,
                ),
                WidgetCommitmentItem(
                    id = "sample_4",
                    title = "Evening Run (5km)",
                    subtitle = "Fitness Goal",
                    isCompleted = false,
                    dueTimeFormatted = "06:30 PM",
                    streakCount = 8,
                    type = WidgetItemType.COMMITMENT,
                ),
            )
            return PromiseWidgetData(
                completedCount = 2,
                totalCount = 4,
                streakCount = 12,
                goalCompletionPercent = 50,
                items = sampleItems,
            )
        }
    }
}
