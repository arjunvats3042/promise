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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.promise.android.domain.WeeklyAiInsights
import app.promise.android.ui.home.WeeklyInsightsUiState
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing

@Composable
fun WeeklyInsightsCard(
    state: WeeklyInsightsUiState,
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current

    when (state) {
        is WeeklyInsightsUiState.Loading -> {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(Radius.md))
                    .semantics { contentDescription = "Loading weekly AI insights" },
                color = colors.surfaceMuted,
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "Analyzing weekly performance…",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        is WeeklyInsightsUiState.Error -> {
            // Quiet, restrained fallback - does not disrupt home screen or show diagnostic jargon
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(Radius.md))
                    .semantics { contentDescription = "Weekly AI insights unavailable" },
                color = colors.surfaceMuted,
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = colors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(
                        text = "Weekly AI insights currently unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
        is WeeklyInsightsUiState.Success -> {
            val insights: WeeklyAiInsights = state.insights
            val content = insights.insights
            val facts = insights.facts

            val accessibilityText = "Weekly AI Insights. ${content.summary}. " +
                "Completed ${facts.completedCommitments} of ${facts.totalCommitments} commitments with ${facts.checkInsPast7Days} practice check-ins."

            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .border(1.dp, colors.accent.copy(alpha = 0.3f), RoundedCornerShape(Radius.md))
                    .semantics(mergeDescendants = true) {
                        contentDescription = accessibilityText
                    },
                color = colors.surfaceMuted,
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "WEEKLY AI INSIGHTS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                        )
                    }

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Text(
                        text = content.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary,
                    )

                    Spacer(modifier = Modifier.height(Spacing.xs))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Column {
                            Text(
                                text = "${facts.completedCommitments}/${facts.totalCommitments}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                            Text(
                                text = "Commitments",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Column {
                            Text(
                                text = "${facts.checkInsPast7Days}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Check-ins (7d)",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                        Column {
                            Text(
                                text = "${facts.activeGoalsCount}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Goals",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    if (content.constructiveSuggestion.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(
                            text = "💡 ${content.constructiveSuggestion}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
