package app.promise.android.ui.roadmap

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.promise.android.ui.matrix.YearProgressCalculator
import app.promise.android.ui.matrix.YearProgressInfo
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class WallpaperTarget(val label: String) {
    LOCK_SCREEN("Lock Screen"),
    HOME_SCREEN("Home Screen"),
    BOTH("Lock & Home Screen"),
}

object RoadmapWallpaperManager {

    private const val TAG = "RoadmapWallpaper"
    private const val PREFS_NAME = "promise_wallpaper_prefs"
    private const val KEY_AUTO_UPDATE = "auto_update_enabled"
    private const val KEY_TARGET = "wallpaper_target"
    private const val KEY_ACCENT_COLOR = "wallpaper_accent_color"
    private const val KEY_IS_DARK = "wallpaper_is_dark"
    private const val KEY_LAST_APPLIED_DAY_OF_YEAR = "wallpaper_last_day_of_year"
    private const val KEY_LAST_APPLIED_YEAR = "wallpaper_last_year"
    private const val WORK_NAME = "daily_roadmap_wallpaper_worker"
    private const val WALLPAPER_ALARM_REQUEST_CODE = 998811

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoUpdateEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_UPDATE, false)
    }

    fun disableAutoUpdate(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_UPDATE, false).apply()
        cancelDailyUpdate(context)
    }

    fun getSavedTarget(context: Context): WallpaperTarget {
        val name = getPrefs(context).getString(KEY_TARGET, WallpaperTarget.BOTH.name)
        return runCatching { WallpaperTarget.valueOf(name ?: WallpaperTarget.BOTH.name) }
            .getOrDefault(WallpaperTarget.BOTH)
    }

    fun getSavedAccentColor(context: Context): Int? {
        val prefs = getPrefs(context)
        return if (prefs.contains(KEY_ACCENT_COLOR)) prefs.getInt(KEY_ACCENT_COLOR, 0) else null
    }

    fun isSavedDarkTheme(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_DARK, true)
    }

    fun getScreenDimensions(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
            val bounds = wm.maximumWindowMetrics.bounds
            val w = bounds.width()
            val h = bounds.height()
            if (w > 0 && h > 0) {
                return Pair(maxOf(w, 1080), maxOf(h, 1920))
            }
        }
        val dm = context.resources.displayMetrics
        return Pair(maxOf(dm.widthPixels, 1080), maxOf(dm.heightPixels, 1920))
    }

    fun generateWallpaperBitmap(
        context: Context,
        info: YearProgressInfo = YearProgressCalculator.calculate(),
        accentColorInt: Int? = null,
        isDarkTheme: Boolean = true,
    ): Bitmap {
        val (screenWidth, screenHeight) = getScreenDimensions(context)

        val bitmap = Bitmap.createBitmap(screenWidth, screenHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val accentColor = accentColorInt ?: android.graphics.Color.parseColor("#6366F1")
        val bgHex = if (isDarkTheme) "#090B0E" else "#F8F7F4"
        val textSecondaryHex = if (isDarkTheme) "#94A3B8" else "#5A626C"
        val pastDotHex = if (isDarkTheme) "#F1F5F9" else "#191C1E"
        val futureDotHex = if (isDarkTheme) "#475569" else "#CBD5E1"

        // 1. Full Screen Minimalist Background
        val bgPaint = Paint().apply { color = android.graphics.Color.parseColor(bgHex) }
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), bgPaint)

        val centerX = screenWidth / 2f

        // 2. Geometry for the 365-Dot Constellation (Centered, Minimal, Balanced)
        val cols = 14
        val rows = (info.totalDays + cols - 1) / cols

        val targetGridW = screenWidth * 0.52f
        val cellSide = targetGridW / cols
        val totalGridW = cellSide * cols
        val totalGridH = cellSide * rows

        val gridStartY = (screenHeight - totalGridH) * 0.48f
        val gridStartX = (screenWidth - totalGridW) / 2f
        val dotRadius = cellSide * 0.32f

        // 3. Minimal Header
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

    suspend fun applyWallpaper(
        context: Context,
        target: WallpaperTarget,
        autoUpdateDaily: Boolean,
        accentColorInt: Int? = null,
        isDarkTheme: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)

            // Validate device wallpaper capability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!wallpaperManager.isWallpaperSupported) {
                    Log.w(TAG, "Wallpaper is not supported on this device")
                    return@withContext false
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (!wallpaperManager.isSetWallpaperAllowed) {
                    Log.w(TAG, "Setting wallpaper is not allowed on this device")
                    return@withContext false
                }
            }

            val savedAccent = accentColorInt ?: getSavedAccentColor(context)
            val info = YearProgressCalculator.calculate()
            val bitmap = generateWallpaperBitmap(
                context = context,
                info = info,
                accentColorInt = savedAccent,
                isDarkTheme = isDarkTheme,
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                when (target) {
                    WallpaperTarget.LOCK_SCREEN -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                    }
                    WallpaperTarget.HOME_SCREEN -> {
                        wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                    }
                    WallpaperTarget.BOTH -> {
                        var applied = false
                        try {
                            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                            applied = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed setting system wallpaper individually", e)
                        }
                        try {
                            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
                            applied = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed setting lock wallpaper individually", e)
                        }
                        if (!applied) {
                            // Fallback to combined flags if individual calls failed
                            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
                        }
                    }
                }
            } else {
                wallpaperManager.setBitmap(bitmap)
            }

            val today = LocalDate.now(ZoneId.systemDefault())

            // Persist preferences and track last applied date
            val editor = getPrefs(context).edit()
                .putBoolean(KEY_AUTO_UPDATE, autoUpdateDaily)
                .putString(KEY_TARGET, target.name)
                .putBoolean(KEY_IS_DARK, isDarkTheme)
                .putInt(KEY_LAST_APPLIED_DAY_OF_YEAR, today.dayOfYear)
                .putInt(KEY_LAST_APPLIED_YEAR, today.year)

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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply wallpaper", e)
            false
        }
    }

    /**
     * Checks if auto-update is enabled and the wallpaper is showing an outdated day.
     * If so, immediately triggers a background update to the current day.
     */
    fun ensureWallpaperUpToDate(context: Context) {
        if (!isAutoUpdateEnabled(context)) return
        val today = LocalDate.now(ZoneId.systemDefault())
        val prefs = getPrefs(context)
        val lastDay = prefs.getInt(KEY_LAST_APPLIED_DAY_OF_YEAR, -1)
        val lastYear = prefs.getInt(KEY_LAST_APPLIED_YEAR, -1)

        if (lastDay != today.dayOfYear || lastYear != today.year) {
            Log.i(TAG, "Wallpaper is outdated (last: day $lastDay of $lastYear, today: day ${today.dayOfYear} of ${today.year}). Refreshing...")
            val target = getSavedTarget(context)
            val isDark = isSavedDarkTheme(context)
            val accent = getSavedAccentColor(context)
            CoroutineScope(Dispatchers.IO).launch {
                applyWallpaper(
                    context = context,
                    target = target,
                    autoUpdateDaily = true,
                    accentColorInt = accent,
                    isDarkTheme = isDark,
                )
            }
        }
    }

    fun scheduleMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = LocalDateTime.now(ZoneId.systemDefault())
        val nextMidnight = now.toLocalDate().plusDays(1).atTime(0, 0, 5) // 12:00:05 AM tomorrow
        val triggerAtMillis = nextMidnight.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, WallpaperDateReceiver::class.java).apply {
            action = WallpaperDateReceiver.ACTION_MIDNIGHT_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WALLPAPER_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    fun cancelMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WallpaperDateReceiver::class.java).apply {
            action = WallpaperDateReceiver.ACTION_MIDNIGHT_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            WALLPAPER_ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleDailyUpdate(context: Context) {
        // 1. Precise midnight alarm for instant RTC date crossover
        scheduleMidnightAlarm(context)

        // 2. Resilient WorkManager periodic backup
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
        cancelMidnightAlarm(context)
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
