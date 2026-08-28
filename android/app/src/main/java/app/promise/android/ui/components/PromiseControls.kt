package app.promise.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.promise.android.ui.theme.Alpha
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseDarkColor
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import app.promise.android.ui.theme.bouncyClickable
import app.promise.android.ui.theme.pressScale

@Composable
fun PromiseFieldLabel(text: String) {
    val colors = PromiseThemeColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textPrimary,
        fontWeight = FontWeight.Medium,
    )
    Spacer(modifier = Modifier.height(Spacing.xxs))
}

@Composable
fun PromiseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = PromiseThemeColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.fieldMin),
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        supportingText = if (!supportingText.isNullOrBlank()) {
            {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            null
        },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(Radius.md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceMuted,
            unfocusedContainerColor = colors.surfaceMuted,
            disabledContainerColor = colors.surfaceMuted.copy(alpha = 0.5f),
            errorContainerColor = colors.surfaceMuted,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.8f),
            errorBorderColor = MaterialTheme.colorScheme.error,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            cursorColor = colors.accent,
        ),
    )
}

@Composable
fun PromisePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = PromiseThemeColors.current
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.buttonMin)
            .pressScale(0.97f, enabled = enabled && !loading),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = Elevation.none,
            pressedElevation = Elevation.none,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryControl,
            contentColor = colors.onPrimaryControl,
            disabledContainerColor = colors.primaryControl.copy(alpha = Alpha.DisabledContainer),
            disabledContentColor = colors.onPrimaryControl.copy(alpha = Alpha.DisabledContent),
        ),
        shape = RoundedCornerShape(Radius.button),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.onPrimaryControl,
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
fun PromiseSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PromiseThemeColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.buttonMin)
            .pressScale(0.97f, enabled = enabled),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = Elevation.none),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.surfaceMuted,
            contentColor = colors.textPrimary,
            disabledContainerColor = colors.surfaceMuted.copy(alpha = Alpha.DisabledContainer),
            disabledContentColor = colors.textSecondary.copy(alpha = Alpha.DisabledContent),
        ),
        shape = RoundedCornerShape(Radius.button),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
fun PromiseGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color? = null,
) {
    val colors = PromiseThemeColors.current
    val fg = color ?: colors.textSecondary
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.button))
            .clickable(
                enabled = enabled,
                onClick = onClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = fg),
            )
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) fg else fg.copy(alpha = Alpha.DisabledContent),
        )
    }
}

/**
 * Modern surface container for card items.
 */
@Composable
fun PromiseCardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color? = null,
    backgroundColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PromiseThemeColors.current
    val actualBg = backgroundColor ?: colors.surfaceRaised
    val actualBorder = borderColor ?: MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)

    val baseModifier = modifier
        .fillMaxWidth()
        .border(
            width = Elevation.hairline,
            color = actualBorder,
            shape = RoundedCornerShape(Radius.lg),
        )
        .clip(RoundedCornerShape(Radius.lg))
        .background(actualBg)

    val clickableModifier = if (onClick != null) {
        baseModifier.bouncyClickable(
            targetScale = 0.98f,
            onClick = onClick,
        )
    } else {
        baseModifier
    }

    Column(
        modifier = clickableModifier.padding(Spacing.cardPadding),
        content = content,
    )
}

/**
 * Extremely restrained tonal grouping for auth forms.
 */
@Composable
fun PromiseQuietFormSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val isDark = colors.ink == PromiseDarkColor.Ink
    val fillAlpha = if (isDark) 0.94f else 0.90f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = Elevation.hairline,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                shape = RoundedCornerShape(Radius.xl),
            ),
        color = colors.surfaceRaised.copy(alpha = fillAlpha),
        shape = RoundedCornerShape(Radius.xl),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(Spacing.inset)) {
            content()
        }
    }
}

@Composable
fun PromiseHairlineDivider(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(Elevation.hairline),
        color = MaterialTheme.colorScheme.outline.copy(alpha = Alpha.Divider),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        content = {},
    )
}

@Composable
fun PromiseMicroLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val colors = PromiseThemeColors.current
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = color ?: colors.textSecondary,
        modifier = modifier,
    )
}

@Composable
fun PromiseLinearProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color? = null,
    trackColor: Color? = null,
) {
    val colors = PromiseThemeColors.current
    val actualColor = color ?: colors.accent
    val actualTrack = trackColor ?: colors.surfaceMuted
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = Motion.standardTween(Motion.CompletionMs),
        label = "ProgressBarProgress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(actualTrack)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = clampedProgress,
                    range = 0f..1f,
                )
            },
    ) {
        if (animatedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(actualColor),
            )
        }
    }
}

/**
 * Modern circular momentum progress indicator.
 */
@Composable
fun PromiseProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strokeWidth: Dp = 5.dp,
    color: Color? = null,
    trackColor: Color? = null,
    centerContent: (@Composable () -> Unit)? = null,
) {
    val colors = PromiseThemeColors.current
    val actualColor = color ?: colors.accent
    val actualTrack = trackColor ?: colors.surfaceMuted
    val clampedProgress = progress.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = clampedProgress,
        animationSpec = Motion.standardTween(Motion.CompletionMs + 100),
        label = "ProgressRingProgress",
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val radius = (this.size.minDimension - strokePx) / 2f
            // Track
            drawCircle(
                color = actualTrack,
                radius = radius,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            // Progress arc
            if (animatedProgress > 0f) {
                drawArc(
                    color = actualColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        centerContent?.invoke()
    }
}

@Composable
fun PromiseStreakBadge(
    streakText: String,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(colors.accent.copy(alpha = 0.12f))
            .border(1.dp, colors.accent.copy(alpha = 0.25f), RoundedCornerShape(Radius.pill))
            .padding(horizontal = Spacing.sm, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = "🔥",
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
        )
        Text(
            text = streakText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
        )
    }
}

@Composable
fun PromiseEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    val colors = PromiseThemeColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl, horizontal = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(colors.textSecondary.copy(alpha = 0.35f)),
            )
        }
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            lineHeight = 20.sp,
        )
        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.height(Spacing.lg))
            PromisePrimaryButton(
                text = actionLabel,
                onClick = onActionClick,
                modifier = Modifier.fillMaxWidth(0.6f),
            )
        }
    }
}

@Composable
fun PromiseErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    val colors = PromiseThemeColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = Elevation.hairline,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                shape = RoundedCornerShape(Radius.md),
            ),
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        shape = RoundedCornerShape(Radius.md),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (onRetry != null) {
                Spacer(modifier = Modifier.width(Spacing.sm))
                Button(
                    onClick = onRetry,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryControl,
                        contentColor = colors.onPrimaryControl,
                    ),
                    shape = RoundedCornerShape(Radius.sm),
                ) {
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

enum class DayProgressState {
    Completed,
    Pending,
    Missed,
    Inactive,
}

@Composable
fun PromiseWeeklyProgressDots(
    days: List<Pair<String, DayProgressState>>,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        days.forEach { (dayLabel, state) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Medium,
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            when (state) {
                                DayProgressState.Completed -> colors.accent.copy(alpha = 0.18f)
                                DayProgressState.Pending -> colors.surfaceMuted
                                DayProgressState.Missed -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                DayProgressState.Inactive -> colors.surfaceMuted.copy(alpha = 0.5f)
                            },
                        )
                        .border(
                            1.dp,
                            when (state) {
                                DayProgressState.Completed -> colors.accent
                                DayProgressState.Pending -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                DayProgressState.Missed -> MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                DayProgressState.Inactive -> MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                            },
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when (state) {
                        DayProgressState.Completed -> {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                        }
                        DayProgressState.Pending -> {
                            Text(
                                text = "○",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        DayProgressState.Missed -> {
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        DayProgressState.Inactive -> {
                            Text(
                                text = "·",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PromiseStatusChip(
    label: String,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false,
    isAccent: Boolean = false,
) {
    val colors = PromiseThemeColors.current
    val bg = when {
        isAccent -> colors.accent.copy(alpha = 0.12f)
        isWarning -> colors.warning.copy(alpha = 0.12f)
        else -> colors.surfaceMuted
    }
    val fg = when {
        isAccent -> colors.accent
        isWarning -> colors.warning
        else -> colors.textSecondary
    }
    val border = when {
        isAccent -> colors.accent.copy(alpha = 0.3f)
        isWarning -> colors.warning.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(Radius.pill))
            .padding(horizontal = Spacing.sm, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = fg,
        )
    }
}


