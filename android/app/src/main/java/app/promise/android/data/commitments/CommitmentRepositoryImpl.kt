package app.promise.android.data.commitments

import app.promise.android.data.network.toApiException
import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentPage
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.CommitmentStatus
import app.promise.android.domain.CreateCommitmentInput
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommitmentRepositoryImpl @Inject constructor(
    private val api: CommitmentApi,
) : CommitmentRepository {
    override suspend fun list(
        filter: CommitmentListFilter,
        page: Int,
        timeZoneId: String,
    ): CommitmentPage {
        return try {
            when (filter) {
                CommitmentListFilter.OPEN -> listOpenMerged(page, timeZoneId)
                CommitmentListFilter.OVERDUE -> {
                    val response = api.list(isOverdue = true, page = page, pageSize = PAGE_SIZE)
                    CommitmentPage(
                        items = CommitmentListBucketing.sortOpen(response.results.map { it.toDomain() }),
                        nextPage = pageNumberFromNext(response.next),
                    )
                }
                CommitmentListFilter.TODAY,
                CommitmentListFilter.UPCOMING,
                -> {
                    // Load open work then bucket locally (API has no due_before/due_after).
                    val open = listOpenMerged(page = 1, timeZoneId = timeZoneId, pageSize = 100)
                    val filtered = CommitmentListBucketing.filterClientSide(
                        filter = filter,
                        items = open.items,
                        timeZoneId = timeZoneId,
                    )
                    CommitmentPage(items = filtered, nextPage = null)
                }
                CommitmentListFilter.DONE -> listDoneMerged(page)
            }
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun get(id: String): Commitment {
        return try {
            api.get(id).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun create(input: CreateCommitmentInput): Commitment {
        return try {
            api.create(
                CreateCommitmentRequest(
                    title = input.title.trim(),
                    description = input.description,
                    dueAt = input.dueAt,
                    duePrecision = input.duePrecision.name,
                ),
            ).toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    override suspend fun complete(id: String): Commitment {
        return mutate { api.complete(id) }
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
            block().toDomain()
        } catch (t: Throwable) {
            throw t.toApiException()
        }
    }

    private suspend fun listOpenMerged(
        page: Int,
        timeZoneId: String,
        pageSize: Int = PAGE_SIZE,
    ): CommitmentPage {
        val statuses = listOf(
            CommitmentStatus.PENDING,
            CommitmentStatus.WAITING,
            CommitmentStatus.SNOOZED,
        )
        val pages = statuses.map { status ->
            api.list(status = status.name, page = page, pageSize = pageSize)
        }
        val items = pages
            .flatMap { it.results }
            .map { it.toDomain() }
            .let { CommitmentListBucketing.sortOpen(it) }
        val next = if (pages.any { it.next != null }) page + 1 else null
        return CommitmentPage(items = items, nextPage = next)
    }

    private suspend fun listDoneMerged(page: Int): CommitmentPage {
        val completed = api.list(status = CommitmentStatus.COMPLETED.name, page = page)
        val cancelled = api.list(status = CommitmentStatus.CANCELLED.name, page = page)
        val items = (completed.results + cancelled.results)
            .map { it.toDomain() }
            .sortedByDescending { it.updatedAt }
        val next = if (completed.next != null || cancelled.next != null) page + 1 else null
        return CommitmentPage(items = items, nextPage = next)
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
