package app.promise.android.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkAccessTest {
    @Test
    fun lanAndEmulatorHostsRequirePermissionOnApi37() {
        assertTrue(
            LocalNetworkAccess.requiresRuntimePermission(
                "http://192.168.1.29:8000/api/v1/",
                sdkInt = 37,
            ),
        )
        assertTrue(
            LocalNetworkAccess.requiresRuntimePermission(
                "http://10.0.2.2:8000/api/v1/",
                sdkInt = 37,
            ),
        )
        assertTrue(
            LocalNetworkAccess.requiresRuntimePermission(
                "http://127.0.0.1:8000/api/v1/",
                sdkInt = 37,
            ),
        )
        assertTrue(
            LocalNetworkAccess.requiresRuntimePermission(
                "http://localhost:8000/api/v1/",
                sdkInt = 37,
            ),
        )
    }

    @Test
    fun olderSdkDoesNotRequirePermissionEvenForLan() {
        assertFalse(
            LocalNetworkAccess.requiresRuntimePermission(
                "http://192.168.1.29:8000/api/v1/",
                sdkInt = 36,
            ),
        )
    }

    @Test
    fun blankOrPublicHostsDoNotRequirePermission() {
        assertFalse(LocalNetworkAccess.requiresRuntimePermission("", sdkInt = 37))
        assertFalse(
            LocalNetworkAccess.requiresRuntimePermission(
                "https://api.example.com/api/v1/",
                sdkInt = 37,
            ),
        )
    }

    @Test
    fun hostClassification() {
        assertTrue(LocalNetworkAccess.isLocalNetworkHost("192.168.1.29"))
        assertTrue(LocalNetworkAccess.isLocalNetworkHost("10.0.2.2"))
        assertTrue(LocalNetworkAccess.isLocalNetworkHost("localhost"))
        assertTrue(LocalNetworkAccess.isLocalNetworkHost("printer.local"))
        assertFalse(LocalNetworkAccess.isLocalNetworkHost("api.example.com"))
        assertFalse(LocalNetworkAccess.isLocalNetworkHost(""))
    }
}
