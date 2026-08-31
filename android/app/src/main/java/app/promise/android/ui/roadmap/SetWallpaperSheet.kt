package app.promise.android.ui.roadmap

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.promise.android.ui.theme.PromiseDarkColor
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetWallpaperSheet(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    var selectedTarget by remember { mutableStateOf(RoadmapWallpaperManager.getSavedTarget(context)) }
    var autoUpdateDaily by remember { mutableStateOf(RoadmapWallpaperManager.isAutoUpdateEnabled(context)) }
    var isApplying by remember { mutableStateOf(false) }

    val dynamicAccent = colors.accent

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screenHorizontal)
                .padding(bottom = Spacing.xl),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(dynamicAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Wallpaper,
                            contentDescription = null,
                            tint = dynamicAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "Set Roadmap Wallpaper",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Minimalist 365 dots on your phone screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Target Selection Cards (Lock, Home, Both)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                WallpaperTargetOption(
                    title = "Lock Screen",
                    subtitle = "See your year progress every time you wake your phone",
                    icon = Icons.Outlined.Lock,
                    isSelected = selectedTarget == WallpaperTarget.LOCK_SCREEN,
                    onClick = { selectedTarget = WallpaperTarget.LOCK_SCREEN },
                )

                WallpaperTargetOption(
                    title = "Home Screen",
                    subtitle = "Live on your home wallpaper behind your apps",
                    icon = Icons.Outlined.PhoneAndroid,
                    isSelected = selectedTarget == WallpaperTarget.HOME_SCREEN,
                    onClick = { selectedTarget = WallpaperTarget.HOME_SCREEN },
                )

                WallpaperTargetOption(
                    title = "Lock & Home Screen",
                    subtitle = "Recommended · Seamless consistency across both screens",
                    icon = Icons.Outlined.Wallpaper,
                    isSelected = selectedTarget == WallpaperTarget.BOTH,
                    onClick = { selectedTarget = WallpaperTarget.BOTH },
                )
            }

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Daily Auto-Update Toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .border(
                        1.dp,
                        colors.cardBorder,
                        RoundedCornerShape(Radius.lg),
                    ),
                color = colors.surfaceMuted,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Update automatically each day",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = "Fills a new dot automatically every night at midnight",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp,
                            color = colors.textSecondary,
                        )
                    }

                    Spacer(modifier = Modifier.width(Spacing.sm))

                    Switch(
                        checked = autoUpdateDaily,
                        onCheckedChange = { autoUpdateDaily = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = dynamicAccent,
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = colors.textSecondary,
                            uncheckedTrackColor = colors.surfaceRaised,
                            uncheckedBorderColor = colors.cardBorder.copy(alpha = 0.5f),
                        ),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xl))

            // Primary Set Wallpaper Button
            Button(
                onClick = {
                    if (isApplying) return@Button
                    isApplying = true
                    coroutineScope.launch {
                        val success = RoadmapWallpaperManager.applyWallpaper(
                            context = context,
                            target = selectedTarget,
                            autoUpdateDaily = autoUpdateDaily,
                            accentColorInt = dynamicAccent.toArgb(),
                            isDarkTheme = colors.isDark,
                        )
                        isApplying = false
                        if (success) {
                            val msg = if (autoUpdateDaily) {
                                "365 Roadmap set as ${selectedTarget.label} · Updates daily at midnight"
                            } else {
                                "365 Roadmap set as ${selectedTarget.label}"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Could not set wallpaper. Please check permissions.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isApplying,
                colors = ButtonDefaults.buttonColors(
                    containerColor = dynamicAccent,
                    contentColor = if (colors.isDark) Color.Black else Color.White,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (isApplying) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    Text(
                        text = "Setting Wallpaper...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.size(Spacing.xs))
                    Text(
                        text = "Set as ${selectedTarget.label}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperTargetOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = PromiseThemeColors.current
    val dynamicAccent = colors.accent

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) dynamicAccent else colors.cardBorder,
        label = "borderColor",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) dynamicAccent.copy(alpha = 0.08f) else colors.surfaceMuted,
        label = "bgColor",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .border(if (isSelected) 1.8.dp else 1.dp, borderColor, RoundedCornerShape(Radius.lg))
            .clickable(onClick = onClick),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) dynamicAccent.copy(alpha = 0.2f) else colors.surfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isSelected) dynamicAccent else colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) colors.textPrimary else colors.textPrimary.copy(alpha = 0.85f),
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .border(
                        if (isSelected) 6.dp else 1.5.dp,
                        if (isSelected) dynamicAccent else colors.textSecondary.copy(alpha = 0.4f),
                        CircleShape,
                    )
                    .background(Color.Transparent),
            )
        }
    }
}
