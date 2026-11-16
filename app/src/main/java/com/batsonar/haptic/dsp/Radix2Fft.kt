package com.batsonar.haptic.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 純 Kotlin 實作的迭代式 Cooley-Tukey Radix-2 複數 FFT。
 * 預先計算位元反轉索引與旋轉因子表，確保 2048 點即時運算零 GC 配置。
 */
class Radix2Fft(val size: Int) {

    private val log2N: Int
    private val bitReversedIndices: IntArray
    private val cosTable: DoubleArray
    private val sinTable: DoubleArray

    init {
        require(size > 0 && (size and (size - 1)) == 0) {
            "FFT size must be a positive power of 2, got $size"
        }
        log2N = Integer.numberOfTrailingZeros(size)
        bitReversedIndices = IntArray(size) { index -> reverseBits(index, log2N) }

        val halfSize = size ushr 1
        cosTable = DoubleArray(halfSize)
        sinTable = DoubleArray(halfSize)
        for (k in 0 until halfSize) {
            val angle = -2.0 * PI * k / size
            cosTable[k] = cos(angle)
            sinTable[k] = sin(angle)
        }
    }

    /**
     * 原地計算複數 FFT（real 與 imag 長度必須等於 size）。
     */
    fun fft(real: DoubleArray, imag: DoubleArray) {
        require(real.size == size && imag.size == size) {
            "Input arrays must have length $size"
        }

        // 1. 位元反轉排列（Bit-reversal permutation）
        for (i in 0 until size) {
            val j = bitReversedIndices[i]
            if (i < j) {
                val tmpR = real[i]
                real[i] = real[j]
                real[j] = tmpR

                val tmpI = imag[i]
                imag[i] = imag[j]
                imag[j] = tmpI
            }
        }

        // 2. 蝶形運算階段（Butterfly stages）
        var len = 2
        while (len <= size) {
            val halfLen = len ushr 1
            val tableStep = size / len

            var start = 0
            while (start < size) {
                var twiddleIdx = 0
                for (j in 0 until halfLen) {
                    val evenIdx = start + j
                    val oddIdx = evenIdx + halfLen

                    val wr = cosTable[twiddleIdx]
                    val wi = sinTable[twiddleIdx]

                    val oddR = real[oddIdx]
                    val oddI = imag[oddIdx]

                    val tReal = oddR * wr - oddI * wi
                    val tImag = oddR * wi + oddI * wr

                    real[oddIdx] = real[evenIdx] - tReal
                    imag[oddIdx] = imag[evenIdx] - tImag
                    real[evenIdx] += tReal
                    imag[evenIdx] += tImag

                    twiddleIdx += tableStep
                }
                start += len
            }
            len = len shl 1
        }
    }

    /**
     * 由 FFT 結果計算單邊正規化振幅頻譜（0 .. size/2 - 1）。
     */
    fun computeMagnitudeSpectrum(
        real: DoubleArray,
        imag: DoubleArray,
        outMagnitudes: DoubleArray
    ) {
        val halfSize = size ushr 1
        require(real.size == size && imag.size == size && outMagnitudes.size >= halfSize) {
            "Array size mismatch for magnitude computation"
        }
        val invSize = 1.0 / size
        for (k in 0 until halfSize) {
            outMagnitudes[k] = hypot(real[k], imag[k]) * invSize
        }
    }

    private fun reverseBits(value: Int, bits: Int): Int {
        var x = value
        var reversed = 0
        for (i in 0 until bits) {
            reversed = (reversed shl 1) or (x and 1)
            x = x ushr 1
        }
        return reversed
    }
}
