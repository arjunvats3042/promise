package app.promise.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Spacing

@Composable
fun PromiseTimelineItem(
    title: String,
    timestamp: String,
    modifier: Modifier = Modifier,
    isCompleted: Boolean = true,
    detail: String? = null,
    avatarInitials: String? = null,
    avatarPhoto: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = PromiseThemeColors.current
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.xs + 2.dp)
    } else {
        modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs + 2.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!avatarInitials.isNullOrBlank()) {
            PromiseAvatar(
                initials = avatarInitials,
                photoPath = avatarPhoto,
                size = AvatarSize.SM,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) colors.success.copy(alpha = 0.15f) else colors.surfaceMuted)
                    .border(
                        1.dp,
                        if (isCompleted) colors.success.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = colors.success,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(Spacing.sm))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = timestamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                )
            }
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
