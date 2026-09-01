package app.promise.android.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.promise.android.MainActivity
import app.promise.android.ui.matrix.YearProgressCalculator
import app.promise.android.ui.matrix.YearProgressInfo
import app.promise.android.ui.roadmap.RoadmapWallpaperManager
import app.promise.android.ui.theme.PromiseColor
import app.promise.android.ui.theme.PromiseDarkColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoadmapGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RoadmapGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RoadmapWallpaperManager.scheduleDailyUpdate(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RoadmapGlanceWidget().updateAll(context)
                RoadmapWallpaperManager.ensureWallpaperUpToDate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RoadmapGlanceWidget().updateAll(context)
                RoadmapWallpaperManager.ensureWallpaperUpToDate(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action == Intent.ACTION_DATE_CHANGED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            RoadmapWallpaperManager.scheduleDailyUpdate(context)
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RoadmapGlanceWidget().updateAll(context)
                    RoadmapWallpaperManager.ensureWallpaperUpToDate(context)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

class RoadmapGlanceWidget : GlanceAppWidget() {

    // Exact size mode lets Glance pass the live, resized dimensions
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isDark = WidgetThemeHelper.isDarkTheme(context)
        val savedAccentInt = RoadmapWallpaperManager.getSavedAccentColor(context)
        val defaultAccentHex = if (isDark) "#818CF8" else "#4F46E5"
        val activeAccentInt = savedAccentInt ?: android.graphics.Color.parseColor(defaultAccentHex)
        val theme = if (isDark) {
            RoadmapThemeTokens.Dark.copy(accent = ColorProvider(Color(activeAccentInt)))
        } else {
            RoadmapThemeTokens.Light.copy(accent = ColorProvider(Color(activeAccentInt)))
        }
        val info = YearProgressCalculator.calculate()

        val roadmapIntent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("promise://roadmap")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                val density = context.resources.displayMetrics.density

                val totalWPx = maxOf((size.width.value * density).toInt(), 80)
                val totalHPx = maxOf((size.height.value * density).toInt(), 80)

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(theme.background)
                        .cornerRadius(22.dp)
                        .clickable(actionStartActivity(roadmapIntent))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    val isWideBanner = size.width >= 200.dp && size.height < 145.dp

                    if (isWideBanner) {
                        // Wide Split Banner (e.g. 4x1, 4x2)
                        val leftColWidthDp = 105.dp
                        val leftColWidthPx = (105 * density).toInt()
                        val padPx = (24 * density).toInt()
                        val canvasWPx = maxOf(totalWPx - leftColWidthPx - padPx, 60)
                        val canvasHPx = maxOf(totalHPx - (20 * density).toInt(), 60)

                        val dotsBitmap = createDotsBitmap(
                            widthPx = canvasWPx,
                            heightPx = canvasHPx,
                            info = info,
                            isDark = isDark,
                            accentColor = activeAccentInt,
                        )

                        Row(
                            modifier = GlanceModifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = GlanceModifier.width(leftColWidthDp).fillMaxHeight(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${info.year} ROADMAP",
                                    style = TextStyle(
                                        color = theme.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = "${info.percentElapsed}%",
                                    style = TextStyle(
                                        color = theme.textPrimary,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Spacer(modifier = GlanceModifier.height(1.dp))
                                Text(
                                    text = "Day ${info.currentDayOfYear} of ${info.totalDays}",
                                    style = TextStyle(
                                        color = theme.accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Text(
                                    text = "${info.daysRemaining}d left",
                                    style = TextStyle(
                                        color = theme.textSecondary,
                                        fontSize = 9.sp,
                                    ),
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(6.dp))

                            Box(
                                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    provider = ImageProvider(dotsBitmap),
                                    contentDescription = "365-day Roadmap Dots",
                                    modifier = GlanceModifier.fillMaxSize(),
                                )
                            }
                        }
                    } else {
                        // Standard / Expanded / Square / Tall Mode
                        val headerHeightDp = if (size.height >= 180.dp) 38.dp else 26.dp
                        val footerHeightDp = if (size.height >= 220.dp) 18.dp else 0.dp
                        val padHPx = (24 * density).toInt()
                        val headerHPx = (headerHeightDp.value * density).toInt()
                        val footerHPx = (footerHeightDp.value * density).toInt()

                        val canvasWPx = maxOf(totalWPx - padHPx, 60)
                        val canvasHPx = maxOf(totalHPx - headerHPx - footerHPx - (20 * density).toInt(), 60)

                        val dotsBitmap = createDotsBitmap(
                            widthPx = canvasWPx,
                            heightPx = canvasHPx,
                            info = info,
                            isDark = isDark,
                            accentColor = activeAccentInt,
                        )

                        Column(
                            modifier = GlanceModifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Header Row
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = GlanceModifier.defaultWeight()) {
                                    Text(
                                        text = if (size.width >= 170.dp) "${info.year} LIFE ROADMAP" else "${info.year} ROADMAP",
                                        style = TextStyle(
                                            color = theme.textSecondary,
                                            fontSize = if (size.width >= 170.dp) 11.sp else 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                    Text(
                                        text = "Day ${info.currentDayOfYear} of ${info.totalDays} · ${info.daysRemaining}d left",
                                        style = TextStyle(
                                            color = theme.accent,
                                            fontSize = if (size.width >= 170.dp) 11.sp else 9.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    )
                                }

                                Text(
                                    text = "${info.percentElapsed}%",
                                    style = TextStyle(
                                        color = theme.textPrimary,
                                        fontSize = if (size.width >= 170.dp) 20.sp else 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }

                            Spacer(modifier = GlanceModifier.height(4.dp))

                            // Dynamic Dots Matrix
                            Box(
                                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    provider = ImageProvider(dotsBitmap),
                                    contentDescription = "365-day Roadmap Dots",
                                    modifier = GlanceModifier.fillMaxSize(),
                                )
                            }

                            if (size.height >= 220.dp) {
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = "P R O M I S E",
                                    style = TextStyle(
                                        color = theme.textSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {

        /**
         * Dynamically generates a dot matrix canvas that adapts its column and row count
         * to fill the available width and height while maximizing dot size and minimizing dead space.
         */
        fun createDotsBitmap(
            widthPx: Int,
            heightPx: Int,
            info: YearProgressInfo,
            isDark: Boolean,
            accentColor: Int = android.graphics.Color.parseColor("#6366F1"),
        ): Bitmap {
            val w = maxOf(widthPx, 20)
            val h = maxOf(heightPx, 20)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val totalDays = info.totalDays
            val cols = calculateOptimalCols(w, h, totalDays)
            val rows = (totalDays + cols - 1) / cols

            val cellSide = minOf(w.toFloat() / cols, h.toFloat() / rows)
            val gridW = cellSide * cols
            val gridH = cellSide * rows

            val startX = (w - gridW) / 2f
            val startY = (h - gridH) / 2f
            val dotRadius = cellSide * 0.35f

            val pastDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) android.graphics.Color.parseColor("#F1F5F9") else android.graphics.Color.parseColor("#191C1E")
                style = Paint.Style.FILL
            }

            val todayCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.FILL
            }

            val todayRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.STROKE
                strokeWidth = maxOf(cellSide * 0.12f, 2.0f)
            }

            val todayHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                alpha = 85
                style = Paint.Style.FILL
            }

            val futureDotFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) android.graphics.Color.parseColor("#475569") else android.graphics.Color.parseColor("#CBD5E1")
                alpha = 60
                style = Paint.Style.FILL
            }

            val futureDotStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) android.graphics.Color.parseColor("#475569") else android.graphics.Color.parseColor("#94A3B8")
                style = Paint.Style.STROKE
                strokeWidth = maxOf(cellSide * 0.09f, 1.6f)
            }

            for (day in 1..totalDays) {
                val dayZero = day - 1
                val row = dayZero / cols
                val col = dayZero % cols

                val cx = startX + col * cellSide + cellSide / 2f
                val cy = startY + row * cellSide + cellSide / 2f

                when {
                    day < info.currentDayOfYear -> {
                        canvas.drawCircle(cx, cy, dotRadius, pastDotPaint)
                    }
                    day == info.currentDayOfYear -> {
                        canvas.drawCircle(cx, cy, dotRadius * 1.85f, todayHaloPaint)
                        canvas.drawCircle(cx, cy, dotRadius * 1.40f, todayRingPaint)
                        canvas.drawCircle(cx, cy, dotRadius * 1.05f, todayCorePaint)
                    }
                    else -> {
                        canvas.drawCircle(cx, cy, dotRadius * 0.90f, futureDotFillPaint)
                        canvas.drawCircle(cx, cy, dotRadius * 0.90f, futureDotStrokePaint)
                    }
                }
            }

            return bitmap
        }

        /**
         * Calculates the optimal column count between 6 and 40 that maximizes
         * dot size (cellSide) for any arbitrary canvas aspect ratio.
         */
        fun calculateOptimalCols(widthPx: Int, heightPx: Int, totalDays: Int): Int {
            if (widthPx <= 0 || heightPx <= 0) return 14
            var bestCols = 14
            var maxCellSide = 0f

            val minCols = 6
            val maxCols = 40

            for (c in minCols..maxCols) {
                val r = (totalDays + c - 1) / c
                val side = minOf(widthPx.toFloat() / c, heightPx.toFloat() / r)
                if (side > maxCellSide) {
                    maxCellSide = side
                    bestCols = c
                }
            }
            return bestCols
        }
    }
}

internal data class RoadmapThemeTokens(
    val background: ColorProvider,
    val surfaceMuted: ColorProvider,
    val accent: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
) {
    companion object {
        val Light = RoadmapThemeTokens(
            background = ColorProvider(PromiseColor.Background),
            surfaceMuted = ColorProvider(PromiseColor.SurfaceMuted),
            accent = ColorProvider(Color(0xFF4F46E5)),
            textPrimary = ColorProvider(PromiseColor.TextPrimary),
            textSecondary = ColorProvider(PromiseColor.TextSecondary),
        )

        val Dark = RoadmapThemeTokens(
            background = ColorProvider(PromiseDarkColor.Background),
            surfaceMuted = ColorProvider(PromiseDarkColor.SurfaceMuted),
            accent = ColorProvider(Color(0xFF818CF8)),
            textPrimary = ColorProvider(PromiseDarkColor.TextPrimary),
            textSecondary = ColorProvider(PromiseDarkColor.TextSecondary),
        )
    }
}
