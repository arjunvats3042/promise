package app.promise.android.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.domain.GoalTrackingKind
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
 * Interactive Monthly History Calendar with past check-in inspection.
 * Allows users to browse past months and tap any date to inspect check-in notes,
 * exact timestamps, logged values, and completion status.
 */
@Composable
fun PromiseHistoryCalendar(
    checkIns: List<GoalCheckIn>,
    trackingKind: GoalTrackingKind = GoalTrackingKind.BINARY,
    targetValue: Int? = null,
    modifier: Modifier = Modifier,
    initialDate: LocalDate = LocalDate.now(),
) {
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current
    val today = remember { LocalDate.now() }

    var currentMonth by remember { mutableStateOf(YearMonth.from(initialDate)) }
    var selectedDate by remember { mutableStateOf(initialDate) }

    val checkInMap = remember(checkIns) {
        checkIns.associateBy { it.periodDate }
    }

    val selectedCheckIn = checkInMap[selectedDate.toString()]
    val monthTitle = remember(currentMonth) {
        currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }

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
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentMonth = currentMonth.minusMonths(1)
                    },
                    modifier = Modifier.size(TouchTarget.min),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                        contentDescription = "Previous month",
                        tint = colors.textPrimary,
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
                            text = "Tap to return to current month",
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
                                val checkIn = checkInMap[dateStr]
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                val isFuture = date.isAfter(today)

                                CalendarDayCell(
                                    day = dayNumber,
                                    date = date,
                                    checkIn = checkIn,
                                    isSelected = isSelected,
                                    isToday = isToday,
                                    isFuture = isFuture,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedDate = date
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

        // 5. Selected Day Detail Inspector Card
        AnimatedContent(
            targetState = selectedDate,
            transitionSpec = { fadeIn(Motion.standardTween(Motion.FilterChangeMs)) togetherWith fadeOut(Motion.standardTween(Motion.FilterChangeMs)) },
            label = "dayInspection",
        ) { date ->
            val dateFormatted = remember(date) {
                date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
            }
            val checkIn = checkInMap[date.toString()]
            val isToday = date == today
            val isFuture = date.isAfter(today)

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(Radius.lg)),
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
                            checkIn?.status == GoalCheckInStatus.COMPLETED -> {
                                PromiseStatusChip(label = "Completed ✓", isAccent = true)
                            }
                            checkIn?.status == GoalCheckInStatus.SKIPPED -> {
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

                    if (checkIn != null) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        PromiseHairlineDivider()
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        // Check-in Timestamp & Target Details
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

                        // Reflection Note if present
                        if (checkIn.note.isNotBlank()) {
                            Spacer(modifier = Modifier.height(Spacing.xs + 2.dp))
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
                    } else if (!isFuture && !isToday) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
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
}

@Composable
private fun CalendarDayCell(
    day: Int,
    date: LocalDate,
    checkIn: GoalCheckIn?,
    isSelected: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current

    val cellBg = when {
        isSelected -> colors.accent.copy(alpha = 0.16f)
        isToday -> colors.surfaceMuted
        else -> Color.Transparent
    }

    val borderColor = when {
        isSelected -> colors.accent
        isToday -> colors.accent.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val textColor = when {
        isFuture -> colors.textSecondary.copy(alpha = 0.35f)
        isSelected -> colors.textPrimary
        isToday -> colors.accent
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .padding(1.dp)
            .size(38.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(cellBg)
            .border(1.dp, borderColor, RoundedCornerShape(Radius.sm))
            .pressScale(0.92f, enabled = !isFuture)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = ripple(color = colors.accent),
                onClick = onClick,
            )
            .semantics {
                contentDescription = "Day $day, ${if (checkIn != null) "Completed" else "No check-in"}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                fontSize = 12.sp,
            )

            if (!isFuture) {
                Spacer(modifier = Modifier.height(1.dp))
                when (checkIn?.status) {
                    GoalCheckInStatus.COMPLETED -> {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                        )
                    }
                    GoalCheckInStatus.SKIPPED -> {
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
