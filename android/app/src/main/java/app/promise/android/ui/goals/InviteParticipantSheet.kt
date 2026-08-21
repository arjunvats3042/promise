package app.promise.android.ui.goals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.promise.android.core.ActionState
import app.promise.android.core.ErrorKind
import app.promise.android.core.toUserMessage
import app.promise.android.ui.components.PromiseModalSheet
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = PromiseThemeColors.current.accent,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InviteParticipantSheet(
    inviteSheetAction: ActionState,
    lookupState: UserLookupUi,
    onDismiss: () -> Unit,
    onLookup: (email: String) -> Unit,
    onInvite: (userId: String) -> Unit,
    onClearLookup: () -> Unit,
    onInviteSucceeded: () -> Unit = {},
) {
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var email by remember { mutableStateOf("") }
    var wasSubmitting by remember { mutableStateOf(false) }
    val submitting = inviteSheetAction is ActionState.InFlight
    val inviteError = (inviteSheetAction as? ActionState.Failed)?.kind
    val isLooking = lookupState is UserLookupUi.Loading

    LaunchedEffect(inviteSheetAction) {
        if (wasSubmitting && inviteSheetAction is ActionState.Idle) {
            onInviteSucceeded()
        }
        wasSubmitting = inviteSheetAction is ActionState.InFlight
    }

    PromiseModalSheet(
        onDismissRequest = {
            onClearLookup()
            onDismiss()
        },
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.inset, vertical = Spacing.md)) {
            Text(
                text = "Invite someone",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Enter their email to find them.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    if (lookupState !is UserLookupUi.Idle) onClearLookup()
                },
                enabled = !submitting && !isLooking,
                singleLine = true,
                label = { Text("Email") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Search,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Email address" },
                shape = RoundedCornerShape(Radius.sm),
                colors = fieldColors(),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            TextButton(
                onClick = { onLookup(email.trim()) },
                enabled = email.isNotBlank() && !isLooking && !submitting &&
                    lookupState !is UserLookupUi.Found,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Look up user" },
            ) {
                Text(
                    text = if (isLooking) "Looking up…" else "Look up",
                    color = if (isLooking || submitting) colors.textSecondary else colors.accent,
                )
            }

            when (val ls = lookupState) {
                is UserLookupUi.Found -> {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = ls.user.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary,
                    )
                    Text(
                        text = ls.user.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    when (inviteError) {
                        ErrorKind.AlreadyParticipant -> {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = inviteError.toUserMessage(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        null -> Unit
                        else -> {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = inviteError.toUserMessage(),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.lg))
                    Button(
                        onClick = { onInvite(ls.user.id) },
                        enabled = !submitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Confirm invite" },
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        shape = RoundedCornerShape(Radius.sm),
                    ) {
                        Text(if (submitting) "Inviting…" else "Invite ${ls.user.name}")
                    }
                }
                is UserLookupUi.NotFound -> {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "No account found for that email.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                is UserLookupUi.Failed -> {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = when (val kind = ls.kind) {
                            is ErrorKind.RateLimited -> {
                                val wait = kind.retryAfterSeconds
                                if (wait != null && wait > 0) {
                                    "Too many tries. Try again in ${wait}s."
                                } else {
                                    kind.toUserMessage()
                                }
                            }
                            else -> kind.toUserMessage()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                else -> Unit
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}
