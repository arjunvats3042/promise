package app.promise.android.data.remote

import java.net.InetAddress
import java.net.URI

/**
 * Android 17+ (API 37) gates LAN access behind ACCESS_LOCAL_NETWORK when
 * targetSdk is 37+. Debug builds that talk to 10.0.2.2 / private LAN hosts
 * must declare and request it; public HTTPS production hosts do not.
 */
object LocalNetworkAccess {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** API level where local-network access is enforced for targetSdk 37+. */
    const val ENFORCED_SDK_INT = 37

    fun requiresRuntimePermission(
        baseUrl: String,
        sdkInt: Int,
    ): Boolean {
        if (sdkInt < ENFORCED_SDK_INT) return false
        return isLocalNetworkBaseUrl(baseUrl)
    }

    fun isLocalNetworkBaseUrl(baseUrl: String): Boolean {
        if (baseUrl.isBlank()) return false
        val host = runCatching { URI(baseUrl).host }.getOrNull()?.trim().orEmpty()
        if (host.isEmpty()) return false
        return isLocalNetworkHost(host)
    }

    fun isLocalNetworkHost(host: String): Boolean {
        val normalized = host.trim().lowercase()
        if (normalized.isEmpty()) return false
        if (normalized == "localhost" || normalized == "ip6-localhost") return true
        if (normalized.endsWith(".local")) return true
        // Literal IPs only — avoid DNS in classification (keeps tests offline-safe).
        if (!isIpLiteral(normalized)) return false
        return runCatching {
            val address = InetAddress.getByName(normalized)
            address.isLoopbackAddress ||
                address.isLinkLocalAddress ||
                address.isSiteLocalAddress ||
                address.isAnyLocalAddress
        }.getOrDefault(false)
    }

    private fun isIpLiteral(host: String): Boolean {
        if (host.contains(":")) return true
        val parts = host.split('.')
        if (parts.size != 4) return false
        return parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
