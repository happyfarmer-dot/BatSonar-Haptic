package com.batsonar.haptic.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 4 階 Butterworth 高通濾波器（兩級 2 階 Biquad 串聯），截止頻率 15,000 Hz。
 * 用於濾除 15 kHz 以下的環境人聲、風切與低頻震動雜訊，保留 17–21 kHz 超聲波回波頻段。
 */
class HighPassFilter(
    sampleRate: Double = 48_000.0,
    cutoffHz: Double = 15_000.0
) {
    private class BiquadSection(
        sampleRate: Double,
        cutoffHz: Double,
        q: Double
    ) {
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private val a1: Double
        private val a2: Double

        private var z1 = 0.0
        private var z2 = 0.0

        init {
            val omega = 2.0 * PI * cutoffHz / sampleRate
            val cosW = cos(omega)
            val sinW = sin(omega)
            val alpha = sinW / (2.0 * q)

            val a0 = 1.0 + alpha
            b0 = ((1.0 + cosW) * 0.5) / a0
            b1 = (-(1.0 + cosW)) / a0
            b2 = ((1.0 + cosW) * 0.5) / a0
            a1 = (-2.0 * cosW) / a0
            a2 = (1.0 - alpha) / a0
        }

        fun process(input: Double): Double {
            val output = b0 * input + z1
            z1 = b1 * input - a1 * output + z2
            z2 = b2 * input - a2 * output
            return output
        }

        fun reset() {
            z1 = 0.0
            z2 = 0.0
        }
    }

    // 4th-order Butterworth Q values: 1 / (2*cos(pi/8)) and 1 / (2*cos(3*pi/8))
    private val stage1 = BiquadSection(sampleRate, cutoffHz, q = 0.541196100146197)
    private val stage2 = BiquadSection(sampleRate, cutoffHz, q = 1.3065629648763766)

    fun processSample(sample: Double): Double {
        return stage2.process(stage1.process(sample))
    }

    fun processBlock(input: DoubleArray, output: DoubleArray) {
        require(input.size == output.size)
        for (i in input.indices) {
            output[i] = processSample(input[i])
        }
    }

    fun reset() {
        stage1.reset()
        stage2.reset()
    }
}
