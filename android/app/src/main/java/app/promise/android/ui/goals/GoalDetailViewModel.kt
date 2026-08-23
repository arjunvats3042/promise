package app.promise.android.ui.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import app.promise.android.core.events.MembershipChangeType
import app.promise.android.core.toErrorKind
import app.promise.android.data.home.HomeFreshness
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalActivityItem
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalInvitePreview
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.domain.LookupUser
import app.promise.android.domain.UserRepository
import app.promise.android.ui.haptics.PromiseHaptics
import app.promise.android.ui.navigation.GoalRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

sealed class GoalDetailUi {
    data class Full(
        val goal: Goal,
        val checkIns: List<GoalCheckIn>,
        val todayCheckIn: GoalCheckIn?,
        val roster: List<GoalParticipant> = emptyList(),
        val recentActivity: List<GoalActivityItem> = emptyList(),
    ) : GoalDetailUi()

    data class Invite(val preview: GoalInvitePreview) : GoalDetailUi()
}

data class GoalActivityState(
    val items: List<GoalActivityItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasMore: Boolean = false,
)

sealed interface UserLookupUi {
    data object Idle : UserLookupUi
    data object Loading : UserLookupUi
    data class Found(val user: LookupUser) : UserLookupUi
    data class NotFound(val email: String) : UserLookupUi
    data class Failed(val kind: ErrorKind) : UserLookupUi
}

@OptIn(FlowPreview::class)
@HiltViewModel
class GoalDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GoalRepository,
    private val userRepository: UserRepository,
    private val authSession: AuthSession,
    private val homeFreshness: HomeFreshness,
    private val haptics: PromiseHaptics,
    private val appEventBus: AppEventBus,
) : ViewModel() {
    private val goalId: String = savedStateHandle.get<String>("goalId")
        ?: savedStateHandle.toRoute<GoalRoute>().goalId

    private val _state = MutableStateFlow<LoadState<GoalDetailUi>>(LoadState.Loading)
    val state: StateFlow<LoadState<GoalDetailUi>> = _state.asStateFlow()

    private val _activityState = MutableStateFlow(GoalActivityState())
    val activityState: StateFlow<GoalActivityState> = _activityState.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    private val _inviteAction = MutableStateFlow<ActionState>(ActionState.Idle)
    val inviteAction: StateFlow<ActionState> = _inviteAction.asStateFlow()

    private val _inviteSheetAction = MutableStateFlow<ActionState>(ActionState.Idle)
    val inviteSheetAction: StateFlow<ActionState> = _inviteSheetAction.asStateFlow()

    private val _lookupState = MutableStateFlow<UserLookupUi>(UserLookupUi.Idle)
    val lookupState: StateFlow<UserLookupUi> = _lookupState.asStateFlow()

    val timeZoneId: String
        get() = authSession.user.value?.timezone ?: "Asia/Kolkata"

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
                        is AppMutationEvent.GoalCheckedIn -> if (event.goalId == goalId) reloadQuiet()
                        is AppMutationEvent.SharedGoalMembershipChanged -> if (event.goalId == goalId) reloadQuiet()
                        is AppMutationEvent.OwnershipTransferred -> if (event.goalId == goalId) reloadQuiet()
                        is AppMutationEvent.InvitationUpdated -> if (event.goalId == goalId) reloadQuiet()
                        is AppMutationEvent.SharedGoalSummaryChanged -> if (event.goalId == goalId) reloadQuiet()
                        is AppMutationEvent.GoalUpdated -> if (event.goalId == goalId) reloadQuiet()
                        is AppMutationEvent.GoalPaused -> if (event.goalId == goalId) reloadQuiet()
                        is AppMutationEvent.GoalResumed -> if (event.goalId == goalId) reloadQuiet()
                        is AppMutationEvent.GoalCompleted -> if (event.goalId == goalId) reloadQuiet()
                        is AppMutationEvent.GoalCancelled -> if (event.goalId == goalId) reloadQuiet()
                        else -> Unit
                    }
                }
        }
    }

    fun loadFullActivity() {
        viewModelScope.launch {
            _activityState.value = GoalActivityState(isLoading = true)
            try {
                val items = repository.listActivity(goalId, limit = 20)
                _activityState.value = GoalActivityState(
                    items = items,
                    isLoading = false,
                    hasMore = items.size >= 20,
                )
            } catch (_: Throwable) {
                _activityState.value = GoalActivityState(isLoading = false)
            }
        }
    }

    fun loadMoreActivity() {
        val current = _activityState.value
        if (current.isLoading || !current.hasMore) return
        val oldest = current.items.lastOrNull() ?: return
        viewModelScope.launch {
            _activityState.value = current.copy(isLoading = true)
            try {
                val older = repository.listActivity(
                    goalId = goalId,
                    limit = 20,
                    beforeCreatedAt = oldest.createdAt,
                    beforeId = oldest.id,
                )
                _activityState.value = current.copy(
                    items = current.items + older,
                    isLoading = false,
                    hasMore = older.size >= 20,
                )
            } catch (_: Throwable) {
                _activityState.value = current.copy(isLoading = false)
            }
        }
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
                homeFreshness.markDirty()
                reloadQuiet()
                _action.value = ActionState.Idle
                haptics.confirm()
                appEventBus.emit(AppMutationEvent.GoalCheckedIn(goalId))
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

    fun convertToShared(onConverted: () -> Unit = {}) {
        if (_action.value is ActionState.InFlight) return
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                repository.convertToShared(goalId)
                _action.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.confirm()
                loadDetail()
                appEventBus.emit(AppMutationEvent.GoalCreated(goalId))
                onConverted()
            } catch (e: ApiException) {
                _action.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _action.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun leave(onLeft: () -> Unit) {
        if (_action.value is ActionState.InFlight) return
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                repository.leave(goalId)
                _action.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.light()
                appEventBus.emit(
                    AppMutationEvent.SharedGoalMembershipChanged(
                        goalId,
                        MembershipChangeType.LEFT,
                    ),
                )
                onLeft()
            } catch (e: ApiException) {
                _action.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _action.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun acceptInvite(onAccepted: () -> Unit = {}) {
        if (_inviteAction.value is ActionState.InFlight) return
        viewModelScope.launch {
            _inviteAction.value = ActionState.InFlight
            try {
                repository.acceptInvitation(goalId)
                _inviteAction.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.confirm()
                loadDetail()
                appEventBus.emit(
                    AppMutationEvent.SharedGoalMembershipChanged(
                        goalId,
                        MembershipChangeType.ACCEPTED,
                    ),
                )
                onAccepted()
            } catch (e: ApiException) {
                _inviteAction.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _inviteAction.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun declineInvite(onLeft: () -> Unit) {
        if (_inviteAction.value is ActionState.InFlight) return
        viewModelScope.launch {
            _inviteAction.value = ActionState.InFlight
            try {
                repository.declineInvitation(goalId)
                _inviteAction.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.light()
                appEventBus.emit(
                    AppMutationEvent.SharedGoalMembershipChanged(
                        goalId,
                        MembershipChangeType.DECLINED,
                    ),
                )
                onLeft()
            } catch (e: ApiException) {
                _inviteAction.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _inviteAction.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun inviteParticipant(userId: String) {
        if (_inviteSheetAction.value is ActionState.InFlight) return
        viewModelScope.launch {
            _inviteSheetAction.value = ActionState.InFlight
            try {
                repository.inviteParticipant(goalId, userId)
                _inviteSheetAction.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.confirm()
                _lookupState.value = UserLookupUi.Idle
                refreshRoster()
                appEventBus.emit(
                    AppMutationEvent.SharedGoalMembershipChanged(
                        goalId,
                        MembershipChangeType.INVITED,
                    ),
                )
            } catch (e: ApiException) {
                _inviteSheetAction.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _inviteSheetAction.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun removeParticipant(userId: String) {
        if (_inviteSheetAction.value is ActionState.InFlight) return
        viewModelScope.launch {
            _inviteSheetAction.value = ActionState.InFlight
            try {
                repository.removeParticipant(goalId, userId)
                _inviteSheetAction.value = ActionState.Idle
                homeFreshness.markDirty()
                val current = (_state.value as? LoadState.Ready)?.value as? GoalDetailUi.Full
                if (current != null) {
                    _state.value = LoadState.Ready(
                        current.copy(roster = current.roster.filterNot { it.userId == userId || it.id == userId }),
                    )
                }
                haptics.light()
                refreshRoster()
                appEventBus.emit(
                    AppMutationEvent.SharedGoalMembershipChanged(
                        goalId,
                        MembershipChangeType.REMOVED,
                    ),
                )
            } catch (e: ApiException) {
                _inviteSheetAction.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _inviteSheetAction.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun reinviteParticipant(participantId: String? = null, userId: String? = null) {
        if (_inviteSheetAction.value is ActionState.InFlight) return
        viewModelScope.launch {
            _inviteSheetAction.value = ActionState.InFlight
            try {
                repository.reinviteParticipant(goalId, participantId = participantId, userId = userId)
                _inviteSheetAction.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.confirm()
                refreshRoster()
                appEventBus.emit(AppMutationEvent.InvitationUpdated(goalId))
            } catch (e: ApiException) {
                _inviteSheetAction.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _inviteSheetAction.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun transferOwnership(participantId: String? = null, userId: String? = null, onDone: () -> Unit = {}) {
        if (_action.value is ActionState.InFlight) return
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                val detail = repository.transferOwnership(goalId, participantId = participantId, userId = userId)
                _action.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.confirm()
                val current = (_state.value as? LoadState.Ready)?.value as? GoalDetailUi.Full
                if (current != null) {
                    when (detail) {
                        is GoalDetail.Full -> _state.value = LoadState.Ready(
                            current.copy(goal = detail.goal, roster = detail.goal.participants),
                        )
                        is GoalDetail.Invite -> _state.value = LoadState.Ready(
                            GoalDetailUi.Invite(preview = detail.preview),
                        )
                    }
                } else {
                    reloadQuiet()
                }
                appEventBus.emit(AppMutationEvent.OwnershipTransferred(goalId))
                onDone()
            } catch (e: ApiException) {
                _action.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _action.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun refreshRoster() {
        viewModelScope.launch {
            val current = (_state.value as? LoadState.Ready)?.value as? GoalDetailUi.Full
                ?: return@launch
            runCatching { repository.listParticipants(goalId) }.onSuccess { roster ->
                _state.value = LoadState.Ready(current.copy(roster = roster))
            }
        }
    }

    fun lookupUser(query: String) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return
        if (_lookupState.value is UserLookupUi.Loading) return
        viewModelScope.launch {
            _lookupState.value = UserLookupUi.Loading
            try {
                if (trimmed.contains("@")) {
                    val user = userRepository.lookupByEmail(trimmed)
                    _lookupState.value = UserLookupUi.Found(user)
                } else {
                    val users = userRepository.searchUsers(trimmed)
                    if (users.isNotEmpty()) {
                        _lookupState.value = UserLookupUi.Found(users.first())
                    } else {
                        _lookupState.value = UserLookupUi.NotFound(trimmed)
                    }
                }
            } catch (e: ApiException) {
                val kind = e.toErrorKind()
                _lookupState.value = if (kind == ErrorKind.NotFound) {
                    UserLookupUi.NotFound(trimmed)
                } else {
                    UserLookupUi.Failed(kind)
                }
            } catch (_: Throwable) {
                _lookupState.value = UserLookupUi.Failed(ErrorKind.Unknown)
            }
        }
    }

    fun clearLookup() {
        _lookupState.value = UserLookupUi.Idle
    }

    fun clearActionError() {
        if (_action.value is ActionState.Failed) {
            _action.value = ActionState.Idle
        }
    }

    fun clearInviteActionError() {
        if (_inviteAction.value is ActionState.Failed) {
            _inviteAction.value = ActionState.Idle
        }
    }

    fun clearInviteSheetActionError() {
        if (_inviteSheetAction.value is ActionState.Failed) {
            _inviteSheetAction.value = ActionState.Idle
        }
    }

    private fun runGoalAction(confirmHaptic: Boolean, block: suspend () -> Goal) {
        if (_action.value is ActionState.InFlight) return
        viewModelScope.launch {
            _action.value = ActionState.InFlight
            try {
                val updated = block()
                val current = (_state.value as? LoadState.Ready)?.value as? GoalDetailUi.Full
                if (current != null) {
                    _state.value = LoadState.Ready(current.copy(goal = updated))
                } else {
                    reloadQuiet()
                }
                _action.value = ActionState.Idle
                homeFreshness.markDirty()
                if (confirmHaptic) haptics.confirm() else haptics.light()
                appEventBus.emit(AppMutationEvent.GoalUpdated(goalId))
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
            when (val detail = repository.getDetail(goalId)) {
                is GoalDetail.Full -> {
                    val goal = detail.goal
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
                    val activity = if (goal.isShared) {
                        runCatching { repository.listActivity(goalId, limit = 4) }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                    _state.value = LoadState.Ready(
                        GoalDetailUi.Full(
                            goal = goal,
                            checkIns = history.items.sortedByDescending { it.periodDate },
                            todayCheckIn = todayCheckIn,
                            roster = goal.participants,
                            recentActivity = activity,
                        ),
                    )
                }
                is GoalDetail.Invite -> {
                    _state.value = LoadState.Ready(GoalDetailUi.Invite(detail.preview))
                }
            }
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
        return runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
    }
}

fun GoalDetailUi.Full.canCheckInToday(): Boolean {
    if (!goal.canCheckIn) return false
    val today = todayCheckIn
    if (today == null) return GoalPresentation.needsCheckInToday(goal)
    return today.status == GoalCheckInStatus.SKIPPED ||
        goal.trackingKind == GoalTrackingKind.COUNT
}

fun GoalDetailUi.Full.checkInPrompt(): String {
    return if (todayCheckIn != null) "Update today's check-in" else "Check in"
}
