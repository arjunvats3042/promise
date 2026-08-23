package app.promise.android.data.commitments

import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object CommitmentListBucketing {
    fun sortOpen(items: List<Commitment>): List<Commitment> {
        return items.sortedWith(
            compareByDescending<Commitment> { it.isOverdue }
                .thenBy { it.dueAt ?: "9999" }
                .thenByDescending { it.createdAt },
        )
    }

    fun filterClientSide(
        filter: CommitmentListFilter,
        items: List<Commitment>,
        timeZoneId: String,
        now: Instant = Instant.now(),
    ): List<Commitment> {
        val zone = zoneId(timeZoneId)
        val today = LocalDate.ofInstant(now, zone)
        return when (filter) {
            CommitmentListFilter.OPEN,
            CommitmentListFilter.OVERDUE,
            CommitmentListFilter.DONE,
            -> items
            CommitmentListFilter.TODAY -> {
                items.filter { c ->
                    val due = c.dueAt ?: return@filter false
                    localDate(due, zone) == today
                }
            }
            CommitmentListFilter.UPCOMING -> {
                items.filter { c ->
                    if (c.isOverdue) return@filter false
                    val due = c.dueAt ?: return@filter false
                    localDate(due, zone).isAfter(today)
                }
            }
        }
    }

    fun isOpenStatus(status: CommitmentStatus): Boolean {
        return status == CommitmentStatus.PENDING ||
            status == CommitmentStatus.WAITING ||
            status == CommitmentStatus.SNOOZED
    }

    private fun localDate(iso: String, zone: ZoneId): LocalDate {
        return runCatching {
            Instant.parse(iso).atZone(zone).toLocalDate()
        }.recoverCatching {
            ZonedDateTime.parse(iso).withZoneSameInstant(zone).toLocalDate()
        }.getOrElse { todayFallback(zone) }
    }

    private fun todayFallback(zone: ZoneId): LocalDate = LocalDate.now(zone)

    private fun zoneId(id: String): ZoneId {
        return runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
    }
}
