package app.promise.android.data.home

import app.promise.android.domain.HomeCommitment
import app.promise.android.domain.HomeFeed
import app.promise.android.domain.HomePractice
import app.promise.android.domain.HomeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@Singleton
class PreviewHomeRepository @Inject constructor() : HomeRepository {
    private val _feed = MutableStateFlow(defaultFeed())
    override fun observeFeed(): Flow<HomeFeed> = _feed.asStateFlow()

    override suspend fun completeCommitment(id: String) {
        _feed.update { feed ->
            feed.copy(
                commitments = feed.commitments.map { c ->
                    if (c.id == id) c.copy(isCompleted = true) else c
                },
            )
        }
    }

    override suspend fun checkInPractice(id: String) {
        _feed.update { feed ->
            feed.copy(
                practices = feed.practices.map { p ->
                    if (p.id == id) p.copy(checkedInToday = true) else p
                },
            )
        }
    }

    override fun loadEmptyScenario() {
        _feed.value = HomeFeed(commitments = emptyList(), practices = emptyList())
    }

    fun resetToDefault() {
        _feed.value = defaultFeed()
    }

    companion object {
        fun defaultFeed(): HomeFeed {
            return HomeFeed(
                commitments = listOf(
                    HomeCommitment(
                        id = "c-overdue",
                        title = "Send weekly update",
                        dueLabel = "Overdue · Mon",
                        isOverdue = true,
                        isDueToday = false,
                    ),
                    HomeCommitment(
                        id = "c-today",
                        title = "Call Mom",
                        dueLabel = "Due today",
                        isOverdue = false,
                        isDueToday = true,
                    ),
                    HomeCommitment(
                        id = "c-upcoming",
                        title = "Review lease",
                        dueLabel = "Due Fri",
                        isOverdue = false,
                        isDueToday = false,
                    ),
                ),
                practices = listOf(
                    HomePractice(
                        id = "p-walk",
                        title = "Morning walk",
                        progressFraction = 0.25f,
                        progressLabel = "1 of 4 this week",
                        streakDays = 0,
                    ),
                    HomePractice(
                        id = "p-read",
                        title = "Read 20 pages",
                        progressFraction = 0.6f,
                        progressLabel = "3 of 5 this week",
                        streakDays = 3,
                    ),
                    HomePractice(
                        id = "p-meditate",
                        title = "Meditate",
                        progressFraction = 0.9f,
                        progressLabel = "6 of 7 this week",
                        streakDays = 12,
                    ),
                ),
            )
        }

        fun sortedOpenCommitments(commitments: List<HomeCommitment>): List<HomeCommitment> {
            return commitments
                .filter { !it.isCompleted }
                .sortedWith(
                    compareByDescending<HomeCommitment> { it.isOverdue }
                        .thenByDescending { it.isDueToday },
                )
        }
    }
}
