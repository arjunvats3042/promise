package app.promise.android.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

data class SessionAccent(
    val id: String,
    val light: Color,
    val dark: Color,
    val onLight: Color = Color(0xFFFFFFFF),
    val onDark: Color = Color(0xFF0E120F),
) {
    fun forMode(mode: PromiseThemeMode): Color =
        if (mode == PromiseThemeMode.Dark) dark else light

    fun onAccent(mode: PromiseThemeMode): Color =
        if (mode == PromiseThemeMode.Dark) onDark else onLight
}

object AccentPalette {
    /** Quiet, paper-friendly accents — not neon. */
    val options: List<SessionAccent> = listOf(
        SessionAccent("forest", Color(0xFF2F6A4A), Color(0xFF4A9B6E)),
        SessionAccent("slate", Color(0xFF3D5A80), Color(0xFF6B9AC4)),
        SessionAccent("terracotta", Color(0xFF8B5A3C), Color(0xFFC4896A)),
        SessionAccent("plum", Color(0xFF5C4A6E), Color(0xFFA089B8)),
        SessionAccent("ocean", Color(0xFF2F5F6A), Color(0xFF5FA8B5)),
        SessionAccent("olive", Color(0xFF5A6A3A), Color(0xFFA3B56F)),
        SessionAccent("brick", Color(0xFF7A3E3E), Color(0xFFC47A7A)),
        SessionAccent("ink-blue", Color(0xFF3A4A6A), Color(0xFF8A9BC4)),
    )

    fun pick(random: Random = Random.Default): SessionAccent {
        return options[random.nextInt(options.size)]
    }

    fun pickExcluding(previousId: String?, random: Random = Random.Default): SessionAccent {
        val pool = options.filter { it.id != previousId }.ifEmpty { options }
        return pool[random.nextInt(pool.size)]
    }
}
