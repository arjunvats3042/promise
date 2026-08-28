package app.promise.android.data.goals

import app.promise.android.data.local.db.GoalDao
import app.promise.android.data.local.db.GoalEntity
import app.promise.android.data.local.db.OutboxDao
import app.promise.android.data.local.db.OutboxEntity
import app.promise.android.data.network.ApiException
import app.promise.android.data.network.toApiException
import app.promise.android.data.sync.SyncManager
import app.promise.android.domain.ChatMessage
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalActivityItem
import app.promise.android.domain.GoalChatReadState
import app.promise.android.domain.GoalChatSummary
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInPage
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalPage
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalPeriodCounts
import app.promise.android.domain.GoalProgress
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class GoalRepositoryImpl @Inject constructor(
    private val api: GoalApi,
    private val goalDao: GoalDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val networkMonitor: app.promise.android.core.NetworkMonitor,
) : GoalRepository {

    override suspend fun list(filter: GoalListFilter, page: Int, pageSize: Int): GoalPage {
        if (!networkMonitor.isOnline.value) {
            val localEntities = when (filter) {
                GoalListFilter.ACTIVE -> goalDao.observeActive().firstOrNull().orEmpty()
                GoalListFilter.PAUSED -> goalDao.observeAll().firstOrNull().orEmpty().filter { it.status == GoalStatus.PAUSED.name }
                GoalListFilter.COMPLETED -> goalDao.observeAll().firstOrNull().orEmpty().filter {
                    it.status == GoalStatus.COMPLETED.name || it.status == GoalStatus.CANCELLED.name
                }
            }
            val listItems = localEntities.map { GoalListItem.Membership(it.toDomain()) }
            return GoalPage(items = listItems, nextPage = null)
        }
        return try {
            when (filter) {
                GoalListFilter.ACTIVE -> {
                    val response = api.list(
                        status = GoalStatus.ACTIVE.name,
                        page = page,
                        pageSize = pageSize,
                    )
                    val listItems = pinInvites(response.results.map { it.toListItem() })
                    val domainGoals = listItems.mapNotNull { (it as? GoalListItem.Membership)?.goal }
                    goalDao.upsertAll(domainGoals.map { it.toEntity(isSynced = true) })
                    GoalPage(
                        items = listItems,
                        nextPage = goalPageNumberFromNext(response.next),
                    )
                }
                GoalListFilter.PAUSED -> {
                    val res = pageOf(
                        api.list(status = GoalStatus.PAUSED.name, page = page, pageSize = pageSize),
                    )
                    val domainGoals = res.items.mapNotNull { (it as? GoalListItem.Membership)?.goal }
                    goalDao.upsertAll(domainGoals.map { it.toEntity(isSynced = true) })
                    res
                }
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
                    val domainGoals = items.map { it.goal }
                    goalDao.upsertAll(domainGoals.map { it.toEntity(isSynced = true) })
                    val next = if (completed.next != null || cancelled.next != null) page + 1 else null
                    GoalPage(items = items, nextPage = next)
                }
            }
        } catch (t: Throwable) {
            // Offline fallback: load from local Room DB
            val localEntities = when (filter) {
                GoalListFilter.ACTIVE -> goalDao.observeActive().firstOrNull().orEmpty()
                GoalListFilter.PAUSED -> goalDao.observeAll().firstOrNull().orEmpty().filter { it.status == GoalStatus.PAUSED.name }
                GoalListFilter.COMPLETED -> goalDao.observeAll().firstOrNull().orEmpty().filter {
                    it.status == GoalStatus.COMPLETED.name || it.status == GoalStatus.CANCELLED.name
                }
            }
            val listItems = localEntities.map { GoalListItem.Membership(it.toDomain()) }
            GoalPage(items = listItems, nextPage = null)
        }
    }

    override suspend fun get(id: String): Goal {
        return try {
            when (val detail = getDetailInternal(id)) {
                is GoalDetail.Full -> {
                    goalDao.upsert(detail.goal.toEntity(isSynced = true))
                    detail.goal
                }
                is GoalDetail.Invite -> throw ApiException(
                    status = 404,
                    code = "GOAL_NOT_FOUND",
                )
            }
        } catch (t: Throwable) {
            val local = goalDao.getById(id)
            local?.toDomain() ?: throw t.toApiException()
        }
    }

    override suspend fun getDetail(id: String): GoalDetail {
        return try {
            getDetailInternal(id)
        } catch (t: Throwable) {
            val local = goalDao.getById(id)?.toDomain()
            if (local != null) {
                GoalDetail.Full(local)
            } else {
                throw t.toApiException()
            }
        }
    }

    override suspend fun create(input: CreateGoalInput): Goal {
        val actionId = UUID.randomUUID().toString()
        val localId = actionId
        val nowIso = Instant.now().toString()

        val optimisticGoal = Goal(
            id = localId,
            title = input.title.trim(),
            description = input.description,
            status = GoalStatus.ACTIVE,
            timezone = input.timezone ?: "Asia/Kolkata",
            startDate = input.startDate ?: "",
            endDate = input.endDate,
            recurrenceKind = input.recurrenceKind,
            weekdays = input.weekdays.orEmpty(),
            periodUnit = input.periodUnit,
            timesPerPeriod = input.timesPerPeriod,
            trackingKind = input.trackingKind,
            targetValue = input.targetValue,
            targetUnit = input.targetUnit,
            source = "MANUAL",
            pausedAt = null,
            completedAt = null,
            cancelledAt = null,
            createdAt = nowIso,
            updatedAt = nowIso,
            isEnded = false,
            progress = GoalProgress(
                currentPeriod = GoalPeriodCounts(required = 1, completed = 0),
                weekProgress = GoalPeriodCounts(required = 1, completed = 0),
                consistencyPercent = 0,
            ),
            currentStreak = 0,
            isSharedField = input.isShared,
        )

        goalDao.upsert(optimisticGoal.toEntity(isSynced = false))

        val payload = buildJsonObject {
            put("title", input.title.trim())
            put("description", input.description)
            put("recurrence_kind", input.recurrenceKind.name)
            put("tracking_kind", input.trackingKind.name)
            input.targetValue?.let { put("target_value", it) }
            put("target_unit", input.targetUnit)
        }.toString()

        outboxDao.enqueue(
            OutboxEntity(
                actionId = actionId,
                actionType = "CREATE_GOAL",
                entityId = localId,
                payloadJson = payload,
                clientTimestampIso = nowIso,
            ),
        )

        syncManager.enqueueSync()

        if (!networkMonitor.isOnline.value) {
            return optimisticGoal
        }

        return try {
            val remote = api.create(
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
            goalDao.upsert(remote.toEntity(isSynced = true))
            outboxDao.delete(actionId)
            remote
        } catch (_: Throwable) {
            optimisticGoal
        }
    }

    override suspend fun pause(id: String): Goal = mutateGoal { api.pause(id) }

    override suspend fun resume(id: String): Goal = mutateGoal { api.resume(id) }

    override suspend fun complete(id: String): Goal = mutateGoal { api.complete(id) }

    override suspend fun cancel(id: String): Goal = mutateGoal { api.cancel(id) }

    override suspend fun checkIn(id: String, input: CheckInInput): GoalCheckIn {
        val actionId = UUID.randomUUID().toString()
        val nowIso = Instant.now().toString()

        goalDao.recordCheckIn(id)

        val payload = buildJsonObject {
            put("status", input.status.name)
            input.value?.let { put("value", it) }
            if (input.note.isNotBlank()) {
                put("note", input.note)
            }
        }.toString()

        outboxDao.enqueue(
            OutboxEntity(
                actionId = actionId,
                actionType = "CHECK_IN_GOAL",
                entityId = id,
                payloadJson = payload,
                clientTimestampIso = nowIso,
            ),
        )

        syncManager.enqueueSync()

        if (!networkMonitor.isOnline.value) {
            return GoalCheckIn(
                id = actionId,
                periodDate = input.periodDate ?: "",
                status = input.status,
                value = input.value,
                note = input.note,
                checkedAt = nowIso,
                createdAt = nowIso,
                updatedAt = nowIso,
            )
        }

        return try {
            val remote = api.checkIn(
                id,
                CheckInRequest(
                    status = input.status.name,
                    periodDate = input.periodDate,
                    value = input.value,
                    note = input.note,
                ),
            ).toDomain()
            outboxDao.delete(actionId)
            remote
        } catch (_: Throwable) {
            GoalCheckIn(
                id = actionId,
                periodDate = input.periodDate ?: "",
                status = input.status,
                value = input.value,
                note = input.note,
                checkedAt = nowIso,
                createdAt = nowIso,
                updatedAt = nowIso,
            )
        }
    }

    override suspend fun convertToShared(goalId: String): GoalDetail {
        return try {
            api.convertToShared(goalId).toDetail()
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

    override suspend fun reinviteParticipant(
        goalId: String,
        participantId: String?,
        userId: String?,
    ): GoalParticipant {
        return try {
            api.reinviteParticipant(
                goalId,
                ReinviteParticipantRequest(participantId = participantId, userId = userId),
            ).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun transferOwnership(
        goalId: String,
        participantId: String?,
        userId: String?,
    ): GoalDetail {
        return try {
            api.transferOwnership(
                goalId,
                TransferOwnershipRequest(participantId = participantId, userId = userId),
            ).toDetail()
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

    override suspend fun searchChatMessages(
        goalId: String,
        query: String,
        limit: Int,
    ): List<app.promise.android.domain.ChatSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()
        return try {
            val res = api.searchChatMessages(
                id = goalId,
                query = cleanQuery,
                limit = limit,
            )
            res.results.map { it.toDomain() }
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    private suspend fun getDetailInternal(id: String): GoalDetail {
        return api.get(id).toDetail()
    }

    private suspend fun mutateGoal(block: suspend () -> GoalDto): Goal {
        return try {
            val res = block().toDomain()
            goalDao.upsert(res.toEntity(isSynced = true))
            res
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

    private fun Goal.toEntity(isSynced: Boolean): GoalEntity {
        val checkedIn = progress.currentPeriod.required > 0 && progress.currentPeriod.completed >= progress.currentPeriod.required
        return GoalEntity(
            id = id,
            title = title,
            description = description,
            recurrenceKind = recurrenceKind.name,
            weekdaysJson = weekdays.toString(),
            trackingKind = trackingKind.name,
            targetValue = targetValue,
            targetUnit = targetUnit,
            status = status.name,
            currentStreak = currentStreak,
            longestStreak = currentStreak,
            checkedInToday = checkedIn,
            periodValue = progress.currentPeriod.value ?: 0,
            isShared = isSharedField,
            unreadChatCount = unreadChatCount,
            isSynced = isSynced,
        )
    }

    private fun GoalEntity.toDomain(): Goal {
        val recKind = runCatching { GoalRecurrenceKind.valueOf(recurrenceKind) }.getOrDefault(GoalRecurrenceKind.DAILY)
        val trkKind = runCatching { GoalTrackingKind.valueOf(trackingKind) }.getOrDefault(GoalTrackingKind.BINARY)
        val goalStatus = runCatching { GoalStatus.valueOf(status) }.getOrDefault(GoalStatus.ACTIVE)
        val completedCount = if (checkedInToday) 1 else 0
        return Goal(
            id = id,
            title = title,
            description = description,
            status = goalStatus,
            timezone = "Asia/Kolkata",
            startDate = "",
            endDate = null,
            recurrenceKind = recKind,
            weekdays = emptyList(),
            periodUnit = null,
            timesPerPeriod = null,
            trackingKind = trkKind,
            targetValue = targetValue,
            targetUnit = targetUnit,
            source = "MANUAL",
            pausedAt = null,
            completedAt = null,
            cancelledAt = null,
            createdAt = "",
            updatedAt = "",
            isEnded = false,
            progress = GoalProgress(
                currentPeriod = GoalPeriodCounts(required = 1, completed = completedCount, value = periodValue),
                weekProgress = GoalPeriodCounts(required = 1, completed = completedCount),
                consistencyPercent = 0,
            ),
            currentStreak = currentStreak,
            isSharedField = isShared,
            unreadChatCount = unreadChatCount,
            latestChatMessage = null,
        )
    }
}
