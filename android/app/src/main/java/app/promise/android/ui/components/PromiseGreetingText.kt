package app.promise.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.promise.android.ui.theme.Motion
import app.promise.android.ui.theme.PromiseThemeColors
import app.promise.android.ui.theme.rememberReduceMotion

@Composable
fun PromiseGreetingText(
    text: String,
    modifier: Modifier = Modifier,
    semanticText: String = text,
    animateIn: Boolean = false,
) {
    val colors = PromiseThemeColors.current
    val reduceMotion = rememberReduceMotion()
    var targetAlpha by remember { mutableFloatStateOf(if (animateIn && !reduceMotion) 0f else 1f) }
    LaunchedEffect(text, animateIn, reduceMotion) {
        targetAlpha = 1f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = Motion.standardTween(
            if (reduceMotion || !animateIn) 0 else Motion.MicroMs,
        ),
        label = "greetingAlpha",
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .alpha(alpha)
            .semantics { contentDescription = semanticText },
    )
}
