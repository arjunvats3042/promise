package app.promise.android.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val user by viewModel.user.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    var showLogoutAllDialog by remember { mutableStateOf(false) }
    val colors = PromiseThemeColors.current
    val swipeHost = LocalTabSwipeHost.current
    val scrollState = androidx.compose.foundation.rememberScrollState()
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

                NotificationPreferencesSection(
                    preferences = preferences,
                    onUpdate = { patch -> viewModel.updatePreferences(patch) },
                )

                Spacer(modifier = Modifier.height(Spacing.section))
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
                    // Email verification status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Email Verification",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = if (user?.emailVerified == true) "Verified" else "Not verified",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (user?.emailVerified == true) colors.accent else colors.warning,
                            )
                        }
                        if (user?.emailVerified != true) {
                            TextButton(
                                onClick = {
                                    viewModel.requestEmailVerification { detail ->
                                        // Feedback handled
                                    }
                                },
                            ) {
                                Text("Send Link", color = colors.accent)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    PromiseHairlineDivider()
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Password management
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "Password",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = if (user?.hasPassword == true) "Password is set" else "No password (Google login)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    if (user?.googleLinked == true) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        PromiseHairlineDivider()
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = "Google Account",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Connected for sign in",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.section))
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
                var showDeleteAccountDialog by remember { mutableStateOf(false) }
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

                if (showDeleteAccountDialog) {
                    androidx.compose.material3.AlertDialog(
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
        }
    }

    if (showLogoutAllDialog) {
        androidx.compose.material3.AlertDialog(
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
