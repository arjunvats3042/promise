package app.promise.android.ui.auth

import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.data.network.ApiException
import app.promise.android.domain.SessionState
import app.promise.android.ui.haptics.FakePromiseHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun registerSuccessAuthenticatesAndConfirmsHaptic() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val haptics = FakePromiseHaptics()
        val vm = RegisterViewModel(repo, FakeLocalNetworkPermission(), haptics)
        vm.onNameChange("Ada")
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("correct-horse-battery-staple")
        vm.submit()
        assertTrue(vm.action.value is ActionState.Idle)
        assertEquals(Triple("Ada", "ada@example.com", "correct-horse-battery-staple"), repo.registeredWith)
        assertTrue(repo.session.value is SessionState.Authenticated)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun duplicateEmailMapsFriendlyKindAndErrorsHaptic() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            registerError = ApiException(status = 409, code = "EMAIL_ALREADY_EXISTS"),
        )
        val haptics = FakePromiseHaptics()
        val vm = RegisterViewModel(repo, FakeLocalNetworkPermission(), haptics)
        vm.onNameChange("Ada")
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("secret")
        vm.submit()
        val failed = vm.action.value as ActionState.Failed
        assertEquals(ErrorKind.EmailAlreadyExists, failed.kind)
        assertEquals(listOf("error"), haptics.events)
    }

    @Test
    fun validationErrorSurfacesFieldMap() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            registerError = ApiException(
                status = 400,
                code = "VALIDATION_ERROR",
                fieldErrors = mapOf("password" to "This password is too short."),
            ),
        )
        val haptics = FakePromiseHaptics()
        val vm = RegisterViewModel(repo, FakeLocalNetworkPermission(), haptics)
        vm.onNameChange("Ada")
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("short")
        vm.submit()
        val failed = vm.action.value as ActionState.Failed
        val kind = failed.kind as ErrorKind.Validation
        assertEquals("This password is too short.", kind.fields["password"])
        assertEquals(listOf("error"), haptics.events)
    }

    @Test
    fun rateLimitedMapsRetryAfter() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            registerError = ApiException(status = 429, code = "RATE_LIMITED", retryAfterSeconds = 15),
        )
        val vm = RegisterViewModel(repo, FakeLocalNetworkPermission(), FakePromiseHaptics())
        vm.onNameChange("Ada")
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("secret")
        vm.submit()
        val failed = vm.action.value as ActionState.Failed
        assertEquals(ErrorKind.RateLimited(15), failed.kind)
    }

    @Test
    fun networkFailureMapsNetworkKind() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            registerError = ApiException.network(),
        )
        val vm = RegisterViewModel(repo, FakeLocalNetworkPermission(), FakePromiseHaptics())
        vm.onNameChange("Ada")
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("secret")
        vm.submit()
        val failed = vm.action.value as ActionState.Failed
        assertEquals(ErrorKind.Network, failed.kind)
    }

    @Test
    fun blankFieldsFailClientSideWithoutCallingRepository() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val haptics = FakePromiseHaptics()
        val vm = RegisterViewModel(repo, FakeLocalNetworkPermission(), haptics)
        vm.onNameChange(" ")
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("secret")
        vm.submit()
        assertNull(repo.registeredWith)
        assertTrue((vm.action.value as ActionState.Failed).kind is ErrorKind.Validation)
        assertEquals(listOf("error"), haptics.events)
    }

    @Test
    fun submitWithoutLocalNetworkPermissionFailsImmediately() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = RegisterViewModel(
            repo,
            FakeLocalNetworkPermission(requiresAccess = true, granted = false),
            FakePromiseHaptics(),
        )
        vm.onNameChange("Ada")
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("secret")
        vm.submit()
        val failed = vm.action.value as ActionState.Failed
        assertEquals(ErrorKind.LocalNetworkDenied, failed.kind)
        assertNull(repo.registeredWith)
    }
}
