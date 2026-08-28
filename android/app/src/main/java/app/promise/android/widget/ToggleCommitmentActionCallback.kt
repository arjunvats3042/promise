package app.promise.android.widget

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
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

val WIDGET_DATA_PREF_KEY = stringPreferencesKey("promise_widget_data_json")

class ToggleCommitmentActionCallback : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun commitmentRepository(): CommitmentRepository
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

                if (targetItem != null) {
                    if (targetItem.type == WidgetItemType.GOAL) {
                        runCatching {
                            entryPoint.homeRepository().checkInPractice(
                                id = itemId,
                                input = CheckInInput(status = GoalCheckInStatus.COMPLETED),
                            )
                        }
                        entryPoint.appEventBus().emit(AppMutationEvent.GoalCheckedIn(itemId))
                    } else {
                        runCatching {
                            entryPoint.commitmentRepository().complete(itemId)
                        }
                        entryPoint.appEventBus().emit(AppMutationEvent.CommitmentCompleted(itemId))
                    }
                }

                val updatedItems = currentData.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(isCompleted = !item.isCompleted)
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

            PromiseGlanceWidget().update(context, glanceId)
        }
    }

    companion object {
        val ITEM_ID_PARAM = ActionParameters.Key<String>("item_id")
        val COMMITMENT_ID_PARAM = ActionParameters.Key<String>("commitment_id")
        val ACTION_TYPE_PARAM = ActionParameters.Key<String>("action_type")
    }
}
