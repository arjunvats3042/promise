package app.promise.android.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
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
