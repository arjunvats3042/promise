package app.promise.android.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.core.toErrorKind
import app.promise.android.data.home.HomeFreshness
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalRepository
import app.promise.android.ui.haptics.PromiseHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GoalsListUi(
    val filter: GoalListFilter,
    val items: List<GoalListItem>,
    val timeZoneId: String,
)

@HiltViewModel
class GoalsListViewModel @Inject constructor(
    private val repository: GoalRepository,
    private val authSession: AuthSession,
    private val homeFreshness: HomeFreshness,
    private val haptics: PromiseHaptics,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<GoalsListUi>>(LoadState.Loading)
    val state: StateFlow<LoadState<GoalsListUi>> = _state.asStateFlow()

    private val _createAction = MutableStateFlow<ActionState>(ActionState.Idle)
    val createAction: StateFlow<ActionState> = _createAction.asStateFlow()

    private val _inviteAction = MutableStateFlow<ActionState>(ActionState.Idle)
    val inviteAction: StateFlow<ActionState> = _inviteAction.asStateFlow()

    private var filter: GoalListFilter = GoalListFilter.ACTIVE
    private var nextPage: Int? = null

    init {
        refresh()
    }

    fun selectFilter(value: GoalListFilter) {
        if (filter == value && _state.value is LoadState.Ready) return
        filter = value
        refresh(fromPull = false)
    }

    fun refresh(fromPull: Boolean = false) {
        viewModelScope.launch {
            val current = _state.value
            when {
                current is LoadState.Ready && fromPull -> {
                    _state.value = current.copy(isRefreshing = true)
                }
                current is LoadState.Ready && !fromPull -> {
                    _state.value = current.copy(
                        value = current.value.copy(filter = filter),
                        isRefreshing = false,
                    )
                }
                else -> {
                    _state.value = LoadState.Loading
                }
            }
            loadPage(page = 1, replace = true)
        }
    }

    fun loadMore() {
        val page = nextPage ?: return
        if (_state.value !is LoadState.Ready) return
        viewModelScope.launch { loadPage(page = page, replace = false) }
    }

    fun create(input: CreateGoalInput, onSuccess: (Goal) -> Unit) {
        if (_createAction.value is ActionState.InFlight) return
        viewModelScope.launch {
            _createAction.value = ActionState.InFlight
            try {
                val created = repository.create(input)
                _createAction.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.confirm()
                filter = GoalListFilter.ACTIVE
                prependOrRefresh(created)
                onSuccess(created)
            } catch (e: ApiException) {
                _createAction.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _createAction.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun quickCheckIn(goalId: String, input: CheckInInput) {
        viewModelScope.launch {
            try {
                repository.checkIn(goalId, input)
                homeFreshness.markDirty()
                haptics.confirm()
                refresh()
            } catch (_: ApiException) {
                haptics.error()
            } catch (_: Throwable) {
                haptics.error()
            }
        }
    }

    fun acceptInvite(goalId: String, onSuccess: () -> Unit = {}) {
        if (_inviteAction.value is ActionState.InFlight) return
        viewModelScope.launch {
            _inviteAction.value = ActionState.InFlight
            try {
                repository.acceptInvitation(goalId)
                _inviteAction.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.confirm()
                refresh()
                onSuccess()
            } catch (e: ApiException) {
                _inviteAction.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _inviteAction.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun declineInvite(goalId: String) {
        if (_inviteAction.value is ActionState.InFlight) return
        viewModelScope.launch {
            _inviteAction.value = ActionState.InFlight
            try {
                repository.declineInvitation(goalId)
                _inviteAction.value = ActionState.Idle
                homeFreshness.markDirty()
                haptics.light()
                refresh()
            } catch (e: ApiException) {
                _inviteAction.value = ActionState.Failed(e.toErrorKind())
                haptics.error()
            } catch (_: Throwable) {
                _inviteAction.value = ActionState.Failed(ErrorKind.Unknown)
                haptics.error()
            }
        }
    }

    fun clearCreateError() {
        if (_createAction.value is ActionState.Failed) {
            _createAction.value = ActionState.Idle
        }
    }

    fun clearInviteError() {
        if (_inviteAction.value is ActionState.Failed) {
            _inviteAction.value = ActionState.Idle
        }
    }

    private suspend fun loadPage(page: Int, replace: Boolean) {
        try {
            val tz = authSession.user.value?.timezone ?: "UTC"
            val result = repository.list(filter = filter, page = page)
            nextPage = result.nextPage
            _state.update { current ->
                val existing = if (!replace && current is LoadState.Ready) {
                    current.value.items
                } else {
                    emptyList()
                }
                val merged = if (replace) {
                    result.items
                } else {
                    (existing + result.items).distinctBy { itemKey(it) }
                }
                LoadState.Ready(
                    GoalsListUi(filter = filter, items = merged, timeZoneId = tz),
                    isRefreshing = false,
                )
            }
        } catch (e: ApiException) {
            _state.value = LoadState.Error(e.toErrorKind(), canRetry = true)
        } catch (_: Throwable) {
            _state.value = LoadState.Error(ErrorKind.Unknown, canRetry = true)
        }
    }

    private fun prependOrRefresh(created: Goal) {
        val current = _state.value
        if (current is LoadState.Ready && current.value.filter == GoalListFilter.ACTIVE) {
            val item = GoalListItem.Membership(created)
            _state.value = LoadState.Ready(
                current.value.copy(
                    items = listOf(item) + current.value.items.filterNot {
                        itemKey(it) == created.id
                    },
                ),
            )
        } else {
            refresh()
        }
    }

    private fun itemKey(item: GoalListItem): String = when (item) {
        is GoalListItem.Membership -> item.goal.id
        is GoalListItem.Invite -> item.preview.id
    }
}
