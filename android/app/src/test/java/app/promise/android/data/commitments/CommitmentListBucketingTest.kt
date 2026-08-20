package app.promise.android.data.commitments

import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentStatus
import app.promise.android.domain.DuePrecision
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommitmentListBucketingTest {
    private val now = Instant.parse("2026-08-20T12:00:00Z")

    @Test
    fun sortOpen_putsOverdueFirst() {
        val items = listOf(
            commitment(id = "a", dueAt = "2026-08-21T12:00:00Z", overdue = false),
            commitment(id = "b", dueAt = "2026-08-19T12:00:00Z", overdue = true),
            commitment(id = "c", dueAt = null, overdue = false),
        )
        assertEquals(listOf("b", "a", "c"), CommitmentListBucketing.sortOpen(items).map { it.id })
    }

    @Test
    fun todayFilter_usesUserTimezone() {
        val today = commitment(id = "t", dueAt = "2026-08-20T18:00:00Z", overdue = false)
        val tomorrow = commitment(id = "u", dueAt = "2026-08-21T18:00:00Z", overdue = false)
        val filtered = CommitmentListBucketing.filterClientSide(
            filter = CommitmentListFilter.TODAY,
            items = listOf(today, tomorrow),
            timeZoneId = "UTC",
            now = now,
        )
        assertEquals(listOf("t"), filtered.map { it.id })
    }

    @Test
    fun upcomingFilter_excludesOverdueAndPast() {
        val upcoming = commitment(id = "u", dueAt = "2026-08-25T12:00:00Z", overdue = false)
        val overdue = commitment(id = "o", dueAt = "2026-08-18T12:00:00Z", overdue = true)
        val filtered = CommitmentListBucketing.filterClientSide(
            filter = CommitmentListFilter.UPCOMING,
            items = listOf(upcoming, overdue),
            timeZoneId = "UTC",
            now = now,
        )
        assertEquals(listOf("u"), filtered.map { it.id })
    }

    @Test
    fun openStatusHelper() {
        assertTrue(CommitmentListBucketing.isOpenStatus(CommitmentStatus.PENDING))
        assertTrue(CommitmentListBucketing.isOpenStatus(CommitmentStatus.WAITING))
        assertTrue(CommitmentListBucketing.isOpenStatus(CommitmentStatus.SNOOZED))
        assertFalse(CommitmentListBucketing.isOpenStatus(CommitmentStatus.COMPLETED))
    }

    @Test
    fun dtoMapsToDomain() {
        val dto = CommitmentDto(
            id = "1",
            title = "Call",
            description = "Mom",
            status = "WAITING",
            dueAt = "2026-08-20T00:00:00Z",
            duePrecision = "DATE",
            source = "MANUAL",
            snoozedUntil = null,
            completedAt = null,
            cancelledAt = null,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            isOverdue = true,
        )
        val domain = dto.toDomain()
        assertEquals(CommitmentStatus.WAITING, domain.status)
        assertEquals(DuePrecision.DATE, domain.duePrecision)
        assertTrue(domain.isOverdue)
        assertTrue(domain.canComplete)
        assertFalse(domain.canSnooze)
        assertFalse(domain.canWait)
    }

    @Test
    fun pageNumberFromNext_parsesQuery() {
        assertEquals(2, pageNumberFromNext("http://x/api/v1/commitments/?page=2"))
        assertEquals(null, pageNumberFromNext(null))
    }

    private fun commitment(
        id: String,
        dueAt: String?,
        overdue: Boolean,
    ): Commitment {
        return Commitment(
            id = id,
            title = id,
            description = "",
            status = CommitmentStatus.PENDING,
            dueAt = dueAt,
            duePrecision = if (dueAt == null) DuePrecision.NONE else DuePrecision.DATETIME,
            source = "MANUAL",
            snoozedUntil = null,
            completedAt = null,
            cancelledAt = null,
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            isOverdue = overdue,
        )
    }
}
