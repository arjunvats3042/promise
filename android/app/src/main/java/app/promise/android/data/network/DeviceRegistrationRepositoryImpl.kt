package app.promise.android.data.network

import android.content.Context
import android.os.Build
import app.promise.android.BuildConfig
import app.promise.android.domain.DeviceRegistrationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DeviceRegistrationRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val deviceApi: DeviceApi,
) : DeviceRegistrationRepository {

    private val prefs = context.getSharedPreferences("promise_device_prefs", Context.MODE_PRIVATE)

    override fun getLocalDeviceId(): String {
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = "android_" + UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        return deviceId
    }

    override fun getStoredFcmToken(): String? {
        return prefs.getString("fcm_token", null)
    }

    override fun storeFcmToken(token: String) {
        prefs.edit().putString("fcm_token", token).apply()
    }

    override suspend fun registerDevice(fcmToken: String): Result<Unit> {
        storeFcmToken(fcmToken)
        val deviceId = getLocalDeviceId()
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val appVersion = BuildConfig.VERSION_NAME

        return try {
            val response = deviceApi.registerDevice(
                RegisterDeviceRequest(
                    fcmToken = fcmToken,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    platform = "ANDROID",
                    appVersion = appVersion,
                )
            )
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to register device: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncDeviceRegistration(): Result<Unit> {
        val token = fetchFirebaseToken() ?: getStoredFcmToken()
        return if (!token.isNullOrBlank()) {
            registerDevice(token)
        } else {
            Result.success(Unit)
        }
    }

    private suspend fun fetchFirebaseToken(): String? = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val t = task.result
                        if (!t.isNullOrBlank()) {
                            storeFcmToken(t)
                        }
                        if (continuation.isActive) continuation.resume(t)
                    } else {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
        } catch (_: Throwable) {
            if (continuation.isActive) continuation.resume(null)
        }
    }

    override suspend fun unregisterDevice(): Result<Unit> {
        val deviceId = getLocalDeviceId()
        return try {
            val response = deviceApi.unregisterDevice(deviceId)
            if (response.isSuccessful || response.code() == 404) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to unregister device: HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
