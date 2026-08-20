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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.promise.android.ui.components.PromiseDateField
import app.promise.android.ui.components.PromiseModalSheet
import app.promise.android.ui.components.PromiseTimeField
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeSheet(
    timeZoneId: String,
    onDismiss: () -> Unit,
    onConfirm: (snoozedUntilIso: String) -> Unit,
) {
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var customDate by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var customTime by remember { mutableStateOf(LocalTime.of(9, 0)) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(instant: Instant) {
        if (!CommitmentTime.isValidSnooze(instant)) {
            error = "Choose a time in the next 30 days."
            return
        }
        onConfirm(instant.toString())
    }

    PromiseModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.inset, vertical = Spacing.md)) {
            Text(
                text = "Snooze",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Reminders pause until then. The original due date stays the same.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            PresetButton("Later today") {
                submit(CommitmentTime.laterToday(timeZoneId))
            }
            PresetButton("Tomorrow morning") {
                submit(CommitmentTime.tomorrowMorning(timeZoneId))
            }
            PresetButton("Next week") {
                submit(CommitmentTime.nextWeek(timeZoneId))
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            Text("Custom", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(Spacing.xs))
            PromiseDateField(
                date = customDate,
                onDateChange = { if (it != null) customDate = it },
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            PromiseTimeField(
                time = customTime,
                onTimeChange = { customTime = it },
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(error!!, color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            Button(
                onClick = {
                    val instant = LocalDateTime.of(customDate, customTime)
                        .atZone(CommitmentTime.zone(timeZoneId))
                        .toInstant()
                    submit(instant)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                shape = RoundedCornerShape(Radius.sm),
            ) {
                Text("Snooze until then")
            }
            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    val colors = PromiseThemeColors.current
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
    ) {
        Text(label, color = colors.accent)
    }
}
