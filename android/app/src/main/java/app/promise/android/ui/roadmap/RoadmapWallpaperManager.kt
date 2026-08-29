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
    ): Bitmap {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = maxOf(displayMetrics.widthPixels, 1080)
        val screenHeight = maxOf(displayMetrics.heightPixels, 1920)

        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Deep Obsidian Background
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor("#0B0F19") }
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgPaint)

        // 2. Card Container (centered)
        val cardWidth = minOf(screenWidth * 0.88f, 920f)
        val cardHeight = minOf(screenHeight * 0.72f, 1400f)
        val cardLeft = (screenWidth - cardWidth) / 2f
        val cardTop = (screenHeight - cardHeight) / 2f
        val cardRight = cardLeft + cardWidth
        val cardBottom = cardTop + cardHeight

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#131B2E")
        }
        val cardRect = RectF(cardLeft, cardTop, cardRight, cardBottom)
        canvas.drawRoundRect(cardRect, 48f, 48f, cardPaint)

        // Subtle Card Border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#1E293B")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(cardRect, 48f, 48f, borderPaint)

        val centerX = screenWidth / 2f

        // 3. Header Text
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = cardWidth * 0.042f
            letterSpacing = 0.18f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.year} LIFE ROADMAP", centerX, cardTop + cardHeight * 0.10f, titlePaint)

        // 4. Hero Metric
        val statPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#FFFFFF")
            textSize = cardWidth * 0.135f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${info.percentElapsed}%", centerX, cardTop + cardHeight * 0.21f, statPaint)

        // 5. Subtitle
        val subStatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6366F1")
            textSize = cardWidth * 0.046f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "Day ${info.currentDayOfYear} of ${info.totalDays} · ${info.daysRemaining} days left",
            centerX,
            cardTop + cardHeight * 0.27f,
            subStatPaint,
        )

        // 6. 365 Dots Grid
        val rows = 7
        val cols = (info.totalDays + rows - 1) / rows
        val gridWidth = cardWidth * 0.85f
        val gridHeight = cardHeight * 0.44f
        val gridStartX = cardLeft + (cardWidth - gridWidth) / 2f
        val gridStartY = cardTop + cardHeight * 0.33f

        val cellW = gridWidth / cols
        val cellH = gridHeight / rows
        val dotRadius = minOf(cellW, cellH) * 0.33f

        val pastDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#E2E8F0")
        }
        val todayDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6366F1")
        }
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#4338CA")
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

            val cx = gridStartX + col * cellW + cellW / 2f
            val cy = gridStartY + row * cellH + cellH / 2f

            when {
                day < info.currentDayOfYear -> canvas.drawCircle(cx, cy, dotRadius, pastDotPaint)
                day == info.currentDayOfYear -> {
                    canvas.drawCircle(cx, cy, dotRadius * 1.8f, haloPaint)
                    canvas.drawCircle(cx, cy, dotRadius * 1.1f, todayDotPaint)
                }
                else -> canvas.drawCircle(cx, cy, dotRadius * 0.85f, futureDotPaint)
            }
        }

        // 7. Quote
        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#94A3B8")
            textSize = cardWidth * 0.040f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("“Every day is a dot. Today is yours to fill.”", centerX, cardTop + cardHeight * 0.85f, quotePaint)

        // 8. Brand Mark
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#6366F1")
            textSize = cardWidth * 0.038f
            isFakeBoldText = true
            letterSpacing = 0.22f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("P R O M I S E", centerX, cardTop + cardHeight * 0.94f, brandPaint)

        return bitmap
    }

    suspend fun applyWallpaper(
        context: Context,
        target: WallpaperTarget,
        autoUpdateDaily: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val bitmap = generateWallpaperBitmap(context)
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

            // Persist preference
            getPrefs(context).edit()
                .putBoolean(KEY_AUTO_UPDATE, autoUpdateDaily)
                .putString(KEY_TARGET, target.name)
                .apply()

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
