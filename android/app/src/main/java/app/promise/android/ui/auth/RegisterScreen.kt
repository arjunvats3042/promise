package app.promise.android.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.toUserMessage
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing

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
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Promise",
                style = MaterialTheme.typography.displayLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Create an account.",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.section))
            AuthFieldLabel("Name")
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !submitting,
                isError = fieldErrors.containsKey("name"),
                supportingText = fieldSupportingText(fieldErrors["name"]),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                shape = RoundedCornerShape(Radius.sm),
                colors = authFieldColors(),
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            AuthFieldLabel("Email")
            OutlinedTextField(
                value = email,
                onValueChange = viewModel::onEmailChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !submitting,
                isError = fieldErrors.containsKey("email") || error == ErrorKind.EmailAlreadyExists,
                supportingText = fieldSupportingText(
                    fieldErrors["email"]
                        ?: if (error == ErrorKind.EmailAlreadyExists) error.toUserMessage() else null,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(Radius.sm),
                colors = authFieldColors(),
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            AuthFieldLabel("Password")
            OutlinedTextField(
                value = password,
                onValueChange = viewModel::onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !submitting,
                isError = fieldErrors.containsKey("password"),
                supportingText = fieldSupportingText(fieldErrors["password"]),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(Radius.sm),
                colors = authFieldColors(),
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
                TextButton(onClick = onAllowLocalNetwork) {
                    Text("Allow local network", color = colors.accent)
                }
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = viewModel::submit,
                enabled = !submitting &&
                    name.isNotBlank() &&
                    email.isNotBlank() &&
                    password.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.ink,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.secondary,
                    disabledContentColor = MaterialTheme.colorScheme.onSecondary,
                ),
                shape = RoundedCornerShape(Radius.sm),
            ) {
                Text(if (submitting) "Creating account…" else "Create account")
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            TextButton(
                onClick = onSignIn,
                enabled = !submitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Already have an account? Sign in", color = colors.accent)
            }
            Spacer(modifier = Modifier.height(Spacing.section))
        }
    }
}

@Composable
private fun AuthFieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = PromiseThemeColors.current.textSecondary,
    )
    Spacer(modifier = Modifier.height(Spacing.xxs))
}

@Composable
private fun fieldSupportingText(message: String?): (@Composable () -> Unit)? {
    if (message.isNullOrBlank()) return null
    return {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
internal fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    disabledContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = PromiseThemeColors.current.accent,
)
