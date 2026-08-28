package app.promise.android.ui.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.toUserMessage
import app.promise.android.ui.components.PromiseFieldLabel
import app.promise.android.ui.components.PromiseGreetingText
import app.promise.android.ui.components.PromisePrimaryButton
import app.promise.android.ui.components.PromiseQuietFormSurface
import app.promise.android.ui.components.PromiseTextField
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.rememberReduceMotion
import java.util.Calendar
import kotlinx.coroutines.delay

import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
                .imePadding()
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
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = "Keep your promises, simply.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                AnimatedLoginGreeting()
                Spacer(modifier = Modifier.height(Spacing.xl))

                PromiseQuietFormSurface {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "Use your Google account to continue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(Spacing.lg))

                    PromisePrimaryButton(
                        text = if (submitting) "Signing in…" else "Continue with Google",
                        loading = submitting,
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
                        modifier = Modifier
                            .heightIn(min = TouchTarget.min)
                            .semantics { contentDescription = "Continue with Google" },
                    )

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
        }
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
