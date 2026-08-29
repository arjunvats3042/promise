package app.promise.android.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import app.promise.android.MainActivity
import app.promise.android.R
import app.promise.android.ui.theme.PromiseColor
import app.promise.android.ui.theme.PromiseDarkColor

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

        val voiceIntent = Intent(Intent.ACTION_VIEW, Uri.parse("promise://voice")).apply {
            setPackage(context.packageName)
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
                        .clickable(actionStartActivity(voiceIntent))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (size.width >= 160.dp) {
                        // Horizontal Pill Mode (2x1)
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .size(44.dp)
                                    .background(theme.accent)
                                    .cornerRadius(22.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_mic),
                                    contentDescription = "Speak promise",
                                    modifier = GlanceModifier.size(24.dp),
                                )
                            }

                            Spacer(modifier = GlanceModifier.width(12.dp))

                            Column(
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Speak Promise",
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
                        }
                    } else {
                        // Compact Square Mode (1x1)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = GlanceModifier
                                    .size(48.dp)
                                    .background(theme.accent)
                                    .cornerRadius(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    provider = ImageProvider(R.drawable.ic_mic),
                                    contentDescription = "Quick Voice Capture",
                                    modifier = GlanceModifier.size(26.dp),
                                )
                            }
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "Voice",
                                style = TextStyle(
                                    color = theme.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private val COMPACT_SQUARE = DpSize(60.dp, 60.dp)
        private val HORIZONTAL_PILL = DpSize(160.dp, 60.dp)
    }
}

internal data class VoiceThemeTokens(
    val background: ColorProvider,
    val surfaceMuted: ColorProvider,
    val accent: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
) {
    companion object {
        val Light = VoiceThemeTokens(
            background = ColorProvider(PromiseColor.Background),
            surfaceMuted = ColorProvider(PromiseColor.SurfaceMuted),
            accent = ColorProvider(PromiseColor.Accent),
            textPrimary = ColorProvider(PromiseColor.TextPrimary),
            textSecondary = ColorProvider(PromiseColor.TextSecondary),
        )

        val Dark = VoiceThemeTokens(
            background = ColorProvider(PromiseDarkColor.Background),
            surfaceMuted = ColorProvider(PromiseDarkColor.SurfaceMuted),
            accent = ColorProvider(PromiseDarkColor.Accent),
            textPrimary = ColorProvider(PromiseDarkColor.TextPrimary),
            textSecondary = ColorProvider(PromiseDarkColor.TextSecondary),
        )
    }
}
