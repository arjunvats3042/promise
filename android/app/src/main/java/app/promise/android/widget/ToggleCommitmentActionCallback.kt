package app.promise.android.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.data.local.db.OutboxDao
import app.promise.android.data.local.db.OutboxEntity
import app.promise.android.data.sync.SyncManager
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.HomeRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

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
            PromiseWidgetUpdater.fetchAndPushWidgetData(context)
        }
    }
}

class ToggleCommitmentActionCallback : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun commitmentRepository(): CommitmentRepository
        fun homeRepository(): HomeRepository
        fun appEventBus(): AppEventBus
        fun outboxDao(): OutboxDao
        fun syncManager(): SyncManager
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val itemId = parameters[ITEM_ID_PARAM] ?: parameters[COMMITMENT_ID_PARAM] ?: return
        val action = parameters[ACTION_TYPE_PARAM] ?: "TOGGLE_COMPLETE"

        withContext(Dispatchers.IO) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )

            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                val currentJson = prefs[WIDGET_DATA_PREF_KEY]
                val currentData = PromiseWidgetData.fromJson(currentJson)
                val targetItem = currentData.items.firstOrNull { it.id == itemId }

                if (action == "TOGGLE_EXPAND") {
                    val nextExpanded = if (currentData.expandedItemId == itemId) null else itemId
                    val newData = currentData.copy(expandedItemId = nextExpanded)
                    val mutable = prefs.toMutablePreferences()
                    mutable[WIDGET_DATA_PREF_KEY] = newData.toJson()
                    return@updateAppWidgetState mutable
                }

                val targetCompleted = targetItem?.isCompleted ?: false
                val newCompletedState = !targetCompleted

                if (targetItem != null) {
                    if (targetItem.type == WidgetItemType.GOAL) {
                        val apiResult = runCatching {
                            val status = if (newCompletedState) GoalCheckInStatus.COMPLETED else GoalCheckInStatus.SKIPPED
                            entryPoint.homeRepository().checkInPractice(
                                id = itemId,
                                input = CheckInInput(status = status),
                            )
                        }
                        // Offline fallback: queue to outbox if API call failed
                        if (apiResult.isFailure) {
                            runCatching {
                                val statusStr = if (newCompletedState) "COMPLETED" else "SKIPPED"
                                entryPoint.outboxDao().enqueue(
                                    OutboxEntity(
                                        actionId = UUID.randomUUID().toString(),
                                        actionType = "CHECK_IN_GOAL",
                                        entityId = itemId,
                                        payloadJson = """{"status":"$statusStr"}""",
                                        clientTimestampIso = Instant.now().toString(),
                                    ),
                                )
                                entryPoint.syncManager().enqueueSync()
                            }
                        }
                        entryPoint.appEventBus().emit(AppMutationEvent.GoalCheckedIn(itemId))
                    } else {
                        val apiResult = runCatching {
                            entryPoint.commitmentRepository().complete(itemId)
                        }
                        // Offline fallback: queue to outbox if API call failed
                        if (apiResult.isFailure) {
                            runCatching {
                                entryPoint.outboxDao().enqueue(
                                    OutboxEntity(
                                        actionId = UUID.randomUUID().toString(),
                                        actionType = "COMPLETE_COMMITMENT",
                                        entityId = itemId,
                                        payloadJson = "{}",
                                        clientTimestampIso = Instant.now().toString(),
                                    ),
                                )
                                entryPoint.syncManager().enqueueSync()
                            }
                        }
                        entryPoint.appEventBus().emit(AppMutationEvent.CommitmentCompleted(itemId))
                    }
                }

                val updatedItems = currentData.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(isCompleted = newCompletedState)
                    } else {
                        item
                    }
                }

                val newCompletedCount = updatedItems.count { it.isCompleted }
                val newPercent = if (updatedItems.isNotEmpty()) ((newCompletedCount.toFloat() / updatedItems.size) * 100).toInt() else 0
                val newData = currentData.copy(
                    completedCount = newCompletedCount,
                    totalCount = updatedItems.size,
                    goalCompletionPercent = newPercent,
                    items = updatedItems,
                    lastUpdatedTimestamp = System.currentTimeMillis(),
                )

                val mutable = prefs.toMutablePreferences()
                mutable[WIDGET_DATA_PREF_KEY] = newData.toJson()
                mutable
            }

            runCatching { GoalsGlanceWidget().update(context, glanceId) }
            runCatching { CommitmentsGlanceWidget().update(context, glanceId) }

            // Sync fresh feed in background
            runCatching { PromiseWidgetUpdater.fetchAndPushWidgetData(context) }
        }
    }

    companion object {
        val ITEM_ID_PARAM = ActionParameters.Key<String>("item_id")
        val COMMITMENT_ID_PARAM = ActionParameters.Key<String>("commitment_id")
        val ACTION_TYPE_PARAM = ActionParameters.Key<String>("action_type")
    }
}

