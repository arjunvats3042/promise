package app.promise.android.ui.ai

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.promise.android.domain.GoalChatAiSummary
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSummarySheet(
    goalId: String,
    onDismiss: () -> Unit,
    onFetchSummary: suspend (String) -> GoalChatAiSummary,
) {
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var summaryResult by remember { mutableStateOf<GoalChatAiSummary?>(null) }

    LaunchedEffect(goalId) {
        try {
            summaryResult = onFetchSummary(goalId)
        } catch (e: Exception) {
            errorMessage = "Could not summarize chat. Please try again."
        } finally {
            isLoading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.inset, vertical = Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = "Chat Summary",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = "Key decisions, actions, dates, and open questions from recent conversation.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(Spacing.md))

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = colors.accent,
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Text(
                        text = "Analyzing messages...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else if (summaryResult != null) {
                val res = summaryResult!!
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .border(1.dp, colors.accent.copy(alpha = 0.2f), RoundedCornerShape(Radius.sm)),
                            color = colors.surfaceMuted,
                        ) {
                            Column(modifier = Modifier.padding(Spacing.md)) {
                                Text(
                                    text = "SUMMARY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.accent,
                                )
                                Spacer(modifier = Modifier.height(Spacing.xxs))
                                Text(
                                    text = res.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                )
                            }
                        }
                    }

                    if (res.keyDecisions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Key Decisions",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                        }
                        items(res.keyDecisions) { decision ->
                            Text(
                                text = "• $decision",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }

                    if (res.agreedActions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Agreed Actions",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                        }
                        items(res.agreedActions) { action ->
                            Text(
                                text = "• $action",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }

                    if (res.importantDates.isNotEmpty()) {
                        item {
                            Text(
                                text = "Important Dates",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                        }
                        items(res.importantDates) { date ->
                            Text(
                                text = "📅 $date",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }

                    if (res.openQuestions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Open Questions",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.textPrimary,
                            )
                        }
                        items(res.openQuestions) { question ->
                            Text(
                                text = "❓ $question",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
        }
    }
}
