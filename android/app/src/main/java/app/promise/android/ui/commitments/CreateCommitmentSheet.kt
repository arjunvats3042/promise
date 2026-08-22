package app.promise.android.ui.commitments

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.promise.android.core.ActionState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.DuePrecision
import app.promise.android.ui.components.PromiseDateField
import app.promise.android.ui.components.PromiseModalSheet
import app.promise.android.ui.components.PromiseTimeField
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCommitmentSheet(
    action: ActionState,
    timeZoneId: String,
    onDismiss: () -> Unit,
    onSubmit: (title: String, description: String, dueAt: String?, precision: DuePrecision) -> Unit,
    onOpenRefiner: (() -> Unit)? = null,
) {
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueMode by remember { mutableStateOf(DueMode.None) }
    var dueDate by remember { mutableStateOf(LocalDate.now()) }
    var dueTime by remember { mutableStateOf(LocalTime.of(18, 0)) }
    val submitting = action is ActionState.InFlight
    val error = (action as? ActionState.Failed)?.kind

    PromiseModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.inset, vertical = Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "New commitment",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                )
                if (onOpenRefiner != null) {
                    TextButton(
                        onClick = onOpenRefiner,
                        enabled = !submitting,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Refine commitment with AI" },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Refine with AI",
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            FieldLabel("Title")
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !submitting,
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Commitment title" },
                shape = RoundedCornerShape(Radius.sm),
                colors = fieldColors(),
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            FieldLabel("Description")
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                enabled = !submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp),
                shape = RoundedCornerShape(Radius.sm),
                colors = fieldColors(),
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            FieldLabel("Due")
            RowDueModes(dueMode = dueMode, onSelect = { dueMode = it }, enabled = !submitting)
            if (dueMode != DueMode.None) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                FieldLabel("Date")
                PromiseDateField(
                    date = dueDate,
                    onDateChange = { if (it != null) dueDate = it },
                    enabled = !submitting,
                )
            }
            if (dueMode == DueMode.DateTime) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                FieldLabel("Time")
                PromiseTimeField(
                    time = dueTime,
                    onTimeChange = { dueTime = it },
                    enabled = !submitting,
                )
            }
            if (error != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(error.toUserMessage(), color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = {
                    val parsed = parseDue(dueMode, dueDate, dueTime, timeZoneId)
                    onSubmit(title.trim(), description, parsed.first, parsed.second)
                },
                enabled = !submitting && title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                shape = RoundedCornerShape(Radius.sm),
            ) {
                Text(if (submitting) "Saving…" else "Create")
            }
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

private enum class DueMode { None, Date, DateTime }

@Composable
private fun RowDueModes(dueMode: DueMode, onSelect: (DueMode) -> Unit, enabled: Boolean) {
    val colors = PromiseThemeColors.current
    androidx.compose.foundation.layout.Row {
        listOf(
            DueMode.None to "None",
            DueMode.Date to "Date",
            DueMode.DateTime to "Date & time",
        ).forEach { (mode, label) ->
            TextButton(
                onClick = { onSelect(mode) },
                enabled = enabled,
            ) {
                Text(
                    text = label,
                    color = if (dueMode == mode) colors.accent else colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = PromiseThemeColors.current.textSecondary,
    )
    Spacer(modifier = Modifier.height(Spacing.xxs))
}

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

private fun parseDue(
    mode: DueMode,
    date: LocalDate,
    time: LocalTime,
    timeZoneId: String,
): Pair<String?, DuePrecision> {
    return when (mode) {
        DueMode.None -> null to DuePrecision.NONE
        DueMode.Date -> {
            CommitmentTime.endOfLocalDayIso(date, timeZoneId) to DuePrecision.DATE
        }
        DueMode.DateTime -> {
            val ldt = LocalDateTime.of(date, time)
            CommitmentTime.localDateTimeIso(ldt, timeZoneId) to DuePrecision.DATETIME
        }
    }
}
