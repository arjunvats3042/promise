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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import app.promise.android.domain.NotificationHistoryItem
import app.promise.android.domain.NotificationPreferences
import app.promise.android.domain.NotificationPreferencesPatch
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
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
                    color = colors.accent,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md)),
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                modifier = Modifier.heightIn(min = TouchTarget.min),
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
                modifier = Modifier.heightIn(min = TouchTarget.min),
                text = {
                    Text(
                        if (history.isNotEmpty()) "History (${history.size})" else "History",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selectedTab == 1) colors.textPrimary else colors.textSecondary,
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(Spacing.md))

        if (preferences == null) {
            // Skeleton loading state while preferences fetch asynchronously
            NotificationPreferencesSkeleton()
        } else if (selectedTab == 0) {
            // Preferences View
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(colors.surfaceMuted)
                    .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            ) {
                // NOTIFICATIONS - Master Toggle
                Text(
                    text = "NOTIFICATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                    letterSpacing = 1.2.sp,
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                PreferenceSwitchRow(
                    title = "Allow Notifications",
                    subtitle = "Master switch for all push notifications and reminders",
                    checked = preferences.enabled,
                    onCheckedChange = { onUpdate(NotificationPreferencesPatch(enabled = it)) },
                )

                AnimatedVisibility(
                    visible = preferences.enabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        PromiseHairlineDivider()
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // REMINDERS Section
                        Text(
                            text = "REMINDERS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                            letterSpacing = 1.0.sp,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        PreferenceSwitchRow(
                            title = "Due Soon",
                            subtitle = "Prompts before upcoming commitment deadlines",
                            checked = preferences.commitmentsDueSoon,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(commitmentsDueSoon = it)) },
                        )
                        PreferenceSwitchRow(
                            title = "Due Now",
                            subtitle = "Alert at the exact moment a commitment is due",
                            checked = preferences.commitmentsDueNow,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(commitmentsDueNow = it)) },
                        )
                        PreferenceSwitchRow(
                            title = "Overdue",
                            subtitle = "Gentle prompts for unfinished commitments",
                            checked = preferences.commitmentsOverdue,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(commitmentsOverdue = it)) },
                        )
                        PreferenceSwitchRow(
                            title = "Morning Practice",
                            subtitle = "Daily morning prompt for active goals",
                            checked = preferences.goalsTodayPractice,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(goalsTodayPractice = it)) },
                        )
                        if (preferences.goalsTodayPractice) {
                            TimePickerInlineRow(
                                label = "Morning Practice Time",
                                isoTime = preferences.morningAnchorTime,
                                defaultHour = 8,
                                defaultMin = 30,
                                onSave = { timeStr ->
                                    onUpdate(NotificationPreferencesPatch(morningAnchorTime = timeStr))
                                },
                            )
                        }
                        PreferenceSwitchRow(
                            title = "Evening Check-In",
                            subtitle = "Evening reflection and streak momentum",
                            checked = preferences.goalsStreakProtection,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(goalsStreakProtection = it)) },
                        )
                        if (preferences.goalsStreakProtection) {
                            TimePickerInlineRow(
                                label = "Evening Check-In Time",
                                isoTime = preferences.eveningAnchorTime,
                                defaultHour = 20,
                                defaultMin = 30,
                                onSave = { timeStr ->
                                    onUpdate(NotificationPreferencesPatch(eveningAnchorTime = timeStr))
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(Spacing.md))
                        PromiseHairlineDivider()
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // SHARED GOALS Section
                        Text(
                            text = "SHARED GOALS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                            letterSpacing = 1.0.sp,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        PreferenceSwitchRow(
                            title = "Shared Goal Chat",
                            subtitle = "Messages from shared goal companions",
                            checked = preferences.sharedGoalsChat,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(sharedGoalsChat = it)) },
                        )
                        PreferenceSwitchRow(
                            title = "Shared Goal Activity",
                            subtitle = "When partners join, complete check-ins, or leave",
                            checked = preferences.sharedGoalsActivity,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(sharedGoalsActivity = it)) },
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))
                        PromiseHairlineDivider()
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // WEEKLY Section
                        Text(
                            text = "WEEKLY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                            letterSpacing = 1.0.sp,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        PreferenceSwitchRow(
                            title = "Weekly Digest",
                            subtitle = "Sunday recap of completed commitments & practices",
                            checked = preferences.weeklyDigestEnabled,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(weeklyDigestEnabled = it)) },
                        )

                        Spacer(modifier = Modifier.height(Spacing.md))
                        PromiseHairlineDivider()
                        Spacer(modifier = Modifier.height(Spacing.md))

                        // QUIET HOURS Section
                        Text(
                            text = "QUIET HOURS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                            letterSpacing = 1.0.sp,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        PreferenceSwitchRow(
                            title = "Enable Quiet Hours",
                            subtitle = "Silence non-urgent alerts during rest",
                            checked = preferences.quietHoursEnabled,
                            onCheckedChange = { onUpdate(NotificationPreferencesPatch(quietHoursEnabled = it)) },
                        )

                        if (preferences.quietHoursEnabled) {
                            QuietHoursTimePickerRow(
                                startIso = preferences.quietHoursStart,
                                endIso = preferences.quietHoursEnd,
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
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(colors.surfaceMuted)
                    .padding(Spacing.cardPadding),
            ) {
                Text(
                    text = "RECENT NOTIFICATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary,
                    letterSpacing = 1.0.sp,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                if (history.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No recent notifications",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
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
private fun PreferenceSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    val stateText = if (checked) "Enabled" else "Disabled"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.min)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics {
                contentDescription = "$title, $subtitle. $stateText"
            }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = Spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(2.dp))
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
                checkedThumbColor = colors.onPrimaryControl,
                checkedTrackColor = colors.primaryControl,
                uncheckedThumbColor = colors.textSecondary,
                uncheckedTrackColor = colors.surfaceRaised,
            ),
        )
    }
}

@Composable
private fun TimePickerInlineRow(
    label: String,
    isoTime: String,
    defaultHour: Int,
    defaultMin: Int,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = PromiseThemeColors.current
    val time = parseLocalTime(isoTime, defaultHour, defaultMin)
    val formatted = formatTime(time)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.min)
            .clickable {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val timeStr = String.format("%02d:%02d:00", hour, minute)
                        onSave(timeStr)
                    },
                    time.hour,
                    time.minute,
                    false,
                ).show()
            }
            .semantics {
                contentDescription = "$label, current time $formatted. Double tap to change."
            }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(
            text = formatted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
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
            .heightIn(min = TouchTarget.min)
            .clickable {
                showQuietHoursDialogs(context, startTime, endTime, onSave)
            }
            .semantics {
                contentDescription = "Quiet hours active from $formatted. Double tap to edit."
            }
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "Active Range",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
            )
        }
        TextButton(
            onClick = { showQuietHoursDialogs(context, startTime, endTime, onSave) },
            modifier = Modifier.heightIn(min = TouchTarget.min),
        ) {
            Text("Edit", style = MaterialTheme.typography.labelMedium, color = colors.accent)
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

    TimePickerDialog(
        context,
        { _, startHour, startMinute ->
            selectedStart = LocalTime.of(startHour, startMinute)
            TimePickerDialog(
                context,
                { _, endHour, endMinute ->
                    val selectedEnd = LocalTime.of(endHour, endMinute)
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
            .background(colors.surfaceRaised)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(Radius.sm))
            .padding(Spacing.cardPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(colors.accent.copy(alpha = 0.15f))
                    .padding(horizontal = Spacing.xs, vertical = 2.dp),
            ) {
                Text(
                    text = item.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
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

@Composable
private fun NotificationPreferencesSkeleton(
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(colors.surfaceMuted)
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(colors.surfaceRaised),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        repeat(4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.surfaceRaised),
                    )
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .height(12.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.surfaceRaised.copy(alpha = 0.6f)),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceRaised),
                )
            }
        }
    }
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
