package app.promise.android.ui.profile

import app.promise.android.data.network.AuthSession
import app.promise.android.domain.NotificationPreferencesPatch
import app.promise.android.ui.auth.FakeAuthRepository
import app.promise.android.ui.haptics.FakePromiseHaptics
import app.promise.android.ui.theme.InMemoryThemeStore
import app.promise.android.ui.theme.PromiseThemeMode
import app.promise.android.ui.theme.ThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var eventBus: AppEventBus

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        eventBus = AppEventBus()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setModeEmitsLightHapticOncePerChange() {
        val haptics = FakePromiseHaptics()
        val authRepo = FakeAuthRepository()
        val notifRepo = FakeNotificationPreferencesRepository()
        val vm = ProfileViewModel(ThemeController(InMemoryThemeStore()), haptics, authRepo, notifRepo, eventBus, AuthSession())
        vm.setMode(PromiseThemeMode.Light)
        assertEquals(emptyList<String>(), haptics.events)
        vm.setMode(PromiseThemeMode.Dark)
        assertEquals(listOf("light"), haptics.events)
        vm.setMode(PromiseThemeMode.Dark)
        assertEquals(listOf("light"), haptics.events)
    }

    @Test
    fun logoutAllDelegatesToAuthRepositoryAndTriggersConfirmHaptic() = runTest(dispatcher) {
        val haptics = FakePromiseHaptics()
        val authRepo = FakeAuthRepository()
        val notifRepo = FakeNotificationPreferencesRepository()
        val vm = ProfileViewModel(ThemeController(InMemoryThemeStore()), haptics, authRepo, notifRepo, eventBus, AuthSession())
        var completed = false

        vm.logoutAll(onComplete = { completed = true })

        assertTrue(authRepo.logoutAllCalled)
        assertTrue(completed)
        assertEquals(listOf("confirm"), haptics.events)
    }

    @Test
    fun preferencesLoadsOnInitAndOptimisticallyUpdates() = runTest(dispatcher) {
        val haptics = FakePromiseHaptics()
        val authRepo = FakeAuthRepository()
        val notifRepo = FakeNotificationPreferencesRepository()
        val vm = ProfileViewModel(ThemeController(InMemoryThemeStore()), haptics, authRepo, notifRepo, eventBus, AuthSession())

        assertNotNull(vm.preferences.value)
        assertTrue(vm.preferences.value!!.enabled)

        // Toggle master off
        vm.updatePreferences(NotificationPreferencesPatch(enabled = false))
        assertFalse(vm.preferences.value!!.enabled)
        assertTrue(haptics.events.contains("light"))
        assertEquals(false, notifRepo.lastPatch?.enabled)
    }

    @Test
    fun preferencesRollsBackOnFailure() = runTest(dispatcher) {
        val haptics = FakePromiseHaptics()
        val authRepo = FakeAuthRepository()
        val notifRepo = FakeNotificationPreferencesRepository(shouldFailUpdate = true)
        val vm = ProfileViewModel(ThemeController(InMemoryThemeStore()), haptics, authRepo, notifRepo, eventBus, AuthSession())

        assertTrue(vm.preferences.value!!.enabled)

        // Attempt toggle
        vm.updatePreferences(NotificationPreferencesPatch(enabled = false))

        // State rolled back to true
        assertTrue(vm.preferences.value!!.enabled)
        assertTrue(haptics.events.contains("error"))
        assertNotNull(vm.errorMessage.value)
    }
}
