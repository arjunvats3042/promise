package app.promise.android.ui.commitments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.core.toErrorKind
import app.promise.android.data.home.HomeFreshness
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentRepository
import app.promise.android.ui.haptics.PromiseHaptics
import app.promise.android.ui.navigation.CommitmentRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

@OptIn(FlowPreview::class)
@HiltViewModel
class CommitmentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CommitmentRepository,
    private val authSession: AuthSession,
    private val homeFreshness: HomeFreshness,
    private val haptics: PromiseHaptics,
    private val appEventBus: AppEventBus,
) : ViewModel() {
    private val commitmentId: String = savedStateHandle.get<String>("commitmentId")
        ?: savedStateHandle.toRoute<CommitmentRoute>().commitmentId

    private val _state = MutableStateFlow<LoadState<Commitment>>(LoadState.Loading)
    val state: StateFlow<LoadState<Commitment>> = _state.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    val timeZoneId: String
        get() = authSession.user.value?.timezone ?: "UTC"

    init {
        reload()
        observeEvents()
    }

    private fun observeEvents() {
        viewModelScope.launch {
            appEventBus.events
                .debounce(50L)
                .collect { event ->
                    when (event) {
                        is AppMutationEvent.CommitmentUpdated -> if (event.commitmentId == commitmentId) reloadQuiet()
                        is AppMutationEvent.CommitmentCompleted -> if (event.commitmentId == commitmentId) reloadQuiet()
                        is AppMutationEvent.CommitmentCancelled -> if (event.commitmentId == commitmentId) reloadQuiet()
                        is AppMutationEvent.CommitmentSnoozed -> if (event.commitmentId == commitmentId) reloadQuiet()
                        is AppMutationEvent.CommitmentWaitChanged -> if (event.commitmentId == commitmentId) reloadQuiet()
                        else -> Unit
                    }
                }
        }
    }

    fun reload() {
        viewModelScope.launch {
            _state.value = LoadState.Loading
            try {
                _state.value = LoadState.Ready(repository.get(commitmentId))
            } catch (e: ApiException) {
                _state.value = LoadState.Error(e.toErrorKind(), canRetry = e.toErrorKind() != ErrorKind.NotFound)
            } catch (_: Throwable) {
                _state.value = LoadState.Error(ErrorKind.Unknown, canRetry = true)
            }
        }
    }

    fun complete() = runAction(
        confirmHaptic = true,
        onSuccess = { appEventBus.emit(AppMutationEvent.CommitmentCompleted(commitmentId)) },
    ) { repository.complete(commitmentId) }

    fun waitOn() = runAction(
        confirmHaptic = false,
        onSuccess = { appEventBus.emit(AppMutationEvent.CommitmentWaitChanged(commitmentId)) },
    ) { repository.wait(commitmentId) }

    fun unsnooze() = runAction(
        confirmHaptic = false,
        onSuccess = { appEventBus.emit(AppMutationEvent.CommitmentWaitChanged(commitmentId)) },
    ) { repository.unsnooze(commitmentId) }

    fun cancel() = runAction(
        confirmHaptic = true,
        onSuccess = { appEventBus.emit(AppMutationEvent.CommitmentCancelled(commitmentId)) },
    ) { repository.cancel(commitmentId) }

    fun snooze(untilIso: String) = runAction(
        confirmHaptic = false,
        onSuccess = { appEventBus.emit(AppMutationEvent.CommitmentSnoozed(commitmentId)) },
    ) {
        repository.snooze(commitmentId, untilIso)
    }

    fun clearActionError() {
        if (_action.value is ActionState.Failed) {
            _action.value = ActionState.Idle
        }
    }

    private fun runAction(
        confirmHaptic: Boolean,
        onSuccess: (() -> Unit)? = null,
        block: suspend () -> Commitment,
    ) {
        if (_action.value is ActionState.InFlight) return
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                val updated = block()
                _state.value = LoadState.Ready(updated)
                _action.value = ActionState.Idle
                homeFreshness.markDirty()
                if (confirmHaptic) haptics.confirm() else haptics.light()
                onSuccess?.invoke()
            } catch (e: ApiException) {
                val kind = e.toErrorKind()
                _action.value = ActionState.Failed(kind)
                haptics.error()
                if (kind == ErrorKind.Conflict) {
                    reloadQuiet()
                }
            } catch (_: Throwable) {
                _action.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    private suspend fun reloadQuiet() {
        try {
            _state.value = LoadState.Ready(repository.get(commitmentId))
        } catch (_: Throwable) {
            // keep prior Ready if any
        }
    }
}
