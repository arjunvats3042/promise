package app.promise.android.ui.roadmap

import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.promise.android.ui.matrix.YearProgressCalculator
import app.promise.android.ui.matrix.YearProgressInfo
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class WallpaperTarget(val label: String) {
    LOCK_SCREEN("Lock Screen"),
    HOME_SCREEN("Home Screen"),
    BOTH("Lock & Home Screen"),
}

object RoadmapWallpaperManager {

    private const val PREFS_NAME = "promise_wallpaper_prefs"
    private const val KEY_AUTO_UPDATE = "auto_update_enabled"
    private const val KEY_TARGET = "wallpaper_target"
    private const val WORK_NAME = "daily_roadmap_wallpaper_worker"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoUpdateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_UPDATE, true)
    }

    fun getSavedTarget(context: Context): WallpaperTarget {
        val name = getPrefs(context).getString(KEY_TARGET, WallpaperTarget.BOTH.name)
        return runCatching { WallpaperTarget.valueOf(name ?: WallpaperTarget.BOTH.name) }
            .getOrDefault(WallpaperTarget.BOTH)
    }

    fun generateWallpaperBitmap(
        context: Context,
        info: YearProgressInfo = YearProgressCalculator.calculate(),
        accentColorInt: Int? = null,
        isDarkTheme: Boolean = true,
    ): Bitmap {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = maxOf(displayMetrics.widthPixels, 1080)
        val screenHeight = maxOf(displayMetrics.heightPixels, 1920)

        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val accentColor = accentColorInt ?: android.graphics.Color.parseColor("#6366F1")
        val bgHex = if (isDarkTheme) "#0B0F19" else "#F8FAFC"
        val cardHex = if (isDarkTheme) "#131B2E" else "#FFFFFF"
        val cardBorderHex = if (isDarkTheme) "#1E293B" else "#E2E8F0"
        val textPrimaryHex = if (isDarkTheme) "#FFFFFF" else "#0F172A"
        val textSecondaryHex = if (isDarkTheme) "#94A3B8" else "#64748B"
        val pastDotHex = if (isDarkTheme) "#E2E8F0" else "#0F172A"
        val futureDotHex = if (isDarkTheme) "#334155" else "#CBD5E1"

        // 1. Screen Background
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor(bgHex) }
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgPaint)

        // 2. Card Container (centered)
        val cardWidth = minOf(screenWidth * 0.88f, 920f)
        val cardHeight = minOf(screenHeight * 0.76f, 1500f)
        val cardLeft = (screenWidth - cardWidth) / 2f
        val cardTop = (screenHeight - cardHeight) / 2f
        val cardRight = cardLeft + cardWidth
        val cardBottom = cardTop + cardHeight

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(cardHex)
        }
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

        // Subtle Card Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(cardBorderHex)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

        val centerX = screenWidth / 2f

        // 3. Header Text
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textSecondaryHex)
            textSize = cardWidth * 0.040f
            letterSpacing = 0.18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.year} LIFE ROADMAP", centerX, cardTop + cardHeight * 0.08f, titlePaint)

        // 4. Hero Metric
        val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textPrimaryHex)
            textSize = cardWidth * 0.125f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.percentElapsed}%", centerX, cardTop + cardHeight * 0.16f, statPaint)

        // 5. Subtitle
        val subStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = cardWidth * 0.044f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Day ${info.currentDayOfYear} of ${info.totalDays} · ${info.daysRemaining} days left",
            centerX,
            cardTop + cardHeight * 0.22f,
            subStatPaint,
        )

        // 6. 365 Dots Grid (14-column symmetric grid with equal margins)
        val cols = 14
        val rows = (info.totalDays + cols - 1) / cols
        val gridAvailableW = cardWidth * 0.85f
        val gridAvailableH = cardHeight * 0.58f

        val cellSide = minOf(gridAvailableW / cols, gridAvailableH / rows)
        val totalGridW = cellSide * cols
        val totalGridH = cellSide * rows

        val gridStartX = cardLeft + (cardWidth - totalGridW) / 2f
        val gridStartY = cardTop + cardHeight * 0.27f
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

            val cx = gridStartX + col * cellSide + cellSide / 2f
            val cy = gridStartY + row * cellSide + cellSide / 2f

            when {
                day < info.currentDayOfYear -> canvas.drawCircle(cx, cy, dotRadius, pastDotPaint)
                day == info.currentDayOfYear -> {
                    canvas.drawCircle(cx, cy, dotRadius * 1.8f, haloPaint)
                    canvas.drawCircle(cx, cy, dotRadius * 1.15f, todayDotPaint)
                }
                else -> canvas.drawCircle(cx, cy, dotRadius * 0.90f, futureDotPaint)
            }
        }

        // 7. Quote
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textSecondaryHex)
            textSize = cardWidth * 0.038f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("“Every day is a dot. Today is yours to fill.”", centerX, cardTop + cardHeight * 0.90f, quotePaint)

        // 8. Brand Mark
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = cardWidth * 0.036f
            isFakeBoldText = true
            letterSpacing = 0.22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("P R O M I S E", centerX, cardTop + cardHeight * 0.96f, brandPaint)

        return bitmap
    }

    private const val KEY_ACCENT_COLOR = "wallpaper_accent_color"
    private const val KEY_IS_DARK = "wallpaper_is_dark"

    fun getSavedAccentColor(context: Context): Int? {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_ACCENT_COLOR)) prefs.getInt(KEY_ACCENT_COLOR, 0) else null
    }

    fun isSavedDarkTheme(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_DARK, true)
    }

    suspend fun applyWallpaper(
        context: Context,
        target: WallpaperTarget,
        autoUpdateDaily: Boolean,
        accentColorInt: Int? = null,
        isDarkTheme: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val savedAccent = accentColorInt ?: getSavedAccentColor(context)
            val bitmap = generateWallpaperBitmap(
                context = context,
                accentColorInt = savedAccent,
                isDarkTheme = isDarkTheme,
            )
            val wallpaperManager = WallpaperManager.getInstance(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val flag = when (target) {
                    WallpaperTarget.LOCK_SCREEN -> WallpaperManager.FLAG_LOCK
                    WallpaperTarget.HOME_SCREEN -> WallpaperManager.FLAG_SYSTEM
                    WallpaperTarget.BOTH -> WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
                }
                wallpaperManager.setBitmap(bitmap, null, true, flag)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }

            // Persist preferences
            val editor = getPrefs(context).edit()
                .putBoolean(KEY_AUTO_UPDATE, autoUpdateDaily)
                .putString(KEY_TARGET, target.name)
                .putBoolean(KEY_IS_DARK, isDarkTheme)

            if (accentColorInt != null) {
                editor.putInt(KEY_ACCENT_COLOR, accentColorInt)
            }
            editor.apply()

            if (autoUpdateDaily) {
                scheduleDailyUpdate(context)
            } else {
                cancelDailyUpdate(context)
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    fun scheduleDailyUpdate(context: Context) {
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val nextMidnight = now.toLocalDate().plusDays(1).atTime(0, 5) // 12:05 AM
        val initialDelay = Duration.between(now, nextMidnight).toMinutes().coerceAtLeast(1)

        val workRequest = PeriodicWorkRequestBuilder<DailyWallpaperWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MINUTES)
            .setConstraints(Constraints.NONE)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }

    fun cancelDailyUpdate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
