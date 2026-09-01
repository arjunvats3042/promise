package app.promise.android.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.promise.android.domain.HomeCommitment
import app.promise.android.domain.HomePractice
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PromiseWidgetUpdater {

    private const val CACHE_PREFS_NAME = "promise_widget_cache"
    private const val CACHED_DATA_KEY = "cached_widget_data_json"

    fun getCachedWidgetData(context: Context): PromiseWidgetData {
        return try {
            val prefs = context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(CACHED_DATA_KEY, null)
            PromiseWidgetData.fromJson(json)
        } catch (_: Throwable) {
            PromiseWidgetData.emptyData()
        }
    }

    suspend fun updateWidget(
        context: Context,
        commitments: List<HomeCommitment>,
        practices: List<HomePractice>,
    ) {
        // Smart Priority Triage: Overdue first -> Due today -> Other pending -> Completed
        val sortedCommitments = commitments.sortedWith(
            compareBy<HomeCommitment> { it.isCompleted } // Incomplete first
                .thenByDescending { it.isOverdue } // Overdue first
                .thenByDescending { it.isDueToday } // Due today next
        )

        val commitmentItems = sortedCommitments.map { commitment ->
            WidgetCommitmentItem(
                id = commitment.id,
                title = commitment.title,
                subtitle = if (commitment.isOverdue) "Overdue" else commitment.dueLabel,
                isCompleted = commitment.isCompleted,
                isOverdue = commitment.isOverdue,
                dueTimeFormatted = commitment.dueLabel,
                streakCount = 0,
                type = WidgetItemType.COMMITMENT,
            )
        }

        // Habit Triage: Incomplete habits first (sorted by highest streak), then completed
        val sortedPractices = practices.sortedWith(
            compareBy<HomePractice> { it.checkedInToday }
                .thenByDescending { it.streakDays }
        )

        val practiceItems = sortedPractices.map { practice ->
            WidgetCommitmentItem(
                id = practice.id,
                title = practice.title,
                subtitle = practice.progressLabel.ifBlank { "Streak ${practice.streakDays}d" },
                isCompleted = practice.checkedInToday,
                isOverdue = false,
                dueTimeFormatted = "${practice.streakDays}d streak",
                streakCount = practice.streakDays,
                type = WidgetItemType.GOAL,
            )
        }

        val allItems = commitmentItems + practiceItems
        val completedCount = allItems.count { it.isCompleted }
        val totalCount = allItems.size
        val maxStreak = practices.maxOfOrNull { it.streakDays } ?: 0
        val completionPercent = if (totalCount > 0) ((completedCount.toFloat() / totalCount) * 100).toInt() else 0

        val widgetData = PromiseWidgetData(
            completedCount = completedCount,
            totalCount = totalCount,
            streakCount = maxStreak,
            goalCompletionPercent = completionPercent,
            items = allItems,
            lastUpdatedTimestamp = System.currentTimeMillis(),
        )

        // Cache persistently in SharedPreferences for instant retrieval by newly added widgets
        runCatching {
            context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(CACHED_DATA_KEY, widgetData.toJson())
                .apply()
        }

        updateGlanceWidgetState(context, widgetData)
    }

    suspend fun updateGlanceWidgetState(context: Context, widgetData: PromiseWidgetData) {
        runCatching {
            val glanceManager = GlanceAppWidgetManager(context)

            // 1. Update Goals Widgets
            val goalsIds = glanceManager.getGlanceIds(GoalsGlanceWidget::class.java)
            for (glanceId in goalsIds) {
                runCatching {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        val mutable = prefs.toMutablePreferences()
                        mutable[WIDGET_DATA_PREF_KEY] = widgetData.toJson()
                        mutable
                    }
                    GoalsGlanceWidget().update(context, glanceId)
                }
            }

            // 2. Update Commitments Widgets
            val commitmentsIds = glanceManager.getGlanceIds(CommitmentsGlanceWidget::class.java)
            for (glanceId in commitmentsIds) {
                runCatching {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        val mutable = prefs.toMutablePreferences()
                        mutable[WIDGET_DATA_PREF_KEY] = widgetData.toJson()
                        mutable
                    }
                    CommitmentsGlanceWidget().update(context, glanceId)
                }
            }

            // 3. Update Roadmap Widgets
            val roadmapIds = glanceManager.getGlanceIds(RoadmapGlanceWidget::class.java)
            for (glanceId in roadmapIds) {
                runCatching {
                    RoadmapGlanceWidget().update(context, glanceId)
                }
            }

            // 4. Update Voice Mic Widgets
            val voiceIds = glanceManager.getGlanceIds(VoiceMicGlanceWidget::class.java)
            for (glanceId in voiceIds) {
                runCatching {
                    VoiceMicGlanceWidget().update(context, glanceId)
                }
            }
        }
    }

    suspend fun updateAllWidgetsTheme(context: Context) {
        withContext(Dispatchers.IO) {
            val cached = getCachedWidgetData(context)
            updateGlanceWidgetState(context, cached)
        }
    }

    suspend fun saveAndBroadcastWidgetData(context: Context, widgetData: PromiseWidgetData) {
        withContext(Dispatchers.IO) {
            runCatching {
                context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(CACHED_DATA_KEY, widgetData.toJson())
                    .apply()
            }
            updateGlanceWidgetState(context, widgetData)
        }
    }

    suspend fun clearWidgetData(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                context.getSharedPreferences(CACHE_PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(CACHED_DATA_KEY)
                    .apply()
            }
            updateGlanceWidgetState(context, PromiseWidgetData.emptyData())
        }
    }

    suspend fun fetchAndPushWidgetData(context: Context) {
        withContext(Dispatchers.IO) {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ToggleCommitmentActionCallback.WidgetEntryPoint::class.java,
                )
                val tz = java.util.TimeZone.getDefault().id.ifBlank { "UTC" }
                val feed = entryPoint.homeRepository().loadFeed(tz)
                if (feed.commitmentsError == null || feed.practicesError == null || feed.commitments.isNotEmpty() || feed.practices.isNotEmpty()) {
                    updateWidget(context, feed.commitments, feed.practices)
                }
            }
        }
    }
}

