package app.promise.android.data.goals

import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalParticipantRole
import app.promise.android.domain.GoalParticipantStatus
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalMappersTest {
    @Test
    fun goalDto_mapsProgressAndStreakFromServer() {
        val dto = GoalDto(
            id = "g1",
            title = "Read",
            status = "ACTIVE",
            startDate = "2026-08-01",
            recurrenceKind = "DAILY",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            progress = GoalProgressDto(
                currentPeriod = GoalPeriodCountsDto(required = 1, completed = 1),
                weekProgress = GoalPeriodCountsDto(required = 7, completed = 4),
                consistencyPercent = 57,
            ),
            currentStreak = 3,
        )
        val goal = dto.toDomain()
        assertEquals(GoalStatus.ACTIVE, goal.status)
        assertEquals(GoalRecurrenceKind.DAILY, goal.recurrenceKind)
        assertEquals(1, goal.progress.currentPeriod.completed)
        assertEquals(4, goal.progress.weekProgress.completed)
        assertEquals(57, goal.progress.consistencyPercent)
        assertEquals(3, goal.currentStreak)
    }

    @Test
    fun checkInDto_mapsCompletedAndSkippedOnly() {
        val completed = GoalCheckInDto(
            id = "c1",
            periodDate = "2026-08-20",
            status = "COMPLETED",
            checkedAt = "2026-08-20T12:00:00Z",
            createdAt = "2026-08-20T12:00:00Z",
            updatedAt = "2026-08-20T12:00:00Z",
        ).toDomain()
        assertEquals(GoalCheckInStatus.COMPLETED, completed.status)
        assertNull(completed.value)

        val skipped = GoalCheckInDto(
            id = "c2",
            periodDate = "2026-08-19",
            status = "SKIPPED",
            checkedAt = "2026-08-19T12:00:00Z",
            createdAt = "2026-08-19T12:00:00Z",
            updatedAt = "2026-08-19T12:00:00Z",
        ).toDomain()
        assertEquals(GoalCheckInStatus.SKIPPED, skipped.status)
    }

    @Test
    fun trackingKind_mapsCount() {
        val dto = GoalDto(
            id = "g1",
            title = "Pages",
            status = "ACTIVE",
            startDate = "2026-08-01",
            recurrenceKind = "N_PER_PERIOD",
            trackingKind = "COUNT",
            targetValue = 20,
            targetUnit = "pages",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
        )
        val goal = dto.toDomain()
        assertEquals(GoalTrackingKind.COUNT, goal.trackingKind)
        assertEquals(20, goal.targetValue)
        assertEquals("pages", goal.targetUnit)
    }

    @Test
    fun pageNumberFromNext_parsesQuery() {
        assertEquals(2, goalPageNumberFromNext("https://example.com/api/v1/goals/?page=2"))
        assertNull(goalPageNumberFromNext(null))
    }

    @Test
    fun sharedGoal_mapsParticipantsAndCollective() {
        val dto = GoalDto(
            id = "g2",
            title = "Run together",
            status = "ACTIVE",
            startDate = "2026-08-01",
            recurrenceKind = "DAILY",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            membershipRole = "OWNER",
            membershipStatus = "ACTIVE",
            participants = listOf(
                GoalParticipantDto(
                    id = "p1",
                    userId = "u1",
                    userName = "Alice",
                    role = "OWNER",
                    status = "ACTIVE",
                    invitedAt = "2026-08-01T00:00:00Z",
                    joinedAt = "2026-08-01T00:00:00Z",
                    leftAt = null,
                ),
                GoalParticipantDto(
                    id = "p2",
                    userId = "u2",
                    userName = "Bob",
                    role = "PARTICIPANT",
                    status = "INVITED",
                    invitedAt = "2026-08-02T00:00:00Z",
                    joinedAt = null,
                    leftAt = null,
                ),
            ),
            collectiveProgress = CollectiveProgressDto(
                currentPeriod = CollectivePeriodCountsDto(
                    required = 2,
                    completed = 1,
                    requiredParticipants = 2,
                    completedParticipants = 1,
                ),
                weekProgress = CollectivePeriodCountsDto(required = 14, completed = 9),
            ),
        )
        val goal = dto.toDomain()
        assertTrue(goal.isShared)
        assertTrue(goal.isOwnerViewer)
        assertEquals(GoalParticipantRole.OWNER, goal.membershipRole)
        assertEquals(GoalParticipantStatus.ACTIVE, goal.membershipStatus)
        assertEquals(2, goal.participants.size)
        assertEquals("Alice", goal.participants[0].userName)
        assertEquals(GoalParticipantRole.PARTICIPANT, goal.participants[1].role)
        assertEquals(GoalParticipantStatus.INVITED, goal.participants[1].status)
        assertNotNull(goal.collectiveProgress)
        assertEquals(1, goal.collectiveProgress!!.currentPeriod.completed)
        assertEquals(2, goal.collectiveProgress.currentPeriod.requiredParticipants)
    }

    @Test
    fun invitePreviewDto_isDetectedAndMapped() {
        val dto = GoalDto(
            id = "g3",
            title = "Morning run",
            startDate = "2026-08-01",
            recurrenceKind = "DAILY",
            inviterUserId = "u1",
            inviterName = "Alice",
            invitationStatus = "INVITED",
            invitationExpiresAt = "2026-09-01T00:00:00Z",
        )
        assertTrue(dto.isInvitePreview())
        val preview = dto.toInvitePreview()
        assertEquals("g3", preview.id)
        assertEquals("Morning run", preview.title)
        assertEquals("u1", preview.inviterUserId)
        assertEquals("Alice", preview.inviterName)
        assertEquals(GoalParticipantStatus.INVITED, preview.invitationStatus)
        assertEquals("2026-09-01T00:00:00Z", preview.invitationExpiresAt)

        val listItem = dto.toListItem()
        assertTrue(listItem is GoalListItem.Invite)

        val detail = dto.toDetail()
        assertTrue(detail is GoalDetail.Invite)
    }

    @Test
    fun personalGoal_isNotSharedAndIsOwner() {
        val dto = GoalDto(
            id = "g4",
            title = "Read",
            status = "ACTIVE",
            startDate = "2026-08-01",
            recurrenceKind = "DAILY",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
        )
        assertFalse(dto.isInvitePreview())
        val goal = dto.toDomain()
        assertFalse(goal.isShared)
        assertTrue(goal.isOwnerViewer)
        assertNull(goal.membershipRole)
        assertTrue(goal.canPause)
        assertTrue(goal.canCancel)

        val listItem = dto.toListItem()
        assertTrue(listItem is GoalListItem.Membership)

        val detail = dto.toDetail()
        assertTrue(detail is GoalDetail.Full)
    }

    @Test
    fun participantRole_canLeaveButNotPause() {
        val dto = GoalDto(
            id = "g5",
            title = "Read",
            status = "ACTIVE",
            startDate = "2026-08-01",
            recurrenceKind = "DAILY",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
            membershipRole = "PARTICIPANT",
            membershipStatus = "ACTIVE",
        )
        val goal = dto.toDomain()
        assertFalse(goal.isOwnerViewer)
        assertTrue(goal.canLeave)
        assertFalse(goal.canPause)
        assertFalse(goal.canCancel)
        assertFalse(goal.canCompleteGoal)
    }

    @Test
    fun pinInvites_movesInvitesBeforeMemberships() {
        val invite = GoalDto(
            id = "i1",
            title = "Shared",
            startDate = "2026-08-01",
            recurrenceKind = "DAILY",
            inviterUserId = "u1",
            inviterName = "Alice",
            invitationStatus = "INVITED",
        ).toListItem()
        val membership = GoalDto(
            id = "g1",
            title = "Personal",
            status = "ACTIVE",
            startDate = "2026-08-01",
            recurrenceKind = "DAILY",
            createdAt = "2026-08-01T00:00:00Z",
            updatedAt = "2026-08-01T00:00:00Z",
        ).toListItem()
        val pinned = pinInvites(listOf(membership, invite))
        assertTrue(pinned[0] is GoalListItem.Invite)
        assertTrue(pinned[1] is GoalListItem.Membership)
    }
}
