package app.promise.android.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlanceStateDataTest {

    @Test
    fun `test progress calculation for zero total items`() {
        val data = PromiseWidgetData(completedCount = 0, totalCount = 0)
        assertEquals(0, data.progressPercent)
        assertEquals("Getting Started", data.consistencyLevel)
    }

    @Test
    fun `test progress percentage and summary formatting`() {
        val data = PromiseWidgetData(
            completedCount = 3,
            totalCount = 4,
            streakCount = 12,
        )
        assertEquals(75, data.progressPercent)
        assertEquals("75% (3/4 Commitments & Goals)", data.progressSummaryString)
        assertEquals("Medium", data.consistencyLevel)
    }

    @Test
    fun `test high consistency level threshold`() {
        val data = PromiseWidgetData(completedCount = 9, totalCount = 10)
        assertEquals(90, data.progressPercent)
        assertEquals("High", data.consistencyLevel)
    }

    @Test
    fun `test JSON serialization and deserialization roundtrip`() {
        val sample = PromiseWidgetData.sampleData()
        val jsonStr = sample.toJson()
        assertTrue(jsonStr.contains("sample_1"))

        val decoded = PromiseWidgetData.fromJson(jsonStr)
        assertEquals(sample.completedCount, decoded.completedCount)
        assertEquals(sample.totalCount, decoded.totalCount)
        assertEquals(sample.streakCount, decoded.streakCount)
        assertEquals(sample.items.size, decoded.items.size)
        assertEquals("Morning Yoga (20m)", decoded.items[0].title)
    }

    @Test
    fun `test top pending item retrieval`() {
        val sample = PromiseWidgetData.sampleData()
        val topPending = sample.topPendingItem
        assertNotNull(topPending)
        assertEquals("Water Intake (2L)", topPending?.title)
    }

    @Test
    fun `test fallback empty data on invalid json string`() {
        val data = PromiseWidgetData.fromJson("invalid_json_str")
        assertEquals(0, data.completedCount)
        assertEquals(0, data.totalCount)
    }

    @Test
    fun `test isOverdue serialization roundtrip`() {
        val item = WidgetCommitmentItem(
            id = "overdue_1",
            title = "File Taxes",
            isCompleted = false,
            isOverdue = true,
            dueTimeFormatted = "Overdue",
        )
        val data = PromiseWidgetData(items = listOf(item))
        val json = data.toJson()
        val decoded = PromiseWidgetData.fromJson(json)
        assertTrue(decoded.items.first().isOverdue)
    }
}
