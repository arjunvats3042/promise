package app.promise.android.ui.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.domain.UserSession
import app.promise.android.ui.auth.GreetingClock
import app.promise.android.ui.components.PromiseGreetingText
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.components.TabSwipeContainer
import app.promise.android.ui.home.HomeViewModel
import app.promise.android.ui.navigation.LocalTabSwipeHost
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.PromiseThemeMode
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import java.util.Calendar

data class FaqItem(
    val question: String,
    val answer: String,
)

val PROMISE_FAQS = listOf(
    FaqItem(
        question = "1. What is Promise?",
        answer = "Promise helps you track recurring practices and one-time commitments with clarity and focus.",
    ),
    FaqItem(
        question = "2. What is a Commitment?",
        answer = "A commitment is a specific one-time promise with a clear due date and target.",
    ),
    FaqItem(
        question = "3. What is a Goal?",
        answer = "A goal is a recurring practice or routine that helps you build long-term consistency.",
    ),
    FaqItem(
        question = "4. How do Shared Goals work?",
        answer = "Shared goals let you partner with friends or teammates to stay accountable together.",
    ),
    FaqItem(
        question = "5. How do notifications work?",
        answer = "Notifications remind you of upcoming commitments and practice check-ins.",
    ),
    FaqItem(
        question = "6. Can I use Promise with friends?",
        answer = "Yes, you can invite friends to shared goals and track progress together.",
    ),
    FaqItem(
        question = "7. What does AI do?",
        answer = "AI acts as an assistant to help you structure goals, refine commitments, and summarize chat history.",
    ),
    FaqItem(
        question = "8. Does AI create things automatically?",
        answer = "No, AI only provides suggestions. Nothing is created or saved until you explicitly confirm.",
    ),
    FaqItem(
        question = "9. What data does Promise send to AI?",
        answer = "Only the text prompts or chat context you explicitly choose to summarize or refine.",
    ),
    FaqItem(
        question = "10. How do I delete my account?",
        answer = "You can delete your account anytime under Security & Account on this screen.",
    ),
)

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val notificationHistory by viewModel.notificationHistory.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    var showLogoutAllDialog by remember { mutableStateOf(false) }
    var showRevokeOtherDialog by remember { mutableStateOf(false) }
    var sessionToRevoke by remember { mutableStateOf<UserSession?>(null) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var expandedFaqIndex by remember { mutableStateOf<Int?>(null) }

    val colors = PromiseThemeColors.current
    val swipeHost = LocalTabSwipeHost.current
    val scrollState = rememberScrollState()
    val name = user?.name?.takeIf { it.isNotBlank() } ?: "—"
    val email = user?.email.orEmpty()
    val initials = HomeViewModel.initialsFor(if (name == "—") "there" else name)
    val greeting = GreetingClock.greetingForHour(
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
    )

    TabSwipeContainer(
        currentIndex = swipeHost?.currentIndex ?: 3,
        tabCount = swipeHost?.tabCount ?: 4,
        enabled = swipeHost?.enabled == true,
        onSwipe = { direction -> swipeHost?.onSwipe(direction) },
        modifier = Modifier.fillMaxSize(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.inset)
                    .padding(top = Spacing.lg, bottom = Spacing.xxl),
            ) {
                // Profile header card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(colors.surfaceMuted)
                        .padding(Spacing.md),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            PromiseGreetingText(text = greeting)
                            Spacer(modifier = Modifier.height(Spacing.xxs))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.displayLarge,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(Spacing.xxs))
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.section))

                // Notifications Section
                NotificationPreferencesSection(
                    preferences = preferences,
                    history = notificationHistory,
                    onUpdate = { patch -> viewModel.updatePreferences(patch) },
                )

                Spacer(modifier = Modifier.height(Spacing.section))

                // Security & Account Section
                Text(
                    text = "Security & Account",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(colors.surfaceMuted)
                        .padding(Spacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Google Account",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Connected • $email",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.accent,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.section))

                // Devices & Active Sessions Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Devices & Sessions",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    if (sessions.count { !it.isCurrent } > 0) {
                        TextButton(onClick = { showRevokeOtherDialog = true }) {
                            Text("Sign out other devices", color = colors.accent)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(colors.surfaceMuted)
                        .padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (sessions.isEmpty()) {
                        Text(
                            text = "No active sessions found",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    } else {
                        sessions.forEachIndexed { index, session ->
                            if (index > 0) {
                                PromiseHairlineDivider()
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = session.deviceName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary,
                                        )
                                        if (session.isCurrent) {
                                            Spacer(modifier = Modifier.width(Spacing.xs))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(Radius.sm))
                                                    .background(colors.accent.copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "This device",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.accent,
                                                    fontWeight = FontWeight.Medium,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "Platform: ${session.platform.replaceFirstChar { it.uppercase() }}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                                if (!session.isCurrent) {
                                    TextButton(onClick = { sessionToRevoke = session }) {
                                        Text("Sign out", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.section))

                // Help & FAQ Section
                Text(
                    text = "Help & FAQ",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.lg))
                        .background(colors.surfaceMuted)
                        .padding(Spacing.md),
                ) {
                    PROMISE_FAQS.forEachIndexed { index, item ->
                        if (index > 0) {
                            PromiseHairlineDivider()
                        }
                        FaqAccordionRow(
                            item = item,
                            expanded = expandedFaqIndex == index,
                            onToggle = {
                                expandedFaqIndex = if (expandedFaqIndex == index) null else index
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.section))

                // Appearance Section
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.min)
                        .clip(RoundedCornerShape(Radius.md))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(Radius.md))
                        .background(colors.surfaceMuted),
                ) {
                    ThemeSegment(
                        label = "Light",
                        selected = mode == PromiseThemeMode.Light,
                        onSelect = { viewModel.setMode(PromiseThemeMode.Light) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeSegment(
                        label = "Dark",
                        selected = mode == PromiseThemeMode.Dark,
                        onSelect = { viewModel.setMode(PromiseThemeMode.Dark) },
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xl))

                // Global Logout & Delete
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.min)
                        .semantics { contentDescription = "Sign out" },
                ) {
                    Text(
                        text = "Sign out",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                TextButton(
                    onClick = { showLogoutAllDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.min)
                        .semantics { contentDescription = "Sign out of all devices" },
                ) {
                    Text(
                        text = "Sign out of all devices",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textSecondary,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.min)
                        .semantics { contentDescription = "Delete account" },
                ) {
                    Text(
                        text = "Delete account",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }

    // Dialogs
    if (showLogoutAllDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutAllDialog = false },
            title = {
                Text(
                    text = "Sign out of all devices?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = "This will revoke all active sessions on other devices and sign you out here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutAllDialog = false
                        viewModel.logoutAll(onSignOut)
                    },
                ) {
                    Text("Sign out all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutAllDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.md),
        )
    }

    if (showRevokeOtherDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeOtherDialog = false },
            title = {
                Text(
                    text = "Sign out all other devices?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = "This will sign out all other devices while keeping your current session active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeOtherDialog = false
                        viewModel.revokeOtherSessions()
                    },
                ) {
                    Text("Sign out others", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeOtherDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.md),
        )
    }

    sessionToRevoke?.let { session ->
        AlertDialog(
            onDismissRequest = { sessionToRevoke = null },
            title = {
                Text(
                    text = "Sign out device?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out ${session.deviceName}?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.revokeSession(session.id)
                        sessionToRevoke = null
                    },
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRevoke = null }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.md),
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Text(
                    text = "Delete your account?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = "This will permanently anonymize your account, cancel all active commitments and goals, and sign you out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        viewModel.deleteAccount(onSignOut)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.md),
        )
    }
}

@Composable
fun FaqAccordionRow(
    item: FaqItem,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val stateDescription = if (expanded) "expanded" else "collapsed"
    val semanticsDesc = "${item.question}, $stateDescription"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.standardTween(Motion.CompletionMs))
            .clickable(onClick = onToggle)
            .semantics(mergeDescendants = true) {
                contentDescription = semanticsDesc
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget.min)
                .padding(vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.question,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = colors.textSecondary,
            )
        }
        if (expanded) {
            Text(
                text = item.answer,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = Spacing.xs),
            )
        }
    }
}

@Composable
private fun ThemeSegment(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    Box(
        modifier = modifier
            .heightIn(min = TouchTarget.min)
            .background(if (selected) colors.primaryControl else colors.surfaceMuted)
            .clickable(onClick = onSelect)
            .semantics { contentDescription = "$label theme" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) colors.onPrimaryControl else colors.textPrimary,
        )
    }
}
