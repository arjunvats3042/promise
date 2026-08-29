package app.promise.android.ui.roadmap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.promise.android.ui.matrix.YearDotMatrix
import app.promise.android.ui.matrix.YearProgressCalculator
import app.promise.android.ui.matrix.YearProgressInfo
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Spacing
import java.io.File
import java.io.FileOutputStream

@Composable
fun RoadmapScreen(
    timeZoneId: String = "Asia/Kolkata",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = PromiseThemeColors.current
    val scrollState = rememberScrollState()

    val progressInfo = remember(timeZoneId) { YearProgressCalculator.calculate(timeZoneId) }
    var showWallpaperSheet by remember { mutableStateOf(false) }

    // Dynamic colors strictly aligned to active theme & dynamic session accent
    val cardBg = colors.surfaceRaised
    val cardBorder = colors.cardBorder
    val pastDotColor = colors.textPrimary.copy(alpha = if (colors.isDark) 0.90f else 0.80f)
    val futureDotColor = colors.outlineStrong.copy(alpha = if (colors.isDark) 0.28f else 0.18f)
    val dynamicAccent = colors.accent
    val dynamicGlow = colors.glowAccent

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(top = Spacing.sm, bottom = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // The Centered 9:16 Roadmap Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, cardBorder, RoundedCornerShape(28.dp)),
            color = cardBg,
            shadowElevation = if (colors.isDark) 0.dp else 2.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.cardPadding, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 1. Top Header Label
                Text(
                    text = "${progressInfo.year} LIFE ROADMAP",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary,
                    letterSpacing = 2.2.sp,
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // 2. Bold Central Metric
                Text(
                    text = "${progressInfo.percentElapsed}%",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = colors.textPrimary,
                    letterSpacing = (-1).sp,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 3. Subtitle (Theme Dynamic Accent)
                Text(
                    text = "Day ${progressInfo.currentDayOfYear} of ${progressInfo.totalDays} · ${progressInfo.daysRemaining} days left",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = dynamicAccent,
                    letterSpacing = 0.4.sp,
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 4. The 365-Dot Constellation Canvas (Symmetric 14-Col Grid, Bigger Dots, Equal Margins)
                YearDotMatrix(
                    currentDayOfYear = progressInfo.currentDayOfYear,
                    totalDays = progressInfo.totalDays,
                    accentColor = dynamicAccent,
                    pastColor = pastDotColor,
                    futureColor = futureDotColor,
                    todayGlowColor = dynamicGlow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 5. Philosophical Italic Quote
                Text(
                    text = "“Every day is a dot. Today is yours to fill.”",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 6. Brand Signature (Theme Dynamic Accent)
                Text(
                    text = "P R O M I S E",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = dynamicAccent,
                    letterSpacing = 4.sp,
                    fontSize = 12.sp,
                )
            }
        }

        // 7. Action Buttons (Set as Wallpaper & Share Story)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Button(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    showWallpaperSheet = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = dynamicAccent,
                    contentColor = if (colors.isDark) Color.Black else Color.White,
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wallpaper,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(modifier = Modifier.size(Spacing.xs))
                Text(
                    text = "Set as Wallpaper",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                )
            }

            OutlinedButton(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    shareMinimalistProgress(
                        context = context,
                        info = progressInfo,
                        accentColor = dynamicAccent.toArgb(),
                        isDark = colors.isDark,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textPrimary,
                ),
                border = BorderStroke(1.dp, cardBorder),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = dynamicAccent,
                )
                Spacer(modifier = Modifier.size(Spacing.xs))
                Text(
                    text = "Share Story Card",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
            }
        }
    }

    if (showWallpaperSheet) {
        SetWallpaperSheet(
            onDismiss = { showWallpaperSheet = false },
        )
    }
}

private fun shareMinimalistProgress(
    context: Context,
    info: YearProgressInfo,
    accentColor: Int,
    isDark: Boolean,
) {
    try {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgHex = if (isDark) "#0B0F19" else "#F8FAFC"
        val cardHex = if (isDark) "#131B2E" else "#FFFFFF"
        val cardBorderHex = if (isDark) "#1E293B" else "#E2E8F0"
        val textPrimaryHex = if (isDark) "#FFFFFF" else "#0F172A"
        val textSecondaryHex = if (isDark) "#94A3B8" else "#64748B"
        val pastDotHex = if (isDark) "#E2E8F0" else "#0F172A"
        val futureDotHex = if (isDark) "#334155" else "#CBD5E1"

        // 1. Screen Background
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor(bgHex) }
        canvas.drawRect(0f, 0f, 1080f, 1920f, bgPaint)

        // 2. Card Container
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.parseColor(cardHex) }
        val cardRect = RectF(80f, 180f, 1000f, 1740f)
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(cardBorderHex)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

        // 3. Header Label
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textSecondaryHex)
            textSize = 34f
            letterSpacing = 0.15f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.year} LIFE ROADMAP", 540f, 300f, titlePaint)

        // 4. Main Metric
        val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textPrimaryHex)
            textSize = 96f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.percentElapsed}%", 540f, 410f, statPaint)

        // 5. Subtitle
        val subStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 38f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Day ${info.currentDayOfYear} of ${info.totalDays} · ${info.daysRemaining} days left", 540f, 480f, subStatPaint)

        // 6. 365 Dots (14-column symmetric grid with equal margins)
        val cols = 14
        val rows = (info.totalDays + cols - 1) / cols
        val cardW = 920f
        val cardH = 1560f
        val gridAvailableW = cardW * 0.86f
        val gridAvailableH = cardH * 0.58f

        val cellSide = minOf(gridAvailableW / cols, gridAvailableH / rows)
        val totalGridW = cellSide * cols
        val totalGridH = cellSide * rows

        val startX = 80f + (cardW - totalGridW) / 2f
        val startY = 560f
        val dotRadius = cellSide * 0.36f

        val pastDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(pastDotHex)
        }
        val todayDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
        }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 80
        }
        val futureDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(futureDotHex)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        for (day in 1..info.totalDays) {
            val dayZero = day - 1
            val row = dayZero / cols
            val col = dayZero % cols

            val cx = startX + col * cellSide + cellSide / 2f
            val cy = startY + row * cellSide + cellSide / 2f

            when {
                day < info.currentDayOfYear -> canvas.drawCircle(cx, cy, dotRadius, pastDotPaint)
                day == info.currentDayOfYear -> {
                    canvas.drawCircle(cx, cy, dotRadius * 1.8f, haloPaint)
                    canvas.drawCircle(cx, cy, dotRadius * 1.15f, todayDotPaint)
                }
                else -> canvas.drawCircle(cx, cy, dotRadius * 0.90f, futureDotPaint)
            }
        }

        // 7. Quote & Footer
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textSecondaryHex)
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("“Every day is a dot. Today is yours to fill.”", 540f, 1550f, quotePaint)

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = 30f
            isFakeBoldText = true
            letterSpacing = 0.2f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("P R O M I S E", 540f, 1660f, brandPaint)

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
