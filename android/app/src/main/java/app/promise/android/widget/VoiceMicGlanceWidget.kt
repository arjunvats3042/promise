package app.promise.android.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.promise.android.R

class VoiceMicGlanceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceMicGlanceWidget()
}

class VoiceMicGlanceWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            COMPACT_SQUARE,
            HORIZONTAL_PILL,
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val isDark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val theme = if (isDark) VoiceThemeTokens.Dark else VoiceThemeTokens.Light

        val voiceIntent = Intent(context, app.promise.android.ui.ai.VoiceQuickCaptureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        provideContent {
            GlanceTheme {
                val size = LocalSize.current

                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(theme.background)
                        .cornerRadius(28.dp)
                        .clickable(actionStartActivity(voiceIntent))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (size.width >= 150.dp) {
                        // Horizontal Pill Mode (2x1)
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Glowing Outer Aura Container
                            Box(
                                modifier = GlanceModifier
                                    .size(46.dp)
                                    .background(theme.aura)
                                    .cornerRadius(23.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxSize()
                                        .background(theme.accent)
                                        .cornerRadius(20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_mic),
                                        contentDescription = "Speak promise",
                                        modifier = GlanceModifier.size(24.dp),
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.width(12.dp))

                            Column(
                                modifier = GlanceModifier.defaultWeight(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Voice Promise",
                                    style = TextStyle(
                                        color = theme.textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = "Tap to speak intentions",
                                    style = TextStyle(
                                        color = theme.textSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(6.dp))

                            Box(
                                modifier = GlanceModifier
                                    .size(28.dp)
                                    .background(theme.surfaceMuted)
                                    .cornerRadius(14.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_sparkle),
                                    contentDescription = "AI Powered",
                                    modifier = GlanceModifier.size(16.dp),
                                )
                            }
                        }
                    } else {
                        // Compact Square Mode (1x1) — Floating Tactical Glass Button
                        Column(
                            modifier = GlanceModifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Floating Glowing Mic Disc
                            Box(
                                modifier = GlanceModifier
                                    .size(54.dp)
                                    .background(theme.aura)
                                    .cornerRadius(27.dp)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = GlanceModifier
                                        .fillMaxSize()
                                        .background(theme.accent)
                                        .cornerRadius(23.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Image(
                                        provider = ImageProvider(R.drawable.ic_mic),
                                        contentDescription = "Quick Voice Capture",
                                        modifier = GlanceModifier.size(28.dp),
                                    )
                                }
                            }

                            Spacer(modifier = GlanceModifier.height(4.dp))

                            Text(
                                text = "Speak",
                                style = TextStyle(
                                    color = theme.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val COMPACT_SQUARE = DpSize(50.dp, 50.dp)
        private val HORIZONTAL_PILL = DpSize(150.dp, 50.dp)
    }
}

internal data class VoiceThemeTokens(
    val background: ColorProvider,
    val surfaceMuted: ColorProvider,
    val aura: ColorProvider,
    val accent: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
) {
    companion object {
        val Light = VoiceThemeTokens(
            background = ColorProvider(Color(0xFFFFFFFF)),
            surfaceMuted = ColorProvider(Color(0xFFF1F5F9)),
            aura = ColorProvider(Color(0xFFDCFCE7)),
            accent = ColorProvider(Color(0xFF10B981)),
            textPrimary = ColorProvider(Color(0xFF0F172A)),
            textSecondary = ColorProvider(Color(0xFF64748B)),
        )

        val Dark = VoiceThemeTokens(
            background = ColorProvider(Color(0xFF131720)),
            surfaceMuted = ColorProvider(Color(0xFF1E2532)),
            aura = ColorProvider(Color(0xFF064E3B)),
            accent = ColorProvider(Color(0xFF10B981)),
            textPrimary = ColorProvider(Color(0xFFF8FAFC)),
            textSecondary = ColorProvider(Color(0xFF94A3B8)),
        )
    }
}
