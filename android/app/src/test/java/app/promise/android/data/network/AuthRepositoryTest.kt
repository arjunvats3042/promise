package app.promise.android.data.network

import app.promise.android.domain.SessionState
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var stack: TestAuthStack

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        stack = TestAuthStack(server.url("/api/v1/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loginSuccessStoresRefreshNotAccess() = runBlocking {
        server.enqueue(
            json(200, AuthFixtures.loginBody()),
        )
        stack.repository.login("ada@example.com", "secret")
        assertEquals("refresh-1", stack.tokenStore.readRefreshToken())
        assertEquals("access-1", stack.session.accessToken)
        assertTrue(stack.repository.session.value is SessionState.Authenticated)
        val storedFile = stack.tokenStore.readRefreshToken()
        assertEquals(false, storedFile == stack.session.accessToken)
    }

    @Test
    fun registerSuccessPersistsTokensAndAuthenticates() = runBlocking {
        server.enqueue(
            json(201, AuthFixtures.loginBody(access = "access-reg", refresh = "refresh-reg")),
        )
        stack.repository.register("Ada", "ada@example.com", "correct-horse-battery-staple")
        assertEquals("refresh-reg", stack.tokenStore.readRefreshToken())
        assertEquals("access-reg", stack.session.accessToken)
        val state = stack.repository.session.value as SessionState.Authenticated
        assertEquals("ada@example.com", state.user.email)
        assertEquals("Ada", state.user.name)
        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/register/", recorded.path)
        assertEquals("POST", recorded.method)
    }

    @Test
    fun registerDuplicateEmailMapsConflictCode() = runBlocking {
        server.enqueue(
            json(409, AuthFixtures.error("EMAIL_ALREADY_EXISTS", "A user with this email already exists.")),
        )
        try {
            stack.repository.register("Ada", "ada@example.com", "secret")
            error("expected failure")
        } catch (e: ApiException) {
            assertEquals("EMAIL_ALREADY_EXISTS", e.code)
            assertEquals(409, e.status)
        }
        assertNull(stack.tokenStore.readRefreshToken())
        assertNull(stack.session.accessToken)
        assertTrue(stack.repository.session.value is SessionState.Restoring)
    }

    @Test
    fun registerValidationIncludesFieldErrors() = runBlocking {
        server.enqueue(
            json(
                400,
                """{"error":{"code":"VALIDATION_ERROR","message":"Invalid input.","details":{"password":["This password is too short."]}}}""",
            ),
        )
        try {
            stack.repository.register("Ada", "ada@example.com", "short")
            error("expected failure")
        } catch (e: ApiException) {
            assertEquals("VALIDATION_ERROR", e.code)
            assertEquals("This password is too short.", e.fieldErrors["password"])
        }
    }

    @Test
    fun registerRateLimitedRespectsRetryAfter() = runBlocking {
        server.enqueue(
            json(429, AuthFixtures.error("RATE_LIMITED")).setHeader("Retry-After", "20"),
        )
        try {
            stack.repository.register("Ada", "ada@example.com", "secret")
            error("expected 429")
        } catch (e: ApiException) {
            assertEquals("RATE_LIMITED", e.code)
            assertEquals(20, e.retryAfterSeconds)
        }
    }

    @Test
    fun registerNetworkFailureDoesNotAuthenticate() = runBlocking {
        server.shutdown()
        try {
            stack.repository.register("Ada", "ada@example.com", "secret")
            error("expected network failure")
        } catch (e: ApiException) {
            assertEquals("NETWORK", e.code)
        }
        assertNull(stack.tokenStore.readRefreshToken())
        assertNull(stack.session.accessToken)
    }

    @Test
    fun loginFailureDoesNotStoreTokens() = runBlocking {
        server.enqueue(
            json(401, AuthFixtures.error("AUTHENTICATION_FAILED", "Authentication failed.")),
        )
        try {
            stack.repository.login("ada@example.com", "bad")
            error("expected failure")
        } catch (e: ApiException) {
            assertEquals("AUTHENTICATION_FAILED", e.code)
        }
        assertNull(stack.tokenStore.readRefreshToken())
        assertNull(stack.session.accessToken)
    }

    @Test
    fun restoreWithoutRefreshGoesToLogin() = runBlocking {
        stack.repository.restoreSession()
        assertTrue(stack.repository.session.value is SessionState.Unauthenticated)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun restoreRefreshSuccessLoadsMe() = runBlocking {
        stack.tokenStore.saveRefreshToken("refresh-1")
        server.enqueue(json(200, AuthFixtures.refreshBody()))
        server.enqueue(json(200, AuthFixtures.meBody()))
        stack.repository.restoreSession()
        assertEquals("refresh-2", stack.tokenStore.readRefreshToken())
        assertEquals("access-2", stack.session.accessToken)
        val state = stack.repository.session.value as SessionState.Authenticated
        assertEquals("ada@example.com", state.user.email)
    }

    @Test
    fun restoreRefreshFailureClearsSession() = runBlocking {
        stack.tokenStore.saveRefreshToken("refresh-stale")
        stack.session.setAccessToken("should-not-survive")
        server.enqueue(json(401, AuthFixtures.error("TOKEN_INVALID")))
        stack.repository.restoreSession()
        assertNull(stack.tokenStore.readRefreshToken())
        assertNull(stack.session.accessToken)
        assertTrue(stack.repository.session.value is SessionState.Unauthenticated)
    }

    @Test
    fun restoreNetworkFailureKeepsRefresh() = runBlocking {
        stack.tokenStore.saveRefreshToken("refresh-keep")
        server.shutdown()
        stack.repository.restoreSession()
        assertEquals("refresh-keep", stack.tokenStore.readRefreshToken())
        assertTrue(stack.repository.session.value is SessionState.Restoring)
    }

    @Test
    fun logoutClearsEvenIfHttpFails() = runBlocking {
        stack.tokenStore.saveRefreshToken("refresh-1")
        stack.session.setAccessToken("access-1")
        server.enqueue(MockResponse().setResponseCode(500).setBody(AuthFixtures.error("INTERNAL_SERVER_ERROR")))
        stack.repository.logout()
        assertNull(stack.tokenStore.readRefreshToken())
        assertNull(stack.session.accessToken)
        assertTrue(stack.repository.session.value is SessionState.Unauthenticated)
    }

    @Test
    fun retryAfter429OnLogin() = runBlocking {
        server.enqueue(
            json(429, AuthFixtures.error("RATE_LIMITED")).setHeader("Retry-After", "15"),
        )
        try {
            stack.repository.login("ada@example.com", "secret")
            error("expected 429")
        } catch (e: ApiException) {
            assertEquals("RATE_LIMITED", e.code)
            assertEquals(15, e.retryAfterSeconds)
        }
    }

    private fun json(code: Int, body: String): MockResponse {
        return MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }
}

class AuthRetryInterceptorTest {
    private lateinit var server: MockWebServer
    private lateinit var stack: TestAuthStack

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        stack = TestAuthStack(server.url("/api/v1/"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun unauthorizedRetriesOnceAfterRefresh() = runBlocking {
        stack.session.setAccessToken("old-access")
        stack.tokenStore.saveRefreshToken("refresh-1")
        server.enqueue(json(401, AuthFixtures.error("UNAUTHENTICATED")))
        server.enqueue(json(200, AuthFixtures.refreshBody(access = "new-access", refresh = "refresh-2")))
        server.enqueue(json(200, AuthFixtures.meBody()))
        val me = stack.authedApi.me()
        assertEquals("ada@example.com", me.user.email)
        assertEquals("new-access", stack.session.accessToken)
        assertEquals("refresh-2", stack.tokenStore.readRefreshToken())
        assertEquals("/api/v1/auth/me/", server.takeRequest().path)
        assertEquals("/api/v1/auth/refresh/", server.takeRequest().path)
        val retried = server.takeRequest()
        assertEquals("/api/v1/auth/me/", retried.path)
        assertEquals("Bearer new-access", retried.getHeader("Authorization"))
    }

    @Test
    fun logoutAllSendsPostAndClearsSession() = runBlocking {
        stack.session.setAccessToken("active-access")
        stack.tokenStore.saveRefreshToken("active-refresh")
        server.enqueue(MockResponse().setResponseCode(204))

        stack.repository.logoutAll()

        assertNull(stack.tokenStore.readRefreshToken())
        assertNull(stack.session.accessToken)
        assertTrue(stack.repository.session.value is SessionState.Unauthenticated)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/logout-all/", request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun concurrent401ShareOneRefresh() = runBlocking {
        val refreshes = AtomicInteger(0)
        val api = object : AuthApi {
            override suspend fun login(body: LoginRequest) = error("unused")
            override suspend fun register(body: RegisterRequest) = error("unused")
            override suspend fun googleAuth(body: GoogleAuthRequest): AuthSessionResponse = error("unused")
            override suspend fun verifyEmailRequest(): MessageDetailResponse = error("unused")
            override suspend fun verifyEmailConfirm(body: VerifyEmailConfirmRequest): MeResponse = error("unused")
            override suspend fun passwordResetRequest(body: PasswordResetRequest): MessageDetailResponse = error("unused")
            override suspend fun passwordResetConfirm(body: PasswordResetConfirmRequest): MeResponse = error("unused")
            override suspend fun setPassword(body: SetPasswordRequest): MeResponse = error("unused")
            override suspend fun changePassword(body: ChangePasswordRequest): MeResponse = error("unused")
            override suspend fun deleteAccount(): retrofit2.Response<Unit> = error("unused")
            override suspend fun linkGoogle(body: GoogleLinkRequest): MeResponse = error("unused")
            override suspend fun unlinkGoogle(): MeResponse = error("unused")
            override suspend fun emailChangeRequest(body: EmailChangeRequest): MessageDetailResponse = error("unused")
            override suspend fun emailChangeConfirm(body: EmailChangeConfirmRequest): MeResponse = error("unused")
            override suspend fun getSessions(): SessionsResponse = error("unused")
            override suspend fun revokeSession(sessionId: String): retrofit2.Response<Unit> = error("unused")
            override suspend fun revokeAllSessions(body: RevokeAllSessionsRequest): retrofit2.Response<Unit> = error("unused")
            override suspend fun getSecurityEvents(): SecurityEventsResponse = error("unused")
            override suspend fun logout(body: RefreshRequest) = error("unused")
            override suspend fun logoutAll(): retrofit2.Response<Unit> = error("unused")
            override suspend fun me() = error("unused")
            override suspend fun refresh(body: RefreshRequest): TokensResponse {
                refreshes.incrementAndGet()
                kotlinx.coroutines.delay(120)
                return TokensResponse(
                    TokensDto(
                        accessToken = "new-access",
                        refreshToken = "refresh-2",
                        tokenType = "Bearer",
                        expiresIn = 900,
                    ),
                )
            }
        }
        val store = app.promise.android.data.local.InMemoryTokenStore()
        store.saveRefreshToken("refresh-1")
        val memory = AuthSession()
        val refresher = SessionRefresher(api, store, memory)
        val first = async { refresher.refresh() }
        val second = async { refresher.refresh() }
        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(1, refreshes.get())
        assertEquals("new-access", memory.accessToken)
        assertEquals("refresh-2", store.readRefreshToken())
    }

    @Test
    fun refreshFailureClearsAndDoesNotRetryForever() = runBlocking {
        stack.session.setAccessToken("old-access")
        stack.tokenStore.saveRefreshToken("refresh-1")
        server.enqueue(json(401, AuthFixtures.error("UNAUTHENTICATED")))
        server.enqueue(json(401, AuthFixtures.error("TOKEN_EXPIRED")))
        try {
            stack.authedApi.me()
            error("expected failure")
        } catch (_: Exception) {
        }
        assertNull(stack.tokenStore.readRefreshToken())
        assertNull(stack.session.accessToken)
        assertEquals(2, server.requestCount)
    }

    private fun json(code: Int, body: String): MockResponse {
        return MockResponse()
            .setResponseCode(code)
            .setHeader("Content-Type", "application/json")
            .setBody(body)
    }
}
