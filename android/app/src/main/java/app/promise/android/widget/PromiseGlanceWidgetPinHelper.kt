package app.promise.android.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build

object PromiseGlanceWidgetPinHelper {

    fun isPinSupported(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        return appWidgetManager.isRequestPinAppWidgetSupported
    }

    fun requestPinWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val appWidgetManager = context.getSystemService(AppWidgetManager::class.java) ?: return false
        if (!appWidgetManager.isRequestPinAppWidgetSupported) return false

        val provider = ComponentName(context, GoalsGlanceWidgetReceiver::class.java)
        return appWidgetManager.requestPinAppWidget(provider, null, null)
    }
}
