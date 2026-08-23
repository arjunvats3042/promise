package app.promise.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PromiseMicroLabel(text = sectionLabel)
            if (streakDays != null && streakDays > 0) {
                PromiseStreakBadge(streakText = "${streakDays}d")
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                text = primaryProgressText,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
            )
            if (!progressSubtitle.isNullOrBlank()) {
                Text(
                    text = progressSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        PromiseLinearProgressBar(
            progress = progressFraction,
            modifier = Modifier.fillMaxWidth(),
        )

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
                            text = "Next check-in: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                        )
                        Text(
                            text = nextCheckInLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
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
