package app.promise.android.ui.components

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateTimeFormat {
    fun formatDate(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        return date.format(DateTimeFormatter.ofPattern("EEE, d MMM", locale))
    }

    fun formatTime(time: LocalTime, locale: Locale = Locale.getDefault()): String {
        return time.format(DateTimeFormatter.ofPattern("h:mm a", locale))
    }

    /** hour12 is 1..12 */
    fun toLocalTime(hour12: Int, minute: Int, isPm: Boolean): LocalTime {
        val h = hour12.coerceIn(1, 12)
        val m = minute.coerceIn(0, 59)
        val hour24 = when {
            h == 12 && !isPm -> 0
            h == 12 && isPm -> 12
            isPm -> h + 12
            else -> h
        }
        return LocalTime.of(hour24, m)
    }

    fun toHour12(time: LocalTime): Int {
        val h = time.hour % 12
        return if (h == 0) 12 else h
    }

    fun isPm(time: LocalTime): Boolean = time.hour >= 12
}
