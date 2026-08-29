package app.promise.android.core.util

import app.promise.android.domain.DuePrecision
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.regex.Pattern

data class ExtractedTimeline(
    val dueAt: String?,
    val duePrecision: DuePrecision,
    val cleanedTitle: String,
)

object NaturalLanguageDateParser {

    private val RELATIVE_OFFSET_REGEX = Pattern.compile(
        "\\b(?:in|after)\\s+(?:(a|an|one|two|three|four|five|six|seven|eight|nine|ten|\\d+))\\s*(days?|weeks?|months?|hours?|hrs?|mins?|minutes?)\\b",
        Pattern.CASE_INSENSITIVE,
    )

    private val RELATIVE_OFFSET_SUFFIX_REGEX = Pattern.compile(
        "\\b(?:(a|an|one|two|three|four|five|six|seven|eight|nine|ten|\\d+))\\s*(days?|weeks?|months?|hours?|hrs?|mins?|minutes?)\\s+(?:after|later|from\\s+now)\\b",
        Pattern.CASE_INSENSITIVE,
    )

    private val TIME_REGEX = Pattern.compile(
        "\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?|am|pm)?\\b",
        Pattern.CASE_INSENSITIVE,
    )

    private val MONTH_DAY_REGEX = Pattern.compile(
        "\\b(?:on|by\\s+)?(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b",
        Pattern.CASE_INSENSITIVE,
    )

    private val DAY_MONTH_REGEX = Pattern.compile(
        "\\b(?:on|by\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b",
        Pattern.CASE_INSENSITIVE,
    )

    fun parse(
        text: String,
        timeZoneId: String = "Asia/Kolkata",
    ): ExtractedTimeline {
        val raw = text.trim()
        if (raw.isBlank()) {
            return ExtractedTimeline(dueAt = null, duePrecision = DuePrecision.NONE, cleanedTitle = "")
        }

        val zone = runCatching { ZoneId.of(timeZoneId) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
        val now = ZonedDateTime.now(zone)
        val lower = raw.lowercase(Locale.ROOT)

        var targetDate: LocalDate? = null
        var targetTime: LocalTime? = null
        var precision = DuePrecision.NONE

        // 1. Detect relative offsets (e.g. "after 3 days", "3 days after", "in 2 weeks", "in an hour")
        val offsetMatcher = RELATIVE_OFFSET_REGEX.matcher(lower)
        val suffixOffsetMatcher = RELATIVE_OFFSET_SUFFIX_REGEX.matcher(lower)
        val matchedMatcher = if (offsetMatcher.find()) offsetMatcher else if (suffixOffsetMatcher.find()) suffixOffsetMatcher else null

        if (matchedMatcher != null) {
            val amountStr = matchedMatcher.group(1)?.lowercase(Locale.ROOT).orEmpty()
            val unitStr = matchedMatcher.group(2)?.lowercase(Locale.ROOT).orEmpty()
            val amount = parseWordNumber(amountStr)

            if (amount > 0) {
                when {
                    unitStr.startsWith("day") -> {
                        targetDate = now.toLocalDate().plusDays(amount.toLong())
                        precision = DuePrecision.DATE
                    }
                    unitStr.startsWith("week") -> {
                        targetDate = now.toLocalDate().plusWeeks(amount.toLong())
                        precision = DuePrecision.DATE
                    }
                    unitStr.startsWith("month") -> {
                        targetDate = now.toLocalDate().plusMonths(amount.toLong())
                        precision = DuePrecision.DATE
                    }
                    unitStr.startsWith("hour") || unitStr.startsWith("hr") -> {
                        val future = now.plusHours(amount.toLong())
                        targetDate = future.toLocalDate()
                        targetTime = future.toLocalTime().withSecond(0).withNano(0)
                        precision = DuePrecision.DATETIME
                    }
                    unitStr.startsWith("min") -> {
                        val future = now.plusMinutes(amount.toLong())
                        targetDate = future.toLocalDate()
                        targetTime = future.toLocalTime().withSecond(0).withNano(0)
                        precision = DuePrecision.DATETIME
                    }
                }
            }
        }

        // 2. Detect Day Offsets (if not already set by relative offset)
        if (targetDate == null) {
            when {
                lower.contains("day after tomorrow") -> {
                    targetDate = now.toLocalDate().plusDays(2)
                    precision = DuePrecision.DATE
                }
                lower.contains("tomorrow") -> {
                    targetDate = now.toLocalDate().plusDays(1)
                    precision = DuePrecision.DATE
                }
                lower.contains("tonight") -> {
                    targetDate = now.toLocalDate()
                    targetTime = LocalTime.of(21, 0)
                    precision = DuePrecision.DATETIME
                }
                lower.contains("today") -> {
                    targetDate = now.toLocalDate()
                    precision = DuePrecision.DATE
                }
                lower.contains("next week") -> {
                    targetDate = now.toLocalDate().plusDays(7)
                    precision = DuePrecision.DATE
                }
                lower.contains("this weekend") || lower.contains("weekend") -> {
                    targetDate = findNextDayOfWeek(now.toLocalDate(), DayOfWeek.SATURDAY)
                    precision = DuePrecision.DATE
                }
                else -> {
                    // Check Month & Day (e.g. "Sep 15", "15th October")
                    val mdMatcher = MONTH_DAY_REGEX.matcher(lower)
                    val dmMatcher = DAY_MONTH_REGEX.matcher(lower)
                    if (mdMatcher.find()) {
                        val month = parseMonth(mdMatcher.group(1).orEmpty())
                        val day = mdMatcher.group(2)?.toIntOrNull() ?: 1
                        if (month != null) {
                            var year = now.year
                            val candidate = LocalDate.of(year, month, day.coerceIn(1, month.length(now.toLocalDate().isLeapYear)))
                            if (candidate.isBefore(now.toLocalDate())) {
                                year += 1
                            }
                            targetDate = LocalDate.of(year, month, day.coerceIn(1, month.length(LocalDate.of(year, 1, 1).isLeapYear)))
                            precision = DuePrecision.DATE
                        }
                    } else if (dmMatcher.find()) {
                        val day = dmMatcher.group(1)?.toIntOrNull() ?: 1
                        val month = parseMonth(dmMatcher.group(2).orEmpty())
                        if (month != null) {
                            var year = now.year
                            val candidate = LocalDate.of(year, month, day.coerceIn(1, month.length(now.toLocalDate().isLeapYear)))
                            if (candidate.isBefore(now.toLocalDate())) {
                                year += 1
                            }
                            targetDate = LocalDate.of(year, month, day.coerceIn(1, month.length(LocalDate.of(year, 1, 1).isLeapYear)))
                            precision = DuePrecision.DATE
                        }
                    } else {
                        // Check for day of week (e.g. "on friday", "next monday")
                        val dayOfWeek = findDayOfWeek(lower)
                        if (dayOfWeek != null) {
                            targetDate = findNextDayOfWeek(now.toLocalDate(), dayOfWeek)
                            precision = DuePrecision.DATE
                        }
                    }
                }
            }
        }

        // 3. Detect Named Time Periods (e.g. "in afternoon", "afternoon", "in the morning", "evening", "at night")
        when {
            lower.contains("afternoon") -> {
                if (targetTime == null) targetTime = LocalTime.of(14, 0)
                precision = DuePrecision.DATETIME
            }
            lower.contains("morning") -> {
                if (targetTime == null) targetTime = LocalTime.of(9, 0)
                precision = DuePrecision.DATETIME
            }
            lower.contains("evening") -> {
                if (targetTime == null) targetTime = LocalTime.of(18, 0)
                precision = DuePrecision.DATETIME
            }
            lower.contains("night") && !lower.contains("tonight") -> {
                if (targetTime == null) targetTime = LocalTime.of(21, 0)
                precision = DuePrecision.DATETIME
            }
            lower.contains("noon") || lower.contains("midday") -> {
                if (targetTime == null) targetTime = LocalTime.of(12, 0)
                precision = DuePrecision.DATETIME
            }
            lower.contains("midnight") -> {
                if (targetTime == null) targetTime = LocalTime.of(23, 59)
                precision = DuePrecision.DATETIME
            }
        }

        // 4. Detect Explicit Clock Time (e.g. "12:00 p.m.", "12:00 pm", "12 pm", "5pm", "17:30")
        val matcher = TIME_REGEX.matcher(lower)
        while (matcher.find()) {
            val hourStr = matcher.group(1)
            val minStr = matcher.group(2)
            val ampmStr = matcher.group(3)

            val rawHour = hourStr?.toIntOrNull() ?: continue
            val rawMin = minStr?.toIntOrNull() ?: 0

            if (ampmStr != null) {
                val cleanAmpm = ampmStr.replace(".", "").lowercase(Locale.ROOT)
                var hour = rawHour
                if (cleanAmpm == "pm" && hour < 12) hour += 12
                if (cleanAmpm == "am" && hour == 12) hour = 0
                if (hour in 0..23 && rawMin in 0..59) {
                    targetTime = LocalTime.of(hour, rawMin)
                    precision = DuePrecision.DATETIME
                    break
                }
            } else if (matcher.group(0)?.contains(":") == true && rawHour in 0..23 && rawMin in 0..59) {
                targetTime = LocalTime.of(rawHour, rawMin)
                precision = DuePrecision.DATETIME
                break
            }
        }

        if (targetDate != null || targetTime != null) {
            val finalDate = targetDate ?: now.toLocalDate()
            val finalTime = targetTime ?: LocalTime.of(18, 0)
            val targetZdt = finalDate.atTime(finalTime).atZone(zone)
            val utcIso = targetZdt.toInstant().toString()

            return ExtractedTimeline(
                dueAt = utcIso,
                duePrecision = precision,
                cleanedTitle = cleanTitle(raw),
            )
        }

        return ExtractedTimeline(
            dueAt = null,
            duePrecision = DuePrecision.NONE,
            cleanedTitle = cleanTitle(raw),
        )
    }

    fun cleanTitle(raw: String): String {
        var cleaned = raw
        val patternsToRemove = listOf(
            "\\b(?:in|after)\\s+(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s*(?:days?|weeks?|months?|hours?|hrs?|mins?|minutes?)\\b",
            "\\b(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s*(?:days?|weeks?|months?|hours?|hrs?|mins?|minutes?)\\s+(?:after|later|from\\s+now)\\b",
            "\\b(?:on|by\\s+)?(?:\\d{1,2})(?:st|nd|rd|th)?\\s+(?:of\\s+)?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b",
            "\\b(?:on|by\\s+)?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+(?:\\d{1,2})(?:st|nd|rd|th)?\\b",
            "\\bday after tomorrow\\b",
            "\\btomorrow\\b",
            "\\btonight\\b",
            "\\btoday\\b",
            "\\bthis weekend\\b",
            "\\bnext week\\b",
            "\\b(?:in\\s+the\\s+|in\\s+)?afternoon\\b",
            "\\b(?:in\\s+the\\s+|in\\s+)?morning\\b",
            "\\b(?:in\\s+the\\s+|in\\s+)?evening\\b",
            "\\b(?:at\\s+)?night\\b",
            "\\b(?:at\\s+)?noon\\b",
            "\\b(?:at|by|on)\\s+(?:monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b",
            "\\b(?:at|by\\s+)?\\d{1,2}(?::\\d{2})?\\s*(?:a\\.m\\.|p\\.m\\.|a\\.m|p\\.m|am|pm)",
            "\\b(?:at|by\\s+)?\\d{1,2}:\\d{2}\\b",
        )
        for (p in patternsToRemove) {
            cleaned = cleaned.replace(Regex(p, RegexOption.IGNORE_CASE), "")
        }
        // Remove leading modal verbs: e.g. "I have to call...", "and I need to send..."
        cleaned = cleaned.replace(Regex("^(?:and\\s+|also\\s+|then\\s+|plus\\s+|so\\s+)?(?:i\\s+(?:have\\s+to|need\\s+to|must|want\\s+to|will|should|gotta|plan\\s+to)\\s+)?", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\b(?:at|by|on|for|in|due)\\s*$", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        if (cleaned.length < 2) {
            cleaned = raw.trim()
        }
        return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun parseWordNumber(str: String): Int {
        return when (str.lowercase(Locale.ROOT)) {
            "a", "an", "one" -> 1
            "two" -> 2
            "three" -> 3
            "four" -> 4
            "five" -> 5
            "six" -> 6
            "seven" -> 7
            "eight" -> 8
            "nine" -> 9
            "ten" -> 10
            else -> str.toIntOrNull() ?: 1
        }
    }

    private fun parseMonth(str: String): Month? {
        val prefix = str.take(3).lowercase(Locale.ROOT)
        return when (prefix) {
            "jan" -> Month.JANUARY
            "feb" -> Month.FEBRUARY
            "mar" -> Month.MARCH
            "apr" -> Month.APRIL
            "may" -> Month.MAY
            "jun" -> Month.JUNE
            "jul" -> Month.JULY
            "aug" -> Month.AUGUST
            "sep" -> Month.SEPTEMBER
            "oct" -> Month.OCTOBER
            "nov" -> Month.NOVEMBER
            "dec" -> Month.DECEMBER
            else -> null
        }
    }

    private fun findDayOfWeek(text: String): DayOfWeek? {
        return when {
            text.contains("monday") -> DayOfWeek.MONDAY
            text.contains("tuesday") -> DayOfWeek.TUESDAY
            text.contains("wednesday") -> DayOfWeek.WEDNESDAY
            text.contains("thursday") -> DayOfWeek.THURSDAY
            text.contains("friday") -> DayOfWeek.FRIDAY
            text.contains("saturday") -> DayOfWeek.SATURDAY
            text.contains("sunday") -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun findNextDayOfWeek(current: LocalDate, targetDay: DayOfWeek): LocalDate {
        var result = current.plusDays(1)
        while (result.dayOfWeek != targetDay) {
            result = result.plusDays(1)
        }
        return result
    }
}
