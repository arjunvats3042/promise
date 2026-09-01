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
        "\\b(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?|am|pm|baje)?\\b",
        Pattern.CASE_INSENSITIVE,
    )

    private val ORDINAL_WORDS = mapOf(
        "first" to 1, "1st" to 1, "second" to 2, "2nd" to 2, "third" to 3, "3rd" to 3,
        "fourth" to 4, "4th" to 4, "fifth" to 5, "5th" to 5, "sixth" to 6, "6th" to 6,
        "seventh" to 7, "7th" to 7, "eighth" to 8, "8th" to 8, "ninth" to 9, "9th" to 9,
        "tenth" to 10, "10th" to 10, "eleventh" to 11, "11th" to 11, "twelfth" to 12, "12th" to 12,
        "thirteenth" to 13, "13th" to 13, "fourteenth" to 14, "14th" to 14, "fifteenth" to 15, "15th" to 15,
        "sixteenth" to 16, "16th" to 16, "seventeenth" to 17, "17th" to 17, "eighteenth" to 18, "18th" to 18,
        "nineteenth" to 19, "19th" to 19, "twentieth" to 20, "20th" to 20,
        "twenty-first" to 21, "21st" to 21, "twenty-second" to 22, "22nd" to 22,
        "twenty-third" to 23, "23rd" to 23, "twenty-fourth" to 24, "24th" to 24,
        "twenty-fifth" to 25, "25th" to 25, "twenty-sixth" to 26, "26th" to 26,
        "twenty-seventh" to 27, "27th" to 27, "twenty-eighth" to 28, "28th" to 28,
        "twenty-ninth" to 29, "29th" to 29, "thirtieth" to 30, "30th" to 30, "thirty-first" to 31, "31st" to 31,
    )

    private const val ORD_PATTERN = "(?:\\d{1,2}(?:st|nd|rd|th)?|first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|eleventh|twelfth|thirteenth|fourteenth|fifteenth|sixteenth|seventeenth|eighteenth|nineteenth|twentieth|twenty-first|twenty-second|twenty-third|twenty-fourth|twenty-fifth|twenty-sixth|twenty-seventh|twenty-eighth|twenty-ninth|thirtieth|thirty-first)"

    private val MONTH_DAY_REGEX = Pattern.compile(
        "\\b(?:on|by\\s+)?(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+($ORD_PATTERN)\\b",
        Pattern.CASE_INSENSITIVE,
    )

    private val DAY_MONTH_REGEX = Pattern.compile(
        "\\b(?:on|by\\s+)?($ORD_PATTERN)\\s+(?:of\\s+)?(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b",
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
                lower.contains("day after tomorrow") || lower.contains("parso") || lower.contains("parson") -> {
                    targetDate = now.toLocalDate().plusDays(2)
                    precision = DuePrecision.DATE
                }
                lower.contains("tomorrow") || lower.contains("kal") -> {
                    targetDate = now.toLocalDate().plusDays(1)
                    precision = DuePrecision.DATE
                }
                lower.contains("narso") -> {
                    targetDate = now.toLocalDate().plusDays(3)
                    precision = DuePrecision.DATE
                }
                lower.contains("tonight") || lower.contains("aaj raat") -> {
                    targetDate = now.toLocalDate()
                    targetTime = LocalTime.of(21, 0)
                    precision = DuePrecision.DATETIME
                }
                lower.contains("today") || lower.contains("aaj") -> {
                    targetDate = now.toLocalDate()
                    precision = DuePrecision.DATE
                }
                lower.contains("next week") || lower.contains("agla hafta") || lower.contains("agle hafte") -> {
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
                        val dayToken = mdMatcher.group(2).orEmpty().lowercase(Locale.ROOT)
                        val day = ORDINAL_WORDS[dayToken] ?: dayToken.filter { it.isDigit() }.toIntOrNull() ?: 1
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
                        val dayToken = dmMatcher.group(1).orEmpty().lowercase(Locale.ROOT)
                        val day = ORDINAL_WORDS[dayToken] ?: dayToken.filter { it.isDigit() }.toIntOrNull() ?: 1
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
                        // Check for day of week (e.g. "on friday", "next monday", "somwar ko")
                        val dayOfWeek = findDayOfWeek(lower)
                        if (dayOfWeek != null) {
                            targetDate = findNextDayOfWeek(now.toLocalDate(), dayOfWeek)
                            precision = DuePrecision.DATE
                        }
                    }
                }
            }
        }

        // 3. Detect Named Time Periods (e.g. "in afternoon", "afternoon", "dopahar", "morning", "subah", "evening", "shaam", "night", "raat")
        when {
            lower.contains("afternoon") || lower.contains("dopahar") -> {
                if (targetTime == null) targetTime = LocalTime.of(14, 0)
                precision = DuePrecision.DATETIME
            }
            lower.contains("morning") || lower.contains("subah") || lower.contains("pratah") -> {
                if (targetTime == null) targetTime = LocalTime.of(9, 0)
                precision = DuePrecision.DATETIME
            }
            lower.contains("evening") || lower.contains("shaam") || lower.contains("sham") -> {
                if (targetTime == null) targetTime = LocalTime.of(18, 0)
                precision = DuePrecision.DATETIME
            }
            (lower.contains("night") || lower.contains("raat")) && !lower.contains("tonight") && !lower.contains("aaj raat") -> {
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

        // 4. Detect Explicit Clock Time (e.g. "12:00 p.m.", "12:00 pm", "12 pm", "5pm", "17:30", "5 baje", "11 baje")
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
                if (cleanAmpm == "baje") {
                    if ((lower.contains("shaam") || lower.contains("sham") || lower.contains("raat") || lower.contains("evening") || lower.contains("night")) && hour < 12) {
                        hour += 12
                    } else if ((lower.contains("dopahar") || lower.contains("afternoon")) && hour < 12 && hour <= 5) {
                        hour += 12
                    }
                }
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
            "\\b(?:and\\s+)?(?:set|schedule|remind|create)\\s+(?:it\\s+)?(?:for|to|at|on)\\b.*$",
            "\\b(?:in|after)\\s+(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s*(?:days?|weeks?|months?|hours?|hrs?|mins?|minutes?)\\b",
            "\\b(?:a|an|one|two|three|four|five|six|seven|eight|nine|ten|\\d+)\\s*(?:days?|weeks?|months?|hours?|hrs?|mins?|minutes?)\\s+(?:after|later|from\\s+now)\\b",
            "\\b(?:on\\s+|by\\s+)?$ORD_PATTERN\\s+(?:of\\s+)?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b",
            "\\b(?:on\\s+|by\\s+)?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+$ORD_PATTERN\\b",
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
            "\\b(?:ko\\s+call\\s+karna\\s+hai|call\\s+karna\\s+hai|karna\\s+hai|jana\\s+hai|bhejna\\s+hai|dena\\s+hai|lena\\s+hai|khareedna\\s+hai|peena\\s+hai|padhna\\s+hai|kar\\s+dena)\\b",
            "\\b(?:kal|parso|parson|narso|aaj|subah|shaam|sham|dopahar|raat|baje|roz|har\\s+din|har\\s+roz|agle?\\s+somwar)\\b",
        )
        for (p in patternsToRemove) {
            cleaned = cleaned.replace(Regex(p, RegexOption.IGNORE_CASE), "")
        }
        // Remove leading modal verbs / task starters: e.g. "I have to call...", "schedule call with...", "remind me to send..."
        cleaned = cleaned.replace(Regex("^(?:and\\s+|also\\s+|then\\s+|plus\\s+|so\\s+|please\\s+|aur\\s+|phir\\s+|fir\\s+|mujhe\\s+|hume\\s+)?(?:schedule\\s+|remind\\s+me\\s+to\\s+|remember\\s+to\\s+|i\\s+(?:have\\s+to|need\\s+to|must|want\\s+to|will|should|gotta|plan\\s+to)\\s+)?", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\b(?:at|by|on|for|in|due|and|also|then|set|it|to|ko|hai|se|me|mein)\\s*$", RegexOption.IGNORE_CASE), "")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim()

        if (cleaned.length < 2) {
            cleaned = raw.trim()
        }
        return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
    }

    private fun parseWordNumber(str: String): Int {
        return when (str.lowercase(Locale.ROOT)) {
            "a", "an", "one", "ek" -> 1
            "two", "do" -> 2
            "three", "teen" -> 3
            "four", "char" -> 4
            "five", "paanch" -> 5
            "six", "chhe" -> 6
            "seven", "saat" -> 7
            "eight", "aath" -> 8
            "nine", "nau" -> 9
            "ten", "das" -> 10
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
            text.contains("monday") || text.contains("somwar") -> DayOfWeek.MONDAY
            text.contains("tuesday") || text.contains("mangalwar") -> DayOfWeek.TUESDAY
            text.contains("wednesday") || text.contains("budhwar") -> DayOfWeek.WEDNESDAY
            text.contains("thursday") || text.contains("guruwar") || text.contains("veervar") -> DayOfWeek.THURSDAY
            text.contains("friday") || text.contains("shukrawar") -> DayOfWeek.FRIDAY
            text.contains("saturday") || text.contains("shaniwar") -> DayOfWeek.SATURDAY
            text.contains("sunday") || text.contains("ravivar") || text.contains("itwar") -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private const val ACTION_VERBS = "ask|call|send|email|mail|buy|purchase|pay|meet|submit|write|finish|clean|visit|" +
            "schedule|pick\\s+up|pickup|order|remind|workout|exercise|meditate|drink|read|" +
            "tell|talk|check|take|go|bring|prepare|book|complete|start|review|reply|" +
            "get|fix|plan|organize|help|practice|contact|message|msg|whatsapp|ping|" +
            "update|discuss|attend|listen|watch|learn|cook|eat|do|make|" +
            "pucho|batao|baat|dekho|karo|bhejo|padho|jao|aao|karein|khareedo|dena|" +
            "gym|water|yoga|meditation|walk|running|stretch|habit|routine|roz|daily|everyday"

    fun splitCompoundThoughts(text: String): List<String> {
        val cleaned = text.trim()
        if (cleaned.isBlank()) return emptyList()

        val coarseChunks = cleaned.split(Regex("\\r?\\n|•|\\*|(?<=\\d)\\.\\s+"))
        val fineChunks = mutableListOf<String>()

        val taskSplitPattern = Pattern.compile(
            "(?:\\s*(?:,\\s*|\\s+)(?:and\\s+also|and\\s+then|plus\\s+also|aur\\s+bhi|aur\\s+phir|aur\\s+fir)\\s+)" +
                    "|(?:\\s+(?:and|aur|plus|then|also)\\s+(?=(?:$ACTION_VERBS|i\\b|we\\b|mujhe\\b|hume\\b|you\\b|to\\b)))" +
                    "|(?:\\s*,\\s*(?=(?:and\\s+|aur\\s+)?(?:$ACTION_VERBS|i\\b|we\\b|mujhe\\b|hume\\b)))" +
                    "|(?:\\s+(?:and|aur)\\s+(?:i\\s+(?:have\\s+to|need\\s+to|must|want\\s+to|will|should|gotta|plan\\s+to))\\s+)",
            Pattern.CASE_INSENSITIVE
        )

        val cleanPrefixRegex = Regex("^(?:and\\s+|also\\s+|then\\s+|plus\\s+|so\\s+|aur\\s+|phir\\s+)?(?:i\\s+(?:have\\s+to|need\\s+to|must|want\\s+to|will|should|gotta|plan\\s+to)\\s+)?", RegexOption.IGNORE_CASE)

        for (chunk in coarseChunks) {
            val strippedChunk = chunk.replace(Regex("^(?:[-*•]|\\d+[.)]\\s+)", RegexOption.IGNORE_CASE), "").trim()
            if (strippedChunk.isBlank()) continue

            val subTasks = taskSplitPattern.split(strippedChunk)
            for (sub in subTasks) {
                val subTrim = sub.trim()
                if (subTrim.length >= 3) {
                    val cleanedSub = cleanPrefixRegex.replace(subTrim, "").trim()
                    if (cleanedSub.length >= 3) {
                        fineChunks.add(cleanedSub)
                    } else if (subTrim.isNotBlank()) {
                        fineChunks.add(subTrim)
                    }
                }
            }
        }

        return if (fineChunks.isNotEmpty()) fineChunks else listOf(cleaned)
    }

    fun decomposeAndParse(thought: String, timeZoneId: String = "Asia/Kolkata"): List<app.promise.android.domain.ParsedThoughtItem> {
        val clauses = splitCompoundThoughts(thought)
        val goalKeywords = setOf(
            "daily", "every day", "everyday", "habit", "gym", "workout",
            "water", "meditat", "read", "exercise", "walk", "stretch",
            "practice", "routine", "weekly", "roz", "har din", "har roz",
            "har subah", "har sham", "kasrat", "paani", "kitab", "dhyan",
            "hafte me", "hafte mein"
        )

        return clauses.map { clause ->
            val lower = clause.lowercase(Locale.ROOT)
            val isGoal = goalKeywords.any { lower.contains(it) }
            val extracted = parse(clause, timeZoneId)
            val cleanTitle = extracted.cleanedTitle.takeIf { it.isNotBlank() } ?: clause

            if (isGoal) {
                app.promise.android.domain.ParsedThoughtItem(
                    type = "goal",
                    title = cleanTitle,
                    description = "",
                    confidence = "MEDIUM",
                    recurrenceKind = "DAILY",
                    trackingKind = "BINARY",
                )
            } else {
                app.promise.android.domain.ParsedThoughtItem(
                    type = "commitment",
                    title = cleanTitle,
                    description = "",
                    confidence = if (extracted.dueAt != null) "HIGH" else "MEDIUM",
                    dueAt = extracted.dueAt,
                    duePrecision = if (extracted.duePrecision == DuePrecision.DATE) "DAY" else if (extracted.duePrecision == DuePrecision.DATETIME) "HOUR" else null,
                )
            }
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
