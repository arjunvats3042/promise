package app.promise.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Spacing

@Composable
fun PromiseGoalHero(
    sectionLabel: String,
    primaryProgressText: String,
    progressFraction: Float,
    modifier: Modifier = Modifier,
    progressSubtitle: String? = null,
    streakDays: Int? = null,
    nextCheckInLabel: String? = null,
    collectiveText: String? = null,
) {
    val colors = PromiseThemeColors.current

    PromiseCardSurface(modifier = modifier) {
        PromiseMicroLabel(text = sectionLabel)

        Spacer(modifier = Modifier.height(Spacing.lg))

        // Ring + details row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Progress ring with percentage inside
            PromiseProgressRing(
                progress = progressFraction,
                size = 72.dp,
                strokeWidth = 6.dp,
            ) {
                Text(
                    text = "${(progressFraction * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.width(Spacing.lg))

            // Text details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = primaryProgressText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                if (!progressSubtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.xxs))
                    Text(
                        text = progressSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                    )
                }
                if (streakDays != null && streakDays > 0) {
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    PromiseStreakBadge(streakText = "${streakDays}d")
                }
            }
        }

        // Footer info row
        if (!nextCheckInLabel.isNullOrBlank() || !collectiveText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Spacing.md))
            PromiseHairlineDivider()
            Spacer(modifier = Modifier.height(Spacing.sm))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!nextCheckInLabel.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Next: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = nextCheckInLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accent,
                        )
                    }
                }
                if (!collectiveText.isNullOrBlank()) {
                    Text(
                        text = collectiveText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                    )
                }
            }
        }
    }
}
