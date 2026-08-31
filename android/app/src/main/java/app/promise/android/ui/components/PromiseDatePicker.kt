package app.promise.android.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import app.promise.android.ui.theme.TouchTarget
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun PromiseDateField(
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String = "Choose date",
    allowClear: Boolean = false,
) {
    val context = LocalContext.current
    val colors = PromiseThemeColors.current
    val displayText = date?.let { DateTimeFormat.formatDate(it) } ?: placeholder

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.min)
            .clip(RoundedCornerShape(Radius.sm))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.sm))
            .background(colors.surfaceMuted)
            .clickable(enabled = enabled) {
                val initial = date ?: LocalDate.now()
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateChange(LocalDate.of(year, month + 1, dayOfMonth))
                    },
                    initial.year,
                    initial.monthValue - 1,
                    initial.dayOfMonth,
                ).show()
            }
            .semantics {
                contentDescription = "$displayText. Double tap to change."
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.weight(1f),
        ) {
            Icon(
                imageVector = Icons.Outlined.CalendarToday,
                contentDescription = null,
                tint = if (date != null) colors.accent else colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyLarge,
                color = if (date != null) colors.textPrimary else colors.textSecondary,
            )
        }
        if (allowClear && date != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(colors.surfaceRaised)
                    .clickable(enabled = enabled) { onDateChange(null) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear date",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Text(
                text = "Change",
                style = MaterialTheme.typography.bodySmall,
                color = colors.accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun PromiseTimeField(
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    val colors = PromiseThemeColors.current
    val formatted = DateTimeFormat.formatTime(time)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget.min)
            .clip(RoundedCornerShape(Radius.sm))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(Radius.sm))
            .background(colors.surfaceMuted)
            .clickable(enabled = enabled) {
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        onTimeChange(LocalTime.of(hour, minute))
                    },
                    time.hour,
                    time.minute,
                    false,
                ).show()
            }
            .semantics {
                contentDescription = "Time: $formatted. Double tap to change."
            }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.AccessTime,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = formatted,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "Change",
            style = MaterialTheme.typography.bodySmall,
            color = colors.accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
