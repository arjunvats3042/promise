package app.promise.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.core.ErrorKind
import app.promise.android.core.LoadState
import app.promise.android.core.toErrorKind
import app.promise.android.data.home.HomeFreshness
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.HomeCommitment
import app.promise.android.domain.HomePractice
import app.promise.android.domain.HomeRepository
import app.promise.android.ui.auth.GreetingClock
import app.promise.android.ui.commitments.CommitmentTime
import app.promise.android.ui.haptics.PromiseHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import app.promise.android.core.events.AppEventBus
import app.promise.android.core.events.AppMutationEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

data class HomeUiModel(
    val greeting: String,
    val userName: String,
    val dateLabel: String,
    val initials: String,
    val commitments: List<HomeCommitment>,
    val practices: List<HomePractice>,
    val commitmentsError: ErrorKind? = null,
    val practicesError: ErrorKind? = null,
    val emailVerified: Boolean = true,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val authSession: AuthSession,
    private val homeFreshness: HomeFreshness,
    private val haptics: PromiseHaptics,
    private val appEventBus: AppEventBus,
) : ViewModel() {
    private val _state = MutableStateFlow<LoadState<HomeUiModel>>(LoadState.Loading)
    val state: StateFlow<LoadState<HomeUiModel>> = _state.asStateFlow()

    private val loadMutex = Mutex()

    init {
        refresh(force = true)
        observeEvents()
    }

    private fun observeEvents() {
        viewModelScope.launch {
            appEventBus.events
                .debounce(50L)
                .collect { event ->
                    when (event) {
                        is AppMutationEvent.GoalCreated,
                        is AppMutationEvent.GoalUpdated,
                        is AppMutationEvent.GoalCheckedIn,
                        is AppMutationEvent.GoalPaused,
                        is AppMutationEvent.GoalResumed,
                        is AppMutationEvent.GoalCompleted,
                        is AppMutationEvent.GoalCancelled,
                        is AppMutationEvent.SharedGoalMembershipChanged,
                        is AppMutationEvent.CommitmentCreated,
                        is AppMutationEvent.CommitmentUpdated,
                        is AppMutationEvent.CommitmentCompleted,
                        is AppMutationEvent.CommitmentCancelled,
                        is AppMutationEvent.CommitmentSnoozed,
                        is AppMutationEvent.CommitmentWaitChanged -> {
                            homeFreshness.markDirty()
                            refresh(force = true, fromPull = false)
                        }
                        else -> Unit
                    }
                }
        }
    }

    fun onVisible() {
        if (!homeFreshness.shouldRefresh()) return
        val current = _state.value
        val fromPull = current is LoadState.Ready
        refresh(force = true, fromPull = fromPull)
    }

    fun refresh(fromPull: Boolean = false, force: Boolean = true) {
        viewModelScope.launch {
            loadMutex.withLock {
                if (!force && !homeFreshness.shouldRefresh()) return@withLock
                val current = _state.value
                when {
                    current is LoadState.Ready && fromPull -> {
                        _state.value = current.copy(isRefreshing = true)
                    }
                    current is LoadState.Ready && !fromPull -> {
                        // keep Ready while silent refresh
                    }
                    else -> {
                        _state.value = LoadState.Loading
                    }
                }
                loadFeed()
            }
        }
    }

    fun retry() {
        refresh(force = true)
    }

    fun retryCommitments() {
        refresh(force = true)
    }

    fun retryPractices() {
        refresh(force = true)
    }

    fun completeCommitment(id: String) {
        viewModelScope.launch {
            try {
                homeRepository.completeCommitment(id)
                haptics.confirm()
                homeFreshness.markDirty()
                loadMutex.withLock { loadFeed() }
                appEventBus.emit(AppMutationEvent.CommitmentCompleted(id))
            } catch (_: ApiException) {
                haptics.error()
                loadMutex.withLock { loadFeed() }
            } catch (_: Throwable) {
                haptics.error()
            }
        }
    }

    fun checkInPractice(id: String, input: CheckInInput = CheckInInput(status = GoalCheckInStatus.COMPLETED)) {
        viewModelScope.launch {
            try {
                homeRepository.checkInPractice(id, input)
                haptics.confirm()
                homeFreshness.markDirty()
                loadMutex.withLock { loadFeed() }
                appEventBus.emit(AppMutationEvent.GoalCheckedIn(id))
            } catch (_: ApiException) {
                haptics.error()
                loadMutex.withLock { loadFeed() }
            } catch (_: Throwable) {
                haptics.error()
            }
        }
    }

    private suspend fun loadFeed() {
        val user = authSession.user.value
        val timeZoneId = user?.timezone?.takeIf { it.isNotBlank() } ?: "UTC"
        val name = user?.name?.takeIf { it.isNotBlank() } ?: "there"
        val zone = CommitmentTime.zone(timeZoneId)
        val now = ZonedDateTime.now(zone)
        try {
            val feed = homeRepository.loadFeed(timeZoneId)
            _state.value = LoadState.Ready(
                HomeUiModel(
                    greeting = greetingForNow(now.hour),
                    userName = name,
                    dateLabel = dateLabelFor(now.toLocalDate()),
                    initials = initialsFor(name),
                    commitments = feed.commitments,
                    practices = feed.practices,
                    commitmentsError = feed.commitmentsError,
                    practicesError = feed.practicesError,
                    emailVerified = authSession.user.value?.emailVerified ?: true,
                ),
                isRefreshing = false,
            )
            homeFreshness.markSuccessfulLoad()
        } catch (e: ApiException) {
            _state.value = LoadState.Error(e.toErrorKind(), canRetry = true)
        } catch (_: Throwable) {
            _state.value = LoadState.Error(ErrorKind.Unknown, canRetry = true)
        }
    }

    companion object {
        fun greetingForNow(hour: Int = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)): String {
            return GreetingClock.greetingForHour(hour)
        }

        fun dateLabelFor(date: LocalDate = LocalDate.now(), locale: Locale = Locale.getDefault()): String {
            return date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
        }

        fun initialsFor(name: String): String {
            val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() || name == "there" -> "?"
                parts.size == 1 -> parts[0].take(1).uppercase()
                else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            }
        }
    }
}
