package app.promise.android.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class PromiseThemeMode {
    Light,
    Dark,
}

data class PromiseExtendedColors(
    val accent: Color,
    val ink: Color,
    val success: Color,
    val warning: Color,
    val error: Color = PromiseColor.Error,
    val textPrimary: Color,
    val textSecondary: Color,
    val surfaceMuted: Color,
    val surfaceRaised: Color,
    val outlineStrong: Color,
    val primaryControl: Color,
    val onPrimaryControl: Color,
    val cardBackground: Color = surfaceRaised,
    val cardBorder: Color = outlineStrong,
    val chipBackground: Color = surfaceMuted,
    val glowAccent: Color = accent.copy(alpha = 0.14f),
    val isDark: Boolean = false,
)

val LocalPromiseColors = staticCompositionLocalOf {
    PromiseExtendedColors(
        accent = PromiseColor.Accent,
        ink = PromiseColor.Primary,
        success = PromiseColor.Success,
        warning = PromiseColor.Warning,
        error = PromiseColor.Error,
        textPrimary = PromiseColor.TextPrimary,
        textSecondary = PromiseColor.TextSecondary,
        surfaceMuted = PromiseColor.SurfaceMuted,
        surfaceRaised = PromiseColor.SurfaceRaised,
        outlineStrong = PromiseColor.OutlineStrong,
        primaryControl = PromiseColor.PrimaryControl,
        onPrimaryControl = PromiseColor.OnPrimaryControl,
        cardBackground = PromiseColor.SurfaceRaised,
        cardBorder = PromiseColor.Outline,
        chipBackground = PromiseColor.SurfaceMuted,
        glowAccent = PromiseColor.Accent.copy(alpha = 0.12f),
        isDark = false,
    )
}

object PromiseThemeColors {
    val current: PromiseExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalPromiseColors.current
}

@Composable
fun PromiseTheme(
    mode: PromiseThemeMode = PromiseThemeMode.Light,
    sessionAccent: SessionAccent = AccentPalette.options.first(),
    content: @Composable () -> Unit,
) {
    val accent = sessionAccent.forMode(mode)
    val onAccent = sessionAccent.onAccent(mode)
    val colorScheme: ColorScheme = when (mode) {
        PromiseThemeMode.Light -> lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = PromiseColor.Secondary,
            onSecondary = PromiseColor.OnPrimary,
            background = PromiseColor.Background,
            onBackground = PromiseColor.TextPrimary,
            surface = PromiseColor.Surface,
            onSurface = PromiseColor.TextPrimary,
            surfaceVariant = PromiseColor.SurfaceMuted,
            onSurfaceVariant = PromiseColor.TextSecondary,
            error = PromiseColor.Error,
            onError = PromiseColor.OnPrimary,
            outline = PromiseColor.Outline,
            outlineVariant = PromiseColor.OutlineStrong,
            scrim = PromiseColor.Scrim,
        )
        PromiseThemeMode.Dark -> darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            secondary = PromiseDarkColor.Secondary,
            onSecondary = PromiseDarkColor.OnPrimary,
            background = PromiseDarkColor.Background,
            onBackground = PromiseDarkColor.TextPrimary,
            surface = PromiseDarkColor.Surface,
            onSurface = PromiseDarkColor.TextPrimary,
            surfaceVariant = PromiseDarkColor.SurfaceMuted,
            onSurfaceVariant = PromiseDarkColor.TextSecondary,
            error = PromiseDarkColor.Error,
            onError = PromiseDarkColor.OnAccent,
            outline = PromiseDarkColor.Outline,
            outlineVariant = PromiseDarkColor.OutlineStrong,
            scrim = PromiseDarkColor.Scrim,
        )
    }
    val extended = when (mode) {
        PromiseThemeMode.Light -> PromiseExtendedColors(
            accent = accent,
            ink = PromiseColor.Primary,
            success = accent,
            warning = PromiseColor.Warning,
            error = PromiseColor.Error,
            textPrimary = PromiseColor.TextPrimary,
            textSecondary = PromiseColor.TextSecondary,
            surfaceMuted = PromiseColor.SurfaceMuted,
            surfaceRaised = PromiseColor.SurfaceRaised,
            outlineStrong = PromiseColor.OutlineStrong,
            primaryControl = PromiseColor.PrimaryControl,
            onPrimaryControl = PromiseColor.OnPrimaryControl,
            cardBackground = PromiseColor.Surface,
            cardBorder = PromiseColor.Outline,
            chipBackground = PromiseColor.SurfaceMuted,
            glowAccent = accent.copy(alpha = 0.12f),
            isDark = false,
        )
        PromiseThemeMode.Dark -> PromiseExtendedColors(
            accent = accent,
            ink = PromiseDarkColor.Ink,
            success = accent,
            warning = PromiseDarkColor.Warning,
            error = PromiseDarkColor.Error,
            textPrimary = PromiseDarkColor.TextPrimary,
            textSecondary = PromiseDarkColor.TextSecondary,
            surfaceMuted = PromiseDarkColor.SurfaceMuted,
            surfaceRaised = PromiseDarkColor.SurfaceRaised,
            outlineStrong = PromiseDarkColor.OutlineStrong,
            primaryControl = PromiseDarkColor.PrimaryControl,
            onPrimaryControl = PromiseDarkColor.OnPrimaryControl,
            cardBackground = PromiseDarkColor.Surface,
            cardBorder = PromiseDarkColor.Outline,
            chipBackground = PromiseDarkColor.SurfaceMuted,
            glowAccent = accent.copy(alpha = 0.16f),
            isDark = true,
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            @Suppress("DEPRECATION")
            window.statusBarColor = colorScheme.background.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            val lightIcons = mode == PromiseThemeMode.Light
            controller.isAppearanceLightStatusBars = lightIcons
            controller.isAppearanceLightNavigationBars = lightIcons
        }
    }
    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val clampedDensity = androidx.compose.runtime.remember(currentDensity) {
        androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale.coerceIn(0.85f, 1.25f),
        )
    }

    CompositionLocalProvider(
        LocalPromiseColors provides extended,
        androidx.compose.ui.platform.LocalDensity provides clampedDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PromiseTypography,
            shapes = PromiseShapes,
            content = content,
        )
    }
}
