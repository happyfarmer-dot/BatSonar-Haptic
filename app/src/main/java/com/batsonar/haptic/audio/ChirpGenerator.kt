package com.batsonar.haptic.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 產生 48,000 Hz 採樣率的 FMCW 線性調頻超聲波（Chirp）參考訊號與 PCM16 發射幀。
 * - 發射週期：50 ms（2,400 samples）
 * - Chirp 長度：40 ms（1,920 samples）
 * - 靜音保護間隔：10 ms（480 samples）
 * - 頻率掃描：17,000 Hz -> 21,000 Hz（頻寬 B = 4,000 Hz）
 * - 前後各加 2 ms（96 samples）漢寧窗淡入淡出消除揚聲器爆音
 */
object ChirpGenerator {
    const val SAMPLE_RATE: Int = 48_000
    const val FRAME_DURATION_MS: Int = 50
    const val FRAME_SAMPLES: Int = SAMPLE_RATE * FRAME_DURATION_MS / 1000 // 2,400
    const val CHIRP_DURATION_MS: Int = 40
    const val CHIRP_SAMPLES: Int = SAMPLE_RATE * CHIRP_DURATION_MS / 1000 // 1,920
    const val FADE_DURATION_MS: Int = 2
    const val FADE_SAMPLES: Int = SAMPLE_RATE * FADE_DURATION_MS / 1000 // 96

    const val START_FREQ_HZ: Double = 17_000.0
    const val END_FREQ_HZ: Double = 21_000.0
    const val BANDWIDTH_HZ: Double = END_FREQ_HZ - START_FREQ_HZ // 4,000 Hz
    const val CHIRP_DURATION_SEC: Double = CHIRP_SAMPLES.toDouble() / SAMPLE_RATE // 0.040 s

    /**
     * 產生正規化 [-1.0, 1.0] 的 40 ms 參考 Chirp 訊號（含 2 ms 漢寧窗淡入淡出）。
     */
    fun generateReferenceChirp(): DoubleArray {
        val chirp = DoubleArray(CHIRP_SAMPLES)
        val sweepRateHalf = BANDWIDTH_HZ / (2.0 * CHIRP_DURATION_SEC)

        for (n in 0 until CHIRP_SAMPLES) {
            val t = n.toDouble() / SAMPLE_RATE
            val phase = 2.0 * PI * (START_FREQ_HZ * t + sweepRateHalf * t * t)
            var sample = sin(phase)

            val window = when {
                n < FADE_SAMPLES -> {
                    0.5 * (1.0 - cos(PI * n / FADE_SAMPLES))
                }
                n >= CHIRP_SAMPLES - FADE_SAMPLES -> {
                    val k = CHIRP_SAMPLES - 1 - n
                    0.5 * (1.0 - cos(PI * k / FADE_SAMPLES))
                }
                else -> 1.0
            }

            chirp[n] = sample * window
        }
        return chirp
    }

    /**
     * 產生完整 50 ms（2,400 samples）的 16-bit PCM 發射幀（前 40 ms 為 Chirp，後 10 ms 補零靜音）。
     */
    fun generateTxFramePcm16(amplitudeScale: Double = 0.85): ShortArray {
        val clampedScale = amplitudeScale.coerceIn(0.05, 0.98)
        val reference = generateReferenceChirp()
        val frame = ShortArray(FRAME_SAMPLES)
        val maxVal = Short.MAX_VALUE * clampedScale

        for (n in 0 until CHIRP_SAMPLES) {
            frame[n] = (reference[n] * maxVal)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return frame
    }
}
