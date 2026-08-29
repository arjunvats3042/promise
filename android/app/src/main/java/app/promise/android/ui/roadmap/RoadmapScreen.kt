package app.promise.android.ui.roadmap

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import app.promise.android.ui.theme.Radius
import app.promise.android.ui.theme.Spacing
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    // Dynamic high-contrast colors matching the viral 9:16 card palette
    val cardBg = if (colors.isDark) Color(0xFF131B2E) else Color.White
    val cardBorder = if (colors.isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val pastDotColor = if (colors.isDark) Color(0xFFE2E8F0) else Color(0xFF0F172A)
    val futureDotColor = if (colors.isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    val accentIndigo = Color(0xFF6366F1)
    val accentHalo = Color(0xFF4338CA)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (colors.isDark) Color(0xFF0B0F19) else Color(0xFFF8FAFC))
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.screenHorizontal)
            .padding(top = Spacing.sm, bottom = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        // The Iconic 9:16 Centered Container Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(1.dp, cardBorder, RoundedCornerShape(28.dp)),
            color = cardBg,
            shadowElevation = if (colors.isDark) 0.dp else 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.cardPadding, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 1. Top Header Label
                Text(
                    text = "${progressInfo.year} LIFE ROADMAP",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 2.2.sp,
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                // 2. Bold Central Metric
                Text(
                    text = "${progressInfo.percentElapsed}%",
                    fontSize = 54.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (colors.isDark) Color.White else Color(0xFF0F172A),
                    letterSpacing = (-1).sp,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // 3. Subtitle
                Text(
                    text = "Day ${progressInfo.currentDayOfYear} of ${progressInfo.totalDays} · ${progressInfo.daysRemaining} days left",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentIndigo,
                    letterSpacing = 0.4.sp,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 4. The 365-Dot Constellation Canvas
                YearDotMatrix(
                    currentDayOfYear = progressInfo.currentDayOfYear,
                    totalDays = progressInfo.totalDays,
                    accentColor = accentIndigo,
                    pastColor = pastDotColor,
                    futureColor = futureDotColor,
                    todayGlowColor = accentHalo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                )

                Spacer(modifier = Modifier.height(28.dp))

                // 6. Philosophical Italic Quote
                Text(
                    text = "“Every day is a dot. Today is yours to fill.”",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF94A3B8),
                    textAlign = TextAlign.Center,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = Spacing.sm),
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 7. Brand Signature
                Text(
                    text = "P R O M I S E",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentIndigo,
                    letterSpacing = 4.sp,
                    fontSize = 12.sp,
                )
            }
        }

        // 8. Action Buttons (Set as Wallpaper & Share Story)
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
                    containerColor = accentIndigo,
                    contentColor = Color.White,
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
                    shareMinimalistProgress(context, progressInfo)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textPrimary,
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (colors.isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1),
                ),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = accentIndigo,
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

private fun shareMinimalistProgress(context: Context, info: YearProgressInfo) {
    try {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background: Deep Obsidian Dark
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#0B0F19") }
        canvas.drawRect(0f, 0f, 1080f, 1920f, bgPaint)

        // Card Container
        val cardPaint = Paint().apply { color = android.graphics.Color.parseColor("#131B2E") }
        val cardRect = RectF(80f, 260f, 1000f, 1660f)
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

        // Header Label
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = 34f
            letterSpacing = 0.15f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.year} LIFE ROADMAP", 540f, 380f, titlePaint)

        // Main Metric
        val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FFFFFF")
            textSize = 96f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.percentElapsed}%", 540f, 500f, statPaint)

        // Subtitle
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
