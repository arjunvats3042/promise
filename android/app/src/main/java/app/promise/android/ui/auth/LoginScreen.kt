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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.toUserMessage
import app.promise.android.ui.components.PromiseGreetingText
import app.promise.android.ui.components.PromiseLogo
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.pressScale
import app.promise.android.ui.theme.rememberReduceMotion
import java.util.Calendar
import kotlin.math.min
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
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Top Bar: Calm motivational live indicator pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(colors.surfaceMuted)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                    RoundedCornerShape(Radius.pill),
                                )
                                .padding(horizontal = Spacing.sm + 4.dp, vertical = 5.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                PulsingLiveDot(dotColor = colors.accent)
                                Text(
                                    text = "ONE DAY AT A TIME",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = colors.textSecondary,
                                    letterSpacing = 1.sp,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // Brand Logo Emblem
                    PromiseLogo(
                        size = 56.dp,
                        animated = true,
                    )

                    Spacer(modifier = Modifier.height(Spacing.md))

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
                            text = "INTEGRITY · MOMENTUM · FOCUS",
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

                    // Static Greeting Capsule (Clean typography, no typewriter)
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
                        StaticLoginGreeting()
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    // Typewriter Benefit Carousel
                    TypewriterBenefitCarousel()

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    // Elevated Sign In Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.xl))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
                                RoundedCornerShape(Radius.xl),
                            ),
                        color = colors.surfaceRaised,
                        shadowElevation = Elevation.none,
                    ) {
                        Column(
                            modifier = Modifier.padding(Spacing.cardPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Welcome to Promise",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Your quiet space for intentional daily follow-through.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                textAlign = TextAlign.Center,
                            )

                            Spacer(modifier = Modifier.height(Spacing.lg))

                            // Google Primary Sign-In Button
                            Button(
                                onClick = {
                                    if (onGoogleSignIn != null) {
                                        onGoogleSignIn()
                                    } else {
                                        coroutineScope.launch {
                                            when (val result = performGoogleSignIn(context)) {
                                                is GoogleSignInResult.Success -> {
                                                    viewModel.submitGoogleLogin(result.idToken)
                                                }
                                                is GoogleSignInResult.Cancelled -> {
                                                    viewModel.onGoogleSignInCancelled()
                                                }
                                                is GoogleSignInResult.Error -> {
                                                    viewModel.onGoogleSignInFailed(ErrorKind.Unknown)
                                                }
                                            }
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
                                    GoogleLogoIcon(modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(Spacing.sm + 4.dp))
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
private fun TypewriterBenefitCarousel(
    modifier: Modifier = Modifier,
    phrases: List<String> = listOf(
        "Build daily habits without the noise",
        "Keep every promise you make to yourself",
        "Never lose track of what matters most",
        "Stay accountable with quiet daily clarity",
        "Celebrate progress one day at a time",
    ),
) {
    val colors = PromiseThemeColors.current
    val reduceMotion = rememberReduceMotion()

    var phraseIndex by remember { mutableIntStateOf(0) }
    var displayedText by remember { mutableStateOf("") }

    // Smooth cursor blink
    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorAlpha",
    )

    LaunchedEffect(reduceMotion, phrases) {
        if (phrases.isEmpty()) return@LaunchedEffect
        if (reduceMotion) {
            while (true) {
                displayedText = phrases[phraseIndex]
                delay(4000)
                phraseIndex = (phraseIndex + 1) % phrases.size
            }
        }

        while (true) {
            val currentPhrase = phrases[phraseIndex]

            // 1. Type forward character-by-character
            for (i in 1..currentPhrase.length) {
                displayedText = currentPhrase.take(i)
                delay(40)
            }

            // 2. Pause and hold for comfortable reading
            delay(2400)

            // 3. Backspace character-by-character
            for (i in currentPhrase.length downTo 0) {
                displayedText = currentPhrase.take(i)
                delay(18)
            }

            // 4. Short gap before next phrase
            delay(280)
            phraseIndex = (phraseIndex + 1) % phrases.size
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                RoundedCornerShape(Radius.lg),
            ),
        color = colors.surfaceMuted,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
                Text(
                    text = "WHY PROMISE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = colors.accent,
                    letterSpacing = 1.2.sp,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs + 2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayedText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                    if (!reduceMotion) {
                        Text(
                            text = " |",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = colors.accent.copy(alpha = cursorAlpha),
                            fontSize = 16.sp,
                        )
                    }
                }
            }
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
    androidx.compose.material3.Icon(
        painter = androidx.compose.ui.res.painterResource(id = app.promise.android.R.drawable.ic_google_logo),
        contentDescription = "Google logo",
        modifier = modifier,
        tint = Color.Unspecified,
    )
}

@Composable
internal fun StaticLoginGreeting(
    hourProvider: () -> Int = {
        Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    },
) {
    val greeting = GreetingClock.editorialGreetingForHour(hourProvider())
    val colors = PromiseThemeColors.current
    Text(
        text = greeting,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = colors.textPrimary,
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
