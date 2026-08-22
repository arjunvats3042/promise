package app.promise.android.data.goals

import app.promise.android.data.network.ApiException
import app.promise.android.data.network.toApiException
import app.promise.android.domain.ChatMessage
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalActivityItem
import app.promise.android.domain.GoalChatReadState
import app.promise.android.domain.GoalChatSummary
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInPage
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalPage
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.GoalStatus
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val api: GoalApi,
) : GoalRepository {
    override suspend fun list(filter: GoalListFilter, page: Int, pageSize: Int): GoalPage {
        return try {
            when (filter) {
                GoalListFilter.ACTIVE -> {
                    val response = api.list(
                        status = GoalStatus.ACTIVE.name,
                        page = page,
                        pageSize = pageSize,
                    )
                    GoalPage(
                        items = pinInvites(response.results.map { it.toListItem() }),
                        nextPage = goalPageNumberFromNext(response.next),
                    )
                }
                GoalListFilter.PAUSED -> pageOf(
                    api.list(status = GoalStatus.PAUSED.name, page = page, pageSize = pageSize),
                )
                GoalListFilter.COMPLETED -> {
                    val completed = api.list(
                        status = GoalStatus.COMPLETED.name,
                        page = page,
                        pageSize = pageSize,
                    )
                    val cancelled = api.list(
                        status = GoalStatus.CANCELLED.name,
                        page = page,
                        pageSize = pageSize,
                    )
                    val items = (completed.results + cancelled.results)
                        .filter { !it.isInvitePreview() }
                        .map { GoalListItem.Membership(it.toDomain()) }
                        .sortedByDescending { it.goal.updatedAt }
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
            when (val detail = getDetailInternal(id)) {
                is GoalDetail.Full -> detail.goal
                is GoalDetail.Invite -> throw ApiException(
                    status = 404,
                    code = "GOAL_NOT_FOUND",
                )
            }
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun getDetail(id: String): GoalDetail {
        return try {
            getDetailInternal(id)
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
                    isShared = input.isShared,
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

    override suspend fun inviteParticipant(goalId: String, userId: String): GoalParticipant {
        return try {
            api.inviteParticipant(goalId, InviteParticipantRequest(userId = userId)).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun listParticipants(goalId: String): List<GoalParticipant> {
        return try {
            api.listParticipants(goalId).map { it.toDomain() }
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun acceptInvitation(goalId: String): GoalDetail {
        return try {
            api.acceptInvitation(goalId).toDetail()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun declineInvitation(goalId: String): GoalParticipant {
        return try {
            api.declineInvitation(goalId).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun removeParticipant(goalId: String, userId: String): GoalParticipant {
        return try {
            api.removeParticipant(goalId, userId).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun leave(goalId: String): GoalParticipant {
        return try {
            api.leave(goalId).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun listChatMessages(
        goalId: String,
        limit: Int,
        beforeCreatedAt: String?,
        beforeId: String?,
    ): List<ChatMessage> {
        return try {
            api.listChatMessages(
                id = goalId,
                limit = limit,
                beforeCreatedAt = beforeCreatedAt,
                beforeId = beforeId,
            ).map { it.toDomain() }
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun sendChatMessage(goalId: String, body: String): ChatMessage {
        return try {
            api.sendChatMessage(goalId, SendChatMessageRequest(body = body)).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun markChatRead(goalId: String, lastReadMessageId: String): GoalChatReadState {
        return try {
            api.markChatRead(goalId, MarkChatReadRequest(lastReadMessageId = lastReadMessageId)).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun getChatSummary(goalId: String): GoalChatSummary {
        return try {
            api.getChatSummary(goalId).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun listActivity(
        goalId: String,
        limit: Int,
        beforeCreatedAt: String?,
        beforeId: String?,
    ): List<GoalActivityItem> {
        return try {
            api.listActivity(
                id = goalId,
                limit = limit,
                beforeCreatedAt = beforeCreatedAt,
                beforeId = beforeId,
            ).map { it.toDomain() }
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    private suspend fun getDetailInternal(id: String): GoalDetail {
        return api.get(id).toDetail()
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
            items = response.results.map { it.toListItem() },
            nextPage = goalPageNumberFromNext(response.next),
        )
    }
}
