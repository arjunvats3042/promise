package app.promise.android.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import app.promise.android.domain.CommitmentRepository
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
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val commitmentId = parameters[COMMITMENT_ID_PARAM] ?: return

        withContext(Dispatchers.IO) {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            val repository = entryPoint.commitmentRepository()

            runCatching {
                repository.complete(commitmentId)
            }

            updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                val currentJson = prefs[WIDGET_DATA_PREF_KEY]
                val currentData = PromiseWidgetData.fromJson(currentJson)

                val updatedItems = currentData.items.map { item ->
                    if (item.id == commitmentId) {
                        item.copy(isCompleted = !item.isCompleted)
                    } else {
                        item
                    }
                }

                val newCompletedCount = updatedItems.count { it.isCompleted }
                val newData = currentData.copy(
                    completedCount = newCompletedCount,
                    totalCount = updatedItems.size,
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
        val COMMITMENT_ID_PARAM = ActionParameters.Key<String>("commitment_id")
    }
}
