package app.promise.android.data.network

import app.promise.android.domain.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthSessionTest {
    @Test
    fun accessTokenStaysInMemoryOnly() {
        val session = AuthSession()
        session.setAccessToken("access-1")
        session.setUser(User("1", "a@b.c", "A", "UTC", "2026-01-01T00:00:00Z"))
        assertEquals("access-1", session.accessToken)
        session.clear()
        assertNull(session.accessToken)
        assertNull(session.user.value)
    }
}
