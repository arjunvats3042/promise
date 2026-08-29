package app.promise.android.core.copy

import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.GoalListFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmptyStateCopyTest {

    @Test
    fun `test all commitment filters have non-repetitive non-empty content`() {
        val titles = mutableSetOf<String>()
        val descriptions = mutableSetOf<String>()

        CommitmentListFilter.entries.forEach { filter ->
            val content = EmptyStateCopy.forCommitmentFilter(filter)
            assertNotNull(content)
            assertTrue(content.title.isNotBlank())
            assertTrue(content.description.isNotBlank())
            assertFalse(content.description.contains("Check another filter, or come back later"))

            titles.add(content.title)
            descriptions.add(content.description)
        }

        // All 5 filters should have unique titles and unique descriptions
        assertTrue(titles.size == CommitmentListFilter.entries.size)
        assertTrue(descriptions.size == CommitmentListFilter.entries.size)
    }

    @Test
    fun `test all goal filters have distinct non-empty content`() {
        val titles = mutableSetOf<String>()
        val descriptions = mutableSetOf<String>()

        GoalListFilter.entries.forEach { filter ->
            val content = EmptyStateCopy.forGoalFilter(filter)
            assertNotNull(content)
            assertTrue(content.title.isNotBlank())
            assertTrue(content.description.isNotBlank())
            assertFalse(content.description.contains("Check another filter, or come back later"))

            titles.add(content.title)
            descriptions.add(content.description)
        }

        assertTrue(titles.size == GoalListFilter.entries.size)
        assertTrue(descriptions.size == GoalListFilter.entries.size)
    }

    @Test
    fun `test home section empty states`() {
        assertTrue(EmptyStateCopy.Home.EmptyPersonalPractices.title.isNotBlank())
        assertTrue(EmptyStateCopy.Home.EmptyTodayCommitments.title.isNotBlank())
        assertTrue(EmptyStateCopy.Home.EmptySharedPractices.title.isNotBlank())
    }

    @Test
    fun `test search empty states`() {
        assertTrue(EmptyStateCopy.Search.Idle.title.isNotBlank())
        assertTrue(EmptyStateCopy.Search.NoMatches.title.isNotBlank())
    }
}
