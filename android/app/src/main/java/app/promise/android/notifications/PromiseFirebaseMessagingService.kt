package app.promise.android.notifications

import android.content.Context
import app.promise.android.domain.DeviceRegistrationRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PromiseFirebaseMessagingService {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FcmEntryPoint {
        fun notificationManager(): PromiseNotificationManager
        fun deviceRegistrationRepository(): DeviceRegistrationRepository
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun onNewToken(context: Context, token: String) {
        val entryPoint = EntryPointAccessors.fromApplication(context, FcmEntryPoint::class.java)
        val repository = entryPoint.deviceRegistrationRepository()
        repository.storeFcmToken(token)
        scope.launch {
            repository.registerDevice(token)
        }
    }

    fun onMessageReceived(context: Context, data: Map<String, String>) {
        val parsed = NotificationPayloadParser.parse(data) ?: return
        val entryPoint = EntryPointAccessors.fromApplication(context, FcmEntryPoint::class.java)
        val notificationManager = entryPoint.notificationManager()
        notificationManager.show(parsed)
    }
}
