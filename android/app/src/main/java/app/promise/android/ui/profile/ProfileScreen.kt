package app.promise.android.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.BuildConfig
import app.promise.android.domain.UserSession
import app.promise.android.ui.ai.support.PromiseSupportChatSheet
import app.promise.android.ui.components.AvatarSize
import app.promise.android.ui.components.PromiseAvatar
import app.promise.android.ui.components.PromiseCardSurface
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.components.PromiseLogo
import app.promise.android.ui.components.PromiseSectionHeader
import app.promise.android.ui.components.PromiseStatusChip
import app.promise.android.ui.components.TabSwipeContainer
import app.promise.android.ui.home.HomeViewModel
import app.promise.android.ui.navigation.LocalTabSwipeHost
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.PromiseThemeMode
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.pressScale
import app.promise.android.widget.PromiseGlanceWidgetPinHelper

data class FaqItem(
    val question: String,
    val answer: String,
)

val PROMISE_FAQS = listOf(
    FaqItem(
        question = "What is the difference between a Commitment and a Goal?",
        answer = "Commitments are one-time tasks with a specific due date (e.g., 'Submit design review by Friday'). Goals are recurring daily or weekly practices designed to build long-term consistency (e.g., 'Read 20 pages daily', 'Gym 3x/week').",
    ),
    FaqItem(
        question = "How does 'Thought → Promise' (AI) work?",
        answer = "Type or paste an unstructured brain dump (e.g., 'call mom tomorrow at 11pm and workout 4 days a week'). AI automatically decomposes it into clear commitments and recurring goals with accurate due dates. Nothing is created until you review and confirm.",
    ),
    FaqItem(
        question = "How do Shared Goals work?",
        answer = "You can convert any goal into a shared goal and invite partners by searching their Promise email address. Once they accept the in-app invite, all members track shared momentum and check-ins together in a private group room.",
    ),
    FaqItem(
        question = "Can I use Promise and check in without internet?",
        answer = "Yes! Promise is local-first. Today's commitments, habit check-ins, and the Android Glance home screen widget work instantly offline. Your actions are saved locally and sync quietly to the cloud when connectivity returns.",
    ),
    FaqItem(
        question = "When does Promise send notifications?",
        answer = "Promise only sends quiet, intentional reminders: a morning practice summary, timely alerts when commitments are due, evening streak protection, and shared goal chat messages. You can customize all notification preferences anytime above.",
    ),
    FaqItem(
        question = "What data is shared with AI?",
        answer = "Only the specific thought or prompt you enter for AI refinement is sent securely to Google Gemini. Your private account details, email, and unrelated commitments are never shared or used for model training.",
    ),
    FaqItem(
        question = "How do I delete my account?",
        answer = "Tap 'Delete account' at the bottom of this screen. Your account will be permanently anonymized, all active commitments cancelled, and your session securely signed out.",
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
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    var showPhotoOptionsDialog by remember { mutableStateOf(false) }
    var showLogoutAllDialog by remember { mutableStateOf(false) }
    var showRevokeOtherDialog by remember { mutableStateOf(false) }
    var sessionToRevoke by remember { mutableStateOf<UserSession?>(null) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var expandedFaqIndex by remember { mutableStateOf<Int?>(null) }
    var showWidgetsDropdown by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.updateProfilePhoto(uri)
        }
    }

    val context = LocalContext.current
    val colors = PromiseThemeColors.current
    val swipeHost = LocalTabSwipeHost.current
    val scrollState = rememberScrollState()
    val name = user?.name?.takeIf { it.isNotBlank() } ?: "—"
    val email = user?.email.orEmpty()
    val initials = HomeViewModel.initialsFor(if (name == "—") "there" else name)

    TabSwipeContainer(
        currentIndex = swipeHost?.currentIndex ?: 4,
        tabCount = swipeHost?.tabCount ?: 5,
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
                    .padding(horizontal = Spacing.screenHorizontal)
                    .padding(top = Spacing.sm, bottom = Spacing.xxl),
            ) {
                // 1. HERO PROFILE CARD (Clean Avatar & Name)
                val effectivePhoto = photoUri ?: user?.avatarUrl
                PromiseCardSurface(
                    onClick = {
                        if (effectivePhoto != null) {
                            showPhotoOptionsDialog = true
                        } else {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        }
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            PromiseAvatar(
                                initials = initials,
                                photoPath = effectivePhoto,
                                size = AvatarSize.LG,
                                contentDescription = "Profile photo. Tap to choose from gallery",
                            )
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(colors.accent)
                                    .border(2.dp, colors.surfaceRaised, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Tap photo to change",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sectionGap))

                // 2. NOTIFICATIONS SECTION
                NotificationPreferencesSection(
                    preferences = preferences,
                    onUpdate = { patch -> viewModel.updatePreferences(patch) },
                )

                Spacer(modifier = Modifier.height(Spacing.sectionGap))

                // 3. HOME SCREEN GLANCE WIDGETS
                PromiseSectionHeader(
                    title = "Home Screen Widgets",
                    subtitle = "Add interactive widgets to your home screen",
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                PromiseCardSurface(
                    modifier = Modifier.animateContentSize(animationSpec = Motion.fluidSpring()),
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(color = colors.accent),
                                ) {
                                    showWidgetsDropdown = !showWidgetsDropdown
                                }
                                .padding(vertical = Spacing.xxs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                modifier = Modifier.weight(1f),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(Radius.md))
                                        .background(colors.accent.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Widgets,
                                        contentDescription = null,
                                        tint = colors.accent,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    ) {
                                        Text(
                                            text = "Glance Widgets",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(Radius.pill))
                                                .background(colors.accent.copy(alpha = 0.12f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = "4 AVAILABLE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colors.accent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (showWidgetsDropdown) "Tap to hide widgets" else "Tap to browse and add widgets",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showWidgetsDropdown) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = if (showWidgetsDropdown) "Collapse widgets" else "Expand widgets",
                                tint = colors.textSecondary,
                            )
                        }

                        if (showWidgetsDropdown) {
                            Spacer(modifier = Modifier.height(Spacing.md))
                            PromiseHairlineDivider()
                            Spacer(modifier = Modifier.height(Spacing.sm))

                            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                GlanceWidgetCatalogItem(
                                    title = "Voice Capture",
                                    description = "Floating microphone straight into AI intention parsing",
                                    icon = Icons.Outlined.Mic,
                                    onPin = { PromiseGlanceWidgetPinHelper.requestPinVoiceWidget(context) },
                                )
                                GlanceWidgetCatalogItem(
                                    title = "Commitments & Tasks",
                                    description = "Today's deadlines, overdue badges & 1-tap completion",
                                    icon = Icons.Outlined.TaskAlt,
                                    onPin = { PromiseGlanceWidgetPinHelper.requestPinCommitmentsWidget(context) },
                                )
                                GlanceWidgetCatalogItem(
                                    title = "Daily Habits & Goals",
                                    description = "Active streaks, daily counts & 1-tap check-in",
                                    icon = Icons.Outlined.TrackChanges,
                                    onPin = { PromiseGlanceWidgetPinHelper.requestPinGoalsWidget(context) },
                                )
                                GlanceWidgetCatalogItem(
                                    title = "365-Day Life Roadmap",
                                    description = "Full year progress percentage and dots matrix",
                                    icon = Icons.Outlined.CalendarMonth,
                                    onPin = { PromiseGlanceWidgetPinHelper.requestPinRoadmapWidget(context) },
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sectionGap))

                // 4. SECURITY & IDENTITY SECTION
                PromiseSectionHeader(
                    title = "Security & Identity",
                    subtitle = "Account credentials and authentication provider",
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                PromiseCardSurface {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(Radius.md))
                                    .background(colors.accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Google Account",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = email.ifBlank { "Connected" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        PromiseStatusChip(
                            label = "Connected",
                            isAccent = true,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sectionGap))

                // 5. ACTIVE DEVICES & SESSIONS
                PromiseSectionHeader(
                    title = "Active Devices",
                    subtitle = "${sessions.size} connected session${if (sessions.size != 1) "s" else ""}",
                    actionLabel = if (sessions.count { !it.isCurrent } > 0) "Sign out others" else null,
                    onActionClick = { showRevokeOtherDialog = true },
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                PromiseCardSurface {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                                    Spacer(modifier = Modifier.height(Spacing.xs))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(if (session.isCurrent) colors.accent.copy(alpha = 0.14f) else colors.surfaceMuted),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Smartphone,
                                                contentDescription = null,
                                                tint = if (session.isCurrent) colors.accent else colors.textSecondary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                            ) {
                                                Text(
                                                    text = session.deviceName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.textPrimary,
                                                )
                                                if (session.isCurrent) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(Radius.pill))
                                                            .background(colors.accent.copy(alpha = 0.15f))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                                    ) {
                                                        Text(
                                                            text = "THIS DEVICE",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = colors.accent,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp,
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
                                    }
                                    if (!session.isCurrent) {
                                        TextButton(onClick = { sessionToRevoke = session }) {
                                            Text("Sign out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sectionGap))

                // 7. ACCOUNT MANAGEMENT (DANGER ZONE)
                PromiseSectionHeader(
                    title = "Account Management",
                    subtitle = "Manage active sessions and account options",
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                PromiseCardSurface {
                    Column {
                        // Sign out current device
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onSignOut)
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Sign out of this device",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.textSecondary,
                            )
                        }

                        PromiseHairlineDivider()

                        // Sign out all devices
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { showLogoutAllDialog = true })
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DevicesOther,
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = "Sign out of all devices",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = colors.textSecondary,
                            )
                        }

                        PromiseHairlineDivider()

                        // Delete account
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { showDeleteAccountDialog = true })
                                .padding(vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Delete account",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 15.sp,
                                )
                                Text(
                                    text = "Permanently delete your account and all data",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sectionGap))

                // 8. THEME & APPEARANCE SECTION (Positioned Last as Requested)
                PromiseSectionHeader(
                    title = "Appearance",
                    subtitle = "Choose Dark or Light appearance",
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                PromiseThemeSelector(
                    mode = mode,
                    onSelectMode = { viewModel.setMode(it) },
                )

                Spacer(modifier = Modifier.height(Spacing.xxl))

                // 9. ARCHITECTURAL & CRAFT FOOTER
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    PromiseLogo(
                        size = 36.dp,
                        animated = false,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
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
                            fontFamily = FontFamily.Monospace,
                            color = colors.textSecondary,
                            letterSpacing = 1.2.sp,
                        )
                    }
                    Text(
                        text = "ARCHITECTED & CRAFTED BY ARJUN VATS",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textSecondary.copy(alpha = 0.7f),
                        letterSpacing = 1.0.sp,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(colors.success),
                        )
                        Text(
                            text = "SYSTEM STATUS: OPERATIONAL",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = colors.success,
                            letterSpacing = 0.8.sp,
                        )
                    }
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
                    text = "Sign out other devices?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = "This will revoke all active sessions on your other phones and browsers, keeping you signed in here.",
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
                    text = "Revoke session?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = "Sign out from ${session.deviceName} (${session.platform})?",
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
private fun PromiseThemeSelector(
    mode: PromiseThemeMode,
    onSelectMode: (PromiseThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current

    PromiseCardSurface(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            // Header Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(colors.accent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (mode == PromiseThemeMode.Dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = if (mode == PromiseThemeMode.Dark) "Deep night contrast" else "Warm daytime brightness",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                PromiseStatusChip(
                    label = if (mode == PromiseThemeMode.Dark) "Dark" else "Light",
                    isAccent = true,
                )
            }

            PromiseHairlineDivider()

            // Smooth Fluid Sliding Segmented Controller
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(colors.surfaceMuted)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        RoundedCornerShape(Radius.pill),
                    )
                    .padding(4.dp),
            ) {
                val totalWidth = maxWidth
                val segmentWidth = (totalWidth - 4.dp) / 2
                val targetOffset = if (mode == PromiseThemeMode.Light) 0.dp else segmentWidth + 4.dp
                val indicatorOffset by animateDpAsState(
                    targetValue = targetOffset,
                    animationSpec = Motion.fluidSpring(),
                    label = "theme-slider-offset",
                )

                // Smooth sliding indicator pill
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(segmentWidth)
                        .height(40.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(colors.surfaceRaised)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(Radius.pill),
                        ),
                )

                // Interactive Segment Buttons
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ThemeSegmentButton(
                        title = "Light",
                        icon = Icons.Outlined.LightMode,
                        selected = mode == PromiseThemeMode.Light,
                        onClick = {
                            if (mode != PromiseThemeMode.Light) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectMode(PromiseThemeMode.Light)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeSegmentButton(
                        title = "Dark",
                        icon = Icons.Outlined.DarkMode,
                        selected = mode == PromiseThemeMode.Dark,
                        onClick = {
                            if (mode != PromiseThemeMode.Dark) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectMode(PromiseThemeMode.Dark)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSegmentButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    val fg by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.textSecondary,
        animationSpec = Motion.standardTween(Motion.FilterChangeMs),
        label = "theme-segment-fg",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(Radius.pill))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = fg,
            )
        }
    }
}

@Composable
private fun GlanceWidgetCatalogItem(
    title: String,
    description: String,
    icon: ImageVector,
    onPin: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(Radius.md)),
        color = colors.surfaceMuted,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
            TextButton(
                onClick = onPin,
                modifier = Modifier.heightIn(min = TouchTarget.min),
            ) {
                Text(
                    text = "Add +",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent,
                )
            }
        }
    }
}


@Composable
private fun FaqAccordionRow(
    item: FaqItem,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = PromiseThemeColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = colors.accent),
                onClick = onToggle,
            )
            .padding(vertical = Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                contentDescription = if (expanded) "Collapse answer" else "Expand answer",
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
                lineHeight = 20.sp,
            )
        }
    }
}
