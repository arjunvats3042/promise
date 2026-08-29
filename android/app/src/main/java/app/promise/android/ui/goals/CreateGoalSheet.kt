package app.promise.android.ui.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.promise.android.core.ActionState
import app.promise.android.core.toUserMessage
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.GoalPeriodUnit
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.ui.components.PromiseDateField
import app.promise.android.ui.components.PromiseModalSheet
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import java.time.LocalDate
import java.time.ZoneId

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment

import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.IconButton
import app.promise.android.core.speech.SpeechRecognitionManager
import app.promise.android.ui.ai.VoiceCaptureSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalSheet(
    action: ActionState,
    timeZoneId: String,
    onDismiss: () -> Unit,
    onSubmit: (CreateGoalInput) -> Unit,
    onOpenAiBuilder: (() -> Unit)? = null,
    speechManager: SpeechRecognitionManager? = null,
) {
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var recurrence by remember { mutableStateOf(GoalRecurrenceKind.DAILY) }
    var weekdays by remember { mutableStateOf(setOf<Int>()) }
    var timesPerWeek by remember { mutableIntStateOf(3) }
    var tracking by remember { mutableStateOf(GoalTrackingKind.BINARY) }
    var targetValue by remember { mutableStateOf("") }
    var targetUnit by remember { mutableStateOf("") }
    var isShared by remember { mutableStateOf(false) }
    var endLocalDate by remember { mutableStateOf<LocalDate?>(null) }
    var showVoiceCapture by remember { mutableStateOf(false) }
    val submitting = action is ActionState.InFlight
    val error = (action as? ActionState.Failed)?.kind

    PromiseModalSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xl)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "New goal",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
                if (onOpenAiBuilder != null) {
                    TextButton(
                        onClick = onOpenAiBuilder,
                        enabled = !submitting,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = "Create goal with AI" },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = "Create with AI",
                            color = colors.accent,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            FieldLabel("Title")
            Spacer(modifier = Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                enabled = !submitting,
                singleLine = true,
                trailingIcon = if (speechManager != null) {
                    {
                        IconButton(
                            onClick = { showVoiceCapture = true },
                            enabled = !submitting,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "Speak goal title",
                                tint = colors.accent,
                            )
                        }
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Goal title" },
                shape = RoundedCornerShape(Radius.sm),
                colors = fieldColors(),
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            FieldLabel("Description")
            Spacer(modifier = Modifier.height(Spacing.xs))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                enabled = !submitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp),
                shape = RoundedCornerShape(Radius.sm),
                colors = fieldColors(),
            )
            Spacer(modifier = Modifier.height(Spacing.lg))
            FieldLabel("Practice")
            Spacer(modifier = Modifier.height(Spacing.xs))
            ChoiceRow(
                options = listOf(
                    GoalTrackingKind.BINARY to "Yes / Skip",
                    GoalTrackingKind.COUNT to "Count",
                ),
                selected = tracking,
                enabled = !submitting,
                onSelect = { tracking = it },
            )
            if (tracking == GoalTrackingKind.COUNT) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = targetValue,
                    onValueChange = { targetValue = it.filter { ch -> ch.isDigit() } },
                    enabled = !submitting,
                    singleLine = true,
                    label = { Text("Target value") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Target value" },
                    shape = RoundedCornerShape(Radius.sm),
                    colors = fieldColors(),
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = targetUnit,
                    onValueChange = { targetUnit = it },
                    enabled = !submitting,
                    singleLine = true,
                    label = { Text("Unit (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = fieldColors(),
                )
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            FieldLabel("Rhythm")
            ChoiceRow(
                options = listOf(
                    GoalRecurrenceKind.DAILY to "Every day",
                    GoalRecurrenceKind.WEEKLY_DAYS to "Selected days",
                    GoalRecurrenceKind.N_PER_PERIOD to "N times each week",
                ),
                selected = recurrence,
                enabled = !submitting,
                onSelect = { recurrence = it },
            )
            if (recurrence == GoalRecurrenceKind.WEEKLY_DAYS) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                WeekdayChips(
                    selected = weekdays,
                    enabled = !submitting,
                    onToggle = { day ->
                        weekdays = if (day in weekdays) weekdays - day else weekdays + day
                    },
                )
            }
            if (recurrence == GoalRecurrenceKind.N_PER_PERIOD) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    (1..7).forEach { n ->
                        TextButton(
                            onClick = { timesPerWeek = n },
                            enabled = !submitting,
                        ) {
                            Text(
                                text = "$n",
                                color = if (timesPerWeek == n) colors.accent else colors.textSecondary,
                                fontWeight = if (timesPerWeek == n) FontWeight.Medium else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(Spacing.md))
            FieldLabel("Goal type")
            ChoiceRow(
                options = listOf(
                    false to "Personal",
                    true to "Shared (with others)",
                ),
                selected = isShared,
                enabled = !submitting,
                onSelect = { isShared = it },
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            FieldLabel("End date (optional)")
            PromiseDateField(
                date = endLocalDate,
                onDateChange = { endLocalDate = it },
                enabled = !submitting,
                placeholder = "No end date",
                allowClear = true,
            )
            if (error != null) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(error.toUserMessage(), color = MaterialTheme.colorScheme.error)
            }
            Spacer(modifier = Modifier.height(Spacing.lg))
            Button(
                onClick = {
                    val start = LocalDate.now(safeZone(timeZoneId)).toString()
                    val parsedEnd = endLocalDate?.toString()
                    onSubmit(
                        CreateGoalInput(
                            title = title.trim(),
                            description = description,
                            timezone = timeZoneId,
                            startDate = start,
                            endDate = parsedEnd,
                            recurrenceKind = recurrence,
                            weekdays = if (recurrence == GoalRecurrenceKind.WEEKLY_DAYS) {
                                weekdays.sorted()
                            } else {
                                null
                            },
                            periodUnit = if (recurrence == GoalRecurrenceKind.N_PER_PERIOD) {
                                GoalPeriodUnit.WEEK
                            } else {
                                null
                            },
                            timesPerPeriod = if (recurrence == GoalRecurrenceKind.N_PER_PERIOD) {
                                timesPerWeek
                            } else {
                                null
                            },
                            trackingKind = tracking,
                            targetValue = if (tracking == GoalTrackingKind.COUNT) {
                                targetValue.toIntOrNull()
                            } else {
                                null
                            },
                            targetUnit = if (tracking == GoalTrackingKind.COUNT) targetUnit else "",
                            isShared = isShared,
                        ),
                    )
                },
                enabled = !submitting &&
                    title.isNotBlank() &&
                    (recurrence != GoalRecurrenceKind.WEEKLY_DAYS || weekdays.isNotEmpty()) &&
                    (tracking != GoalTrackingKind.COUNT || (targetValue.toIntOrNull() ?: 0) > 0),
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

    if (showVoiceCapture && speechManager != null) {
        VoiceCaptureSheet(
            speechManager = speechManager,
            onDismiss = { showVoiceCapture = false },
            onTranscriptReady = { voiceText ->
                showVoiceCapture = false
                title = voiceText
            },
            titleText = "Dictate Goal",
            subtitleText = "Speak your goal or habit title clearly.",
        )
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    val colors = PromiseThemeColors.current
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { (value, label) ->
            TextButton(
                onClick = { onSelect(value) },
                enabled = enabled,
            ) {
                Text(
                    text = label,
                    color = if (selected == value) colors.accent else colors.textSecondary,
                    fontWeight = if (selected == value) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun WeekdayChips(
    selected: Set<Int>,
    enabled: Boolean,
    onToggle: (Int) -> Unit,
) {
    val colors = PromiseThemeColors.current
    val labels = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")
    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        labels.forEach { (day, label) ->
            val on = day in selected
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
                color = if (on) colors.accent else colors.textSecondary,
                modifier = Modifier
                    .clickable(enabled = enabled) { onToggle(day) }
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    .semantics { contentDescription = "$label weekday" },
            )
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

private fun safeZone(id: String): ZoneId =
    runCatching { ZoneId.of(id) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
