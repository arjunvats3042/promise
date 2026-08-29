package app.promise.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object PromiseGlanceWidgetPinHelper {

    fun isPinSupported(context: Context): Boolean {
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        return appWidgetManager.isRequestPinAppWidgetSupported
    }

    fun requestPinGoalsWidget(context: Context): Boolean {
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!appWidgetManager.isRequestPinAppWidgetSupported) return false

        val provider = ComponentName(context, GoalsGlanceWidgetReceiver::class.java)
        return appWidgetManager.requestPinAppWidget(provider, null, null)
    }

    fun requestPinCommitmentsWidget(context: Context): Boolean {
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!appWidgetManager.isRequestPinAppWidgetSupported) return false

        val provider = ComponentName(context, CommitmentsGlanceWidgetReceiver::class.java)
        return appWidgetManager.requestPinAppWidget(provider, null, null)
    }

    fun requestPinVoiceWidget(context: Context): Boolean {
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!appWidgetManager.isRequestPinAppWidgetSupported) return false

        val provider = ComponentName(context, VoiceMicGlanceWidgetReceiver::class.java)
        return appWidgetManager.requestPinAppWidget(provider, null, null)
    }
}
