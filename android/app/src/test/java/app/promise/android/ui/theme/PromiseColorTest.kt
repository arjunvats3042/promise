package app.promise.android.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromiseColorTest {
    @Test
    fun lightTokensMatchLockedPalette() {
        assertEquals(0xFFF6F3EE.toInt(), PromiseColor.Background.toArgbCompat())
        assertEquals(0xFFFFFCF8.toInt(), PromiseColor.Surface.toArgbCompat())
        assertEquals(0xFF24352C.toInt(), PromiseColor.Primary.toArgbCompat())
        assertEquals(0xFF2F6A4A.toInt(), PromiseColor.Accent.toArgbCompat())
        assertEquals(0xFF1C1B19.toInt(), PromiseColor.TextPrimary.toArgbCompat())
        assertEquals(0xFFF0ECE5.toInt(), PromiseColor.SurfaceMuted.toArgbCompat())
        assertEquals(0xFFD2CCC2.toInt(), PromiseColor.OutlineStrong.toArgbCompat())
        assertEquals(0xFF24352C.toInt(), PromiseColor.PrimaryControl.toArgbCompat())
        assertEquals(0xFFFFFCF8.toInt(), PromiseColor.OnPrimaryControl.toArgbCompat())
    }

    @Test
    fun darkTokensMatchApprovedPalette() {
        assertEquals(0xFF141614.toInt(), PromiseDarkColor.Background.toArgbCompat())
        assertEquals(0xFF1E211F.toInt(), PromiseDarkColor.Surface.toArgbCompat())
        assertEquals(0xFF4A9B6E.toInt(), PromiseDarkColor.Accent.toArgbCompat())
        assertEquals(0xFFF2EFE8.toInt(), PromiseDarkColor.TextPrimary.toArgbCompat())
        assertEquals(0xFF242824.toInt(), PromiseDarkColor.SurfaceMuted.toArgbCompat())
        assertEquals(0xFF282C29.toInt(), PromiseDarkColor.SurfaceRaised.toArgbCompat())
        assertEquals(0xFF353A36.toInt(), PromiseDarkColor.Outline.toArgbCompat())
        assertEquals(0xFFDCE3DD.toInt(), PromiseDarkColor.Ink.toArgbCompat())
        assertEquals(0xFFDCE3DD.toInt(), PromiseDarkColor.PrimaryControl.toArgbCompat())
    }

    @Test
    fun darkInkIsNotAccentGreen() {
        assertTrue(PromiseDarkColor.Ink != PromiseDarkColor.Accent)
        assertEquals(0xFFDCE3DD.toInt(), PromiseDarkColor.Primary.toArgbCompat())
    }

    @Test
    fun canonicalPairsMeetAaContrast() {
        assertTrue(Contrast.meetsAa(PromiseColor.TextPrimary, PromiseColor.Background))
        assertTrue(Contrast.meetsAa(PromiseColor.TextSecondary, PromiseColor.Background))
        assertTrue(Contrast.meetsAa(PromiseColor.OnPrimaryControl, PromiseColor.PrimaryControl))
        assertTrue(Contrast.meetsAa(PromiseDarkColor.TextPrimary, PromiseDarkColor.Background))
        assertTrue(Contrast.meetsAa(PromiseDarkColor.TextSecondary, PromiseDarkColor.Background))
        assertTrue(
            Contrast.meetsAa(PromiseDarkColor.OnPrimaryControl, PromiseDarkColor.PrimaryControl),
        )
    }

    private fun androidx.compose.ui.graphics.Color.toArgbCompat(): Int {
        return ((alpha * 255.0f + 0.5f).toInt() shl 24) or
            ((red * 255.0f + 0.5f).toInt() shl 16) or
            ((green * 255.0f + 0.5f).toInt() shl 8) or
            (blue * 255.0f + 0.5f).toInt()
    }
}
