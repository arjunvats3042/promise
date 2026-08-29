package app.promise.android.ui.ai

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.speech.SpeechRecognitionManager
import app.promise.android.core.speech.SpeechRecognitionState
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.rememberReduceMotion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCaptureSheet(
    speechManager: SpeechRecognitionManager,
    onDismiss: () -> Unit,
    onTranscriptReady: (String) -> Unit,
    titleText: String = "Voice → Promise",
    subtitleText: String = "Speak naturally. Mention tasks, deadlines, or recurring habits.",
) {
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val speechState by speechManager.state.collectAsStateWithLifecycle()
    var hasRequestedPermissionOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            speechManager.startListening()
        }
    }

    LaunchedEffect(Unit) {
        if (!speechManager.hasRecordAudioPermission()) {
            if (!hasRequestedPermissionOnce) {
                hasRequestedPermissionOnce = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            speechManager.startListening()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.reset()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            speechManager.reset()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                    )
                }
                IconButton(
                    onClick = {
                        speechManager.reset()
                        onDismiss()
                    },
                    modifier = Modifier.size(TouchTarget.min),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.xxl))

            // Central Acoustic Visualization
            when (val current = speechState) {
                is SpeechRecognitionState.PermissionRequired -> {
                    PermissionRequiredView(
                        onGrantPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                    )
                }
                is SpeechRecognitionState.Error -> {
                    VoiceErrorView(
                        errorMessage = current.message,
                        onRetry = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            speechManager.startListening()
                        },
                    )
                }
                else -> {
                    val isListening = current is SpeechRecognitionState.Listening
                    val rms = if (current is SpeechRecognitionState.Listening) current.rmsNormalized else 0f
                    val transcript = when (current) {
                        is SpeechRecognitionState.Listening -> current.partialText
                        is SpeechRecognitionState.Finished -> current.finalText
                        else -> ""
                    }

                    AcousticWaveform(
                        isListening = isListening,
                        rmsNormalized = rms,
                        reduceMotion = reduceMotion,
                        onToggleListening = {
                            if (isListening) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                speechManager.stopListening()
                            } else {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                speechManager.startListening()
                            }
                        },
                    )

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    // Live Transcript Display Card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 180.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .border(
                                width = 1.dp,
                                color = if (transcript.isNotBlank()) colors.accent.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(Radius.md),
                            ),
                        color = colors.surfaceMuted,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md)
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = if (transcript.isBlank()) Alignment.Center else Alignment.TopStart,
                        ) {
                            if (transcript.isBlank()) {
                                Text(
                                    text = if (isListening) "Listening... Start speaking" else "Tap mic to speak",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                Text(
                                    text = transcript,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 24.sp,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.xl))

                    // Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        OutlinedButton(
                            onClick = {
                                speechManager.reset()
                                speechManager.startListening()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(TouchTarget.min),
                            shape = RoundedCornerShape(Radius.sm),
                            enabled = transcript.isNotBlank() || !isListening,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Clear / Retake")
                        }

                        Button(
                            onClick = {
                                val text = transcript.trim()
                                if (text.isNotBlank()) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    speechManager.reset()
                                    onTranscriptReady(text)
                                } else {
                                    speechManager.stopListening()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(TouchTarget.min),
                            shape = RoundedCornerShape(Radius.sm),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accent,
                                contentColor = colors.surfaceMuted,
                            ),
                            enabled = transcript.isNotBlank(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text("Use Voice")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun AcousticWaveform(
    isListening: Boolean,
    rmsNormalized: Float,
    reduceMotion: Boolean,
    onToggleListening: () -> Unit,
) {
    val colors = PromiseThemeColors.current

    val smoothRms by animateFloatAsState(
        targetValue = if (isListening) rmsNormalized else 0f,
        animationSpec = spring(stiffness = 200f, dampingRatio = 0.7f),
        label = "rmsAnimation",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulseLoop")
    val idlePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idlePulse",
    )

    val baseScale = if (reduceMotion) 1f else if (isListening) (1f + smoothRms * 0.4f) else idlePulse

    Box(
        modifier = Modifier
            .size(180.dp)
            .semantics { contentDescription = if (isListening) "Microphone active" else "Microphone idle" },
        contentAlignment = Alignment.Center,
    ) {
        // Acoustic ripple rings drawn on Canvas
        if (!reduceMotion && isListening) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val baseRadius = 42.dp.toPx()

                // Outer Ring 2
                drawCircle(
                    color = colors.accent.copy(alpha = (0.12f + smoothRms * 0.25f).coerceIn(0f, 0.4f)),
                    radius = baseRadius + 36.dp.toPx() * (0.6f + smoothRms * 0.6f),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )

                // Outer Ring 1
                drawCircle(
                    color = colors.accent.copy(alpha = (0.2f + smoothRms * 0.35f).coerceIn(0f, 0.6f)),
                    radius = baseRadius + 18.dp.toPx() * (0.4f + smoothRms * 0.6f),
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }

        // Inner Action Circle Button
        Surface(
            onClick = onToggleListening,
            modifier = Modifier
                .size((72 * baseScale).dp)
                .clip(CircleShape),
            shape = CircleShape,
            color = if (isListening) colors.accent else colors.surfaceMuted,
            shadowElevation = if (isListening) 4.dp else 1.dp,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Outlined.Mic else Icons.Outlined.MicOff,
                    contentDescription = if (isListening) "Tap to pause" else "Tap to speak",
                    tint = if (isListening) colors.surfaceMuted else colors.textPrimary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredView(
    onGrantPermission: () -> Unit,
) {
    val colors = PromiseThemeColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.MicOff,
            contentDescription = null,
            tint = colors.accent,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Microphone Access Needed",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = "Promise uses on-device speech recognition to transcribe your commitments and habits.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        Button(
            onClick = onGrantPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.surfaceMuted,
            ),
            shape = RoundedCornerShape(Radius.sm),
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchTarget.min),
        ) {
            Text("Grant Microphone Access")
        }
    }
}

@Composable
private fun VoiceErrorView(
    errorMessage: String,
    onRetry: () -> Unit,
) {
    val colors = PromiseThemeColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.MicOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Voice Recognition Error",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.xl))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.surfaceMuted,
            ),
            shape = RoundedCornerShape(Radius.sm),
            modifier = Modifier
                .fillMaxWidth()
                .height(TouchTarget.min),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
            Text("Try Speaking Again")
        }
    }
}
