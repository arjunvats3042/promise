package app.promise.android.core.copy

import java.util.Calendar

/**
 * Pure, deterministic greeting generator that varies copy based on
 * (Hour of Day, Day of Week, and Daily Momentum state) to prevent repetitive monotony.
 */
object GreetingEngine {

    fun getEditorialGreeting(
        hour: Int,
        dayOfWeek: Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK),
        completedCount: Int = 0,
        totalCount: Int = 0,
    ): String {
        // Special momentum state: 100% completed in evening
        if (totalCount > 0 && completedCount == totalCount && hour >= 16) {
            return when (dayOfWeek) {
                Calendar.FRIDAY -> "All promises fulfilled · Enjoy your weekend ahead"
                Calendar.SUNDAY -> "Week wrapped completely · Prepared and grounded"
                else -> "All promises fulfilled today · Rest and recharge"
            }
        }

        return when (dayOfWeek) {
            Calendar.MONDAY -> when (hour) {
                in 5..11 -> "Monday morning · Set your core intention for the week"
                in 12..16 -> "Monday afternoon · Building early weekly momentum"
                in 17..21 -> "Monday evening · Solid start to the week"
                else -> "Quiet hours · Rest and prepare for the week"
            }
            Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY -> when (hour) {
                in 5..11 -> "Good morning · Focus on your single most vital promise"
                in 12..16 -> "Good afternoon · Stay in steady, uninterrupted flow"
                in 17..21 -> "Good evening · Reflect and follow through on open loops"
                else -> "Quiet night · Rest and let today's progress settle"
            }
            Calendar.FRIDAY -> when (hour) {
                in 5..11 -> "Friday morning · Finish strong and close out open promises"
                in 12..16 -> "Friday afternoon · Final stretch of the week"
                in 17..21 -> "Friday evening · Reflect on a week of kept promises"
                else -> "Quiet weekend eve · Rest and unwind"
            }
            Calendar.SATURDAY -> when (hour) {
                in 5..11 -> "Saturday morning · Space for mindful, personal practices"
                in 12..16 -> "Saturday afternoon · Move at an intentional, calm pace"
                in 17..21 -> "Saturday evening · Unwind and celebrate your consistency"
                else -> "Quiet night · Recharge and rest"
            }
            Calendar.SUNDAY -> when (hour) {
                in 5..11 -> "Sunday morning · A gentle start to recharge"
                in 12..16 -> "Sunday afternoon · Space for reflection and personal habits"
                in 17..21 -> "Sunday evening · Ground your mindset for the week ahead"
                else -> "Quiet night · Prepared and ready for tomorrow"
            }
            else -> when (hour) {
                in 5..11 -> "Good morning · Set your daily intention"
                in 12..16 -> "Good afternoon · Stay in steady flow"
                in 17..21 -> "Good evening · Reflect and follow through"
                else -> "Quiet night · Rest and prepare for tomorrow"
            }
        }
    }

    fun getShortGreeting(hour: Int): String {
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
