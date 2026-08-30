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
        val bgHex = if (isDarkTheme) "#090B0E" else "#F8FAFC"
        val textPrimaryHex = if (isDarkTheme) "#F8FAFC" else "#0F172A"
        val textSecondaryHex = if (isDarkTheme) "#94A3B8" else "#64748B"
        val pastDotHex = if (isDarkTheme) "#F1F5F9" else "#0F172A"
        val futureDotHex = if (isDarkTheme) "#475569" else "#CBD5E1"

        // 1. Full Screen Background
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor(bgHex) }
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgPaint)

        val centerX = screenWidth / 2f

        // 2. Header Text (matches RoadmapScreen top header)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textSecondaryHex)
            textSize = screenWidth * 0.034f
            letterSpacing = 0.20f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val headerY = screenHeight * 0.080f
        canvas.drawText("${info.year} LIFE ROADMAP", centerX, headerY, titlePaint)

        // 3. Hero Metric (large bold percentage)
        val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textPrimaryHex)
            textSize = screenWidth * 0.125f
            isFakeBoldText = true
            letterSpacing = -0.02f
            textAlign = Paint.Align.CENTER
        }
        val heroMetricY = screenHeight * 0.145f
        canvas.drawText("${info.percentElapsed}%", centerX, heroMetricY, statPaint)

        // 4. Subtitle (Theme Dynamic Accent)
        val subStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = screenWidth * 0.038f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val subtitleY = screenHeight * 0.185f
        canvas.drawText(
            "Day ${info.currentDayOfYear} of ${info.totalDays} · ${info.daysRemaining} days left",
            centerX,
            subtitleY,
            subStatPaint,
        )

        // 5. 365 Dots Grid (14-column symmetric grid enlarged to screen width)
        val cols = 14
        val rows = (info.totalDays + cols - 1) / cols

        val gridStartY = screenHeight * 0.220f
        val bottomReserved = screenHeight * 0.125f
        val maxGridH = screenHeight - gridStartY - bottomReserved
        val maxGridW = screenWidth * 0.88f

        val cellSide = minOf(maxGridW / cols, maxGridH / rows)
        val totalGridW = cellSide * cols
        val totalGridH = cellSide * rows

        val gridStartX = (screenWidth - totalGridW) / 2f
        val dotRadius = cellSide * 0.36f

        val pastDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(pastDotHex)
            style = Paint.Style.FILL
        }
        val todayDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.FILL
        }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 90
            style = Paint.Style.FILL
        }
        val futureDotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(futureDotHex)
            alpha = 64 // ~0.25f alpha fill disc
            style = Paint.Style.FILL
        }
        val futureDotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(futureDotHex)
            style = Paint.Style.STROKE
            strokeWidth = maxOf(cellSide * 0.08f, 3f)
        }

        for (day in 1..info.totalDays) {
            val dayZero = day - 1
            val row = dayZero / cols
            val col = dayZero % cols

            val cx = gridStartX + col * cellSide + cellSide / 2f
            val cy = gridStartY + row * cellSide + cellSide / 2f

            when {
                day < info.currentDayOfYear -> {
                    canvas.drawCircle(cx, cy, dotRadius, pastDotPaint)
                }
                day == info.currentDayOfYear -> {
                    canvas.drawCircle(cx, cy, dotRadius * 1.65f, haloPaint)
                    canvas.drawCircle(cx, cy, dotRadius * 1.15f, todayDotPaint)
                }
                else -> {
                    canvas.drawCircle(cx, cy, dotRadius * 0.90f, futureDotFillPaint)
                    canvas.drawCircle(cx, cy, dotRadius * 0.90f, futureDotStrokePaint)
                }
            }
        }

        // 6. Quote (Italic philosophical quote)
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textSecondaryHex)
            textSize = screenWidth * 0.033f
            textSkewX = -0.20f
            textAlign = Paint.Align.CENTER
        }
        val quoteY = gridStartY + totalGridH + (screenHeight * 0.038f)
        canvas.drawText("“Every day is a dot. Today is yours to fill.”", centerX, quoteY, quotePaint)

        // 7. Brand Signature (Theme Dynamic Accent)
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = screenWidth * 0.032f
            isFakeBoldText = true
            letterSpacing = 0.30f
            textAlign = Paint.Align.CENTER
        }
        val brandY = quoteY + (screenHeight * 0.030f)
        canvas.drawText("P R O M I S E", centerX, brandY, brandPaint)

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
