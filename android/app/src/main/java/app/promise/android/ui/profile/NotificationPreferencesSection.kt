package app.promise.android.ui.profile

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import app.promise.android.domain.NotificationHistoryItem
import app.promise.android.domain.NotificationPreferences
import app.promise.android.domain.NotificationPreferencesPatch
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun NotificationPreferencesSection(
    preferences: NotificationPreferences?,
    history: List<NotificationHistoryItem> = emptyList(),
    onUpdate: (NotificationPreferencesPatch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = PromiseThemeColors.current
    val isOsPermissionGranted = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    var selectedTab by remember { mutableIntStateOf(0) }

    val prefs = preferences ?: return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Notifications & Reminders",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.sm))

        // OS Permission warning banner if disabled at OS level
        if (!isOsPermissionGranted) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(colors.surfaceMuted)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(Radius.md))
                    .padding(Spacing.md),
            ) {
                Text(
                    text = "Notifications are blocked in Android settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Turn them on in settings to receive timely reminders for commitments and practices.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("Open Settings", color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
        }

        // Sub-tabs: Preferences vs History
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = colors.surfaceMuted,
            contentColor = colors.textPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = MaterialTheme.colorScheme.primary,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md)),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = {
                    Text(
                        "Preferences",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedTab == 0) colors.textPrimary else colors.textSecondary,
                    )
                },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = {
                    Text(
                        if (history.isNotEmpty()) "History (${history.size})" else "History",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedTab == 1) colors.textPrimary else colors.textSecondary,
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        if (selectedTab == 0) {
            // Preferences View
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            ) {
                PreferenceSwitchRow(
                    title = "Reminders",
                    subtitle = "Timely prompts for promises & practices",
                    checked = prefs.enabled,
                    onCheckedChange = { onUpdate(NotificationPreferencesPatch(enabled = it)) },
                )

                AnimatedVisibility(
                    visible = prefs.enabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "COMMITMENTS",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        PreferenceSwitchRow(
                            title = "Before deadlines",
                            subtitle = "Remind me before a commitment is due",
                            checked = prefs.commitmentsDueSoon,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(commitmentsDueSoon = it)) },
                        )
                        PreferenceSwitchRow(
                            title = "At deadline",
                            subtitle = "Alert me at the exact moment",
                            checked = prefs.commitmentsDueNow,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(commitmentsDueNow = it)) },
                        )
                        PreferenceSwitchRow(
                            title = "Unfinished review",
                            subtitle = "Gentle reminder if a promise is past due",
                            checked = prefs.commitmentsOverdue,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(commitmentsOverdue = it)) },
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "PRACTICES",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        PreferenceSwitchRow(
                            title = "Daily practice",
                            subtitle = "Morning prompt for active goals",
                            checked = prefs.goalsTodayPractice,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(goalsTodayPractice = it)) },
                        )
                        PreferenceSwitchRow(
                            title = "Streak protection",
                            subtitle = "Evening check-in to keep momentum",
                            checked = prefs.goalsStreakProtection,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(goalsStreakProtection = it)) },
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "SHARED GOALS",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        PreferenceSwitchRow(
                            title = "Group activity",
                            subtitle = "When participants join, leave, or ownership changes",
                            checked = prefs.sharedGoalsActivity,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(sharedGoalsActivity = it)) },
                        )
                        PreferenceSwitchRow(
                            title = "Group chat messages",
                            subtitle = "Messages from shared goal companions",
                            checked = prefs.sharedGoalsChat,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(sharedGoalsChat = it)) },
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "WEEKLY DIGEST",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        PreferenceSwitchRow(
                            title = "Sunday recap",
                            subtitle = "Opt-in weekly summary of completed commitments & practices",
                            checked = prefs.weeklyDigestEnabled,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(weeklyDigestEnabled = it)) },
                        )

                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "QUIET HOURS",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        PreferenceSwitchRow(
                            title = "Pause during rest",
                            subtitle = "Silence non-urgent alerts during sleep",
                            checked = prefs.quietHoursEnabled,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(quietHoursEnabled = it)) },
                        )

                        if (prefs.quietHoursEnabled) {
                            QuietHoursTimePickerRow(
                                startIso = prefs.quietHoursStart,
                                endIso = prefs.quietHoursEnd,
                                onSave = { start, end ->
                                    onUpdate(NotificationPreferencesPatch(quietHoursStart = start, quietHoursEnd = end))
                                },
                            )
                        }
                    }
                }
            }
        } else {
            // Notification History View
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .background(colors.surfaceMuted)
                    .padding(Spacing.md),
            ) {
                if (history.isEmpty()) {
                    Text(
                        text = "No recent notifications.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = Spacing.md),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        history.forEach { item ->
                            NotificationHistoryCard(item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationHistoryCard(
    item: NotificationHistoryItem,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(Radius.sm))
            .padding(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .padding(horizontal = Spacing.xs, vertical = 2.dp),
            ) {
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = formatHistoryDate(item.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xxs))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
        )
        if (item.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
    }
}

private fun formatHistoryDate(isoString: String): String {
    return try {
        val parts = isoString.split("T")
        if (parts.size >= 2) {
            val date = parts[0]
            val time = parts[1].substring(0, 5)
            "$date $time"
        } else {
            isoString
        }
    } catch (_: Exception) {
        isoString
    }
}

@Composable
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = Spacing.md)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceMuted,
            ),
        )
    }
}

@Composable
private fun QuietHoursTimePickerRow(
    startIso: String,
    endIso: String,
    onSave: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = PromiseThemeColors.current

    val startTime = parseLocalTime(startIso, 22, 0)
    val endTime = parseLocalTime(endIso, 8, 0)

    val formatted = "${formatTime(startTime)} – ${formatTime(endTime)}"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                showQuietHoursDialogs(context, startTime, endTime, onSave)
            }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Active hours",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textPrimary,
            )
            Text(
                text = formatted,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(onClick = { showQuietHoursDialogs(context, startTime, endTime, onSave) }) {
            Text("Edit", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun showQuietHoursDialogs(
    context: Context,
    initialStart: LocalTime,
    initialEnd: LocalTime,
    onSave: (String, String) -> Unit,
) {
    var selectedStart = initialStart
    var selectedEnd = initialEnd

    // Step 1: Pick start time
    TimePickerDialog(
        context,
        { _, startHour, startMinute ->
            selectedStart = LocalTime.of(startHour, startMinute)
            // Step 2: Pick end time, then save both together
            TimePickerDialog(
                context,
                { _, endHour, endMinute ->
                    selectedEnd = LocalTime.of(endHour, endMinute)
                    val startStr = String.format("%02d:%02d:00", selectedStart.hour, selectedStart.minute)
                    val endStr = String.format("%02d:%02d:00", selectedEnd.hour, selectedEnd.minute)
                    onSave(startStr, endStr)
                },
                initialEnd.hour,
                initialEnd.minute,
                false,
            ).show()
        },
        initialStart.hour,
        initialStart.minute,
        false,
    ).show()
}

private fun parseLocalTime(iso: String, defaultHour: Int, defaultMin: Int): LocalTime {
    return try {
        val parts = iso.split(":")
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    } catch (_: Exception) {
        LocalTime.of(defaultHour, defaultMin)
    }
}

private fun formatTime(time: LocalTime): String {
    val formatter = DateTimeFormatter.ofPattern("h:mm a")
    return time.format(formatter)
}
