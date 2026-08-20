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
class LoginViewModelTest {
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
    fun loginSuccessReturnsToIdleAndConfirmsHaptic() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val haptics = FakePromiseHaptics()
        val vm = LoginViewModel(repo, FakeLocalNetworkPermission(), haptics)
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("secret")
        vm.submit()
        assertTrue(vm.action.value is ActionState.Idle)
        assertEquals("ada@example.com" to "secret", repo.loggedInWith)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun loginFailureMapsInvalidCredentialsAndErrorsHaptic() = runTest(dispatcher) {
        val repo = FakeAuthRepository(
            loginError = ApiException(status = 401, code = "AUTHENTICATION_FAILED"),
        )
        val haptics = FakePromiseHaptics()
        val vm = LoginViewModel(repo, FakeLocalNetworkPermission(), haptics)
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("bad")
        vm.submit()
        val failed = vm.action.value as ActionState.Failed
        assertEquals(ErrorKind.InvalidCredentials, failed.kind)
        assertEquals(listOf("error"), haptics.events)
    }

    @Test
    fun emptySubmitEmitsErrorHapticWithoutPressHaptic() = runTest(dispatcher) {
        val haptics = FakePromiseHaptics()
        val vm = LoginViewModel(FakeAuthRepository(), FakeLocalNetworkPermission(), haptics)
        vm.submit()
        assertEquals(ErrorKind.Validation(), (vm.action.value as ActionState.Failed).kind)
        assertEquals(listOf("error"), haptics.events)
    }

    @Test
    fun submitWithoutLocalNetworkPermissionFailsImmediately() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val haptics = FakePromiseHaptics()
        val vm = LoginViewModel(
            repo,
            FakeLocalNetworkPermission(requiresAccess = true, granted = false),
            haptics,
        )
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("secret")
        vm.submit()
        val failed = vm.action.value as ActionState.Failed
        assertEquals(ErrorKind.LocalNetworkDenied, failed.kind)
        assertNull(repo.loggedInWith)
        assertEquals(emptyList<String>(), haptics.events)
    }

    @Test
    fun onLocalNetworkGrantedClearsDeniedState() = runTest(dispatcher) {
        val vm = LoginViewModel(
            FakeAuthRepository(),
            FakeLocalNetworkPermission(requiresAccess = true, granted = false),
            FakePromiseHaptics(),
        )
        vm.onEmailChange("ada@example.com")
        vm.onPasswordChange("secret")
        vm.submit()
        assertEquals(ErrorKind.LocalNetworkDenied, (vm.action.value as ActionState.Failed).kind)
        vm.onLocalNetworkGranted()
        assertTrue(vm.action.value is ActionState.Idle)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelLocalNetworkTest {
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
    fun permissionNotRequiredRestoresImmediately() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = SessionViewModel(repo, FakeLocalNetworkPermission(), FakePromiseHaptics())
        assertEquals(LocalNetworkAccessState.NotRequired, vm.localNetwork.value)
        assertEquals(SessionState.Unauthenticated, vm.session.value)
        assertTrue(repo.restoreCalled)
    }

    @Test
    fun needsRequestDoesNotRestoreUntilGranted() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val permission = FakeLocalNetworkPermission(requiresAccess = true, granted = false)
        val vm = SessionViewModel(repo, permission, FakePromiseHaptics())
        assertEquals(LocalNetworkAccessState.NeedsRequest, vm.localNetwork.value)
        assertEquals(SessionState.Restoring, vm.session.value)
        assertTrue(!repo.restoreCalled)

        permission.granted = true
        vm.onLocalNetworkPermissionResult(granted = true)
        assertEquals(LocalNetworkAccessState.Granted, vm.localNetwork.value)
        assertTrue(repo.restoreCalled)
        assertEquals(SessionState.Unauthenticated, vm.session.value)
    }

    @Test
    fun denialKeepsSessionFromCallingApi() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val vm = SessionViewModel(
            repo,
            FakeLocalNetworkPermission(requiresAccess = true, granted = false),
            FakePromiseHaptics(),
        )
        vm.onLocalNetworkPermissionResult(granted = false)
        assertEquals(LocalNetworkAccessState.Denied, vm.localNetwork.value)
        assertTrue(!repo.restoreCalled)
    }

    @Test
    fun logoutEmitsConfirmHapticAfterClearing() = runTest(dispatcher) {
        val repo = FakeAuthRepository()
        val haptics = FakePromiseHaptics()
        val vm = SessionViewModel(repo, FakeLocalNetworkPermission(), haptics)
        vm.logout()
        assertEquals(listOf("confirm"), haptics.events)
    }
}
