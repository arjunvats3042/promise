package app.promise.android.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.toUserMessage
import app.promise.android.ui.components.PromiseGreetingText
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.pressScale
import app.promise.android.ui.theme.rememberReduceMotion
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onGoogleSignIn: (() -> Unit)? = null,
    onAllowLocalNetwork: (() -> Unit)? = null,
) {
    val action by viewModel.action.collectAsStateWithLifecycle()
    val submitting = action is ActionState.InFlight
    val error = (action as? ActionState.Failed)?.kind
    val localNetworkDenied = error == ErrorKind.LocalNetworkDenied
    val colors = PromiseThemeColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle ambient background atmospheric gradient
            LoginAtmosphericBackground(accentColor = colors.accent)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = Spacing.authMaxWidth)
                        .fillMaxWidth(),
                ) {
                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Brand Monogram Emblem
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BrandMonogramBadge(accentColor = colors.accent)

                        // Live status indicator pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(colors.surfaceMuted)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    RoundedCornerShape(Radius.pill),
                                )
                                .padding(horizontal = Spacing.sm + 2.dp, vertical = 4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PulsingLiveDot(dotColor = Color(0xFF10B981))
                                Text(
                                    text = "SECURE CLOUD SYNC",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = colors.textSecondary,
                                    letterSpacing = 0.8.sp,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    // Category Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(colors.accent.copy(alpha = 0.12f))
                            .border(
                                1.dp,
                                colors.accent.copy(alpha = 0.3f),
                                RoundedCornerShape(Radius.pill),
                            )
                            .padding(horizontal = Spacing.md, vertical = 4.dp),
                    ) {
                        Text(
                            text = "✨ INTEGRITY · MOMENTUM · FOCUS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent,
                            letterSpacing = 1.2.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Editorial Hero Title
                    Text(
                        text = "Promise",
                        style = MaterialTheme.typography.displayLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )

                    Spacer(modifier = Modifier.height(Spacing.xxs))

                    Text(
                        text = "Keep every promise you make to yourself.",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Normal,
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Dynamic Greeting Capsule
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.md))
                            .background(colors.surfaceRaised)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                RoundedCornerShape(Radius.md),
                            )
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs + 2.dp),
                    ) {
                        AnimatedLoginGreeting()
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    // 3 Feature Showcase Cards
                    Column(
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
                    ) {
                        FeatureHighlightRow(
                            iconEmoji = "🎯",
                            title = "Daily Practice & Habit Rings",
                            subtitle = "Build daily consistency with streaks & progress rings",
                        )
                        FeatureHighlightRow(
                            iconEmoji = "⚡",
                            title = "Zero-Overdue Commitment Tracker",
                            subtitle = "Calm due dates, time precision & overdue alerts",
                        )
                        FeatureHighlightRow(
                            iconEmoji = "🧠",
                            title = "Gemini AI Behavioral Insights",
                            subtitle = "Thought parser, weekly pattern discovery & coaching",
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    // Elevated Sign In Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.xl))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                                RoundedCornerShape(Radius.xl),
                            ),
                        color = colors.surfaceRaised,
                        shadowElevation = Elevation.none,
                    ) {
                        Column(modifier = Modifier.padding(Spacing.cardPadding)) {
                            Text(
                                text = "Get Started",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Sign in securely with your Google account to sync seamlessly across all your Android devices.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )

                            Spacer(modifier = Modifier.height(Spacing.lg))

                            // Google Primary Sign-In Button
                            Button(
                                onClick = {
                                    if (onGoogleSignIn != null) {
                                        onGoogleSignIn()
                                    } else {
                                        coroutineScope.launch {
                                            launchGoogleSignIn(
                                                context = context,
                                                onSuccess = { idToken ->
                                                    viewModel.submitGoogleLogin(idToken)
                                                },
                                                onError = {
                                                    viewModel.onGoogleSignInFailed(ErrorKind.Unknown)
                                                },
                                                onCancelled = {
                                                    viewModel.onGoogleSignInCancelled()
                                                },
                                            )
                                        }
                                    }
                                },
                                enabled = !submitting,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primaryControl,
                                    contentColor = colors.onPrimaryControl,
                                ),
                                shape = RoundedCornerShape(Radius.button),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = TouchTarget.buttonMin)
                                    .pressScale(0.96f, enabled = !submitting)
                                    .semantics { contentDescription = "Continue with Google" },
                            ) {
                                if (submitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = colors.onPrimaryControl,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    Text(
                                        text = "Signing in…",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                } else {
                                    GoogleLogoIcon(modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(Spacing.sm + 2.dp))
                                    Text(
                                        text = "Continue with Google",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp,
                                    )
                                }
                            }

                            if (error != null) {
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                Text(
                                    text = error.toUserMessage(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            if (localNetworkDenied && onAllowLocalNetwork != null) {
                                Spacer(modifier = Modifier.height(Spacing.sm))
                                TextButton(
                                    onClick = onAllowLocalNetwork,
                                    modifier = Modifier.heightIn(min = TouchTarget.min),
                                ) {
                                    Text("Allow local network", color = colors.accent)
                                }
                            }

                            Spacer(modifier = Modifier.height(Spacing.md))

                            // Privacy & Security Guarantee
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "🔒 No passwords · End-to-end sync · Private by design",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xxl))

                    // Editorial Studio Footer
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "PROMISE STUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textSecondary.copy(alpha = 0.7f),
                            letterSpacing = 1.5.sp,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Crafted for clarity and focus",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = colors.textSecondary.copy(alpha = 0.6f),
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))
                }
            }
        }
    }
}

@Composable
private fun BrandMonogramBadge(accentColor: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor,
                        accentColor.copy(alpha = 0.75f),
                    ),
                ),
            )
            .border(
                1.5.dp,
                Color.White.copy(alpha = 0.25f),
                RoundedCornerShape(Radius.md),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "P",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = Color.White,
        )
    }
}

@Composable
private fun FeatureHighlightRow(
    iconEmoji: String,
    title: String,
    subtitle: String,
) {
    val colors = PromiseThemeColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(colors.surfaceMuted)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = iconEmoji,
            fontSize = 16.sp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PulsingLiveDot(dotColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot-alpha",
    )

    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(dotColor.copy(alpha = alpha)),
    )
}

@Composable
private fun LoginAtmosphericBackground(accentColor: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        // Top right subtle ambient bloom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.08f),
                    Color.Transparent,
                ),
                center = Offset(width * 0.9f, height * 0.15f),
                radius = width * 0.7f,
            ),
            center = Offset(width * 0.9f, height * 0.15f),
            radius = width * 0.7f,
        )

        // Bottom left subtle purple glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF8B5CF6).copy(alpha = 0.06f),
                    Color.Transparent,
                ),
                center = Offset(width * 0.1f, height * 0.75f),
                radius = width * 0.6f,
            ),
            center = Offset(width * 0.1f, height * 0.75f),
            radius = width * 0.6f,
        )
    }
}

@Composable
private fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.46f

        // Draw 4-color authentic Google arcs
        val blue = Color(0xFF4285F4)
        val red = Color(0xFFEA4335)
        val yellow = Color(0xFFFBBC05)
        val green = Color(0xFF34A853)
        val strokeWidth = w * 0.22f

        // Red arc (top)
        drawArc(
            color = red,
            startAngle = 180f,
            sweepAngle = 120f,
            useCenter = false,
            style = Stroke(width = strokeWidth),
        )
        // Yellow arc (left)
        drawArc(
            color = yellow,
            startAngle = 120f,
            sweepAngle = 60f,
            useCenter = false,
            style = Stroke(width = strokeWidth),
        )
        // Green arc (bottom)
        drawArc(
            color = green,
            startAngle = 0f,
            sweepAngle = 120f,
            useCenter = false,
            style = Stroke(width = strokeWidth),
        )
        // Blue arc & crossbar (right)
        drawArc(
            color = blue,
            startAngle = -45f,
            sweepAngle = 45f,
            useCenter = false,
            style = Stroke(width = strokeWidth),
        )
        // Horizontal bar
        drawLine(
            color = blue,
            start = Offset(cx, cy),
            end = Offset(w - strokeWidth / 4f, cy),
            strokeWidth = strokeWidth,
        )
    }
}

@Composable
internal fun AnimatedLoginGreeting(
    hourProvider: () -> Int = {
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    },
) {
    val reduceMotion = rememberReduceMotion()
    var state by remember {
        mutableStateOf(GreetingAnimationState(fullText = ""))
    }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(reduceMotion) {
        val initial = GreetingClock.greetingForHour(hourProvider())
        if (reduceMotion) {
            state = GreetingAnimator.reduce(
                GreetingAnimationState(fullText = ""),
                GreetingEvent.Start(initial),
                reduceMotion = true,
            )
            started = true
            return@LaunchedEffect
        }
        delay(Motion.GreetingInitialDelayMs.toLong())
        state = GreetingAnimator.reduce(
            GreetingAnimationState(fullText = ""),
            GreetingEvent.Start(initial),
        )
        started = true
        while (state.phase == GreetingPhase.Typing) {
            delay(Motion.GreetingTypePerCharMs.toLong())
            state = GreetingAnimator.reduce(state, GreetingEvent.Tick)
        }
    }

    LaunchedEffect(started, reduceMotion) {
        if (!started) return@LaunchedEffect
        while (true) {
            delay(60_000)
            val target = GreetingClock.greetingForHour(hourProvider())
            if (target == state.fullText && state.pendingText == null) continue
            if (reduceMotion) {
                state = GreetingAnimator.reduce(
                    state,
                    GreetingEvent.Change(target),
                    reduceMotion = true,
                )
                continue
            }
            if (state.phase == GreetingPhase.Settled) {
                delay(Motion.GreetingChangeHoldMs.toLong())
            }
            state = GreetingAnimator.reduce(state, GreetingEvent.Change(target))
            while (state.phase == GreetingPhase.Deleting) {
                delay(Motion.GreetingDeletePerCharMs.toLong())
                state = GreetingAnimator.reduce(state, GreetingEvent.Tick)
            }
            if (state.phase == GreetingPhase.Typing) {
                delay(Motion.GreetingChangeGapMs.toLong())
            }
            while (state.phase == GreetingPhase.Typing) {
                delay(Motion.GreetingTypePerCharMs.toLong())
                state = GreetingAnimator.reduce(state, GreetingEvent.Tick)
            }
        }
    }

    val semantic = state.fullText.ifBlank {
        GreetingClock.greetingForHour(hourProvider())
    }
    PromiseGreetingText(
        text = state.visibleText,
        semanticText = semantic,
    )
}

@Composable
fun SessionRestoringScreen(
    onRetry: (() -> Unit)? = null,
    localNetworkDenied: Boolean = false,
    onAllowLocalNetwork: (() -> Unit)? = null,
) {
    val colors = PromiseThemeColors.current
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
                .padding(horizontal = Spacing.inset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = Spacing.authMaxWidth)
                    .fillMaxWidth()
                    .padding(top = Spacing.authTop),
            ) {
                Text(
                    text = "Promise",
                    style = MaterialTheme.typography.displayLarge,
                    color = colors.textPrimary,
                )
                if (localNetworkDenied) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = ErrorKind.LocalNetworkDenied.toUserMessage(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                    if (onAllowLocalNetwork != null) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        TextButton(
                            onClick = onAllowLocalNetwork,
                            modifier = Modifier.heightIn(min = TouchTarget.min),
                        ) {
                            Text("Allow local network", color = colors.accent)
                        }
                    }
                } else if (onRetry != null) {
                    Spacer(modifier = Modifier.height(Spacing.md))
                    Text(
                        text = "Couldn't reach the server.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.heightIn(min = TouchTarget.min),
                    ) {
                        Text("Try again", color = colors.accent)
                    }
                }
            }
        }
    }
}
