package app.promise.android.ui.profile

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.domain.SecurityEventItem
import app.promise.android.domain.UserSession
import app.promise.android.ui.auth.GreetingClock
import app.promise.android.ui.components.PromiseGreetingText
import app.promise.android.ui.components.PromiseHairlineDivider
import app.promise.android.ui.components.TabSwipeContainer
import app.promise.android.ui.home.HomeViewModel
import app.promise.android.ui.navigation.LocalTabSwipeHost
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.PromiseThemeMode
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import java.util.Calendar

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
    val securityEvents by viewModel.securityEvents.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    var showLogoutAllDialog by remember { mutableStateOf(false) }
    var showRevokeOtherDialog by remember { mutableStateOf(false) }
    var sessionToRevoke by remember { mutableStateOf<UserSession?>(null) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showSetPasswordDialog by remember { mutableStateOf(false) }
    var showEmailChangeDialog by remember { mutableStateOf(false) }
    var showEmailConfirmDialog by remember { mutableStateOf(false) }
    var showUnlinkGoogleDialog by remember { mutableStateOf(false) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

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
                    // Email Verification & Change
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Email Address",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = if (user?.emailVerified == true) "Verified • $email" else "Unverified • $email",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (user?.emailVerified == true) colors.accent else colors.warning,
                            )
                        }
                        Row {
                            if (user?.emailVerified != true) {
                                TextButton(
                                    onClick = {
                                        viewModel.requestEmailVerification { detail ->
                                            feedbackMessage = detail
                                        }
                                    },
                                ) {
                                    Text("Verify", color = colors.accent)
                                }
                            }
                            TextButton(onClick = { showEmailChangeDialog = true }) {
                                Text("Change", color = colors.textPrimary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    PromiseHairlineDivider()
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Password
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Password",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = if (user?.hasPassword == true) "Password is set" else "No password (Google account)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                        if (user?.hasPassword == true) {
                            TextButton(onClick = { showChangePasswordDialog = true }) {
                                Text("Change", color = colors.textPrimary)
                            }
                        } else {
                            TextButton(onClick = { showSetPasswordDialog = true }) {
                                Text("Set Password", color = colors.accent)
                            }
                        }
                    }

                    // Google Account
                    if (user?.googleLinked == true) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        PromiseHairlineDivider()
                        Spacer(modifier = Modifier.height(Spacing.sm))

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
                                Text(
                                    text = "Connected for sign-in",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary,
                                )
                            }
                            TextButton(
                                onClick = {
                                    if (user?.hasPassword != true) {
                                        feedbackMessage = "Please set a password first before unlinking your Google account."
                                    } else {
                                        showUnlinkGoogleDialog = true
                                    }
                                },
                            ) {
                                Text("Unlink", color = colors.textSecondary)
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

                // Security Activity Section
                Text(
                    text = "Security Activity",
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
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    if (securityEvents.isEmpty()) {
                        Text(
                            text = "No recent security activity",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    } else {
                        securityEvents.take(5).forEachIndexed { index, event ->
                            if (index > 0) {
                                PromiseHairlineDivider()
                            }
                            SecurityEventRow(event = event)
                        }
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

    if (showUnlinkGoogleDialog) {
        AlertDialog(
            onDismissRequest = { showUnlinkGoogleDialog = false },
            title = {
                Text(
                    text = "Unlink Google Account?",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            },
            text = {
                Text(
                    text = "You will need to use your email and password to log in next time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnlinkGoogleDialog = false
                        viewModel.unlinkGoogle(
                            onSuccess = { feedbackMessage = "Google account unlinked." },
                            onError = { feedbackMessage = it },
                        )
                    },
                ) {
                    Text("Unlink", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkGoogleDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.md),
        )
    }

    if (showChangePasswordDialog) {
        var oldPass by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = {
                Text("Change Password", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = oldPass,
                        onValueChange = { oldPass = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.changePassword(
                            oldPass = oldPass,
                            newPass = newPass,
                            onSuccess = {
                                showChangePasswordDialog = false
                                feedbackMessage = "Password changed successfully."
                            },
                            onError = { error = it },
                        )
                    },
                ) {
                    Text("Update", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.md),
        )
    }

    if (showSetPasswordDialog) {
        var newPass by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showSetPasswordDialog = false },
            title = {
                Text("Set Password", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = newPass,
                        onValueChange = { newPass = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setPassword(
                            newPass = newPass,
                            onSuccess = {
                                showSetPasswordDialog = false
                                feedbackMessage = "Password set successfully."
                            },
                            onError = { error = it },
                        )
                    },
                ) {
                    Text("Set", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetPasswordDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.md),
        )
    }

    if (showEmailChangeDialog) {
        var newEmail by remember { mutableStateOf("") }
        var currentPass by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showEmailChangeDialog = false },
            title = {
                Text("Change Email", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = newEmail,
                        onValueChange = { newEmail = it },
                        label = { Text("New Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (user?.hasPassword == true) {
                        OutlinedTextField(
                            value = currentPass,
                            onValueChange = { currentPass = it },
                            label = { Text("Current Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    error?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.requestEmailChange(
                            newEmail = newEmail,
                            currentPassword = if (user?.hasPassword == true) currentPass else null,
                            onResult = {
                                showEmailChangeDialog = false
                                showEmailConfirmDialog = true
                            },
                            onError = { error = it },
                        )
                    },
                ) {
                    Text("Continue", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailChangeDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.md),
        )
    }

    if (showEmailConfirmDialog) {
        var token by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showEmailConfirmDialog = false },
            title = {
                Text("Verify New Email", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        text = "Enter the verification token sent to your new email address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        label = { Text("Verification Token") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmEmailChange(
                            token = token,
                            onSuccess = {
                                showEmailConfirmDialog = false
                                feedbackMessage = "Email updated and verified successfully."
                            },
                            onError = { error = it },
                        )
                    },
                ) {
                    Text("Confirm", color = colors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailConfirmDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(Radius.md),
        )
    }

    feedbackMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { feedbackMessage = null },
            title = { Text("Account Update", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary) },
            text = { Text(msg, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { feedbackMessage = null }) {
                    Text("OK", color = colors.accent)
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
private fun SecurityEventRow(event: SecurityEventItem) {
    val colors = PromiseThemeColors.current
    val formattedAction = when (event.eventType) {
        "LOGIN" -> "Sign in"
        "GOOGLE_LOGIN" -> "Google sign in"
        "NEW_DEVICE_LOGIN" -> "New device sign in"
        "PASSWORD_CHANGED" -> "Password changed"
        "PASSWORD_RESET" -> "Password reset"
        "EMAIL_VERIFIED" -> "Email verified"
        "GOOGLE_LINKED" -> "Google account linked"
        "GOOGLE_UNLINKED" -> "Google account unlinked"
        "SESSION_REVOKED" -> "Session signed out"
        "LOGOUT_ALL" -> "Signed out of all devices"
        "EMAIL_CHANGE_REQUESTED" -> "Email change requested"
        "EMAIL_CHANGED" -> "Email address changed"
        "ACCOUNT_DELETION_REQUESTED" -> "Account deletion requested"
        else -> event.eventType.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = formattedAction,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
            )
            if (event.deviceName.isNotBlank()) {
                Text(
                    text = event.deviceName,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
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
