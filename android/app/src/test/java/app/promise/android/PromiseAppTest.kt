package app.promise.android

import dagger.hilt.android.HiltAndroidApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PromiseAppTest {
    @Test
    fun applicationClassIsHiltEntry() {
        assertEquals("app.promise.android.PromiseApp", PromiseApp::class.java.name)
        assertNotNull(PromiseApp::class.java.getAnnotation(HiltAndroidApp::class.java))
    }
}
