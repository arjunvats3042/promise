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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.toUserMessage
import app.promise.android.ui.components.PromiseFieldLabel
import app.promise.android.ui.components.PromisePrimaryButton
import app.promise.android.ui.components.PromiseQuietFormSurface
import app.promise.android.ui.components.PromiseTextField
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget

@Composable
fun RegisterScreen(
    viewModel: RegisterViewModel,
    onSignIn: () -> Unit,
    onAllowLocalNetwork: (() -> Unit)? = null,
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val password by viewModel.password.collectAsStateWithLifecycle()
    val action by viewModel.action.collectAsStateWithLifecycle()
    val submitting = action is ActionState.InFlight
    val error = (action as? ActionState.Failed)?.kind
    val fieldErrors = (error as? ErrorKind.Validation)?.fields.orEmpty()
    val localNetworkDenied = error == ErrorKind.LocalNetworkDenied
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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.inset),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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
                Spacer(modifier = Modifier.height(Spacing.lg))
                Text(
                    text = "Create account",
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(Spacing.xxs))
                Text(
                    text = "Start keeping promises.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(Spacing.lg))
                app.promise.android.ui.components.PromiseQuietFormSurface {
                    app.promise.android.ui.components.PromiseFieldLabel("NAME")
                    app.promise.android.ui.components.PromiseTextField(
                        value = name,
                        onValueChange = viewModel::onNameChange,
                        enabled = !submitting,
                        isError = fieldErrors.containsKey("name"),
                        supportingText = fieldErrors["name"],
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    app.promise.android.ui.components.PromiseFieldLabel("EMAIL")
                    app.promise.android.ui.components.PromiseTextField(
                        value = email,
                        onValueChange = viewModel::onEmailChange,
                        enabled = !submitting,
                        isError = fieldErrors.containsKey("email") || error == ErrorKind.EmailAlreadyExists,
                        supportingText = fieldErrors["email"]
                            ?: if (error == ErrorKind.EmailAlreadyExists) error.toUserMessage() else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    )
                    Spacer(modifier = Modifier.height(Spacing.md))
                    app.promise.android.ui.components.PromiseFieldLabel("PASSWORD")
                    app.promise.android.ui.components.PromiseTextField(
                        value = password,
                        onValueChange = viewModel::onPasswordChange,
                        enabled = !submitting,
                        isError = fieldErrors.containsKey("password"),
                        supportingText = fieldErrors["password"],
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    if (error != null &&
                        error !is ErrorKind.Validation &&
                        error != ErrorKind.EmailAlreadyExists
                    ) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Text(
                            text = error.toUserMessage(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (error is ErrorKind.Validation && fieldErrors.isEmpty()) {
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
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    app.promise.android.ui.components.PromisePrimaryButton(
                        text = if (submitting) "Creating account…" else "Create account",
                        onClick = viewModel::submit,
                        enabled = !submitting &&
                            name.isNotBlank() &&
                            email.isNotBlank() &&
                            password.isNotEmpty(),
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.md))
                TextButton(
                    onClick = onSignIn,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TouchTarget.min),
                ) {
                    Text(
                        text = "Already have an account? Sign in",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent,
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.section))
            }
        }
    }
}
