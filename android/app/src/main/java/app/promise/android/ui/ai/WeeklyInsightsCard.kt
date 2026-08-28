package app.promise.android.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.sp
import app.promise.android.domain.WeeklyAiFacts
import app.promise.android.domain.WeeklyAiInsights
import app.promise.android.ui.components.PromiseHairlineDivider
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
            WeeklyInsightsSkeleton(modifier = modifier)
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
                    modifier = Modifier.padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = colors.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    Text(
                        text = "Weekly insights currently updating…",
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

            val ratePercent = (facts.completionRate * 100).toInt()
            val accessibilityText = "Weekly Insights. Completion rate $ratePercent percent. " +
                "${facts.completedCommitments} of ${facts.totalCommitments} completed, " +
                "${facts.checkInsPast7Days} check-ins, ${facts.activeGoalsCount} goals. " +
                content.summary

            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .border(1.dp, colors.accent.copy(alpha = 0.28f), RoundedCornerShape(Radius.lg))
                    .semantics(mergeDescendants = true) {
                        contentDescription = accessibilityText
                    },
                color = colors.surfaceRaised,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.cardPadding),
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .background(colors.accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                            Text(
                                text = "WEEKLY INSIGHTS",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.accent,
                                letterSpacing = 1.2.sp,
                            )
                        }
                        if (facts.totalCommitments > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(colors.accent.copy(alpha = 0.12f))
                                    .padding(horizontal = Spacing.sm, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "$ratePercent% DONE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.md))

                    // 1. Top Metrics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        // Metric 1: Completion
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = colors.surfaceMuted,
                            shape = RoundedCornerShape(Radius.md),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.sm)) {
                                Text(
                                    text = if (facts.totalCommitments > 0) "$ratePercent%" else "—",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (ratePercent >= 70) colors.success else colors.textPrimary,
                                )
                                Text(
                                    text = "Completed",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }

                        // Metric 2: Check-ins
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = colors.surfaceMuted,
                            shape = RoundedCornerShape(Radius.md),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.sm)) {
                                Text(
                                    text = "${facts.checkInsPast7Days}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Check-ins",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }

                        // Metric 3: Active Goals
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = colors.surfaceMuted,
                            shape = RoundedCornerShape(Radius.md),
                        ) {
                            Column(modifier = Modifier.padding(Spacing.sm)) {
                                Text(
                                    text = "${facts.activeGoalsCount}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.textPrimary,
                                )
                                Text(
                                    text = "Active Goals",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary,
                                )
                            }
                        }
                    }

                    // 2. AI / Factual Insight Summary
                    if (content.summary.isNotBlank()) {
                        Spacer(modifier = Modifier.height(Spacing.md))
                        PromiseHairlineDivider()
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Text(
                            text = if (insights.isFallback) "INSIGHT" else "AI INSIGHT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textSecondary,
                            letterSpacing = 1.0.sp,
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Text(
                            text = content.summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyInsightsSkeleton(
    modifier: Modifier = Modifier,
) {
    val colors = PromiseThemeColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), RoundedCornerShape(Radius.md))
            .semantics { contentDescription = "Loading weekly insights" },
        color = colors.surfaceMuted,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.cardPadding, vertical = Spacing.md),
        ) {
            // Header skeleton
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(colors.surfaceRaised),
            )
            Spacer(modifier = Modifier.height(Spacing.md))

            // Metric blocks skeleton
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                repeat(3) {
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .width(50.dp)
                                .height(22.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(colors.surfaceRaised),
                        )
                        Spacer(modifier = Modifier.height(Spacing.xxs))
                        Box(
                            modifier = Modifier
                                .width(65.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(colors.surfaceRaised.copy(alpha = 0.6f)),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))
            PromiseHairlineDivider()
            Spacer(modifier = Modifier.height(Spacing.sm))

            // Summary text skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(colors.surfaceRaised.copy(alpha = 0.5f)),
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(colors.surfaceRaised.copy(alpha = 0.4f)),
            )
        }
    }
}
