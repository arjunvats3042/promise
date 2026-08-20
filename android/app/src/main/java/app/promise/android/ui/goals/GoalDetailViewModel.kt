package app.promise.android.ui.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.core.toErrorKind
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.ui.haptics.PromiseHaptics
import app.promise.android.ui.navigation.GoalRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GoalDetailUi(
    val goal: Goal,
    val checkIns: List<GoalCheckIn>,
    val todayCheckIn: GoalCheckIn?,
)

@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoalRepository,
    private val authSession: AuthSession,
    private val haptics: PromiseHaptics,
) : ViewModel() {
    private val goalId: String = savedStateHandle.get<String>("goalId")
        ?: savedStateHandle.toRoute<GoalRoute>().goalId

    private val _state = MutableStateFlow<LoadState<GoalDetailUi>>(LoadState.Loading)
    val state: StateFlow<LoadState<GoalDetailUi>> = _state.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    val timeZoneId: String
        get() = authSession.user.value?.timezone ?: "UTC"

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.value = LoadState.Loading
            loadDetail()
        }
    }

    fun checkIn(input: CheckInInput) {
        if (_action.value is ActionState.InFlight) return
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                repository.checkIn(goalId, input)
                reloadQuiet()
                _action.value = ActionState.Idle
                haptics.confirm()
            } catch (e: ApiException) {
                val kind = e.toErrorKind()
                _action.value = ActionState.Failed(kind)
                haptics.error()
                if (kind == ErrorKind.Conflict ||
                    kind == ErrorKind.InvalidCheckIn ||
                    kind == ErrorKind.ScheduleLocked ||
                    kind == ErrorKind.TimezoneLocked
                ) {
                    reloadQuiet()
                }
            } catch (_: Throwable) {
                _action.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun pause() = runGoalAction(confirmHaptic = false) { repository.pause(goalId) }

    fun resume() = runGoalAction(confirmHaptic = false) { repository.resume(goalId) }

    fun complete() = runGoalAction(confirmHaptic = true) { repository.complete(goalId) }

    fun cancel() = runGoalAction(confirmHaptic = true) { repository.cancel(goalId) }

    fun clearActionError() {
        if (_action.value is ActionState.Failed) {
            _action.value = ActionState.Idle
        }
    }

    private fun runGoalAction(confirmHaptic: Boolean, block: suspend () -> Goal) {
        if (_action.value is ActionState.InFlight) return
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                val updated = block()
                val current = (_state.value as? LoadState.Ready)?.value
                if (current != null) {
                    _state.value = LoadState.Ready(current.copy(goal = updated))
                } else {
                    reloadQuiet()
                }
                _action.value = ActionState.Idle
                if (confirmHaptic) haptics.confirm() else haptics.light()
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

    private suspend fun loadDetail() {
        try {
            val goal = repository.get(goalId)
            val zone = safeZone(goal.timezone)
            val end = LocalDate.now(zone)
            val start = end.minusDays(27)
            val history = repository.listCheckIns(
                id = goalId,
                startDate = start.toString(),
                endDate = end.toString(),
            )
            val today = end.toString()
            val todayCheckIn = history.items.firstOrNull { it.periodDate == today }
            _state.value = LoadState.Ready(
                GoalDetailUi(
                    goal = goal,
                    checkIns = history.items.sortedByDescending { it.periodDate },
                    todayCheckIn = todayCheckIn,
                ),
            )
        } catch (e: ApiException) {
            _state.value = LoadState.Error(e.toErrorKind(), canRetry = e.toErrorKind() != ErrorKind.NotFound)
        } catch (_: Throwable) {
            _state.value = LoadState.Error(ErrorKind.Unknown, canRetry = true)
        }
    }

    private suspend fun reloadQuiet() {
        try {
            loadDetail()
        } catch (_: Throwable) {
            // keep prior Ready if any
        }
    }

    private fun safeZone(id: String): ZoneId {
        return runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("UTC"))
    }
}

fun GoalDetailUi.canCheckInToday(): Boolean {
    if (!goal.canCheckIn) return false
    val today = todayCheckIn
    if (today == null) return GoalPresentation.needsCheckInToday(goal)
    return today.status == GoalCheckInStatus.SKIPPED ||
        goal.trackingKind == GoalTrackingKind.COUNT
}

fun GoalDetailUi.checkInPrompt(): String {
    return if (todayCheckIn != null) "Update today’s check-in" else "Check in"
}
