package app.promise.android.data.remote

import app.promise.android.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigTest {
    @Test
    fun debugBaseUrlComesFromBuildConfig() {
        assertEquals(BuildConfig.API_BASE_URL, ApiConfig.baseUrl)
        assertTrue(BuildConfig.DEBUG)
        assertTrue(ApiConfig.baseUrl.startsWith("http://") || ApiConfig.baseUrl.startsWith("https://"))
        assertTrue(ApiConfig.baseUrl.endsWith("/"))
        assertFalse(ApiConfig.baseUrl.isBlank())
    }

    @Test
    fun timeoutsMatchDesign() {
        assertEquals(10L, ApiConfig.CONNECT_TIMEOUT_SECONDS)
        assertEquals(20L, ApiConfig.READ_WRITE_TIMEOUT_SECONDS)
        assertEquals(30L, ApiConfig.CALL_TIMEOUT_SECONDS)
    }
}
