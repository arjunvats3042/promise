package app.promise.android.widget

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WidgetCommitmentItem(
    val id: String,
    val title: String,
    val isCompleted: Boolean = false,
    val dueTimeFormatted: String = "",
    val streakCount: Int = 0,
)

@Serializable
data class PromiseWidgetData(
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val streakCount: Int = 0,
    val goalCompletionPercent: Int = 0,
    val items: List<WidgetCommitmentItem> = emptyList(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis(),
) {
    val progressPercent: Int
        get() = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 0

    val progressSummaryString: String
        get() = "$progressPercent% ($completedCount/$totalCount Habits)"

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
            if (jsonStr.isNullOrBlank()) return sampleData()
            return try {
                jsonInstance.decodeFromString(jsonStr)
            } catch (_: Throwable) {
                sampleData()
            }
        }

        fun sampleData(): PromiseWidgetData {
            val sampleItems = listOf(
                WidgetCommitmentItem(
                    id = "sample_1",
                    title = "Morning Yoga (20m)",
                    isCompleted = true,
                    dueTimeFormatted = "07:15 AM",
                    streakCount = 12,
                ),
                WidgetCommitmentItem(
                    id = "sample_2",
                    title = "Read 15 Pages",
                    isCompleted = true,
                    dueTimeFormatted = "08:30 AM",
                    streakCount = 12,
                ),
                WidgetCommitmentItem(
                    id = "sample_3",
                    title = "Water Intake (2L)",
                    isCompleted = false,
                    dueTimeFormatted = "10:00 PM",
                    streakCount = 5,
                ),
                WidgetCommitmentItem(
                    id = "sample_4",
                    title = "Evening Run (5km)",
                    isCompleted = false,
                    dueTimeFormatted = "06:30 PM",
                    streakCount = 8,
                ),
            )
            return PromiseWidgetData(
                completedCount = 3,
                totalCount = 4,
                streakCount = 12,
                goalCompletionPercent = 85,
                items = sampleItems,
            )
        }
    }
}
