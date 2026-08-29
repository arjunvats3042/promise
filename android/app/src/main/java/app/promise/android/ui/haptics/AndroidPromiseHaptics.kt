package app.promise.android.ui.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidPromiseHaptics @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PromiseHaptics {

    @SuppressLint("InlinedApi")
    override fun light() {
        // Slightly above the softest tick.
        vibratePredefined(VibrationEffect.EFFECT_CLICK)
    }

    @SuppressLint("InlinedApi")
    override fun selection() {
        // Delicate crisp tick for scrolling or pill selection
        vibratePredefined(VibrationEffect.EFFECT_TICK)
    }

    @SuppressLint("InlinedApi")
    override fun confirm() {
        // Stronger confirmation without a harsh double-thud.
        vibratePredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    override fun celebrate() {
        // Multi-pulse rhythmic tactile celebration for completed goals/commitments
        if (!hapticsEnabled()) return
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return
        val timings = longArrayOf(0, 30, 60, 45, 60, 60)
        val amplitudes = intArrayOf(0, 140, 0, 180, 0, 255)
        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
    }

    @SuppressLint("InlinedApi")
    override fun error() {
        vibratePredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }

    private fun vibratePredefined(effectId: Int) {
        if (!hapticsEnabled()) return
        val vibrator = vibrator() ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(effectId))
        } else {
            @Suppress("DEPRECATION")
            @SuppressLint("InlinedApi")
            val durationMs = if (effectId == VibrationEffect.EFFECT_HEAVY_CLICK) 40L else 25L
            @Suppress("DEPRECATION")
            vibrator.vibrate(durationMs)
        }
    }

    @Suppress("DEPRECATION")
    private fun hapticsEnabled(): Boolean {
        return Settings.System.getInt(
            context.contentResolver,
            Settings.System.HAPTIC_FEEDBACK_ENABLED,
            1,
        ) != 0
    }

    private fun vibrator(): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }
}
