package app.promise.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.promise.android.ui.theme.Alpha
import app.promise.android.ui.theme.Elevation
import app.promise.android.ui.theme.PromiseDarkColor
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget

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
        shape = RoundedCornerShape(Radius.sm),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceMuted,
            unfocusedContainerColor = colors.surfaceMuted,
            disabledContainerColor = colors.surfaceMuted,
            errorContainerColor = colors.surfaceMuted,
            focusedBorderColor = colors.accent.copy(alpha = Alpha.FocusAccent),
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
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
) {
    val colors = PromiseThemeColors.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.buttonMin),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = Elevation.none),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primaryControl,
            contentColor = colors.onPrimaryControl,
            disabledContainerColor = colors.primaryControl.copy(alpha = Alpha.DisabledContainer),
            disabledContentColor = colors.onPrimaryControl.copy(alpha = Alpha.DisabledContent),
        ),
        shape = RoundedCornerShape(Radius.button),
    ) {
        Text(text)
    }
}

/**
 * Extremely restrained tonal grouping for auth forms.
 * Prefer no floating-card appearance: quiet fill + low-contrast hairline.
 */
@Composable
fun PromiseQuietFormSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val isDark = colors.ink == PromiseDarkColor.Ink
    val fillAlpha = if (isDark) 0.92f else 0.88f
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = Elevation.hairline,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                shape = RoundedCornerShape(Radius.lg),
            ),
        color = colors.surfaceRaised.copy(alpha = fillAlpha),
        shape = RoundedCornerShape(Radius.lg),
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
        style = MaterialTheme.typography.labelMedium,
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(actualTrack),
    ) {
        if (clampedProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(clampedProgress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(actualColor),
            )
        }
    }
}

@Composable
fun PromiseCardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = PromiseThemeColors.current
    val baseModifier = modifier
        .fillMaxWidth()
        .border(
            width = Elevation.hairline,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            shape = RoundedCornerShape(Radius.lg),
        )
        .clip(RoundedCornerShape(Radius.lg))
        .background(colors.surfaceRaised)

    val clickableModifier = if (onClick != null) {
        baseModifier.clickable(onClick = onClick)
    } else {
        baseModifier
    }

    Column(
        modifier = clickableModifier.padding(Spacing.md),
        content = content,
    )
}

@Composable
fun PromiseStreakBadge(
    streakText: String,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(colors.accent.copy(alpha = 0.12f))
            .padding(horizontal = Spacing.xs + 2.dp, vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Text(
            text = "STREAK",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
        )
        Text(
            text = streakText,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.accent,
        )
    }
}
