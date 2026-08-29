package app.promise.android.core.util

import app.promise.android.domain.DuePrecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NaturalLanguageDateParserTest {

    @Test
    fun testTomorrowAt12pm() {
        val result = NaturalLanguageDateParser.parse("call mom at 12:00 p.m. tomorrow", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATETIME, result.duePrecision)
        assertEquals("Call mom", result.cleanedTitle)
    }

    @Test
    fun testDayAfterTomorrowAt12pm() {
        val result = NaturalLanguageDateParser.parse("call mom day after tomorrow at 12:00 p.m.", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATETIME, result.duePrecision)
        assertEquals("Call mom", result.cleanedTitle)
    }

    @Test
    fun testAfter3Days() {
        val result = NaturalLanguageDateParser.parse("call mom after 3 days", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATE, result.duePrecision)
        assertEquals("Call mom", result.cleanedTitle)
    }

    @Test
    fun test3DaysAfter() {
        val result = NaturalLanguageDateParser.parse("I have to call my sister 3 days after", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATE, result.duePrecision)
        assertEquals("Call my sister", result.cleanedTitle)
    }

    @Test
    fun test1stOfSeptember() {
        val result = NaturalLanguageDateParser.parse("I have to send a pdf to my manager on 1st of September", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATE, result.duePrecision)
        assertEquals("Send a pdf to my manager", result.cleanedTitle)
    }

    @Test
    fun testInAfternoon() {
        val result = NaturalLanguageDateParser.parse("call doctor in afternoon", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATETIME, result.duePrecision)
        assertEquals("Call doctor", result.cleanedTitle)
    }

    @Test
    fun testTomorrowMorning() {
        val result = NaturalLanguageDateParser.parse("submit report tomorrow morning", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATETIME, result.duePrecision)
    }

    @Test
    fun testIn2Hours() {
        val result = NaturalLanguageDateParser.parse("take laundry in 2 hours", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATETIME, result.duePrecision)
    }

    @Test
    fun testIn2Weeks() {
        val result = NaturalLanguageDateParser.parse("pay rent in 2 weeks", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATE, result.duePrecision)
    }

    @Test
    fun testSubmitReportBy5pm() {
        val result = NaturalLanguageDateParser.parse("submit report by 5pm", "Asia/Kolkata")
        assertNotNull(result.dueAt)
        assertEquals(DuePrecision.DATETIME, result.duePrecision)
    }

    @Test
    fun testNoDateText() {
        val result = NaturalLanguageDateParser.parse("buy groceries", "Asia/Kolkata")
        assertEquals(null, result.dueAt)
        assertEquals(DuePrecision.NONE, result.duePrecision)
        assertEquals("Buy groceries", result.cleanedTitle)
    }
}
