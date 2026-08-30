package app.promise.android.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.HomeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val WIDGET_DATA_PREF_KEY = stringPreferencesKey("promise_widget_data_json")

fun openGoalAction(goalId: String) = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse("promise://goal/$goalId")).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
)

fun openCommitmentAction(commitmentId: String) = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse("promise://commitment/$commitmentId")).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
)

fun openHomeAction() = actionStartActivity(
    Intent(Intent.ACTION_VIEW, Uri.parse("promise://home")).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    },
)

class RefreshWidgetActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        withContext(Dispatchers.IO) {
            runCatching {
                PromiseWidgetUpdater.fetchAndPushWidgetData(context)
            }
        }
    }
}

class ToggleCommitmentActionCallback : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun commitmentRepository(): CommitmentRepository
        fun goalRepository(): GoalRepository
        fun homeRepository(): HomeRepository
        fun appEventBus(): AppEventBus
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val itemId = parameters[ITEM_ID_PARAM] ?: parameters[COMMITMENT_ID_PARAM] ?: return
        val action = parameters[ACTION_TYPE_PARAM] ?: "TOGGLE_COMPLETE"

        withContext(Dispatchers.IO) {
            runCatching {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java,
                )

                // 1. Get cached state
                val cachedData = PromiseWidgetUpdater.getCachedWidgetData(context)
                val targetItem = cachedData.items.firstOrNull { it.id == itemId }

                if (action == "TOGGLE_EXPAND") {
                    val nextExpanded = if (cachedData.expandedItemId == itemId) null else itemId
                    val newData = cachedData.copy(expandedItemId = nextExpanded)
                    PromiseWidgetUpdater.saveAndBroadcastWidgetData(context, newData)
                    return@withContext
                }

                // If item is already completed, do nothing to prevent repeated streak increment
                if (targetItem == null || targetItem.isCompleted) {
                    return@withContext
                }

                // 2. Perform optimistic widget update
                val updatedItems = cachedData.items.map { item ->
                    if (item.id == itemId) {
                        val newStreak = if (item.type == WidgetItemType.GOAL) item.streakCount + 1 else item.streakCount
                        item.copy(
                            isCompleted = true,
                            streakCount = newStreak,
                            subtitle = if (item.type == WidgetItemType.GOAL) "Streak ${newStreak}d" else item.subtitle,
                        )
                    } else {
                        item
                    }
                }

                val newCompletedCount = updatedItems.count { it.isCompleted }
                val newPercent = if (updatedItems.isNotEmpty()) ((newCompletedCount.toFloat() / updatedItems.size) * 100).toInt() else 0
                val newData = cachedData.copy(
                    completedCount = newCompletedCount,
                    totalCount = updatedItems.size,
                    goalCompletionPercent = newPercent,
                    items = updatedItems,
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                )

                // 3. Immediately broadcast to widgets
                PromiseWidgetUpdater.saveAndBroadcastWidgetData(context, newData)

                // 4. Perform actual repository mutations (Room + Outbox + API)
                if (targetItem.type == WidgetItemType.GOAL) {
                    runCatching {
                        entryPoint.goalRepository().checkIn(
                            id = itemId,
                            input = CheckInInput(status = GoalCheckInStatus.COMPLETED),
                        )
                    }
                    runCatching {
                        entryPoint.appEventBus().emit(AppMutationEvent.GoalCheckedIn(itemId))
                    }
                } else {
                    runCatching {
                        entryPoint.commitmentRepository().complete(itemId)
                    }
                    runCatching {
                        entryPoint.appEventBus().emit(AppMutationEvent.CommitmentCompleted(itemId))
                    }
                }
            }
        }
    }

    companion object {
        val ITEM_ID_PARAM = ActionParameters.Key<String>("item_id")
        val COMMITMENT_ID_PARAM = ActionParameters.Key<String>("commitment_id")
        val ACTION_TYPE_PARAM = ActionParameters.Key<String>("action_type")
    }
}


