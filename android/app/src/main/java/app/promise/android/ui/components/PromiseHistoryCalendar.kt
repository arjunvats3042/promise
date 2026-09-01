package app.promise.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalParticipant
import app.promise.android.domain.GoalParticipantRole
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.ui.home.HomeViewModel
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.pressScale
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Interactive Monthly History Calendar with multi-member inspection popover.
 * Allows users to browse past months and tap any date to open a detailed day popover
 * showing check-in notes, exact timestamps, logged values, and each member's status in shared goals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromiseHistoryCalendar(
    checkIns: List<GoalCheckIn>,
    trackingKind: GoalTrackingKind = GoalTrackingKind.BINARY,
    targetValue: Int? = null,
    participants: List<GoalParticipant> = emptyList(),
    isShared: Boolean = false,
    startDate: String? = null,
    modifier: Modifier = Modifier,
    initialDate: LocalDate = LocalDate.now(),
) {
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current
    val today = remember { LocalDate.now() }

    val parsedStartDate = remember(startDate) {
        runCatching {
            startDate?.take(10)?.let { LocalDate.parse(it) }
        }.getOrNull()
    }

    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var selectedDate by remember {
        val candidate = initialDate
        val validCandidate = when {
            candidate.isAfter(today) -> today
            parsedStartDate != null && candidate.isBefore(parsedStartDate) -> parsedStartDate
            else -> candidate
        }
        mutableStateOf(validCandidate)
    }
    var showDayDetailSheet by remember { mutableStateOf(false) }

    // Map date -> list of check-ins on that date
    val checkInsByDate = remember(checkIns) {
        checkIns.groupBy { it.periodDate }
    }

    val selectedDateCheckIns = checkInsByDate[selectedDate.toString()].orEmpty()
    val monthTitle = remember(currentMonth) {
        currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }

    val canGoPrev = parsedStartDate == null || currentMonth.isAfter(YearMonth.from(parsedStartDate))
    val canGoNext = currentMonth.isBefore(YearMonth.from(today))

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // 1. Month Navigation Header
        PromiseCardSurface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        if (canGoPrev) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentMonth = currentMonth.minusMonths(1)
                        }
                    },
                    enabled = canGoPrev,
                    modifier = Modifier.size(TouchTarget.min),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = if (canGoPrev) colors.textPrimary else colors.textSecondary.copy(alpha = 0.3f),
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = monthTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    if (currentMonth != YearMonth.from(today)) {
                        Text(
                            text = "Tap to return to today",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                            modifier = Modifier
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentMonth = YearMonth.from(today)
                                    selectedDate = today
                                }
                                .padding(vertical = 2.dp),
                        )
                    }
                }

                IconButton(
                    onClick = {
                        if (canGoNext) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentMonth = currentMonth.plusMonths(1)
                        }
                    },
                    enabled = canGoNext,
                    modifier = Modifier.size(TouchTarget.min),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Next month",
                        tint = if (canGoNext) colors.textPrimary else colors.textSecondary.copy(alpha = 0.3f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))
            PromiseHairlineDivider()
            Spacer(modifier = Modifier.height(Spacing.sm))

            // 2. Day-of-Week Column Headers (Mon - Sun)
            val dayHeaders = remember {
                DayOfWeek.entries.map { it.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                dayHeaders.forEach { dow ->
                    Text(
                        text = dow.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            // 3. Month Days Matrix
            val firstDayOfMonth = currentMonth.atDay(1)
            val daysInMonth = currentMonth.lengthOfMonth()
            val startDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1 = Mon, 7 = Sun
            val leadingEmptyDays = startDayOfWeek - 1

            val totalCells = leadingEmptyDays + daysInMonth
            val rows = (totalCells + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        for (col in 0 until 7) {
                            val cellIndex = (row * 7) + col
                            val dayNumber = cellIndex - leadingEmptyDays + 1

                            if (dayNumber in 1..daysInMonth) {
                                val date = currentMonth.atDay(dayNumber)
                                val dateStr = date.toString()
                                val dayCheckIns = checkInsByDate[dateStr].orEmpty()
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                val isFuture = date.isAfter(today)
                                val isBeforeStart = parsedStartDate != null && date.isBefore(parsedStartDate)
                                val isClickable = !isFuture && !isBeforeStart

                                CalendarDayCell(
                                    day = dayNumber,
                                    date = date,
                                    dayCheckIns = dayCheckIns,
                                    totalParticipants = if (isShared && participants.isNotEmpty()) participants.size else 1,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    isFuture = isFuture,
                                    isBeforeStart = isBeforeStart,
                                    onClick = {
                                        if (isClickable) {
                                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedDate = date
                                            showDayDetailSheet = true
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            // 4. Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CalendarLegendItem(color = colors.accent, label = "Completed")
                Spacer(modifier = Modifier.width(Spacing.md))
                CalendarLegendItem(color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), label = "Skipped")
                Spacer(modifier = Modifier.width(Spacing.md))
                CalendarLegendItem(color = colors.textSecondary.copy(alpha = 0.4f), label = "Pending")
            }
        }

        // 5. Inline Selected Day Detail Card (Instant Glance)
        DayDetailCard(
            date = selectedDate,
            dayCheckIns = selectedDateCheckIns,
            participants = participants,
            isShared = isShared,
            trackingKind = trackingKind,
            targetValue = targetValue,
            onOpenFullDetails = { showDayDetailSheet = true },
        )
    }

    // 6. Smooth Day Detail Modal Sheet (Interactive Popover)
    if (showDayDetailSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDayDetailSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = Spacing.sm)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.textSecondary.copy(alpha = 0.4f)),
                )
            },
        ) {
            DayDetailSheetContent(
                date = selectedDate,
                dayCheckIns = selectedDateCheckIns,
                participants = participants,
                isShared = isShared,
                trackingKind = trackingKind,
                targetValue = targetValue,
                onClose = { showDayDetailSheet = false },
            )
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    date: LocalDate,
    dayCheckIns: List<GoalCheckIn>,
    totalParticipants: Int,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    isBeforeStart: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    val isClickable = !isFuture && !isBeforeStart

    val cellBg = when {
        isSelected && isClickable -> colors.accent.copy(alpha = 0.16f)
        isToday -> colors.surfaceMuted
        else -> Color.Transparent
    }

    val borderColor = when {
        isSelected && isClickable -> colors.accent
        isToday -> colors.accent.copy(alpha = 0.6f)
        else -> Color.Transparent
    }

    val textColor = when {
        !isClickable -> colors.textSecondary.copy(alpha = 0.28f)
        isSelected -> colors.textPrimary
        isToday -> colors.accent
        else -> colors.textPrimary
    }

    val completedCount = dayCheckIns.count { it.status == GoalCheckInStatus.COMPLETED }
    val skippedCount = dayCheckIns.count { it.status == GoalCheckInStatus.SKIPPED }

    Box(
        modifier = modifier
            .padding(1.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(cellBg)
            .border(1.dp, borderColor, RoundedCornerShape(Radius.sm))
            .pressScale(0.92f, enabled = isClickable)
            .then(
                if (isClickable) {
                    Modifier.clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = ripple(color = colors.accent),
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = "Day $day, $completedCount of $totalParticipants completed"
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if ((isSelected || isToday) && isClickable) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                fontSize = 13.sp,
            )

            if (isClickable) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        completedCount > 0 -> {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent),
                            )
                            if (completedCount > 1) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(colors.accent.copy(alpha = 0.6f)),
                                )
                            }
                        }
                        skippedCount > 0 -> {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.8f)),
                            )
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .clip(CircleShape)
                                    .background(colors.textSecondary.copy(alpha = 0.25f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetailCard(
    date: LocalDate,
    dayCheckIns: List<GoalCheckIn>,
    participants: List<GoalParticipant>,
    isShared: Boolean,
    trackingKind: GoalTrackingKind,
    targetValue: Int?,
    onOpenFullDetails: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val today = remember { LocalDate.now() }
    val isToday = date == today
    val isFuture = date.isAfter(today)

    val dateFormatted = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
    }

    val completedCheckIns = dayCheckIns.filter { it.status == GoalCheckInStatus.COMPLETED }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(Radius.lg))
            .clickable(onClick = onOpenFullDetails),
        color = colors.surfaceRaised,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.cardPadding),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dateFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    if (isToday) {
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                        )
                    }
                }

                when {
                    isFuture -> {
                        PromiseStatusChip(label = "Upcoming", isAccent = false)
                    }
                    isShared && participants.isNotEmpty() -> {
                        val activeMembers = participants.size
                        PromiseStatusChip(
                            label = "${completedCheckIns.size}/$activeMembers Done",
                            isAccent = completedCheckIns.size == activeMembers,
                        )
                    }
                    completedCheckIns.isNotEmpty() -> {
                        PromiseStatusChip(label = "Completed ✓", isAccent = true)
                    }
                    dayCheckIns.any { it.status == GoalCheckInStatus.SKIPPED } -> {
                        PromiseStatusChip(label = "Skipped", isWarning = true)
                    }
                    isToday -> {
                        PromiseStatusChip(label = "Pending Today", isAccent = false)
                    }
                    else -> {
                        PromiseStatusChip(label = "No Check-in", isAccent = false)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.sm))
            PromiseHairlineDivider()
            Spacer(modifier = Modifier.height(Spacing.sm))

            if (isShared && participants.isNotEmpty()) {
                // Shared goal: member check-in breakdown
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    participants.forEach { participant ->
                        val checkIn = dayCheckIns.firstOrNull { it.userId == participant.userId || it.userId == participant.id }
                        MemberCheckInRow(
                            participant = participant,
                            checkIn = checkIn,
                            trackingKind = trackingKind,
                            targetValue = targetValue,
                            isFuture = isFuture,
                        )
                    }
                }
            } else {
                // Individual goal check-in inspection
                val checkIn = dayCheckIns.firstOrNull()
                if (checkIn != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                        val timeLabel = formatCheckInTime(checkIn.checkedAt.ifBlank { checkIn.createdAt })
                        Text(
                            text = if (timeLabel.isNotBlank()) "Checked in at $timeLabel" else "Check-in recorded",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )

                        if (trackingKind == GoalTrackingKind.COUNT && checkIn.value != null) {
                            Text(
                                text = "· Value: ${checkIn.value}${targetValue?.let { " of $it target" } ?: ""}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent,
                            )
                        }
                    }

                    if (checkIn.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(colors.surfaceMuted),
                            color = colors.surfaceMuted,
                        ) {
                            Row(
                                modifier = Modifier.padding(Spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FormatQuote,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = checkIn.note,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }
                } else if (!isFuture) {
                    Text(
                        text = "No check-in was recorded for this day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayDetailSheetContent(
    date: LocalDate,
    dayCheckIns: List<GoalCheckIn>,
    participants: List<GoalParticipant>,
    isShared: Boolean,
    trackingKind: GoalTrackingKind,
    targetValue: Int?,
    onClose: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val today = remember { LocalDate.now() }
    val isToday = date == today
    val isFuture = date.isAfter(today)

    val dateFormatted = remember(date) {
        date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
    }

    val completedCheckIns = dayCheckIns.filter { it.status == GoalCheckInStatus.COMPLETED }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(bottom = Spacing.xxl),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateFormatted,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Text(
                    text = if (isToday) "Today's Ledger" else "Historical Check-in Record",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier.size(TouchTarget.min),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = colors.textSecondary,
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.md))
        PromiseHairlineDivider()
        Spacer(modifier = Modifier.height(Spacing.md))

        if (isShared && participants.isNotEmpty()) {
            Text(
                text = "TEAM ACTIVITY (${completedCheckIns.size} of ${participants.size} checked in)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                participants.forEach { participant ->
                    val checkIn = dayCheckIns.firstOrNull { it.userId == participant.userId || it.userId == participant.id }
                    MemberCheckInDetailCard(
                        participant = participant,
                        checkIn = checkIn,
                        trackingKind = trackingKind,
                        targetValue = targetValue,
                        isFuture = isFuture,
                    )
                }
            }
        } else {
            val checkIn = dayCheckIns.firstOrNull()
            if (checkIn != null) {
                MemberCheckInDetailCard(
                    participant = null,
                    checkIn = checkIn,
                    trackingKind = trackingKind,
                    targetValue = targetValue,
                    isFuture = isFuture,
                )
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.md))
                        .background(colors.surfaceMuted)
                        .padding(Spacing.cardPadding),
                    color = colors.surfaceMuted,
                ) {
                    Text(
                        text = if (isFuture) "This date is in the future." else "No check-in was recorded for this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberCheckInRow(
    participant: GoalParticipant,
    checkIn: GoalCheckIn?,
    trackingKind: GoalTrackingKind,
    targetValue: Int?,
    isFuture: Boolean,
) {
    val colors = PromiseThemeColors.current
    val initials = HomeViewModel.initialsFor(participant.userName.ifBlank { "User" })

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.weight(1f),
        ) {
            PromiseAvatar(
                initials = initials,
                photoPath = participant.avatarUrl,
                size = AvatarSize.SM,
            )
            Column {
                Text(
                    text = participant.userName.ifBlank { "Member" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                )
                if (checkIn != null) {
                    val timeLabel = formatCheckInTime(checkIn.checkedAt.ifBlank { checkIn.createdAt })
                    if (timeLabel.isNotBlank()) {
                        Text(
                            text = "at $timeLabel",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        when {
            isFuture -> {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            checkIn?.status == GoalCheckInStatus.COMPLETED -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = if (trackingKind == GoalTrackingKind.COUNT && checkIn.value != null) "${checkIn.value}" else "Done",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accent,
                    )
                }
            }
            checkIn?.status == GoalCheckInStatus.SKIPPED -> {
                Text(
                    text = "Skipped",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            else -> {
                Text(
                    text = "Pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun MemberCheckInDetailCard(
    participant: GoalParticipant?,
    checkIn: GoalCheckIn?,
    trackingKind: GoalTrackingKind,
    targetValue: Int?,
    isFuture: Boolean,
) {
    val colors = PromiseThemeColors.current
    val name = participant?.userName?.ifBlank { "Member" } ?: checkIn?.userName?.ifBlank { "You" } ?: "You"
    val initials = HomeViewModel.initialsFor(name)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(Radius.md)),
        color = colors.surfaceMuted,
    ) {
        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    PromiseAvatar(
                        initials = initials,
                        photoPath = participant?.avatarUrl ?: checkIn?.userAvatarUrl,
                        size = AvatarSize.MD,
                    )
                    Column {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        if (participant?.role == GoalParticipantRole.OWNER) {
                            Text(
                                text = "Host / Creator",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.accent,
                            )
                        }
                    }
                }

                when {
                    isFuture -> {
                        PromiseStatusChip(label = "Upcoming", isAccent = false)
                    }
                    checkIn?.status == GoalCheckInStatus.COMPLETED -> {
                        PromiseStatusChip(label = "Completed ✓", isAccent = true)
                    }
                    checkIn?.status == GoalCheckInStatus.SKIPPED -> {
                        PromiseStatusChip(label = "Skipped", isWarning = true)
                    }
                    else -> {
                        PromiseStatusChip(label = "No Check-in", isAccent = false)
                    }
                }
            }

            if (checkIn != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(15.dp),
                    )
                    val timeLabel = formatCheckInTime(checkIn.checkedAt.ifBlank { checkIn.createdAt })
                    Text(
                        text = if (timeLabel.isNotBlank()) "Checked in at $timeLabel" else "Check-in logged",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )

                    if (trackingKind == GoalTrackingKind.COUNT && checkIn.value != null) {
                        Text(
                            text = "· Value: ${checkIn.value}${targetValue?.let { " of $it target" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                        )
                    }
                }

                if (checkIn.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.surfaceRaised),
                        color = colors.surfaceRaised,
                    ) {
                        Row(
                            modifier = Modifier.padding(Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FormatQuote,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = checkIn.note,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarLegendItem(color: Color, label: String) {
    val colors = PromiseThemeColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            fontSize = 10.sp,
        )
    }
}

private fun formatCheckInTime(iso: String): String {
    if (iso.isBlank()) return ""
    return runCatching {
        val instant = Instant.parse(iso)
        val zdt = instant.atZone(ZoneId.systemDefault())
        zdt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }.getOrDefault("")
}
