package app.promise.android.ui.roadmap

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Standalone receiver for DATE_CHANGED and BOOT_COMPLETED that updates
 * the roadmap wallpaper independently of whether the Glance widget is placed.
 * This guarantees the wallpaper advances to the new day even if the user
 * never added the home-screen widget.
 */
class WallpaperDateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (action != Intent.ACTION_DATE_CHANGED &&
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) return

        if (!RoadmapWallpaperManager.isAutoUpdateEnabled(context)) return

        // If the user manually changed their wallpaper, respect that and stop auto-updating
        if (!RoadmapWallpaperManager.isWallpaperStillOurs(context)) {
            RoadmapWallpaperManager.disableAutoUpdate(context)
            return
        }

        // Re-ensure the periodic worker is scheduled (survives reboots / force-stops)
        RoadmapWallpaperManager.scheduleDailyUpdate(context)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val target = RoadmapWallpaperManager.getSavedTarget(context)
                RoadmapWallpaperManager.applyWallpaper(
                    context = context,
                    target = target,
                    autoUpdateDaily = true,
                    isDarkTheme = RoadmapWallpaperManager.isSavedDarkTheme(context),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
