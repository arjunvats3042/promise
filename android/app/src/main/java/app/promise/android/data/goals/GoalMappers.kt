package app.promise.android.data.goals

import app.promise.android.data.commitments.pageNumberFromNext
import app.promise.android.domain.CollectivePeriodCounts
import app.promise.android.domain.CollectiveProgress
import app.promise.android.domain.Goal
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalDetail
import app.promise.android.domain.GoalInvitePreview
import app.promise.android.domain.GoalListItem
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalParticipantRole
import app.promise.android.domain.GoalParticipantStatus
import app.promise.android.domain.GoalPeriodCounts
import app.promise.android.domain.GoalPeriodUnit
import app.promise.android.domain.GoalProgress
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalStatus
import app.promise.android.domain.GoalTrackingKind

fun GoalDto.isInvitePreview(): Boolean = invitationStatus != null || inviterUserId != null

fun GoalDto.toInvitePreview(): GoalInvitePreview {
    return GoalInvitePreview(
        id = id,
        title = title,
        description = description,
        timezone = timezone,
        startDate = startDate,
        endDate = endDate,
        recurrenceKind = recurrenceKind.toRecurrenceKind(),
        weekdays = weekdays,
        periodUnit = periodUnit?.toPeriodUnit(),
        timesPerPeriod = timesPerPeriod,
        trackingKind = trackingKind.toTrackingKind(),
        targetValue = targetValue,
        targetUnit = targetUnit,
        inviterUserId = inviterUserId ?: "",
        inviterName = inviterName ?: "",
        invitationStatus = (invitationStatus ?: "INVITED").toParticipantStatus(),
        invitationExpiresAt = invitationExpiresAt,
    )
}

fun GoalDto.toDomain(): Goal {
    return Goal(
        id = id,
        title = title,
        description = description,
        status = status.toGoalStatus(),
        timezone = timezone,
        startDate = startDate,
        endDate = endDate,
        recurrenceKind = recurrenceKind.toRecurrenceKind(),
        weekdays = weekdays,
        periodUnit = periodUnit?.toPeriodUnit(),
        timesPerPeriod = timesPerPeriod,
        trackingKind = trackingKind.toTrackingKind(),
        targetValue = targetValue,
        targetUnit = targetUnit,
        source = source,
        pausedAt = pausedAt,
        completedAt = completedAt,
        cancelledAt = cancelledAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isEnded = isEnded,
        progress = progress.toDomain(),
        currentStreak = currentStreak,
        collectiveProgress = collectiveProgress?.toDomain(),
        participants = participants.map { it.toDomain() },
        membershipRole = membershipRole?.toParticipantRole(),
        membershipStatus = membershipStatus?.toParticipantStatus(),
    )
}

fun GoalDto.toListItem(): GoalListItem {
    return if (isInvitePreview()) GoalListItem.Invite(toInvitePreview())
    else GoalListItem.Membership(toDomain())
}

fun GoalDto.toDetail(): GoalDetail {
    return if (isInvitePreview()) GoalDetail.Invite(toInvitePreview())
    else GoalDetail.Full(toDomain())
}

fun GoalProgressDto.toDomain(): GoalProgress {
    return GoalProgress(
        currentPeriod = currentPeriod.toDomain(),
        weekProgress = weekProgress.toDomain(),
        consistencyPercent = consistencyPercent,
    )
}

fun GoalPeriodCountsDto.toDomain(): GoalPeriodCounts {
    return GoalPeriodCounts(
        required = required,
        completed = completed,
        value = value,
        targetValue = targetValue,
    )
}

fun CollectiveProgressDto.toDomain(): CollectiveProgress {
    return CollectiveProgress(
        currentPeriod = currentPeriod.toDomain(),
        weekProgress = weekProgress.toDomain(),
    )
}

fun CollectivePeriodCountsDto.toDomain(): CollectivePeriodCounts {
    return CollectivePeriodCounts(
        required = required,
        completed = completed,
        requiredParticipants = requiredParticipants,
        completedParticipants = completedParticipants,
        valueSum = valueSum,
        targetSum = targetSum,
    )
}

fun GoalParticipantDto.toDomain(): GoalParticipant {
    return GoalParticipant(
        id = id,
        userId = userId,
        userName = userName,
        role = role.toParticipantRole(),
        status = status.toParticipantStatus(),
        invitedAt = invitedAt,
        joinedAt = joinedAt,
        leftAt = leftAt,
    )
}

fun GoalCheckInDto.toDomain(): GoalCheckIn {
    return GoalCheckIn(
        id = id,
        periodDate = periodDate,
        status = status.toCheckInStatus(),
        value = value,
        note = note,
        checkedAt = checkedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun String.toGoalStatus(): GoalStatus =
    runCatching { GoalStatus.valueOf(this) }.getOrDefault(GoalStatus.ACTIVE)

fun String.toRecurrenceKind(): GoalRecurrenceKind =
    runCatching { GoalRecurrenceKind.valueOf(this) }.getOrDefault(GoalRecurrenceKind.DAILY)

fun String.toPeriodUnit(): GoalPeriodUnit =
    runCatching { GoalPeriodUnit.valueOf(this) }.getOrDefault(GoalPeriodUnit.WEEK)

fun String.toTrackingKind(): GoalTrackingKind =
    runCatching { GoalTrackingKind.valueOf(this) }.getOrDefault(GoalTrackingKind.BINARY)

fun String.toCheckInStatus(): GoalCheckInStatus =
    runCatching { GoalCheckInStatus.valueOf(this) }.getOrDefault(GoalCheckInStatus.COMPLETED)

fun String.toParticipantRole(): GoalParticipantRole =
    runCatching { GoalParticipantRole.valueOf(this) }.getOrDefault(GoalParticipantRole.PARTICIPANT)

fun String.toParticipantStatus(): GoalParticipantStatus =
    runCatching { GoalParticipantStatus.valueOf(this) }.getOrDefault(GoalParticipantStatus.ACTIVE)

fun pinInvites(items: List<GoalListItem>): List<GoalListItem> {
    val invites = items.filterIsInstance<GoalListItem.Invite>()
    val memberships = items.filterIsInstance<GoalListItem.Membership>()
    return invites + memberships
}

fun goalPageNumberFromNext(next: String?): Int? = pageNumberFromNext(next)
