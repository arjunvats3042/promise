package app.promise.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.promise.android.domain.GoalCheckIn
import app.promise.android.domain.GoalCheckInStatus
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Spacing
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * A compact 4-week × 7-day grid of check-in history dots.
 * Renders the last [weeks] weeks of check-in activity as colored circles,
 * with Mon–Sun column headers.
 */
@Composable
fun PromiseWeeklyHistoryGrid(
    checkIns: List<GoalCheckIn>,
    modifier: Modifier = Modifier,
    weeks: Int = 4,
    dotSize: Dp = 22.dp,
    today: LocalDate = LocalDate.now(),
) {
    val colors = PromiseThemeColors.current
    val checkInMap = checkIns.associateBy { it.periodDate }

    // Calculate the grid start: go back (weeks) weeks from today,
    // aligned to Monday of that week.
    val todayDow = today.dayOfWeek
    val currentWeekMonday = today.minusDays((todayDow.value - 1).toLong())
    val gridStartMonday = currentWeekMonday.minusWeeks((weeks - 1).toLong())

    // Day-of-week headers
    val dayHeaders = DayOfWeek.entries.map { dow ->
        dow.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault())
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            dayHeaders.forEach { label ->
                Box(
                    modifier = Modifier.size(dotSize),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = colors.textSecondary,
                    )
                }
            }
        }

        // Week rows
        for (weekOffset in 0 until weeks) {
            val weekMonday = gridStartMonday.plusWeeks(weekOffset.toLong())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                for (dayOffset in 0 until 7) {
                    val date = weekMonday.plusDays(dayOffset.toLong())
                    val dateStr = date.toString()
                    val checkIn = checkInMap[dateStr]
                    val isFuture = date.isAfter(today)
                    val isToday = date == today

                    val state = when {
                        isFuture -> DayProgressState.Inactive
                        checkIn?.status == GoalCheckInStatus.COMPLETED -> DayProgressState.Completed
                        checkIn?.status == GoalCheckInStatus.SKIPPED -> DayProgressState.Missed
                        isToday -> DayProgressState.Pending
                        date.isBefore(today) && checkIn == null -> DayProgressState.Inactive
                        else -> DayProgressState.Inactive
                    }

                    val bgColor = when (state) {
                        DayProgressState.Completed -> colors.accent.copy(alpha = 0.18f)
                        DayProgressState.Pending -> colors.surfaceMuted
                        DayProgressState.Missed -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        DayProgressState.Inactive -> colors.surfaceMuted.copy(alpha = 0.4f)
                    }
                    val borderColor = when (state) {
                        DayProgressState.Completed -> colors.accent.copy(alpha = 0.5f)
                        DayProgressState.Pending -> colors.accent.copy(alpha = 0.3f)
                        DayProgressState.Missed -> MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        DayProgressState.Inactive -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    }

                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(1.dp, borderColor, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (state) {
                            DayProgressState.Completed -> Text(
                                text = "✓",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                            DayProgressState.Missed -> Text(
                                text = "–",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            DayProgressState.Pending -> Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent.copy(alpha = 0.5f)),
                            )
                            DayProgressState.Inactive -> Unit
                        }
                    }
                }
            }
        }
    }
}
