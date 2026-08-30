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
        val bgHex = if (isDarkTheme) "#090B0E" else "#F8F7F4"
        val textPrimaryHex = if (isDarkTheme) "#F8FAFC" else "#14171A"
        val textSecondaryHex = if (isDarkTheme) "#94A3B8" else "#5A626C"
        val pastDotHex = if (isDarkTheme) "#F1F5F9" else "#191C1E"
        val futureDotHex = if (isDarkTheme) "#475569" else "#CBD5E1"

        // 1. Full Screen Pure Minimalist Canvas
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor(bgHex) }
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgPaint)

        val centerX = screenWidth / 2f

        // 2. Geometry for the 365-Dot Constellation (Centered, Minimal, Balanced)
        val cols = 14
        val rows = (info.totalDays + cols - 1) / cols

        // Grid spans ~52% of width (delicate, refined, uncluttered)
        val targetGridW = screenWidth * 0.52f
        val cellSide = targetGridW / cols
        val totalGridW = cellSide * cols
        val totalGridH = cellSide * rows

        // Vertically centered in the golden viewport zone (clearing lock screen clock above and dock below)
        val gridStartY = (screenHeight - totalGridH) * 0.48f
        val gridStartX = (screenWidth - totalGridW) / 2f
        val dotRadius = cellSide * 0.32f

        // 3. Minimal Header (Quiet, elegant typography)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textSecondaryHex)
            textSize = screenWidth * 0.026f
            letterSpacing = 0.22f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        val headerY = gridStartY - (screenHeight * 0.048f)
        canvas.drawText("${info.year} LIFE ROADMAP", centerX, headerY, titlePaint)

        // 4. Compact Metric Subtitle
        val subStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            textSize = screenWidth * 0.030f
            isFakeBoldText = true
            letterSpacing = 0.06f
            textAlign = Paint.Align.CENTER
        }
        val subtitleY = gridStartY - (screenHeight * 0.020f)
        canvas.drawText("${info.percentElapsed}%  ·  DAY ${info.currentDayOfYear} OF ${info.totalDays}", centerX, subtitleY, subStatPaint)

        // 5. Dots Paint Styles
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
            alpha = 85
            style = Paint.Style.FILL
        }
        val futureDotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(futureDotHex)
            alpha = 50
            style = Paint.Style.FILL
        }
        val futureDotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(futureDotHex)
            style = Paint.Style.STROKE
            strokeWidth = maxOf(cellSide * 0.09f, 2.5f)
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
                    canvas.drawCircle(cx, cy, dotRadius * 1.6f, haloPaint)
                    canvas.drawCircle(cx, cy, dotRadius * 1.1f, todayDotPaint)
                }
                else -> {
                    canvas.drawCircle(cx, cy, dotRadius * 0.90f, futureDotFillPaint)
                    canvas.drawCircle(cx, cy, dotRadius * 0.90f, futureDotStrokePaint)
                }
            }
        }

        // 6. Minimalist Brand Whisper
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor(textSecondaryHex)
            alpha = 150
            textSize = screenWidth * 0.024f
            isFakeBoldText = true
            letterSpacing = 0.35f
            textAlign = Paint.Align.CENTER
        }
        val brandY = gridStartY + totalGridH + (screenHeight * 0.038f)
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
