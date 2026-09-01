package app.promise.android.ui.roadmap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import app.promise.android.widget.RoadmapGlanceWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receiver for exact midnight alarms, DATE_CHANGED, TIME_SET, TIMEZONE_CHANGED,
 * and BOOT_COMPLETED that advances the roadmap wallpaper and refreshes widgets.
 */
class WallpaperDateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != ACTION_MIDNIGHT_ALARM &&
            action != Intent.ACTION_DATE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (!RoadmapWallpaperManager.isAutoUpdateEnabled(context)) return

        // Reschedule next midnight alarm and ensure daily worker is enqueued
        RoadmapWallpaperManager.scheduleDailyUpdate(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Refresh home widget
                runCatching {
                    RoadmapGlanceWidget().updateAll(context)
                }

                // Apply new day's wallpaper
                val target = RoadmapWallpaperManager.getSavedTarget(context)
                RoadmapWallpaperManager.applyWallpaper(
                    context = context,
                    target = target,
                    autoUpdateDaily = true,
                    accentColorInt = RoadmapWallpaperManager.getSavedAccentColor(context),
                    isDarkTheme = RoadmapWallpaperManager.isSavedDarkTheme(context),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT_ALARM = "app.promise.android.action.MIDNIGHT_WALLPAPER_UPDATE"
    }
}
