package app.promise.android.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
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
import android.appwidget.AppWidgetManager
import androidx.glance.appwidget.updateAll
import app.promise.android.MainActivity
import app.promise.android.ui.matrix.YearProgressCalculator
import app.promise.android.ui.matrix.YearProgressInfo
import app.promise.android.ui.roadmap.RoadmapWallpaperManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RoadmapGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RoadmapGlanceWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Ensure daily midnight worker is active
        RoadmapWallpaperManager.scheduleDailyUpdate(context)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                RoadmapGlanceWidget().updateAll(context)
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
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}

class RoadmapGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            SMALL_SQUARE,
            HORIZONTAL_RECTANGLE,
            EXPANDED_RECTANGLE,
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
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

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(theme.background)
                        .cornerRadius(20.dp)
                        .clickable(actionStartActivity(roadmapIntent))
                        .padding(12.dp),
                ) {
                    when {
                        size.height >= 200.dp -> {
                            // Expanded Mode (4x3 / 4x4)
                            ExpandedRoadmapWidgetContent(info = info, theme = theme, isDark = isDark, accentInt = activeAccentInt)
                        }
                        size.width >= 200.dp -> {
                            // Horizontal Rectangle Mode (4x2 / 3x2)
                            HorizontalRoadmapWidgetContent(info = info, theme = theme, isDark = isDark, accentInt = activeAccentInt)
                        }
                        else -> {
                            // Compact Square Mode (2x2)
                            CompactRoadmapWidgetContent(info = info, theme = theme, isDark = isDark, accentInt = activeAccentInt)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val SMALL_SQUARE = DpSize(120.dp, 120.dp)
        private val HORIZONTAL_RECTANGLE = DpSize(220.dp, 120.dp)
        private val EXPANDED_RECTANGLE = DpSize(220.dp, 220.dp)

        fun createDotsBitmap(
            widthPx: Int,
            heightPx: Int,
            info: YearProgressInfo,
            isDark: Boolean,
            accentColor: Int = android.graphics.Color.parseColor("#6366F1"),
        ): Bitmap {
            val bitmap = Bitmap.createBitmap(maxOf(widthPx, 10), maxOf(heightPx, 10), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val cols = 14
            val rows = (info.totalDays + cols - 1) / cols

            val cellSide = minOf(widthPx.toFloat() / cols, heightPx.toFloat() / rows)
            val gridW = cellSide * cols
            val gridH = cellSide * rows

            val startX = (widthPx - gridW) / 2f
            val startY = (heightPx - gridH) / 2f
            val dotRadius = cellSide * 0.33f

            val pastDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isDark) android.graphics.Color.parseColor("#F8FAFC") else android.graphics.Color.parseColor("#0F172A")
                style = Paint.Style.FILL
            }

            val todayCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.FILL
            }

            val todayRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accentColor
                style = Paint.Style.STROKE
                strokeWidth = maxOf(cellSide * 0.12f, 2.2f)
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
                strokeWidth = maxOf(cellSide * 0.09f, 1.8f)
            }

            for (day in 1..info.totalDays) {
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
    }
}

@androidx.compose.runtime.Composable
private fun CompactRoadmapWidgetContent(
    info: YearProgressInfo,
    theme: RoadmapThemeTokens,
    isDark: Boolean,
    accentInt: Int,
) {
    val dotsBitmap = RoadmapGlanceWidget.createDotsBitmap(
        widthPx = 280,
        heightPx = 180,
        info = info,
        isDark = isDark,
        accentColor = accentInt,
    )

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header Row: Year & Percent
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
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
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${info.percentElapsed}%",
                style = TextStyle(
                    color = theme.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Dots Chart
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

        Spacer(modifier = GlanceModifier.height(2.dp))

        // Subtitle Footer
        Text(
            text = "Day ${info.currentDayOfYear} / ${info.totalDays}",
            style = TextStyle(
                color = theme.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@androidx.compose.runtime.Composable
private fun HorizontalRoadmapWidgetContent(
    info: YearProgressInfo,
    theme: RoadmapThemeTokens,
    isDark: Boolean,
    accentInt: Int,
) {
    val dotsBitmap = RoadmapGlanceWidget.createDotsBitmap(
        widthPx = 280,
        heightPx = 220,
        info = info,
        isDark = isDark,
        accentColor = accentInt,
    )

    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left Column: Stats
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
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

            Spacer(modifier = GlanceModifier.height(2.dp))

            Text(
                text = "${info.percentElapsed}%",
                style = TextStyle(
                    color = theme.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Spacer(modifier = GlanceModifier.height(2.dp))

            Text(
                text = "Day ${info.currentDayOfYear} of ${info.totalDays}",
                style = TextStyle(
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Text(
                text = "${info.daysRemaining} days left",
                style = TextStyle(
                    color = theme.textSecondary,
                    fontSize = 10.sp,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Right Column: 365-Dot Matrix Canvas
        Box(
            modifier = GlanceModifier.width(130.dp).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(dotsBitmap),
                contentDescription = "365-day Roadmap Dots",
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun ExpandedRoadmapWidgetContent(
    info: YearProgressInfo,
    theme: RoadmapThemeTokens,
    isDark: Boolean,
    accentInt: Int,
) {
    val dotsBitmap = RoadmapGlanceWidget.createDotsBitmap(
        widthPx = 360,
        heightPx = 320,
        info = info,
        isDark = isDark,
        accentColor = accentInt,
    )

    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "${info.year} LIFE ROADMAP",
                    style = TextStyle(
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Text(
                    text = "Day ${info.currentDayOfYear} of ${info.totalDays} · ${info.daysRemaining}d left",
                    style = TextStyle(
                        color = theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = "${info.percentElapsed}%",
                style = TextStyle(
                    color = theme.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // 365 Dots Matrix Canvas
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

        Spacer(modifier = GlanceModifier.height(4.dp))

        // Footer brandmark
        Text(
            text = "P R O M I S E",
            style = TextStyle(
                color = theme.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
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
            background = ColorProvider(Color(0xFFFFFFFF)),
            surfaceMuted = ColorProvider(Color(0xFFF1F5F9)),
            accent = ColorProvider(Color(0xFF4F46E5)),
            textPrimary = ColorProvider(Color(0xFF0F172A)),
            textSecondary = ColorProvider(Color(0xFF64748B)),
        )

        val Dark = RoadmapThemeTokens(
            background = ColorProvider(Color(0xFF090B0E)),
            surfaceMuted = ColorProvider(Color(0xFF131720)),
            accent = ColorProvider(Color(0xFF818CF8)),
            textPrimary = ColorProvider(Color(0xFFF8FAFC)),
            textSecondary = ColorProvider(Color(0xFF94A3B8)),
        )
    }
}
