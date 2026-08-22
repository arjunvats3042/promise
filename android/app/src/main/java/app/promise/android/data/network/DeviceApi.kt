package app.promise.android.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

@Serializable
data class RegisterDeviceRequest(
    @SerialName("fcm_token") val fcmToken: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("platform") val platform: String = "ANDROID",
    @SerialName("app_version") val appVersion: String = "",
)

@Serializable
data class DeviceResponse(
    @SerialName("id") val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String = "",
    @SerialName("platform") val platform: String = "ANDROID",
    @SerialName("app_version") val appVersion: String = "",
    @SerialName("is_active") val isActive: Boolean = true,
)

interface DeviceApi {
    @POST("notifications/devices/")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<DeviceResponse>

    @DELETE("notifications/devices/{device_id}/")
    suspend fun unregisterDevice(@Path("device_id") deviceId: String): Response<Unit>
}
