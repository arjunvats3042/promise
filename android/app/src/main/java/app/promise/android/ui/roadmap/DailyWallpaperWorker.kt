package app.promise.android.ui.roadmap

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.promise.android.widget.RoadmapGlanceWidget
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DailyWallpaperWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Automatically refresh Roadmap home screen widgets daily
        runCatching {
            RoadmapGlanceWidget().updateAll(appContext)
        }

        if (!RoadmapWallpaperManager.isAutoUpdateEnabled(appContext)) {
            return Result.success()
        }

        // Respect the user: if they changed their wallpaper externally, stop overwriting it
        if (!RoadmapWallpaperManager.isWallpaperStillOurs(appContext)) {
            RoadmapWallpaperManager.disableAutoUpdate(appContext)
            return Result.success()
        }

        val target = RoadmapWallpaperManager.getSavedTarget(appContext)
        val success = RoadmapWallpaperManager.applyWallpaper(
            context = appContext,
            target = target,
            autoUpdateDaily = true,
            isDarkTheme = RoadmapWallpaperManager.isSavedDarkTheme(appContext),
        )

        return if (success) Result.success() else Result.retry()
    }
}
