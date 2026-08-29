package app.promise.android.core.copy

import java.util.Calendar
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingEngineTest {

    @Test
    fun `test morning greetings across weekdays`() {
        val mondayMorning = GreetingEngine.getEditorialGreeting(hour = 8, dayOfWeek = Calendar.MONDAY)
        assertTrue(mondayMorning.contains("Monday morning"))
        assertFalse(mondayMorning.isBlank())

        val fridayMorning = GreetingEngine.getEditorialGreeting(hour = 9, dayOfWeek = Calendar.FRIDAY)
        assertTrue(fridayMorning.contains("Friday morning"))

        val sundayMorning = GreetingEngine.getEditorialGreeting(hour = 7, dayOfWeek = Calendar.SUNDAY)
        assertTrue(sundayMorning.contains("Sunday morning"))
    }

    @Test
    fun `test evening greetings across weekdays`() {
        val wednesdayEvening = GreetingEngine.getEditorialGreeting(hour = 19, dayOfWeek = Calendar.WEDNESDAY)
        assertTrue(wednesdayEvening.contains("Good evening"))

        val fridayEvening = GreetingEngine.getEditorialGreeting(hour = 20, dayOfWeek = Calendar.FRIDAY)
        assertTrue(fridayEvening.contains("Friday evening"))
    }

    @Test
    fun `test late night quiet hours`() {
        val lateNight = GreetingEngine.getEditorialGreeting(hour = 23, dayOfWeek = Calendar.TUESDAY)
        assertTrue(lateNight.contains("Quiet") || lateNight.contains("night") || lateNight.contains("Rest"))
    }

    @Test
    fun `test special momentum completion greeting in evening`() {
        val fullCompletion = GreetingEngine.getEditorialGreeting(
            hour = 18,
            dayOfWeek = Calendar.WEDNESDAY,
            completedCount = 5,
            totalCount = 5,
        )
        assertTrue(fullCompletion.contains("All promises fulfilled today"))
    }

    @Test
    fun `test short greeting for hour intervals`() {
        assertTrue(GreetingEngine.getShortGreeting(8).contains("morning"))
        assertTrue(GreetingEngine.getShortGreeting(14).contains("afternoon"))
        assertTrue(GreetingEngine.getShortGreeting(20).contains("evening"))
    }
}
