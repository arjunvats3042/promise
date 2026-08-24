package app.promise.android.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import app.promise.android.widget.PromiseGlanceWidgetPinHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.BuildConfig
import app.promise.android.domain.UserSession
import app.promise.android.ui.auth.GreetingClock
import app.promise.android.ui.components.AvatarSize
import app.promise.android.ui.components.PromiseAvatar
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
        answer = "Promise is a calm accountability app that combines one-time deadline commitments, recurring practice routines, and shared partner goals.",
    ),
    FaqItem(
        question = "2. What is a Commitment?",
        answer = "A Commitment is a bounded promise with a specific due date and time. It helps you stay true to high-leverage tasks.",
    ),
    FaqItem(
        question = "3. What is a Goal?",
        answer = "A Goal is an ongoing recurring practice (e.g. daily, weekdays, or N times per week) designed to build lasting consistency.",
    ),
    FaqItem(
        question = "4. How do Shared Goals work?",
        answer = "You invite friends or teammates to a mutual goal. You see each other's check-ins, celebrate milestones, and chat in a private group channel.",
    ),
    FaqItem(
        question = "5. How do notifications work?",
        answer = "Promise delivers quiet, timely alerts for due commitments, daily morning practice routines, evening streak protection, and shared group activity.",
    ),
    FaqItem(
        question = "6. Can I use Promise with friends?",
        answer = "Yes! You can invite friends to any goal with an invite code or link and practice together with shared accountability.",
    ),
    FaqItem(
        question = "7. What does AI do?",
        answer = "AI helps refine vague thoughts into structured commitments, builds goal schedules from natural language, and summarizes group discussions.",
    ),
    FaqItem(
        question = "8. Does AI create things automatically?",
        answer = "No. AI only suggests structured options. Nothing is created or scheduled until you review and explicitly tap Confirm & Create.",
    ),
    FaqItem(
        question = "9. What data does Promise send to AI?",
        answer = "Only the specific prompt, thought text, or chat snippet you request refinement for is sent to Google Gemini. No extraneous personal data is shared.",
    ),
    FaqItem(
        question = "10. How do I delete my account?",
        answer = "Tap 'Delete account' at the bottom of this screen. Your personal data is immediately anonymized, all active commitments are cancelled, and you are signed out.",
    ),
)

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val photoUri by viewModel.photoUri.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val unreadHistory by viewModel.unreadHistory.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val isSendingTest by viewModel.isSendingTest.collectAsStateWithLifecycle()
    val testSuccessMessage by viewModel.testSuccessMessage.collectAsStateWithLifecycle()

    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var showLogoutAllDialog by remember { mutableStateOf(false) }
    var showRevokeOtherDialog by remember { mutableStateOf(false) }
    var sessionToRevoke by remember { mutableStateOf<UserSession?>(null) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var expandedFaqIndex by remember { mutableStateOf<Int?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.updateProfilePhoto(uri)
        }
    }

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
                // Profile header card: Prominent avatar, greeting, editorial serif name, email, and change action
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
                        val effectivePhoto = photoUri ?: user?.avatarUrl
                        PromiseAvatar(
                            initials = initials,
                            photoPath = effectivePhoto,
                            size = AvatarSize.LG,
                            onClick = {
                                if (effectivePhoto != null) {
                                    showPhotoOptionsDialog = true
                                } else {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                }
                            },
                            contentDescription = "Profile photo. Tap to choose from gallery",
                        )
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
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = "Choose profile picture",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .clickable {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                        )
                                    }
                                    .padding(vertical = 2.dp),
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.section))

                // Notifications Section
                NotificationPreferencesSection(
                    preferences = preferences,
                    unreadHistory = unreadHistory,
                    onUpdate = { patch -> viewModel.updatePreferences(patch) },
                    onOpenNotification = { item -> viewModel.openNotification(item) },
                    onMarkAllRead = { viewModel.markAllNotificationsRead() },
                    onSendTest = { viewModel.sendTestNotification() },
                    isSendingTest = isSendingTest,
                    testSuccessMessage = testSuccessMessage,
                )

                Spacer(modifier = Modifier.height(Spacing.section))

                // Security & Account Section (Google-only)
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
                                text = "Connected · $email",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.accent,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.section))

                // Home Screen Widget Section
                val context = LocalContext.current
                Text(
                    text = "Home Screen Widget",
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
                                text = "Interactive Glance Widget",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "One-tap habit check-in & progress ring",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        if (PromiseGlanceWidgetPinHelper.isPinSupported(context)) {
                            TextButton(
                                onClick = { PromiseGlanceWidgetPinHelper.requestPinWidget(context) },
                            ) {
                                Text("Add to Home", color = colors.accent)
                            }
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

                // Help & FAQ Section (Concise 10 Items with 48dp touch targets)
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
                        .padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
                ) {
                    PROMISE_FAQS.forEachIndexed { index, item ->
                        if (index > 0) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            PromiseHairlineDivider()
                            Spacer(modifier = Modifier.height(Spacing.xs))
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

                Spacer(modifier = Modifier.height(Spacing.sectionGap))

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

                Spacer(modifier = Modifier.height(Spacing.xxl))

                // Techy Developer Signature Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.accent),
                        )
                        Text(
                            text = "PROMISE CORE v${BuildConfig.VERSION_NAME} // BUILD 3042",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = colors.textSecondary,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Text(
                        text = "ARCHITECTED & CRAFTED BY ARJUN VATS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = colors.textSecondary.copy(alpha = 0.7f),
                        letterSpacing = 1.0.sp,
                    )
                    Text(
                        text = "SYSTEM STATUS: OPERATIONAL",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = colors.success,
                        letterSpacing = 0.8.sp,
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

    if (showPhotoOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoOptionsDialog = false },
            title = {
                Text(
                    text = "Profile Picture",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = "Choose a new photo from your gallery or remove the existing one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPhotoOptionsDialog = false
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) {
                    Text("Choose from Gallery", color = colors.accent, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showPhotoOptionsDialog = false
                            viewModel.clearProfilePhoto()
                        },
                    ) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { showPhotoOptionsDialog = false }) {
                        Text("Cancel", color = colors.textSecondary)
                    }
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
    val semanticsDesc = "${item.question}, $stateDescription. Double tap to toggle."

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
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = item.answer,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.padding(bottom = Spacing.md),
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
