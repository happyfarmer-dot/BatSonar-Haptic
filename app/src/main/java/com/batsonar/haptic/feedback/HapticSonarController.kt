package com.batsonar.haptic.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Samsung Galaxy S24 Ultra X 軸線性馬達（LRA）觸覺聲納控制器。
 * 使用 Android 12+ VibratorManager 與 VibrationEffect.createOneShot，
 * 依障礙物距離動態調變脈衝間隔與震動強度：
 * - 3.0 m 內開始輕微低頻提示（約 720 ms 間隔、低振幅）
 * - 越靠近（特別是 0.5 m 內）震動間隔越急促（最低 95 ms）、振幅直達 255
 */
class HapticSonarController(
    context: Context,
    private val scope: CoroutineScope
) {
    private val vibratorManager: VibratorManager =
        context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager

    private val vibrator: Vibrator = vibratorManager.defaultVibrator
    private val hasVibrator: Boolean = vibrator.hasVibrator()
    private val supportsAmplitude: Boolean = vibrator.hasAmplitudeControl()

    @Volatile
    private var currentDistanceMeters: Float? = null

    @Volatile
    private var maxDetectRangeMeters: Float = 3.0f

    @Volatile
    private var isHapticActive: Boolean = false

    private var pulseLoopJob: Job? = null

    fun start() {
        if (!hasVibrator || pulseLoopJob?.isActive == true) return
        isHapticActive = true

        pulseLoopJob = scope.launch {
            while (isActive && isHapticActive) {
                val dist = currentDistanceMeters
                val maxRange = maxDetectRangeMeters

                if (dist != null && dist <= maxRange) {
                    val params = computeHapticParameters(dist)
                    triggerOneShot(params.durationMs, params.amplitude)
                    delay(params.intervalMs)
                } else {
                    delay(80L)
                }
            }
        }
    }

    fun updateMeasurement(distanceMeters: Float?, maxDistanceMeters: Float) {
        currentDistanceMeters = distanceMeters
        maxDetectRangeMeters = maxDistanceMeters.coerceIn(1.0f, 3.0f)
    }

    fun stop() {
        isHapticActive = false
        currentDistanceMeters = null
        pulseLoopJob?.cancel()
        pulseLoopJob = null
        if (hasVibrator) {
            vibrator.cancel()
        }
    }

    private fun triggerOneShot(durationMs: Long, amplitude: Int) {
        if (!hasVibrator) return
        val resolvedAmplitude = if (supportsAmplitude) {
            amplitude.coerceIn(1, 255)
        } else {
            VibrationEffect.DEFAULT_AMPLITUDE
        }

        val effect = VibrationEffect.createOneShot(durationMs, resolvedAmplitude)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ACCESSIBILITY)
                .build()
            vibrator.vibrate(effect, attrs)
        } else {
            vibrator.vibrate(effect)
        }
    }

    private data class HapticParams(
        val intervalMs: Long,
        val durationMs: Long,
        val amplitude: Int
    )

    private fun computeHapticParameters(distanceMeters: Float): HapticParams {
        val d = distanceMeters.coerceIn(0.30f, 3.00f)
        // closeness = 0.0 (3.0m 最遠) -> 1.0 (0.3m 最近)
        val closeness = ((3.00f - d) / (3.00f - 0.30f)).coerceIn(0f, 1f)

        return if (d <= 0.50f) {
            // 0.5 公尺內極近警告區：急促高強度 X 軸線性脈衝
            val nearRatio = ((0.50f - d) / 0.20f).coerceIn(0f, 1f)
            val intervalMs = (135f - nearRatio * 40f).roundToLong() // 135 ms -> 95 ms
            val durationMs = (34f + nearRatio * 8f).roundToLong()   // 34 ms -> 42 ms
            val amplitude = (220f + nearRatio * 35f).roundToInt()   // 220 -> 255
            HapticParams(intervalMs, durationMs, amplitude)
        } else {
            // 0.5 m ~ 3.0 m 漸進式觸覺引導
            val curve = closeness.toDouble().pow(1.45).toFloat()
            val intervalMs = (740f - curve * 590f).roundToLong()    // 740 ms -> 150 ms
            val durationMs = (20f + curve * 14f).roundToLong()      // 20 ms -> 34 ms
            val amplitude = (48f + curve * 168f).roundToInt()       // 48 -> 216
            HapticParams(intervalMs, durationMs, amplitude)
        }
    }
}
