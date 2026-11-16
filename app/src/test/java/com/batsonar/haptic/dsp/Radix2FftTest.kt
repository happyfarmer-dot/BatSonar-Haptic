package com.batsonar.haptic.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class Radix2FftTest {

    @Test
    fun `2048-point FFT detects exact sine tone bin`() {
        val fftSize = 2048
        val sampleRate = 48_000.0
        val targetBin = 42
        val freqHz = targetBin * (sampleRate / fftSize)

        val real = DoubleArray(fftSize) { n ->
            sin(2.0 * PI * freqHz * n / sampleRate)
        }
        val imag = DoubleArray(fftSize)
        val magnitudes = DoubleArray(fftSize / 2)

        val fft = Radix2Fft(fftSize)
        fft.fft(real, imag)
        fft.computeMagnitudeSpectrum(real, imag, magnitudes)

        var maxBin = 0
        var maxVal = -1.0
        for (k in magnitudes.indices) {
            if (magnitudes[k] > maxVal) {
                maxVal = magnitudes[k]
                maxBin = k
            }
        }

        assertEquals(targetBin, maxBin)
        assertTrue("Expected normalized magnitude ~0.5, got $maxVal", maxVal > 0.49 && maxVal < 0.51)
    }
}
