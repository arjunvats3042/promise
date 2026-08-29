package app.promise.android.core.copy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentumCopyTest {

    @Test
    fun `test zero items copy`() {
        val content = MomentumCopy.forProgress(completedCount = 0, totalCount = 0)
        assertEquals("Clear slate for today", content.headline)
        assertTrue(content.subtitle.contains("voice") || content.subtitle.contains("thoughts"))
    }

    @Test
    fun `test zero completed of multiple planned items`() {
        val content = MomentumCopy.forProgress(completedCount = 0, totalCount = 4)
        assertEquals("4 actions planned today", content.headline)
        assertTrue(content.subtitle.contains("priority"))
    }

    @Test
    fun `test in progress partial items`() {
        val content = MomentumCopy.forProgress(completedCount = 2, totalCount = 5)
        assertEquals("2 of 5 completed", content.headline)
        assertTrue(content.subtitle.contains("3 actions remaining"))
    }

    @Test
    fun `test 1 action remaining`() {
        val content = MomentumCopy.forProgress(completedCount = 3, totalCount = 4)
        assertEquals("1 final action remaining", content.headline)
        assertTrue(content.subtitle.contains("streak") || content.subtitle.contains("finish line"))
    }

    @Test
    fun `test 100 percent full completion`() {
        val content = MomentumCopy.forProgress(completedCount = 5, totalCount = 5)
        assertEquals("All promises fulfilled!", content.headline)
        assertTrue(content.subtitle.contains("consistency") || content.subtitle.contains("follow-through"))
    }
}
