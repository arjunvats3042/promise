package app.promise.android.data.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFreshnessTest {
    @Test
    fun dirty_forcesRefresh() {
        val freshness = HomeFreshness()
        freshness.markSuccessfulLoad(nowMs = 1_000L)
        assertFalse(freshness.shouldRefresh(nowMs = 1_000L + 1_000L))
        freshness.markDirty()
        assertTrue(freshness.shouldRefresh(nowMs = 1_000L + 1_000L))
    }

    @Test
    fun withinTtl_keepsFresh() {
        val freshness = HomeFreshness()
        freshness.markSuccessfulLoad(nowMs = 10_000L)
        assertFalse(freshness.shouldRefresh(nowMs = 10_000L + 59_000L))
        assertTrue(freshness.shouldRefresh(nowMs = 10_000L + 60_000L))
    }
}
