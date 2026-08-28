package app.promise.android.data.commitments

import app.promise.android.data.local.db.CommitmentDao
import app.promise.android.data.local.db.CommitmentEntity
import app.promise.android.data.local.db.OutboxDao
import app.promise.android.data.local.db.OutboxEntity
import app.promise.android.data.network.toApiException
import app.promise.android.data.sync.SyncManager
import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentPage
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.CommitmentStatus
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.DuePrecision
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Singleton
class CommitmentRepositoryImpl @Inject constructor(
    private val api: CommitmentApi,
    private val commitmentDao: CommitmentDao,
    private val outboxDao: OutboxDao,
    private val syncManager: SyncManager,
    private val networkMonitor: app.promise.android.core.NetworkMonitor,
) : CommitmentRepository {

    override suspend fun list(
        filter: CommitmentListFilter,
        page: Int,
        timeZoneId: String,
        pageSize: Int,
    ): CommitmentPage {
        if (!networkMonitor.isOnline.value) {
            val localEntities = when (filter) {
                CommitmentListFilter.DONE -> commitmentDao.observeCompleted().firstOrNull().orEmpty()
                else -> commitmentDao.observeOpen().firstOrNull().orEmpty()
            }
            val domainItems = localEntities.map { it.toDomain() }
            val filtered = when (filter) {
                CommitmentListFilter.OPEN -> domainItems
                CommitmentListFilter.OVERDUE -> domainItems.filter { it.isOverdue }
                CommitmentListFilter.TODAY, CommitmentListFilter.UPCOMING -> {
                    CommitmentListBucketing.filterClientSide(filter, domainItems, timeZoneId)
                }
                CommitmentListFilter.DONE -> domainItems
            }
            return CommitmentPage(items = filtered, nextPage = null)
        }
        return try {
            when (filter) {
                CommitmentListFilter.OPEN -> {
                    val remote = listOpenMerged(page, pageSize)
                    // Cache remote items to local database
                    val entities = remote.items.map { it.toEntity(isSynced = true) }
                    commitmentDao.upsertAll(entities)
                    remote
                }
                CommitmentListFilter.OVERDUE -> {
                    val response = api.list(isOverdue = true, page = page, pageSize = pageSize)
                    val domainItems = CommitmentListBucketing.sortOpen(response.results.map { it.toDomain() })
                    commitmentDao.upsertAll(domainItems.map { it.toEntity(isSynced = true) })
                    CommitmentPage(
                        items = domainItems,
                        nextPage = pageNumberFromNext(response.next),
                    )
                }
                CommitmentListFilter.TODAY,
                CommitmentListFilter.UPCOMING,
                -> {
                    val open = listOpenMerged(page = 1, pageSize = 100)
                    commitmentDao.upsertAll(open.items.map { it.toEntity(isSynced = true) })
                    val filtered = CommitmentListBucketing.filterClientSide(
                        filter = filter,
                        items = open.items,
                        timeZoneId = timeZoneId,
                    )
                    CommitmentPage(items = filtered, nextPage = null)
                }
                CommitmentListFilter.DONE -> {
                    val remote = listDoneMerged(page)
                    commitmentDao.upsertAll(remote.items.map { it.toEntity(isSynced = true) })
                    remote
                }
            }
        } catch (t: Throwable) {
            // Offline fallback: load from local Room DB
            val localEntities = when (filter) {
                CommitmentListFilter.DONE -> commitmentDao.observeCompleted().firstOrNull().orEmpty()
                else -> commitmentDao.observeOpen().firstOrNull().orEmpty()
            }
            val domainItems = localEntities.map { it.toDomain() }
            val filtered = when (filter) {
                CommitmentListFilter.OPEN -> domainItems
                CommitmentListFilter.OVERDUE -> domainItems.filter { it.isOverdue }
                CommitmentListFilter.TODAY, CommitmentListFilter.UPCOMING -> {
                    CommitmentListBucketing.filterClientSide(filter, domainItems, timeZoneId)
                }
                CommitmentListFilter.DONE -> domainItems
            }
            CommitmentPage(items = filtered, nextPage = null)
        }
    }

    override suspend fun get(id: String): Commitment {
        return try {
            val remote = api.get(id).toDomain()
            commitmentDao.upsert(remote.toEntity(isSynced = true))
            remote
        } catch (t: Throwable) {
            val local = commitmentDao.getById(id)
            local?.toDomain() ?: throw t.toApiException()
        }
    }

    override suspend fun create(input: CreateCommitmentInput): Commitment {
        val actionId = UUID.randomUUID().toString()
        val localId = actionId
        val nowIso = Instant.now().toString()

        val optimisticCommitment = Commitment(
            id = localId,
            title = input.title.trim(),
            description = input.description,
            status = CommitmentStatus.PENDING,
            dueAt = input.dueAt,
            duePrecision = input.duePrecision,
            source = "MANUAL",
            snoozedUntil = null,
            completedAt = null,
            cancelledAt = null,
            createdAt = nowIso,
            updatedAt = nowIso,
            isOverdue = false,
        )

        // Save optimistic record locally
        commitmentDao.upsert(optimisticCommitment.toEntity(isSynced = false))

        val payload = buildJsonObject {
            put("title", input.title.trim())
            put("description", input.description)
            input.dueAt?.let { put("due_at", it) }
            put("due_precision", input.duePrecision.name)
        }.toString()

        outboxDao.enqueue(
            OutboxEntity(
                actionId = actionId,
                actionType = "CREATE_COMMITMENT",
                entityId = localId,
                payloadJson = payload,
                clientTimestampIso = nowIso,
            ),
        )

        syncManager.enqueueSync()

        if (!networkMonitor.isOnline.value) {
            return optimisticCommitment
        }

        return try {
            val remote = api.create(
                CreateCommitmentRequest(
                    title = input.title.trim(),
                    description = input.description,
                    dueAt = input.dueAt,
                    duePrecision = input.duePrecision.name,
                ),
            ).toDomain()
            commitmentDao.upsert(remote.toEntity(isSynced = true))
            outboxDao.delete(actionId)
            remote
        } catch (_: Throwable) {
            optimisticCommitment
        }
    }

    override suspend fun complete(id: String): Commitment {
        val actionId = UUID.randomUUID().toString()
        val nowIso = Instant.now().toString()

        commitmentDao.markCompleted(id, completedAt = nowIso)

        outboxDao.enqueue(
            OutboxEntity(
                actionId = actionId,
                actionType = "COMPLETE_COMMITMENT",
                entityId = id,
                payloadJson = "{}",
                clientTimestampIso = nowIso,
            ),
        )

        syncManager.enqueueSync()

        if (!networkMonitor.isOnline.value) {
            val local = commitmentDao.getById(id)?.toDomain()
            return local ?: Commitment(
                id = id,
                title = "",
                description = "",
                status = CommitmentStatus.COMPLETED,
                dueAt = null,
                duePrecision = DuePrecision.NONE,
                source = "MANUAL",
                snoozedUntil = null,
                completedAt = nowIso,
                cancelledAt = null,
                createdAt = nowIso,
                updatedAt = nowIso,
                isOverdue = false,
            )
        }

        return try {
            val remote = api.complete(id).toDomain()
            commitmentDao.upsert(remote.toEntity(isSynced = true))
            outboxDao.delete(actionId)
            remote
        } catch (_: Throwable) {
            val local = commitmentDao.getById(id)?.toDomain()
            local ?: Commitment(
                id = id,
                title = "",
                description = "",
                status = CommitmentStatus.COMPLETED,
                dueAt = null,
                duePrecision = DuePrecision.NONE,
                source = "MANUAL",
                snoozedUntil = null,
                completedAt = nowIso,
                cancelledAt = null,
                createdAt = nowIso,
                updatedAt = nowIso,
                isOverdue = false,
            )
        }
    }

    override suspend fun snooze(id: String, snoozedUntilIso: String): Commitment {
        return mutate { api.snooze(id, SnoozeRequest(snoozedUntilIso)) }
    }

    override suspend fun unsnooze(id: String): Commitment {
        return mutate { api.unsnooze(id) }
    }

    override suspend fun wait(id: String): Commitment {
        return mutate { api.wait(id) }
    }

    override suspend fun cancel(id: String): Commitment {
        return mutate { api.cancel(id) }
    }

    private suspend fun mutate(block: suspend () -> CommitmentDto): Commitment {
        return try {
            val res = block().toDomain()
            commitmentDao.upsert(res.toEntity(isSynced = true))
            res
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    private suspend fun listOpenMerged(page: Int, pageSize: Int): CommitmentPage {
        val pending = api.list(status = CommitmentStatus.PENDING.name, page = page, pageSize = pageSize)
        val waiting = api.list(status = CommitmentStatus.WAITING.name, page = page, pageSize = pageSize)
        val snoozed = api.list(status = CommitmentStatus.SNOOZED.name, page = page, pageSize = pageSize)

        val merged = (pending.results + waiting.results + snoozed.results)
            .map { it.toDomain() }
            .distinctBy { it.id }

        val sorted = CommitmentListBucketing.sortOpen(merged)
        val next = if (pending.next != null || waiting.next != null || snoozed.next != null) page + 1 else null
        return CommitmentPage(items = sorted, nextPage = next)
    }

    private suspend fun listDoneMerged(page: Int): CommitmentPage {
        val completed = api.list(status = CommitmentStatus.COMPLETED.name, page = page)
        val cancelled = api.list(status = CommitmentStatus.CANCELLED.name, page = page)
        val merged = (completed.results + cancelled.results)
            .map { it.toDomain() }
            .distinctBy { it.id }
            .sortedByDescending { it.completedAt ?: it.updatedAt }
        val next = if (completed.next != null || cancelled.next != null) page + 1 else null
        return CommitmentPage(items = merged, nextPage = next)
    }

    private fun pageNumberFromNext(nextUrl: String?): Int? {
        if (nextUrl == null) return null
        val match = Regex("[?&]page=(\\d+)").find(nextUrl)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun Commitment.toEntity(isSynced: Boolean): CommitmentEntity {
        return CommitmentEntity(
            id = id,
            title = title,
            description = description,
            dueAt = dueAt,
            duePrecision = duePrecision.name,
            status = status.name,
            completedAt = completedAt,
            isOverdue = isOverdue,
            isSynced = isSynced,
        )
    }

    private fun CommitmentEntity.toDomain(): Commitment {
        val precision = runCatching { DuePrecision.valueOf(duePrecision) }.getOrDefault(DuePrecision.NONE)
        val commitmentStatus = runCatching { CommitmentStatus.valueOf(status) }.getOrDefault(CommitmentStatus.PENDING)
        return Commitment(
            id = id,
            title = title,
            description = description,
            status = commitmentStatus,
            dueAt = dueAt,
            duePrecision = precision,
            source = "MANUAL",
            snoozedUntil = null,
            completedAt = completedAt,
            cancelledAt = null,
            createdAt = "",
            updatedAt = "",
            isOverdue = isOverdue,
        )
    }
}
