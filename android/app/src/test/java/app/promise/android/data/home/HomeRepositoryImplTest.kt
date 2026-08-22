package app.promise.android.data.home

import app.promise.android.core.ErrorKind
import app.promise.android.data.network.ApiException
import app.promise.android.domain.CheckInInput
import app.promise.android.domain.Commitment
import app.promise.android.domain.CommitmentListFilter
import app.promise.android.domain.CommitmentPage
import app.promise.android.domain.CommitmentRepository
import app.promise.android.domain.CommitmentStatus
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.DuePrecision
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInPage
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalListFilter
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalPage
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalPeriodCounts
import app.promise.android.domain.GoalProgress
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalRepository
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRepositoryImplTest {
    @Test
    fun bothSuccess_mapsCommitmentsAndPractices() = runTest {
        val commitments = FakeCommitmentRepository(
            openItems = listOf(
                commitment("c1", dueAt = "2026-08-21T18:29:59Z", isOverdue = true),
            ),
        )
        val goals = FakeGoalRepository(
            active = listOf(goal("g1", required = 1, completed = 0)),
        )
        val repo = HomeRepositoryImpl(commitments, goals)
        val feed = repo.loadFeed("Asia/Kolkata")
        assertEquals(1, feed.commitments.size)
        assertEquals(1, feed.practices.size)
        assertNull(feed.commitmentsError)
        assertNull(feed.practicesError)
        assertEquals(CommitmentRepository.HOME_PAGE_SIZE, commitments.lastPageSize)
        assertEquals(GoalRepository.HOME_PAGE_SIZE, goals.lastPageSize)
    }

    @Test
    fun commitmentsFail_goalsSucceed_partial() = runTest {
        val repo = HomeRepositoryImpl(
            FakeCommitmentRepository(error = ApiException(status = null, code = "NETWORK")),
            FakeGoalRepository(active = listOf(goal("g1", 1, 0))),
        )
        val feed = repo.loadFeed("UTC")
        assertTrue(feed.commitments.isEmpty())
        assertEquals(1, feed.practices.size)
        assertTrue(feed.commitmentsError is ErrorKind.Network)
        assertNull(feed.practicesError)
    }

    @Test
    fun goalsFail_commitmentsSucceed_partial() = runTest {
        val repo = HomeRepositoryImpl(
            FakeCommitmentRepository(
                openItems = listOf(commitment("c1", dueAt = "2026-08-21T18:29:59Z", isOverdue = true)),
            ),
            FakeGoalRepository(error = ApiException(status = 429, code = "RATE_LIMITED", retryAfterSeconds = 5)),
        )
        val feed = repo.loadFeed("UTC")
        assertEquals(1, feed.commitments.size)
        assertTrue(feed.practices.isEmpty())
        assertNull(feed.commitmentsError)
        assertTrue(feed.practicesError is ErrorKind.RateLimited)
    }

    @Test
    fun bothEmpty_readyEmpty() = runTest {
        val feed = HomeRepositoryImpl(FakeCommitmentRepository(), FakeGoalRepository()).loadFeed("UTC")
        assertTrue(feed.commitments.isEmpty())
        assertTrue(feed.practices.isEmpty())
    }

    @Test
    fun bothFail_throws() = runTest {
        val repo = HomeRepositoryImpl(
            FakeCommitmentRepository(error = ApiException(status = null, code = "NETWORK")),
            FakeGoalRepository(error = ApiException(status = null, code = "NETWORK")),
        )
        try {
            repo.loadFeed("UTC")
            throw AssertionError("expected")
        } catch (e: ApiException) {
            assertEquals("NETWORK", e.code)
        }
    }

    @Test
    fun completeAndCheckIn_delegate() = runTest {
        val commitments = FakeCommitmentRepository()
        val goals = FakeGoalRepository()
        val repo = HomeRepositoryImpl(commitments, goals)
        repo.completeCommitment("c1")
        repo.checkInPractice("g1", CheckInInput(status = GoalCheckInStatus.COMPLETED))
        assertEquals("c1", commitments.completedId)
        assertEquals("g1", goals.checkedInId)
    }
}

private class FakeCommitmentRepository(
    private val openItems: List<Commitment> = emptyList(),
    private val error: ApiException? = null,
) : CommitmentRepository {
    var lastPageSize: Int? = null
    var completedId: String? = null

    override suspend fun list(
        filter: CommitmentListFilter,
        page: Int,
        timeZoneId: String,
        pageSize: Int,
    ): CommitmentPage {
        error?.let { throw it }
        lastPageSize = pageSize
        return CommitmentPage(openItems, null)
    }

    override suspend fun get(id: String) = openItems.first()
    override suspend fun create(input: CreateCommitmentInput) = openItems.first()
    override suspend fun complete(id: String): Commitment {
        completedId = id
        return openItems.firstOrNull() ?: commitment(id, null, false)
    }
    override suspend fun snooze(id: String, snoozedUntilIso: String) = openItems.first()
    override suspend fun unsnooze(id: String) = openItems.first()
    override suspend fun wait(id: String) = openItems.first()
    override suspend fun cancel(id: String) = openItems.first()
}

private class FakeGoalRepository(
    private val active: List<Goal> = emptyList(),
    private val error: ApiException? = null,
) : GoalRepository {
    var lastPageSize: Int? = null
    var checkedInId: String? = null

    override suspend fun list(filter: GoalListFilter, page: Int, pageSize: Int): GoalPage {
        error?.let { throw it }
        lastPageSize = pageSize
        return GoalPage(active.map { GoalListItem.Membership(it) }, null)
    }

    override suspend fun get(id: String) = active.first()
    override suspend fun getDetail(id: String): GoalDetail = GoalDetail.Full(active.first())
    override suspend fun create(input: CreateGoalInput) = active.first()
    override suspend fun pause(id: String) = active.first()
    override suspend fun resume(id: String) = active.first()
    override suspend fun complete(id: String) = active.first()
    override suspend fun cancel(id: String) = active.first()
    override suspend fun checkIn(id: String, input: CheckInInput): GoalCheckIn {
        checkedInId = id
        return GoalCheckIn(
            id = "ci",
            periodDate = "2026-08-21",
            status = input.status,
            value = input.value,
            note = "",
            checkedAt = "2026-08-21T12:00:00Z",
            createdAt = "2026-08-21T12:00:00Z",
            updatedAt = "2026-08-21T12:00:00Z",
        )
    }

    override suspend fun listCheckIns(
        id: String,
        startDate: String?,
        endDate: String?,
        page: Int,
    ) = GoalCheckInPage(emptyList(), null)

    override suspend fun inviteParticipant(goalId: String, userId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun listParticipants(goalId: String): List<GoalParticipant> = emptyList()

    override suspend fun acceptInvitation(goalId: String): GoalDetail =
        GoalDetail.Full(active.first())

    override suspend fun declineInvitation(goalId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun removeParticipant(goalId: String, userId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun leave(goalId: String): GoalParticipant =
        throw UnsupportedOperationException()

    override suspend fun listChatMessages(
        goalId: String,
        limit: Int,
        beforeCreatedAt: String?,
        beforeId: String?,
    ): List<app.promise.android.domain.ChatMessage> = emptyList()

    override suspend fun sendChatMessage(
        goalId: String,
        body: String,
    ): app.promise.android.domain.ChatMessage = throw UnsupportedOperationException()

    override suspend fun markChatRead(
        goalId: String,
        lastReadMessageId: String,
    ): app.promise.android.domain.GoalChatReadState =
        app.promise.android.domain.GoalChatReadState(lastReadMessageId, "2026-08-21T12:00:00Z")

    override suspend fun getChatSummary(
        goalId: String,
    ): app.promise.android.domain.GoalChatSummary =
        app.promise.android.domain.GoalChatSummary(0, null)

    override suspend fun listActivity(
        goalId: String,
        limit: Int,
        beforeCreatedAt: String?,
        beforeId: String?,
    ): List<app.promise.android.domain.GoalActivityItem> = emptyList()
}

private fun commitment(id: String, dueAt: String?, isOverdue: Boolean) = Commitment(
    id = id,
    title = id,
    description = "",
    status = CommitmentStatus.PENDING,
    dueAt = dueAt,
    duePrecision = DuePrecision.DATE,
    source = "MANUAL",
    snoozedUntil = null,
    completedAt = null,
    cancelledAt = null,
    createdAt = "2026-08-01T00:00:00Z",
    updatedAt = "2026-08-01T00:00:00Z",
    isOverdue = isOverdue,
)

private fun goal(id: String, required: Int, completed: Int) = Goal(
    id = id,
    title = id,
    description = "",
    status = GoalStatus.ACTIVE,
    timezone = "UTC",
    startDate = "2026-08-01",
    endDate = null,
    recurrenceKind = GoalRecurrenceKind.DAILY,
    weekdays = emptyList(),
    periodUnit = null,
    timesPerPeriod = null,
    trackingKind = GoalTrackingKind.BINARY,
    targetValue = null,
    targetUnit = "",
    source = "MANUAL",
    pausedAt = null,
    completedAt = null,
    cancelledAt = null,
    createdAt = "2026-08-01T00:00:00Z",
    updatedAt = "2026-08-01T00:00:00Z",
    isEnded = false,
    progress = GoalProgress(
        currentPeriod = GoalPeriodCounts(required, completed),
        weekProgress = GoalPeriodCounts(7, completed),
        consistencyPercent = 50,
    ),
    currentStreak = 2,
)
