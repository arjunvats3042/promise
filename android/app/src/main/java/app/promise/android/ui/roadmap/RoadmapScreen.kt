package app.promise.android.ui.roadmap

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.promise.android.ui.matrix.YearDotMatrix
import app.promise.android.ui.matrix.YearProgressCalculator
import app.promise.android.ui.theme.PromiseDarkColor
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.Spacing
import java.time.ZoneId

@Composable
fun RoadmapScreen(
    timeZoneId: String = ZoneId.systemDefault().id,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val colors = PromiseThemeColors.current
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        RoadmapWallpaperManager.ensureWallpaperUpToDate(context)
    }

    val progressInfo = remember(timeZoneId) { YearProgressCalculator.calculate(timeZoneId) }
    var showWallpaperSheet by remember { mutableStateOf(false) }

    // Dynamic Theme matching app mode (Light / Dark)
    val screenBg = MaterialTheme.colorScheme.background
    val textPrimaryColor = colors.textPrimary
    val textSecondaryColor = colors.textSecondary
    val pastDotColor = if (colors.isDark) Color(0xFFF1F5F9) else Color(0xFF191C1E)
    val futureDotColor = if (colors.isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
    val dynamicAccent = colors.accent
    val dynamicGlow = colors.glowAccent

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.lg)
            .padding(top = Spacing.md, bottom = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(modifier = Modifier.height(Spacing.xs))

        // 1. Top Header Label
        Text(
            text = "${progressInfo.year} LIFE ROADMAP",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = textSecondaryColor,
            letterSpacing = 2.4.sp,
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        // 2. Bold Central Metric
        Text(
            text = "${progressInfo.percentElapsed}%",
            fontSize = 56.sp,
            fontWeight = FontWeight.ExtraBold,
            color = textPrimaryColor,
            letterSpacing = (-1).sp,
        )

        Spacer(modifier = Modifier.height(2.dp))

        // 3. Subtitle (Theme Dynamic Accent)
        Text(
            text = "Day ${progressInfo.currentDayOfYear} of ${progressInfo.totalDays} · ${progressInfo.daysRemaining} days left",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = dynamicAccent,
            letterSpacing = 0.4.sp,
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 4. The 365-Dot Constellation Canvas (Refined 14-Col Grid)
        YearDotMatrix(
            currentDayOfYear = progressInfo.currentDayOfYear,
            totalDays = progressInfo.totalDays,
            accentColor = dynamicAccent,
            pastColor = pastDotColor,
            futureColor = futureDotColor,
            todayGlowColor = dynamicGlow,
            modifier = Modifier
                .width(236.dp)
                .aspectRatio(14f / 27f),
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Philosophical Italic Quote
        Text(
            text = "“Every day is a dot. Today is yours to fill.”",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = textSecondaryColor,
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 6. Brand Signature (Theme Dynamic Accent)
        Text(
            text = "P R O M I S E",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = dynamicAccent,
            letterSpacing = 4.sp,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.height(22.dp))

        // 7. Single Action Button: "Set as Wallpaper"
        Button(
            onClick = {
                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                showWallpaperSheet = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = dynamicAccent,
                contentColor = colors.onPrimaryControl,
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
    }

    if (showWallpaperSheet) {
        SetWallpaperSheet(
            onDismiss = { showWallpaperSheet = false },
        )
    }
}
