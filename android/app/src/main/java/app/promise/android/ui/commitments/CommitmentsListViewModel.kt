package app.promise.android.ui.commitments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.core.toErrorKind
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.DuePrecision
import app.promise.android.ui.haptics.PromiseHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommitmentsListUi(
    val filter: CommitmentListFilter,
    val items: List<Commitment>,
    val timeZoneId: String,
)

@HiltViewModel
class CommitmentsListViewModel @Inject constructor(
    private val repository: CommitmentRepository,
    private val authSession: AuthSession,
    private val haptics: PromiseHaptics,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<CommitmentsListUi>>(LoadState.Loading)
    val state: StateFlow<LoadState<CommitmentsListUi>> = _state.asStateFlow()

    private val _createAction = MutableStateFlow<ActionState>(ActionState.Idle)
    val createAction: StateFlow<ActionState> = _createAction.asStateFlow()

    private var filter: CommitmentListFilter = CommitmentListFilter.OPEN
    private var nextPage: Int? = null

    init {
        refresh()
    }

    fun selectFilter(value: CommitmentListFilter) {
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
                    // Keep Ready visible; silent filter reload (no green spinner).
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

    fun create(
        title: String,
        description: String,
        dueAt: String?,
        duePrecision: DuePrecision,
        onSuccess: (Commitment) -> Unit,
    ) {
        if (_createAction.value is ActionState.InFlight) return
        viewModelScope.launch {
            _createAction.value = ActionState.InFlight
            try {
                val created = repository.create(
                    CreateCommitmentInput(
                        title = title,
                        description = description,
                        dueAt = dueAt,
                        duePrecision = duePrecision,
                    ),
                )
                _createAction.value = ActionState.Idle
                haptics.confirm()
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

    fun clearCreateError() {
        if (_createAction.value is ActionState.Failed) {
            _createAction.value = ActionState.Idle
        }
    }

    private suspend fun loadPage(page: Int, replace: Boolean) {
        try {
            val tz = authSession.user.value?.timezone ?: "UTC"
            val result = repository.list(filter = filter, page = page, timeZoneId = tz)
            nextPage = result.nextPage
            _state.update { current ->
                val existing = if (!replace && current is LoadState.Ready) current.value.items else emptyList()
                val merged = if (replace) result.items else (existing + result.items).distinctBy { it.id }
                LoadState.Ready(
                    CommitmentsListUi(filter = filter, items = merged, timeZoneId = tz),
                    isRefreshing = false,
                )
            }
        } catch (e: ApiException) {
            _state.value = LoadState.Error(e.toErrorKind(), canRetry = true)
        } catch (_: Throwable) {
            _state.value = LoadState.Error(ErrorKind.Unknown, canRetry = true)
        }
    }

    private fun prependOrRefresh(created: Commitment) {
        val current = _state.value
        if (current is LoadState.Ready && current.value.filter == CommitmentListFilter.OPEN) {
            _state.value = LoadState.Ready(
                current.value.copy(items = listOf(created) + current.value.items.filterNot { it.id == created.id }),
            )
        } else {
            filter = CommitmentListFilter.OPEN
            refresh()
        }
    }
}
