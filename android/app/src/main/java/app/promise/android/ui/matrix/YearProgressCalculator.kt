package app.promise.android.ui.matrix

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class YearProgressInfo(
    val year: Int,
    val totalDays: Int,
    val currentDayOfYear: Int,
    val percentElapsed: Float,
    val daysRemaining: Int,
    val todayFormatted: String,
)

data class DayDotInfo(
    val dayOfYear: Int,
    val date: LocalDate,
    val isPast: Boolean,
    val isToday: Boolean,
    val isFuture: Boolean,
    val formattedDate: String,
)

object YearProgressCalculator {

    fun calculate(timeZoneId: String = ZoneId.systemDefault().id): YearProgressInfo {
        val zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val today = LocalDate.now(zone)
        val year = today.year
        val isLeap = today.isLeapYear
        val totalDays = if (isLeap) 366 else 365
        val currentDay = today.dayOfYear
        val percent = (currentDay.toFloat() / totalDays.toFloat()) * 100f
        val remaining = totalDays - currentDay

        val formatter = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault())
        val todayFormatted = today.format(formatter)

        return YearProgressInfo(
            year = year,
            totalDays = totalDays,
            currentDayOfYear = currentDay,
            percentElapsed = (percent * 10).toInt() / 10f,
            daysRemaining = remaining,
            todayFormatted = todayFormatted,
        )
    }

    fun getAllDayDots(year: Int = LocalDate.now().year, timeZoneId: String = ZoneId.systemDefault().id): List<DayDotInfo> {
        val zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.systemDefault())
        val today = LocalDate.now(zone)
        val isLeap = LocalDate.of(year, 1, 1).isLeapYear
        val totalDays = if (isLeap) 366 else 365

        val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())

        return (1..totalDays).map { dayIndex ->
            val date = LocalDate.ofYearDay(year, dayIndex)
            val isToday = (date == today)
            val isPast = date.isBefore(today)
            val isFuture = date.isAfter(today)

            DayDotInfo(
                dayOfYear = dayIndex,
                date = date,
                isPast = isPast,
                isToday = isToday,
                isFuture = isFuture,
                formattedDate = date.format(dateFormatter),
            )
        }
    }
}
