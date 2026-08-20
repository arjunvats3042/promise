package app.promise.android.ui.theme

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One accent for the lifetime of the process. New color each cold start.
 */
@Singleton
class AccentSession @Inject constructor() {
    private val _accent = MutableStateFlow(AccentPalette.pick())
    val accent: StateFlow<SessionAccent> = _accent.asStateFlow()

    fun current(): SessionAccent = _accent.value

    /** Test / rare reseed helper. */
    fun reseed(random: kotlin.random.Random = kotlin.random.Random.Default) {
        _accent.value = AccentPalette.pickExcluding(_accent.value.id, random)
    }
}
