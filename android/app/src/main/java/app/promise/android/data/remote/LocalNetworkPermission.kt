package app.promise.android.data.remote

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface LocalNetworkPermission {
    /** True when this build's API base URL needs ACCESS_LOCAL_NETWORK on this device. */
    fun requiresAccess(): Boolean

    /** True when access is not required, or the runtime permission is already granted. */
    fun isGranted(): Boolean
}

@Singleton
class AndroidLocalNetworkPermission @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocalNetworkPermission {
    override fun requiresAccess(): Boolean {
        return LocalNetworkAccess.requiresRuntimePermission(
            baseUrl = ApiConfig.baseUrl,
            sdkInt = Build.VERSION.SDK_INT,
        )
    }

    override fun isGranted(): Boolean {
        if (!requiresAccess()) return true
        return ContextCompat.checkSelfPermission(
            context,
            LocalNetworkAccess.PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
