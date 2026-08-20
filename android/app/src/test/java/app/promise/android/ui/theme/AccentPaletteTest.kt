package app.promise.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class AccentPaletteTest {
    @Test
    fun paletteHasMultipleQuietOptions() {
        assertTrue(AccentPalette.options.size >= 6)
    }

    @Test
    fun pickIsDeterministicWithSeed() {
        val a = AccentPalette.pick(Random(42))
        val b = AccentPalette.pick(Random(42))
        assertEquals(a.id, b.id)
    }

    @Test
    fun pickExcludingAvoidsPreviousWhenPossible() {
        val first = AccentPalette.options.first()
        repeat(20) {
            val next = AccentPalette.pickExcluding(first.id, Random(it))
            assertTrue(next.id != first.id || AccentPalette.options.size == 1)
        }
    }
}
