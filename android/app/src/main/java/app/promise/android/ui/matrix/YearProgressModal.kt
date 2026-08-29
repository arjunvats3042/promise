package app.promise.android.ui.matrix

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearProgressModal(
    onDismiss: () -> Unit,
    timeZoneId: String = "Asia/Kolkata",
) {
    val context = LocalContext.current
    val colors = PromiseThemeColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val progressInfo = remember(timeZoneId) { YearProgressCalculator.calculate(timeZoneId) }
    var selectedDay by remember { mutableIntStateOf(progressInfo.currentDayOfYear) }

    val selectedDate = remember(selectedDay, progressInfo.year) {
        LocalDate.ofYearDay(progressInfo.year, selectedDay)
    }
    val selectedDateFormatted = remember(selectedDate) {
        selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.getDefault()))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceRaised,
        contentColor = colors.textPrimary,
        shape = RoundedCornerShape(topStart = Radius.xl, topEnd = Radius.xl),
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
                Column {
                    Text(
                        text = "${progressInfo.year} LIFE ROADMAP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textSecondary,
                        letterSpacing = 1.5.sp,
                    )
                    Text(
                        text = "Day ${progressInfo.currentDayOfYear} of ${progressInfo.totalDays}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Main Stat Overview Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(Radius.lg)),
                color = colors.surfaceMuted,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.cardPadding),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = "${progressInfo.percentElapsed}% ELAPSED",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.accent,
                            )
                            Text(
                                text = "${progressInfo.daysRemaining} days left to make this year count",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.lg))

                    // Full interactive 365-dot canvas
                    YearDotMatrix(
                        currentDayOfYear = progressInfo.currentDayOfYear,
                        totalDays = progressInfo.totalDays,
                        accentColor = colors.accent,
                        pastColor = colors.textPrimary.copy(alpha = 0.85f),
                        futureColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        todayGlowColor = colors.accent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        onDaySelected = { selectedDay = it },
                    )

                    Spacer(modifier = Modifier.height(Spacing.sm))

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendItem(color = colors.textPrimary.copy(alpha = 0.85f), label = "Past")
                        Spacer(modifier = Modifier.size(Spacing.md))
                        LegendItem(color = colors.accent, label = "Today", isPulse = true)
                        Spacer(modifier = Modifier.size(Spacing.md))
                        LegendItem(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), label = "Future", isHollow = true)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Selected Day Inspection Pill
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.md))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(Radius.md)),
                color = colors.surfaceMuted.copy(alpha = 0.6f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "DAY $selectedDay",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedDay == progressInfo.currentDayOfYear) colors.accent else colors.textSecondary,
                        )
                        Text(
                            text = selectedDateFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary,
                        )
                    }

                    val statusLabel = when {
                        selectedDay == progressInfo.currentDayOfYear -> "TODAY"
                        selectedDay < progressInfo.currentDayOfYear -> "PAST"
                        else -> "${selectedDay - progressInfo.currentDayOfYear}d AWAY"
                    }

                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedDay == progressInfo.currentDayOfYear) colors.accent else colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // Minimalist Quote
            Text(
                text = "“Every single day is a dot on your timeline.\nYou cannot change the past dots, but today is yours to fill.”",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            // Share to Instagram / Social Button
            Button(
                onClick = {
                    shareMinimalistProgress(context, progressInfo)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(Radius.md),
            ) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(Spacing.xs))
                Text(
                    text = "Share 365 Roadmap Card",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun LegendItem(
    color: Color,
    label: String,
    isPulse: Boolean = false,
    isHollow: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .then(
                    if (isHollow) {
                        Modifier.border(1.dp, color, CircleShape)
                    } else {
                        Modifier.background(color)
                    }
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

private fun shareMinimalistProgress(context: Context, info: YearProgressInfo) {
    try {
        // Generate high-resolution 1080x1920 minimalist story card
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: Deep Obsidian Dark
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#0B0F19") }
        canvas.drawRect(0f, 0f, 1080f, 1920f, bgPaint)

        // Card Container
        val cardPaint = Paint().apply { color = android.graphics.Color.parseColor("#131B2E") }
        val cardRect = RectF(80f, 260f, 1000f, 1660f)
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

        // Text Paint
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 34f
            letterSpacing = 0.15f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.year} LIFE ROADMAP", 540f, 380f, titlePaint)

        val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FFFFFF")
            textSize = 96f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.percentElapsed}%", 540f, 500f, statPaint)

        val subStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6366F1")
            textSize = 40f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Day ${info.currentDayOfYear} of ${info.totalDays} · ${info.daysRemaining} days left", 540f, 570f, subStatPaint)

        // Render 365 dots
        val rows = 7
        val cols = (info.totalDays + rows - 1) / rows
        val startX = 130f
        val startY = 660f
        val gridWidth = 820f
        val gridHeight = 650f
        val cellW = gridWidth / cols
        val cellH = gridHeight / rows
        val dotRadius = minOf(cellW, cellH) * 0.32f

        val pastDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#E2E8F0")
        }
        val todayDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6366F1")
        }
        val futureDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#334155")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        for (day in 1..info.totalDays) {
            val dayZero = day - 1
            val col = dayZero / rows
            val row = dayZero % rows

            val cx = startX + col * cellW + cellW / 2f
            val cy = startY + row * cellH + cellH / 2f

            when {
                day < info.currentDayOfYear -> canvas.drawCircle(cx, cy, dotRadius, pastDotPaint)
                day == info.currentDayOfYear -> {
                    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.parseColor("#4338CA")
                    }
                    canvas.drawCircle(cx, cy, dotRadius * 1.8f, haloPaint)
                    canvas.drawCircle(cx, cy, dotRadius * 1.1f, todayDotPaint)
                }
                else -> canvas.drawCircle(cx, cy, dotRadius * 0.85f, futureDotPaint)
            }
        }

        // Quote & Footer
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 34f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("“Every day is a dot. Today is yours to fill.”", 540f, 1420f, quotePaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6366F1")
            textSize = 32f
            isFakeBoldText = true
            letterSpacing = 0.2f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("P R O M I S E", 540f, 1550f, brandPaint)

        // Save to cache and share
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "promise_365_roadmap.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val contentUri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_TEXT, "Day ${info.currentDayOfYear}/365 · ${info.percentElapsed}% of ${info.year} elapsed. #Promise365")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share 365 Roadmap"))
    } catch (_: Exception) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Day ${info.currentDayOfYear}/365 · ${info.percentElapsed}% of ${info.year} completed. #Promise365")
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share 365 Roadmap"))
    }
}
