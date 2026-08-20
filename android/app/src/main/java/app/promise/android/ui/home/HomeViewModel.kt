package app.promise.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.promise.android.core.LoadState
import app.promise.android.data.home.PreviewHomeRepository
import app.promise.android.data.network.AuthSession
import app.promise.android.domain.HomeCommitment
import app.promise.android.domain.HomePractice
import app.promise.android.domain.HomeRepository
import app.promise.android.ui.auth.GreetingClock
import app.promise.android.ui.haptics.PromiseHaptics
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiModel(
    val greeting: String,
    val userName: String,
    val dateLabel: String,
    val initials: String,
    val commitments: List<HomeCommitment>,
    val practices: List<HomePractice>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    authSession: AuthSession,
    private val haptics: PromiseHaptics,
) : ViewModel() {
    val state: StateFlow<LoadState<HomeUiModel>> = combine(
        homeRepository.observeFeed(),
        authSession.user,
    ) { feed, user ->
        val name = user?.name?.takeIf { it.isNotBlank() } ?: "there"
        LoadState.Ready(
            HomeUiModel(
                greeting = greetingForNow(),
                userName = name,
                dateLabel = dateLabelFor(),
                initials = initialsFor(name),
                commitments = PreviewHomeRepository.sortedOpenCommitments(feed.commitments),
                practices = feed.practices,
            ),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LoadState.Loading,
    )

    fun completeCommitment(id: String) {
        viewModelScope.launch {
            homeRepository.completeCommitment(id)
            haptics.confirm()
        }
    }

    fun checkInPractice(id: String) {
        viewModelScope.launch {
            homeRepository.checkInPractice(id)
            haptics.confirm()
        }
    }

    fun retry() {
        // Preview feed is local; keep a no-op seam for LoadState.Error UI wiring.
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
