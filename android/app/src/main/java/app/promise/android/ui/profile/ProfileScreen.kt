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
    val colors = PromiseThemeColors.current
    val swipeHost = LocalTabSwipeHost.current
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
                    .padding(horizontal = Spacing.inset)
                    .padding(top = Spacing.lg),
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
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.min)
                        .clip(RoundedCornerShape(Radius.sm))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.sm))
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
                        .heightIn(min = TouchTarget.min)
                        .semantics { contentDescription = "Sign out" },
                ) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
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
