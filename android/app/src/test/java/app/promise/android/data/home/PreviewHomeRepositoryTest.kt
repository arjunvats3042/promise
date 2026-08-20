package app.promise.android.data.home

import app.promise.android.domain.HomeFeed
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewHomeRepositoryTest {
    @Test
    fun defaultFeed_ordersOverdueThenDueTodayThenUpcoming() = runTest {
        val repo = PreviewHomeRepository()
        val open = PreviewHomeRepository.sortedOpenCommitments(repo.observeFeed().first().commitments)
        assertEquals(listOf("c-overdue", "c-today", "c-upcoming"), open.map { it.id })
    }

    @Test
    fun completeCommitment_marksCompleted() = runTest {
        val repo = PreviewHomeRepository()
        repo.completeCommitment("c-today")
        val today = repo.observeFeed().first().commitments.first { it.id == "c-today" }
        assertTrue(today.isCompleted)
        val open = PreviewHomeRepository.sortedOpenCommitments(repo.observeFeed().first().commitments)
        assertFalse(open.any { it.id == "c-today" })
    }

    @Test
    fun checkInPractice_setsCheckedInToday() = runTest {
        val repo = PreviewHomeRepository()
        repo.checkInPractice("p-read")
        val practice = repo.observeFeed().first().practices.first { it.id == "p-read" }
        assertTrue(practice.checkedInToday)
    }

    @Test
    fun emptyScenario_hasNoItems() = runTest {
        val repo = PreviewHomeRepository()
        repo.loadEmptyScenario()
        val feed: HomeFeed = repo.observeFeed().first()
        assertTrue(feed.commitments.isEmpty())
        assertTrue(feed.practices.isEmpty())
    }

    @Test
    fun defaultFeed_hasThreePracticesWithVariedProgress() = runTest {
        val practices = PreviewHomeRepository().observeFeed().first().practices
        assertEquals(3, practices.size)
        assertEquals(0.25f, practices[0].progressFraction)
        assertEquals(0.6f, practices[1].progressFraction)
        assertEquals(0.9f, practices[2].progressFraction)
        assertEquals(0, practices[0].streakDays)
        assertEquals(3, practices[1].streakDays)
        assertEquals(12, practices[2].streakDays)
    }
}
