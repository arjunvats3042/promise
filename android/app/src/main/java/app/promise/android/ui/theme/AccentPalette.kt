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
    /** Curated high-contrast accent palette for Promise sessions. */
    val options: List<SessionAccent> = listOf(
        SessionAccent("blue", Color(0xFF2563EB), Color(0xFF60A5FA)),
        SessionAccent("indigo", Color(0xFF4F46E5), Color(0xFF818CF8)),
        SessionAccent("purple", Color(0xFF7C3AED), Color(0xFFA78BFA)),
        SessionAccent("teal", Color(0xFF0D9488), Color(0xFF2DD4BF)),
        SessionAccent("green", Color(0xFF16A34A), Color(0xFF4ADE80)),
        SessionAccent("amber", Color(0xFFD97706), Color(0xFFFBBF24)),
        SessionAccent("coral", Color(0xFFE11D48), Color(0xFFFB7185)),
    )

    fun pick(random: Random = Random.Default): SessionAccent {
        return options[random.nextInt(options.size)]
    }

    fun pickExcluding(previousId: String?, random: Random = Random.Default): SessionAccent {
        val pool = options.filter { it.id != previousId }.ifEmpty { options }
        return pool[random.nextInt(pool.size)]
    }
}
