package app.promise.android.core.copy

import app.promise.android.core.ErrorKind
import app.promise.android.core.toHumanizedDetails
import app.promise.android.core.toUserMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMappingTest {

    @Test
    fun `test network error provides offline reassurance`() {
        val msg = ErrorKind.Network.toUserMessage()
        assertTrue(msg.contains("offline", ignoreCase = true))
        assertTrue(msg.contains("saved locally", ignoreCase = true))

        val details = ErrorKind.Network.toHumanizedDetails()
        assertEqualsString("You're currently offline", details.title)
        assertTrue(details.description.contains("sync automatically", ignoreCase = true))
        assertNotNull(details.actionLabel)
    }

    @Test
    fun `test timeout error reassurance`() {
        val details = ErrorKind.Timeout.toHumanizedDetails()
        assertTrue(details.description.contains("safe", ignoreCase = true) || details.description.contains("locally", ignoreCase = true))
    }

    @Test
    fun `test schedule locked explanation`() {
        val msg = ErrorKind.ScheduleLocked.toUserMessage()
        assertTrue(msg.contains("streak", ignoreCase = true) || msg.contains("locked", ignoreCase = true))
    }

    @Test
    fun `test unauthenticated session expiry message`() {
        val msg = ErrorKind.Unauthenticated.toUserMessage()
        assertTrue(msg.contains("expired", ignoreCase = true) || msg.contains("sign in", ignoreCase = true))
    }

    private fun assertEqualsString(expected: String, actual: String) {
        org.junit.Assert.assertEquals(expected, actual)
    }
}
