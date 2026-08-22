package app.promise.android.ui.search

import app.promise.android.core.events.AppEventBus
import app.promise.android.domain.CommitmentSearchResult
import app.promise.android.domain.GlobalSearchResult
import app.promise.android.domain.GoalSearchResult
import app.promise.android.domain.SearchRepository
import app.promise.android.ui.haptics.FakePromiseHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeHaptics: FakePromiseHaptics
    private lateinit var eventBus: AppEventBus

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeHaptics = FakePromiseHaptics()
        eventBus = AppEventBus()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun queryBlank_emitsIdle() = runTest {
        val repo = FakeSearchRepository()
        val vm = SearchViewModel(repo, fakeHaptics, eventBus)
        backgroundScope.launch { vm.uiState.collect {} }

        assertEquals(SearchUiState.Idle, vm.uiState.value)

        vm.onQueryChange("   ")
        advanceTimeBy(400L)
        advanceUntilIdle()

        assertEquals(SearchUiState.Idle, vm.uiState.value)
    }

    @Test
    fun queryTyped_debounces300ms_andReturnsSuccess() = runTest {
        val repo = FakeSearchRepository(
            result = GlobalSearchResult(
                commitments = listOf(
                    CommitmentSearchResult(
                        id = "c1",
                        title = "Sprint Planning",
                        status = "PENDING",
                        createdAt = "2026-08-22T10:00:00Z",
                        updatedAt = "2026-08-22T10:00:00Z",
                    ),
                ),
                goals = listOf(
                    GoalSearchResult(
                        id = "g1",
                        title = "Sprint Goal",
                        status = "ACTIVE",
                        recurrenceKind = "DAILY",
                        startDate = "2026-08-22",
                        createdAt = "2026-08-22T10:00:00Z",
                        updatedAt = "2026-08-22T10:00:00Z",
                    ),
                ),
            ),
        )
        val vm = SearchViewModel(repo, fakeHaptics, eventBus)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onQueryChange("Sprint")
        advanceTimeBy(100L)
        // Repo should not be called yet due to 300ms debounce
        assertEquals(0, repo.searchCallCount)

        advanceTimeBy(250L)
        advanceUntilIdle()

        assertEquals(1, repo.searchCallCount)
        assertEquals("Sprint", repo.lastQuery)

        val state = vm.uiState.value
        assertTrue(state is SearchUiState.Success)
        val success = state as SearchUiState.Success
        assertEquals(1, success.result.commitments.size)
        assertEquals(1, success.result.goals.size)
    }

    @Test
    fun queryTyped_noResults_emitsEmpty() = runTest {
        val repo = FakeSearchRepository(result = GlobalSearchResult())
        val vm = SearchViewModel(repo, fakeHaptics, eventBus)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onQueryChange("NonExistentItem")
        advanceTimeBy(350L)
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty, vm.uiState.value)
    }

    @Test
    fun clearQuery_resetsToIdle_andHapticsLight() = runTest {
        val repo = FakeSearchRepository()
        val vm = SearchViewModel(repo, fakeHaptics, eventBus)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onQueryChange("Search something")
        vm.clearQuery()

        assertEquals("", vm.query.value)
        assertEquals(listOf("light"), fakeHaptics.events)
    }
}

private class FakeSearchRepository(
    private val result: GlobalSearchResult = GlobalSearchResult(),
    private val error: Throwable? = null,
) : SearchRepository {
    var searchCallCount = 0
    var lastQuery: String? = null

    override suspend fun search(query: String, type: String): GlobalSearchResult {
        searchCallCount++
        lastQuery = query
        error?.let { throw it }
        return result
    }
}
