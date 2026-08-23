package app.promise.android.domain

interface DeviceRegistrationRepository {
    suspend fun registerDevice(fcmToken: String): Result<Unit>
    suspend fun unregisterDevice(): Result<Unit>
    suspend fun syncDeviceRegistration(): Result<Unit>
    fun getLocalDeviceId(): String
    fun getStoredFcmToken(): String?
    fun storeFcmToken(token: String)
}
