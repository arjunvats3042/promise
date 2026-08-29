package app.promise.android.ui.roadmap

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyWallpaperWorker(
    private val appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!RoadmapWallpaperManager.isAutoUpdateEnabled(appContext)) {
            return Result.success()
        }

        val target = RoadmapWallpaperManager.getSavedTarget(appContext)
        val success = RoadmapWallpaperManager.applyWallpaper(
            context = appContext,
            target = target,
            autoUpdateDaily = true,
        )

        return if (success) Result.success() else Result.retry()
    }
}
