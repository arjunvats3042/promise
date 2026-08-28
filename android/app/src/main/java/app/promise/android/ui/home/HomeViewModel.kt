package app.promise.android.ui.home

import android.content.Context
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

import app.promise.android.domain.AiRepository
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.DailyMotivationQuote
import app.promise.android.domain.DuePrecision
import app.promise.android.domain.ParsedThoughtItem
import app.promise.android.domain.WeeklyAiInsights

sealed class DailyMotivationUiState {
    data object Loading : DailyMotivationUiState()
    data class Success(val quote: DailyMotivationQuote) : DailyMotivationUiState()
    data class Error(val message: String? = null) : DailyMotivationUiState()
}

sealed class WeeklyInsightsUiState {
    data object Loading : WeeklyInsightsUiState()
    data class Success(val insights: WeeklyAiInsights) : WeeklyInsightsUiState()
    data class Error(val message: String? = null) : WeeklyInsightsUiState()
}

data class HomeUiModel(
    val greeting: String,
    val userName: String,
    val dateLabel: String,
    val initials: String,
    val commitments: List<HomeCommitment>,
    val practices: List<HomePractice>,
    val pendingInvites: List<app.promise.android.domain.GoalInvitePreview> = emptyList(),
    val commitmentsError: ErrorKind? = null,
    val practicesError: ErrorKind? = null,
    val emailVerified: Boolean = true,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val homeRepository: HomeRepository,
    private val authSession: AuthSession,
    private val homeFreshness: HomeFreshness,
    private val haptics: PromiseHaptics,
    private val appEventBus: AppEventBus,
    private val aiRepository: AiRepository,
    private val goalRepository: app.promise.android.domain.GoalRepository,
    private val commitmentRepository: app.promise.android.domain.CommitmentRepository,
    private val profilePhotoStore: app.promise.android.data.local.ProfilePhotoStore,
) : ViewModel() {
    val photoUri: StateFlow<String?> = profilePhotoStore.photoUri
    private val _state = MutableStateFlow<LoadState<HomeUiModel>>(LoadState.Loading)
    val state: StateFlow<LoadState<HomeUiModel>> = _state.asStateFlow()

    private val _weeklyInsightsState = MutableStateFlow<WeeklyInsightsUiState>(WeeklyInsightsUiState.Loading)
    val weeklyInsightsState: StateFlow<WeeklyInsightsUiState> = _weeklyInsightsState.asStateFlow()

    private val _dailyMotivationState = MutableStateFlow<DailyMotivationUiState>(DailyMotivationUiState.Loading)
    val dailyMotivationState: StateFlow<DailyMotivationUiState> = _dailyMotivationState.asStateFlow()

    private val loadMutex = Mutex()

    suspend fun parseThought(thought: String, timezone: String = "Asia/Kolkata"): List<ParsedThoughtItem> {
        return aiRepository.parseThought(thought, timezone)
    }

    fun createCommitmentFromThought(input: CreateCommitmentInput) {
        viewModelScope.launch {
            try {
                commitmentRepository.create(input)
                haptics.confirm()
                homeFreshness.markDirty()
                refresh(force = true)
            } catch (_: Throwable) {
                haptics.error()
            }
        }
    }

    fun createGoalFromThought(input: CreateGoalInput) {
        viewModelScope.launch {
            try {
                goalRepository.create(input)
                haptics.confirm()
                homeFreshness.markDirty()
                refresh(force = true)
            } catch (_: Throwable) {
                haptics.error()
            }
        }
    }

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

    fun acceptInvitation(goalId: String) {
        viewModelScope.launch {
            try {
                goalRepository.acceptInvitation(goalId)
                haptics.confirm()
                homeFreshness.markDirty()
                loadMutex.withLock { loadFeed() }
                appEventBus.emit(AppMutationEvent.InvitationUpdated(goalId))
            } catch (_: Throwable) {
                haptics.error()
            }
        }
    }

    fun declineInvitation(goalId: String) {
        viewModelScope.launch {
            try {
                goalRepository.declineInvitation(goalId)
                haptics.confirm()
                homeFreshness.markDirty()
                loadMutex.withLock { loadFeed() }
                appEventBus.emit(AppMutationEvent.InvitationUpdated(goalId))
            } catch (_: Throwable) {
                haptics.error()
            }
        }
    }

    private suspend fun loadFeed() {
        val user = authSession.user.value
        val timeZoneId = user?.timezone?.takeIf { it.isNotBlank() } ?: "Asia/Kolkata"
        val name = user?.name?.takeIf { it.isNotBlank() } ?: "there"
        val zone = CommitmentTime.zone(timeZoneId)
        val now = ZonedDateTime.now(zone)

        // Asynchronously load Daily Motivation quote
        viewModelScope.launch {
            try {
                val quote = aiRepository.getDailyMotivation()
                _dailyMotivationState.value = DailyMotivationUiState.Success(quote)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                _dailyMotivationState.value = DailyMotivationUiState.Error("Daily motivation unavailable.")
            }
        }

        // Asynchronously load AI weekly insights; failures never break home screen
        viewModelScope.launch {
            try {
                val insights = aiRepository.getWeeklyInsights()
                _weeklyInsightsState.value = WeeklyInsightsUiState.Success(insights)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                _weeklyInsightsState.value = WeeklyInsightsUiState.Error("AI insights unavailable.")
            }
        }

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
                    pendingInvites = feed.pendingInvites,
                    commitmentsError = feed.commitmentsError,
                    practicesError = feed.practicesError,
                    emailVerified = authSession.user.value?.emailVerified ?: true,
                ),
                isRefreshing = false,
            )
            homeFreshness.markSuccessfulLoad()
            viewModelScope.launch {
                runCatching {
                    app.promise.android.widget.PromiseWidgetUpdater.updateWidget(context, feed.commitments, feed.practices)
                }
            }
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
