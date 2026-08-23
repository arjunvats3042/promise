package app.promise.android.ui.commitments

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object CommitmentTime {
    fun zone(timeZoneId: String): ZoneId {
        return runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
    }

    fun endOfLocalDayIso(date: LocalDate, timeZoneId: String): String {
        val zdt = date.atTime(LocalTime.of(23, 59, 59)).atZone(zone(timeZoneId))
        return zdt.toInstant().toString()
    }

    fun localDateTimeIso(dateTime: LocalDateTime, timeZoneId: String): String {
        return dateTime.atZone(zone(timeZoneId)).toInstant().toString()
    }

    fun formatDue(dueAt: String?, precision: app.promise.android.domain.DuePrecision, timeZoneId: String): String? {
        if (dueAt.isNullOrBlank()) return null
        val z = zone(timeZoneId)
        val zdt = parse(dueAt, z) ?: return dueAt
        return when (precision) {
            app.promise.android.domain.DuePrecision.DATE ->
                DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(zdt.toLocalDate())
            app.promise.android.domain.DuePrecision.DATETIME ->
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).format(zdt)
            app.promise.android.domain.DuePrecision.NONE -> null
        }
    }

    fun laterToday(timeZoneId: String, now: ZonedDateTime = ZonedDateTime.now(zone(timeZoneId))): Instant {
        val candidate = now.plusHours(3)
        val end = now.toLocalDate().atTime(22, 0).atZone(now.zone)
        return (if (candidate.isBefore(end)) candidate else end).toInstant()
    }

    fun tomorrowMorning(timeZoneId: String, now: ZonedDateTime = ZonedDateTime.now(zone(timeZoneId))): Instant {
        return now.toLocalDate().plusDays(1).atTime(9, 0).atZone(now.zone).toInstant()
    }

    fun nextWeek(timeZoneId: String, now: ZonedDateTime = ZonedDateTime.now(zone(timeZoneId))): Instant {
        return now.plusDays(7).toInstant()
    }

    fun maxSnoozeInstant(now: Instant = Instant.now()): Instant {
        return now.plusSeconds(30L * 24 * 60 * 60)
    }

    fun isValidSnooze(target: Instant, now: Instant = Instant.now()): Boolean {
        return target.isAfter(now) && !target.isAfter(maxSnoozeInstant(now))
    }

    private fun parse(iso: String, zone: ZoneId): ZonedDateTime? {
        return runCatching { Instant.parse(iso).atZone(zone) }
            .recoverCatching { ZonedDateTime.parse(iso).withZoneSameInstant(zone) }
            .getOrNull()
    }
}
