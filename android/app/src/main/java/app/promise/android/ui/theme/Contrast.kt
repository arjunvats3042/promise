package app.promise.android.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

object Contrast {
    fun ratio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun meetsAa(foreground: Color, background: Color, largeText: Boolean = false): Boolean {
        val minimum = if (largeText) 3.0 else 4.5
        return ratio(foreground, background) >= minimum
    }

    private fun relativeLuminance(color: Color): Double {
        // Compose Color.luminance() already follows WCAG relative luminance.
        return color.luminance().toDouble()
    }
}
