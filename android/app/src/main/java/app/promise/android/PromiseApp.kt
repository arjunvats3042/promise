package app.promise.android

import android.app.Application
import androidx.work.Configuration
import app.promise.android.core.events.AppEventBus
import app.promise.android.notifications.NotificationChannels
import app.promise.android.widget.PromiseWidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PromiseApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory

    @Inject
    lateinit var appEventBus: AppEventBus

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureChannels(this)

        // Initial widget refresh and mutation listener
        applicationScope.launch {
            runCatching { PromiseWidgetUpdater.fetchAndPushWidgetData(this@PromiseApp) }
            runCatching { app.promise.android.ui.roadmap.RoadmapWallpaperManager.ensureWallpaperUpToDate(this@PromiseApp) }
            appEventBus.events.collect {
                runCatching { PromiseWidgetUpdater.fetchAndPushWidgetData(this@PromiseApp) }
            }
        }
    }
}

