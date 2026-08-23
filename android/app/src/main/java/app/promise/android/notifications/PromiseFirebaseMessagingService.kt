package app.promise.android.notifications

import app.promise.android.domain.DeviceRegistrationRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PromiseFirebaseMessagingService : FirebaseMessagingService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FcmEntryPoint {
        fun notificationManager(): PromiseNotificationManager
        fun deviceRegistrationRepository(): DeviceRegistrationRepository
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, FcmEntryPoint::class.java)
        val repository = entryPoint.deviceRegistrationRepository()
        repository.storeFcmToken(token)
        scope.launch {
            repository.registerDevice(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data.toMutableMap()
        message.notification?.let { notif ->
            if (!data.containsKey("title") && !notif.title.isNullOrBlank()) {
                data["title"] = notif.title!!
            }
            if (!data.containsKey("body") && !notif.body.isNullOrBlank()) {
                data["body"] = notif.body!!
            }
        }
        val parsed = NotificationPayloadParser.parse(data) ?: return
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, FcmEntryPoint::class.java)
        val notificationManager = entryPoint.notificationManager()
        notificationManager.show(parsed)
    }
}
