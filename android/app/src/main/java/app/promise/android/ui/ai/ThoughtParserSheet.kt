package app.promise.android.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import app.promise.android.domain.CreateCommitmentInput
import app.promise.android.domain.CreateGoalInput
import app.promise.android.domain.DuePrecision
import app.promise.android.domain.GoalRecurrenceKind
import app.promise.android.domain.GoalTrackingKind
import app.promise.android.domain.ParsedThoughtItem
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThoughtParserSheet(
    onDismiss: () -> Unit,
    onParseThought: suspend (String) -> List<ParsedThoughtItem>,
    onCreateCommitment: (CreateCommitmentInput) -> Unit,
    onCreateGoal: (CreateGoalInput) -> Unit,
) {
    val colors = PromiseThemeColors.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var thoughtText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var parsedItems by remember { mutableStateOf<List<ParsedThoughtItem>?>(null) }
    val selectedIndices = remember { mutableStateListOf<Int>() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.xl)
                .imePadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Thought → Promise",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "Paste a brain dump of multiple tasks or habits. AI will decompose it into structured items for your selection.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.xl))

            if (parsedItems == null) {
                OutlinedTextField(
                    value = thoughtText,
                    onValueChange = {
                        thoughtText = it
                        if (errorMessage != null) errorMessage = null
                    },
                    label = { Text("E.g. I need to submit resume, call doctor, and read 20 mins every day") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                    ),
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Button(
                    onClick = {
                        val query = thoughtText.trim()
                        if (query.isNotBlank()) {
                            isLoading = true
                            errorMessage = null
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                try {
                                    val items = onParseThought(query)
                                    parsedItems = items
                                    selectedIndices.clear()
                                    selectedIndices.addAll(items.indices)
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (_: kotlinx.coroutines.CancellationException) {
                                    // Ignored silently on lifecycle/sheet cancel
                                } catch (e: Exception) {
                                    errorMessage = "Could not parse thoughts. Please try again."
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    },
                    enabled = thoughtText.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TouchTarget.min),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.surfaceMuted,
                    ),
                    shape = RoundedCornerShape(Radius.sm),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = colors.surfaceMuted,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "Decompose Thoughts",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            } else {
                val items = parsedItems!!
                Text(
                    text = "SELECT ITEMS TO CREATE (${selectedIndices.size}/${items.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                )
                Spacer(modifier = Modifier.height(Spacing.sm))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    itemsIndexed(items) { index, item ->
                        val isSelected = selectedIndices.contains(index)
                        Surface(
                            onClick = {
                                if (isSelected) selectedIndices.remove(index) else selectedIndices.add(index)
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .border(
                                    1.dp,
                                    if (isSelected) colors.accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    RoundedCornerShape(Radius.sm),
                                ),
                            color = if (isSelected) colors.surfaceMuted else MaterialTheme.colorScheme.surface,
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isSelected) colors.accent else colors.textSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                                    ) {
                                        Text(
                                            text = item.type.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.accent,
                                        )
                                        Text(
                                            text = "• ${item.confidence} confidence",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary,
                                    )
                                    if (item.description.isNotBlank()) {
                                        Text(
                                            text = item.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.lg))

                Button(
                    onClick = {
                        val chosen = items.filterIndexed { index, _ -> selectedIndices.contains(index) }
                        chosen.forEach { item ->
                            if (item.type == "commitment") {
                                onCreateCommitment(
                                    CreateCommitmentInput(
                                        title = item.title,
                                        description = item.description,
                                        dueAt = item.dueAt,
                                        duePrecision = when (item.duePrecision?.uppercase()) {
                                            "MINUTE" -> DuePrecision.DATETIME
                                            "HOUR" -> DuePrecision.DATETIME
                                            "DAY" -> DuePrecision.DATE
                                            else -> DuePrecision.NONE
                                        },
                                    ),
                                )
                            } else {
                                onCreateGoal(
                                    CreateGoalInput(
                                        title = item.title,
                                        description = item.description,
                                        recurrenceKind = when (item.recurrenceKind?.uppercase()) {
                                            "WEEKLY_DAYS" -> GoalRecurrenceKind.WEEKLY_DAYS
                                            "N_PER_PERIOD" -> GoalRecurrenceKind.N_PER_PERIOD
                                            else -> GoalRecurrenceKind.DAILY
                                        },
                                        weekdays = item.weekdays,
                                        trackingKind = if (item.trackingKind?.uppercase() == "COUNT") GoalTrackingKind.COUNT else GoalTrackingKind.BINARY,
                                        targetValue = item.targetValue?.toInt(),
                                        targetUnit = item.targetUnit,
                                    ),
                                )
                            }
                        }
                        onDismiss()
                    },
                    enabled = selectedIndices.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TouchTarget.min),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.surfaceMuted,
                    ),
                    shape = RoundedCornerShape(Radius.sm),
                ) {
                    Icon(imageVector = Icons.Outlined.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "Create ${selectedIndices.size} Item(s)",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                TextButton(
                    onClick = {
                        parsedItems = null
                        selectedIndices.clear()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Edit thoughts",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))
        }
    }
}
