package app.promise.android.ui.profile

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import app.promise.android.domain.NotificationHistoryItem
import app.promise.android.domain.NotificationPreferences
import app.promise.android.domain.NotificationPreferencesPatch
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import java.time.LocalTime
import java.time.format.DateTimeFormatter

enum class NotificationAccordion {
    REMINDERS,
    SHARED_GOALS,
    WEEKLY,
    QUIET_HOURS,
}

@Composable
fun NotificationPreferencesSection(
    preferences: NotificationPreferences?,
    history: List<NotificationHistoryItem> = emptyList(),
    onUpdate: (NotificationPreferencesPatch) -> Unit,
    onSendTest: () -> Unit = {},
    isSendingTest: Boolean = false,
    testSuccessMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = PromiseThemeColors.current
    val isOsPermissionGranted = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var expandedSections by remember { mutableStateOf(setOf<NotificationAccordion>()) }

    fun toggleSection(section: NotificationAccordion) {
        expandedSections = if (expandedSections.contains(section)) {
            expandedSections - section
        } else {
            expandedSections + section
        }
    }

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
            // Preferences View with Accordions
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                // Master Toggle Card
                MasterNotificationToggleCard(
                    enabled = preferences.enabled,
                    onToggle = { onUpdate(NotificationPreferencesPatch(enabled = it)) },
                )

                AnimatedVisibility(
                    visible = preferences.enabled,
                    enter = expandVertically(animationSpec = Motion.standardTween(Motion.CompletionMs)) + fadeIn(),
                    exit = shrinkVertically(animationSpec = Motion.exitTween(Motion.CompletionMs)) + fadeOut(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        // 1. Reminders & Anchor Times Accordion
                        val remindersActiveCount = listOf(
                            preferences.commitmentsDueSoon,
                            preferences.commitmentsDueNow,
                            preferences.commitmentsOverdue,
                            preferences.goalsTodayPractice,
                            preferences.goalsStreakProtection,
                        ).count { it }

                        val remindersSubtitle = if (remindersActiveCount == 0) {
                            "All reminders paused"
                        } else {
                            val morningTime = formatTime(parseLocalTime(preferences.morningAnchorTime, 8, 30))
                            val eveningTime = formatTime(parseLocalTime(preferences.eveningAnchorTime, 20, 30))
                            "Morning $morningTime • Evening $eveningTime"
                        }

                        NotificationAccordionCard(
                            icon = Icons.Outlined.Alarm,
                            title = "Reminders & Routines",
                            subtitle = remindersSubtitle,
                            statusBadge = "$remindersActiveCount/5 Active",
                            isBadgeAccent = remindersActiveCount > 0,
                            expanded = expandedSections.contains(NotificationAccordion.REMINDERS),
                            onToggle = { toggleSection(NotificationAccordion.REMINDERS) },
                        ) {
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
                        }

                        // 2. Shared Goals Accordion
                        val sharedActiveCount = listOf(
                            preferences.sharedGoalsChat,
                            preferences.sharedGoalsActivity,
                        ).count { it }

                        NotificationAccordionCard(
                            icon = Icons.Outlined.Group,
                            title = "Shared Goals & Chat",
                            subtitle = if (sharedActiveCount == 0) "Partner alerts paused" else "Group chat & check-in activity",
                            statusBadge = "$sharedActiveCount/2 Active",
                            isBadgeAccent = sharedActiveCount > 0,
                            expanded = expandedSections.contains(NotificationAccordion.SHARED_GOALS),
                            onToggle = { toggleSection(NotificationAccordion.SHARED_GOALS) },
                        ) {
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
                        }

                        // 3. Weekly Digest Accordion
                        NotificationAccordionCard(
                            icon = Icons.Outlined.DateRange,
                            title = "Weekly Digest",
                            subtitle = "Sunday recap of completed commitments & practices",
                            statusBadge = if (preferences.weeklyDigestEnabled) "Active" else "Off",
                            isBadgeAccent = preferences.weeklyDigestEnabled,
                            expanded = expandedSections.contains(NotificationAccordion.WEEKLY),
                            onToggle = { toggleSection(NotificationAccordion.WEEKLY) },
                        ) {
                            PreferenceSwitchRow(
                                title = "Weekly Digest Summary",
                                subtitle = "Receive a comprehensive summary every Sunday evening",
                                checked = preferences.weeklyDigestEnabled,
                                onCheckedChange = { onUpdate(NotificationPreferencesPatch(weeklyDigestEnabled = it)) },
                            )
                        }

                        // 4. Quiet Hours Accordion
                        val quietHoursStart = parseLocalTime(preferences.quietHoursStart, 22, 0)
                        val quietHoursEnd = parseLocalTime(preferences.quietHoursEnd, 8, 0)
                        val quietHoursFormatted = "${formatTime(quietHoursStart)} – ${formatTime(quietHoursEnd)}"

                        NotificationAccordionCard(
                            icon = Icons.Outlined.Bedtime,
                            title = "Quiet Hours",
                            subtitle = if (preferences.quietHoursEnabled) quietHoursFormatted else "Silence non-urgent alerts during rest",
                            statusBadge = if (preferences.quietHoursEnabled) "Active" else "Off",
                            isBadgeAccent = preferences.quietHoursEnabled,
                            expanded = expandedSections.contains(NotificationAccordion.QUIET_HOURS),
                            onToggle = { toggleSection(NotificationAccordion.QUIET_HOURS) },
                        ) {
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

                        // 5. Test Push Notification Action Card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.lg))
                                .background(colors.surfaceMuted)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(Radius.lg))
                                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = Spacing.md)) {
                                    Text(
                                        text = "Test Push Notification",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Send an immediate test alert to this device to verify delivery",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                                TextButton(
                                    onClick = onSendTest,
                                    enabled = !isSendingTest,
                                    modifier = Modifier.heightIn(min = TouchTarget.min),
                                ) {
                                    Text(
                                        text = if (isSendingTest) "Sending..." else "Send Test",
                                        color = if (isSendingTest) colors.textSecondary else colors.accent,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            if (testSuccessMessage != null) {
                                Spacer(modifier = Modifier.height(Spacing.xs))
                                Text(
                                    text = testSuccessMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.accent,
                                )
                            }
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
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(Radius.lg))
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
private fun MasterNotificationToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(colors.surfaceMuted)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(Radius.lg))
            .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.min)
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onToggle,
                )
                .semantics {
                    contentDescription = "Allow Notifications master switch, ${if (enabled) "Enabled" else "Disabled"}"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(if (enabled) colors.accent.copy(alpha = 0.15f) else colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (enabled) Icons.Outlined.NotificationsActive else Icons.Outlined.NotificationsOff,
                        contentDescription = null,
                        tint = if (enabled) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column {
                    Text(
                        text = "Allow Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (enabled) "Push notifications & reminders active" else "All alerts and routine reminders are paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            Switch(
                checked = enabled,
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
}

@Composable
private fun NotificationAccordionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    statusBadge: String? = null,
    isBadgeAccent: Boolean = false,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PromiseThemeColors.current
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = Motion.standardTween(Motion.CompletionMs),
        label = "chevronRotation",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(colors.surfaceMuted)
            .border(
                1.dp,
                if (expanded) colors.accent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(Radius.lg),
            )
            .animateContentSize(animationSpec = Motion.standardTween(Motion.CompletionMs)),
    ) {
        // Accordion Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.min)
                .clickable(onClick = onToggle)
                .semantics {
                    contentDescription = "$title accordion, $subtitle. ${if (expanded) "Expanded" else "Collapsed"}. Double tap to toggle."
                }
                .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(if (expanded) colors.accent.copy(alpha = 0.15f) else colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (expanded) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (statusBadge != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(if (isBadgeAccent) colors.accent.copy(alpha = 0.15f) else colors.surfaceRaised)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = statusBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (isBadgeAccent) colors.accent else colors.textSecondary,
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(rotation),
                )
            }
        }

        // Accordion Expandable Content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = Motion.standardTween(Motion.CompletionMs)) + fadeIn(),
            exit = shrinkVertically(animationSpec = Motion.exitTween(Motion.CompletionMs)) + fadeOut(),
        ) {
            Column {
                PromiseHairlineDivider()
                Column(
                    modifier = Modifier.padding(horizontal = Spacing.cardPadding, vertical = Spacing.xs),
                ) {
                    content()
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
            .clip(RoundedCornerShape(Radius.sm))
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
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .background(colors.accent.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = formatted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
            )
        }
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
            .clip(RoundedCornerShape(Radius.sm))
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
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(colors.accent.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Bedtime,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = formatted,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                )
            }
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
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // Master skeleton card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(Radius.lg))
                .background(colors.surfaceMuted),
        )

        // Accordion skeleton cards
        repeat(4) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(Radius.lg))
                    .background(colors.surfaceMuted),
            )
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
