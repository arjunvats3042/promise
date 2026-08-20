package app.promise.android.data.goals

import app.promise.android.data.network.toApiException
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInPage
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalPage
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.GoalStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val api: GoalApi,
) : GoalRepository {
    override suspend fun list(filter: GoalListFilter, page: Int): GoalPage {
        return try {
            when (filter) {
                GoalListFilter.ACTIVE -> pageOf(api.list(status = GoalStatus.ACTIVE.name, page = page))
                GoalListFilter.PAUSED -> pageOf(api.list(status = GoalStatus.PAUSED.name, page = page))
                GoalListFilter.COMPLETED -> {
                    val completed = api.list(status = GoalStatus.COMPLETED.name, page = page)
                    val cancelled = api.list(status = GoalStatus.CANCELLED.name, page = page)
                    val items = (completed.results + cancelled.results)
                        .map { it.toDomain() }
                        .sortedByDescending { it.updatedAt }
                    val next = if (completed.next != null || cancelled.next != null) page + 1 else null
                    GoalPage(items = items, nextPage = next)
                }
            }
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun get(id: String): Goal {
        return try {
            api.get(id).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun create(input: CreateGoalInput): Goal {
        return try {
            api.create(
                CreateGoalRequest(
                    title = input.title.trim(),
                    description = input.description,
                    timezone = input.timezone,
                    startDate = input.startDate,
                    endDate = input.endDate,
                    recurrenceKind = input.recurrenceKind.name,
                    weekdays = input.weekdays,
                    periodUnit = input.periodUnit?.name,
                    timesPerPeriod = input.timesPerPeriod,
                    trackingKind = input.trackingKind.name,
                    targetValue = input.targetValue,
                    targetUnit = input.targetUnit,
                ),
            ).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun pause(id: String): Goal = mutateGoal { api.pause(id) }

    override suspend fun resume(id: String): Goal = mutateGoal { api.resume(id) }

    override suspend fun complete(id: String): Goal = mutateGoal { api.complete(id) }

    override suspend fun cancel(id: String): Goal = mutateGoal { api.cancel(id) }

    override suspend fun checkIn(id: String, input: CheckInInput): GoalCheckIn {
        return try {
            api.checkIn(
                id,
                CheckInRequest(
                    status = input.status.name,
                    periodDate = input.periodDate,
                    value = input.value,
                    note = input.note,
                ),
            ).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun listCheckIns(
        id: String,
        startDate: String?,
        endDate: String?,
        page: Int,
    ): GoalCheckInPage {
        return try {
            val response = api.listCheckIns(
                id = id,
                startDate = startDate,
                endDate = endDate,
                page = page,
            )
            GoalCheckInPage(
                items = response.results.map { it.toDomain() },
                nextPage = goalPageNumberFromNext(response.next),
            )
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    private suspend fun mutateGoal(block: suspend () -> GoalDto): Goal {
        return try {
            block().toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    private fun pageOf(response: GoalPageDto): GoalPage {
        return GoalPage(
            items = response.results.map { it.toDomain() },
            nextPage = goalPageNumberFromNext(response.next),
        )
    }
}
