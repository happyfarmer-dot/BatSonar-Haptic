package com.batsonar.haptic.dsp

import com.batsonar.haptic.audio.ChirpGenerator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * FMCW 測距單幀輸出結果。
 */
data class FmcwMeasurement(
    val isCalibrating: Boolean,
    val calibrationProgress: Float,
    val hasCalibration: Boolean,
    val obstacleDetected: Boolean,
    val distanceMeters: Float?,
    val echoStrength: Float,
    val snrRatio: Float,
    val rxRms: Float,
    val spectrumSlice: FloatArray
)

/**
 * 純 Kotlin FMCW 差頻測距處理器：
 * 1. 狀態式 15 kHz 高通濾波器消除環境低頻噪聲
 * 2. 兩幀歷史滑動緩衝區（4,800 samples）與正交包絡互相關對齊（Tx-Rx 同步鎖定）
 * 3. 混頻解調（Dechirping）：時域逐點乘上原始參考 Chirp 並套用漢寧窗
 * 4. 2048 點 Radix-2 FFT 計算差頻頻譜
 * 5. 機身直達波靜態基線消除（對空採集 1 秒 = 20 幀平均頻譜）
 * 6. 支援 1–10 級靈敏度調整，針對 UNPROCESSED 低增益麥克風優化微弱回波門檻
 */
class FmcwProcessor {

    companion object {
        const val SAMPLE_RATE: Double = ChirpGenerator.SAMPLE_RATE.toDouble() // 48,000 Hz
        const val SPEED_OF_SOUND: Double = 343.0 // m/s (常溫空氣)
        const val CHIRP_SAMPLES: Int = ChirpGenerator.CHIRP_SAMPLES // 1,920
        const val FRAME_SAMPLES: Int = ChirpGenerator.FRAME_SAMPLES // 2,400
        const val FFT_SIZE: Int = 2_048
        const val HALF_FFT_SIZE: Int = FFT_SIZE / 2 // 1,024

        const val BANDWIDTH_HZ: Double = ChirpGenerator.BANDWIDTH_HZ // 4,000 Hz
        const val CHIRP_DURATION_SEC: Double = ChirpGenerator.CHIRP_DURATION_SEC // 0.040 s
        const val FREQ_RESOLUTION_HZ: Double = SAMPLE_RATE / FFT_SIZE // 23.4375 Hz/bin

        const val MIN_DISTANCE_METERS: Double = 0.30
        const val MAX_DISTANCE_LIMIT_METERS: Double = 3.00

        // 1 秒 = 20 個 50 ms 幀
        const val CALIBRATION_FRAMES: Int = 20
        const val DEFAULT_SENSITIVITY: Int = 7
    }

    private val fft = Radix2Fft(FFT_SIZE)
    private val highPassFilter = HighPassFilter(sampleRate = SAMPLE_RATE, cutoffHz = 15_000.0)
    private val referenceChirp: DoubleArray = ChirpGenerator.generateReferenceChirp()

    // 90° 正交參考 Chirp（cos 相位），用於包絡相關對齊，避免 19 kHz 載波相位造成鎖定抖動
    private val referenceChirpQuad: DoubleArray = DoubleArray(CHIRP_SAMPLES).also { quad ->
        val sweepRateHalf = BANDWIDTH_HZ / (2.0 * CHIRP_DURATION_SEC)
        for (n in 0 until CHIRP_SAMPLES) {
            val t = n.toDouble() / SAMPLE_RATE
            val phase = 2.0 * PI * (ChirpGenerator.START_FREQ_HZ * t + sweepRateHalf * t * t)
            val window = when {
                n < ChirpGenerator.FADE_SAMPLES -> {
                    0.5 * (1.0 - cos(PI * n / ChirpGenerator.FADE_SAMPLES))
                }
                n >= CHIRP_SAMPLES - ChirpGenerator.FADE_SAMPLES -> {
                    val k = CHIRP_SAMPLES - 1 - n
                    0.5 * (1.0 - cos(PI * k / ChirpGenerator.FADE_SAMPLES))
                }
                else -> 1.0
            }
            quad[n] = cos(phase) * window
        }
    }

    // Dechirp 後乘上的 1920 點漢寧窗，可大幅抑制 0 Hz 附近殘餘直達波旁瓣洩漏
    private val dechirpHannWindow = DoubleArray(CHIRP_SAMPLES) { n ->
        0.5 * (1.0 - cos(2.0 * PI * n / (CHIRP_SAMPLES - 1)))
    }

    // 4800 點高通濾波後滑動歷史緩衝：[前 2400 | 新 2400]
    private val hpHistory = DoubleArray(FRAME_SAMPLES * 2)
    private val filteredChunk = DoubleArray(FRAME_SAMPLES)

    // FFT 工作緩衝（重覆使用避免 GC）
    private val fftReal = DoubleArray(FFT_SIZE)
    private val fftImag = DoubleArray(FFT_SIZE)
    private val magnitudes = DoubleArray(HALF_FFT_SIZE)
    private val residualSpectrum = DoubleArray(HALF_FFT_SIZE)

    // 機身直達波校準基線
    private val calibrationSum = DoubleArray(HALF_FFT_SIZE)
    private val baselineSpectrum = DoubleArray(HALF_FFT_SIZE)

    @Volatile
    private var isCalibrating: Boolean = false

    @Volatile
    private var hasCalibration: Boolean = false

    private var calibrationCount: Int = 0
    private var framesReceived: Int = 0

    // Tx-Rx 直達波對齊索引（位於 [0, FRAME_SAMPLES) 之間）
    private var lockedSyncOffset: Int = -1
    private var smoothedDistance: Double? = null
    private var missCounter: Int = 0

    @Synchronized
    fun startCalibration() {
        calibrationSum.fill(0.0)
        baselineSpectrum.fill(0.0)
        calibrationCount = 0
        isCalibrating = true
        hasCalibration = false
        lockedSyncOffset = -1
        smoothedDistance = null
        missCounter = 0
    }

    @Synchronized
    fun reset() {
        highPassFilter.reset()
        hpHistory.fill(0.0)
        calibrationSum.fill(0.0)
        baselineSpectrum.fill(0.0)
        isCalibrating = false
        hasCalibration = false
        calibrationCount = 0
        framesReceived = 0
        lockedSyncOffset = -1
        smoothedDistance = null
        missCounter = 0
    }

    /**
     * 輸入一段 50 ms（2,400 samples）麥克風正規化訊號進行處理。
     * @param sensitivity 靈敏度 1..10（預設 7；越高對遠距與低增益回波越敏感）
     */
    @Synchronized
    fun processFrame(
        rxFrame2400: DoubleArray,
        maxDistanceMeters: Double,
        sensitivity: Int = DEFAULT_SENSITIVITY
    ): FmcwMeasurement {
        require(rxFrame2400.size == FRAME_SAMPLES) {
            "Expected $FRAME_SAMPLES samples, got ${rxFrame2400.size}"
        }
        val clampedMaxDist = maxDistanceMeters.coerceIn(1.0, MAX_DISTANCE_LIMIT_METERS)
        val clampedSens = sensitivity.coerceIn(1, 10)

        // 1. 高通濾波並推入 4800 點滑動緩衝，同時計算 15 kHz 以上頻段 RMS
        highPassFilter.processBlock(rxFrame2400, filteredChunk)
        var sumSq = 0.0
        for (i in filteredChunk.indices) {
            val v = filteredChunk[i]
            sumSq += v * v
        }
        val rxRms = sqrt(sumSq / FRAME_SAMPLES).toFloat()

        System.arraycopy(hpHistory, FRAME_SAMPLES, hpHistory, 0, FRAME_SAMPLES)
        System.arraycopy(filteredChunk, 0, hpHistory, FRAME_SAMPLES, FRAME_SAMPLES)
        framesReceived++

        if (framesReceived < 2) {
            return FmcwMeasurement(
                isCalibrating = isCalibrating,
                calibrationProgress = if (isCalibrating) 0f else 1f,
                hasCalibration = hasCalibration,
                obstacleDetected = false,
                distanceMeters = null,
                echoStrength = 0f,
                snrRatio = 0f,
                rxRms = rxRms,
                spectrumSlice = FloatArray(0)
            )
        }

        // 2. 正交包絡對齊：初次全搜尋，後續在鎖定點 ±8 samples 微調追蹤
        val syncOffset = if (lockedSyncOffset < 0) {
            findFullSyncOffset(hpHistory).also { lockedSyncOffset = it }
        } else {
            refineSyncOffset(hpHistory, lockedSyncOffset).also { lockedSyncOffset = it }
        }

        // 3. 混頻解調（Dechirping）+ 漢寧窗 + 2048 點 Radix-2 FFT
        computeDechirpSpectrum(hpHistory, syncOffset, magnitudes)

        // 4. 若處於 1 秒環境校準期間，累積靜態基線頻譜
        if (isCalibrating) {
            for (k in 0 until HALF_FFT_SIZE) {
                calibrationSum[k] += magnitudes[k]
            }
            calibrationCount++

            val progress = (calibrationCount.toFloat() / CALIBRATION_FRAMES).coerceIn(0f, 1f)
            if (calibrationCount >= CALIBRATION_FRAMES) {
                val invCount = 1.0 / CALIBRATION_FRAMES
                for (k in 0 until HALF_FFT_SIZE) {
                    baselineSpectrum[k] = calibrationSum[k] * invCount
                }
                isCalibrating = false
                hasCalibration = true
            }

            return FmcwMeasurement(
                isCalibrating = isCalibrating,
                calibrationProgress = progress,
                hasCalibration = hasCalibration,
                obstacleDetected = false,
                distanceMeters = null,
                echoStrength = 0f,
                snrRatio = 0f,
                rxRms = rxRms,
                spectrumSlice = FloatArray(0)
            )
        }

        // 5. 扣除靜態機身串擾基線頻譜
        if (hasCalibration) {
            for (k in 0 until HALF_FFT_SIZE) {
                residualSpectrum[k] = max(0.0, magnitudes[k] - baselineSpectrum[k])
            }
        } else {
            System.arraycopy(magnitudes, 0, residualSpectrum, 0, HALF_FFT_SIZE)
        }

        // 6. 在 [0.30 m, clampedMaxDist] 對應差頻範圍搜尋最強回波峰值
        val minBin = max(1, kotlin.math.ceil(distanceToBin(MIN_DISTANCE_METERS)).toInt())
        val maxBin = min(HALF_FFT_SIZE - 2, kotlin.math.floor(distanceToBin(clampedMaxDist)).toInt())

        var peakBin = minBin
        var peakVal = -1.0
        for (bin in minBin..maxBin) {
            val v = residualSpectrum[bin]
            if (v > peakVal) {
                peakVal = v
                peakBin = bin
            }
        }

        // 計算搜尋頻帶內的雜訊底噪（排除峰值 ±2 bin 主瓣）
        var noiseSum = 0.0
        var noiseCount = 0
        for (bin in minBin..maxBin) {
            if (abs(bin - peakBin) > 2) {
                noiseSum += residualSpectrum[bin]
                noiseCount++
            }
        }
        val noiseFloor = if (noiseCount > 0) max(noiseSum / noiseCount, 1e-10) else 1e-10
        val snr = peakVal / noiseFloor

        // 依據靈敏度 (1..10) 動態調整檢測門檻，適配 UNPROCESSED 低增益麥克風
        val sensFactor = (clampedSens - 1) / 9.0 // 0.0 (最低靈敏度) .. 1.0 (最高靈敏度)
        val minSnrThreshold = 3.10 - sensFactor * 1.50 // 3.10 -> 1.60
        val minPeakThreshold = 2.5e-5 * Math.pow(0.08, sensFactor) // 2.5e-5 -> 2.0e-6

        // 提取視覺化頻譜片段（正規化 0..1）
        val sliceLen = (maxBin - minBin + 1).coerceAtLeast(1)
        val slice = FloatArray(sliceLen)
        val normDenom = max(peakVal, minPeakThreshold * 4.0)
        for (i in 0 until sliceLen) {
            slice[i] = (residualSpectrum[minBin + i] / normDenom).toFloat().coerceIn(0f, 1f)
        }

        val isValidObstacle = peakVal >= minPeakThreshold && snr >= minSnrThreshold

        val detectedDistance: Double?
        val echoStrength: Float

        if (isValidObstacle) {
            val refinedBin = if (peakBin > minBin && peakBin < maxBin) {
                val yLeft = residualSpectrum[peakBin - 1]
                val yCenter = residualSpectrum[peakBin]
                val yRight = residualSpectrum[peakBin + 1]
                val denom = yLeft - 2.0 * yCenter + yRight
                if (abs(denom) > 1e-12) {
                    val delta = (0.5 * (yLeft - yRight) / denom).coerceIn(-0.5, 0.5)
                    peakBin + delta
                } else {
                    peakBin.toDouble()
                }
            } else {
                peakBin.toDouble()
            }

            val rawDistance = binToDistance(refinedBin).coerceIn(MIN_DISTANCE_METERS, clampedMaxDist)

            val prev = smoothedDistance
            smoothedDistance = if (prev == null || abs(rawDistance - prev) > 0.65) {
                rawDistance
            } else {
                prev * 0.55 + rawDistance * 0.45
            }
            missCounter = 0
            detectedDistance = smoothedDistance

            val snrScore = ((snr - minSnrThreshold) / 7.5).coerceIn(0.0, 1.0)
            val ampScore = (peakVal / (minPeakThreshold * 80.0)).coerceIn(0.0, 1.0)
            echoStrength = (0.70 * snrScore + 0.30 * ampScore).toFloat().coerceIn(0.10f, 1.0f)
        } else {
            missCounter++
            if (missCounter <= 2 && smoothedDistance != null) {
                detectedDistance = smoothedDistance
                echoStrength = 0.05f
            } else {
                smoothedDistance = null
                detectedDistance = null
                echoStrength = 0f
            }
        }

        return FmcwMeasurement(
            isCalibrating = false,
            calibrationProgress = 1f,
            hasCalibration = hasCalibration,
            obstacleDetected = detectedDistance != null,
            distanceMeters = detectedDistance?.toFloat(),
            echoStrength = echoStrength,
            snrRatio = snr.toFloat(),
            rxRms = rxRms,
            spectrumSlice = slice
        )
    }

    internal fun computeDechirpSpectrum(
        rxBuffer: DoubleArray,
        startOffset: Int,
        outMagnitudes: DoubleArray
    ) {
        fftReal.fill(0.0)
        fftImag.fill(0.0)

        for (n in 0 until CHIRP_SAMPLES) {
            val mixed = rxBuffer[startOffset + n] * referenceChirp[n]
            fftReal[n] = mixed * dechirpHannWindow[n]
        }

        fft.fft(fftReal, fftImag)
        fft.computeMagnitudeSpectrum(fftReal, fftImag, outMagnitudes)
    }

    /**
     * 使用 I/Q 正交包絡相關（I² + Q²）找出機身直達波在 50 ms 週期中的起始索引。
     */
    private fun findFullSyncOffset(history4800: DoubleArray): Int {
        var bestCoarseOffset = 0
        var bestCoarseEnergy = -1.0

        var offset = 0
        while (offset < FRAME_SAMPLES) {
            var corrI = 0.0
            var corrQ = 0.0
            var n = 0
            while (n < CHIRP_SAMPLES) {
                val sample = history4800[offset + n]
                corrI += sample * referenceChirp[n]
                corrQ += sample * referenceChirpQuad[n]
                n += 4
            }
            val energy = corrI * corrI + corrQ * corrQ
            if (energy > bestCoarseEnergy) {
                bestCoarseEnergy = energy
                bestCoarseOffset = offset
            }
            offset += 6
        }

        var bestOffset = bestCoarseOffset
        var bestEnergy = -1.0
        val start = max(0, bestCoarseOffset - 10)
        val end = min(FRAME_SAMPLES - 1, bestCoarseOffset + 10)

        for (cand in start..end) {
            var corrI = 0.0
            var corrQ = 0.0
            for (n in 0 until CHIRP_SAMPLES step 2) {
                val sample = history4800[cand + n]
                corrI += sample * referenceChirp[n]
                corrQ += sample * referenceChirpQuad[n]
            }
            val energy = corrI * corrI + corrQ * corrQ
            if (energy > bestEnergy) {
                bestEnergy = energy
                bestOffset = cand
            }
        }
        return bestOffset
    }

    private fun refineSyncOffset(history4800: DoubleArray, currentOffset: Int): Int {
        var bestOffset = currentOffset
        var bestEnergy = -1.0
        val start = max(0, currentOffset - 8)
        val end = min(FRAME_SAMPLES - 1, currentOffset + 8)

        for (cand in start..end) {
            var corrI = 0.0
            var corrQ = 0.0
            for (n in 0 until CHIRP_SAMPLES step 2) {
                val sample = history4800[cand + n]
                corrI += sample * referenceChirp[n]
                corrQ += sample * referenceChirpQuad[n]
            }
            val energy = corrI * corrI + corrQ * corrQ
            if (energy > bestEnergy) {
                bestEnergy = energy
                bestOffset = cand
            }
        }
        return bestOffset
    }

    fun distanceToBeatFreq(distanceMeters: Double): Double {
        return (BANDWIDTH_HZ / CHIRP_DURATION_SEC) * (2.0 * distanceMeters / SPEED_OF_SOUND)
    }

    fun beatFreqToDistance(beatFreqHz: Double): Double {
        return (SPEED_OF_SOUND * CHIRP_DURATION_SEC * beatFreqHz) / (2.0 * BANDWIDTH_HZ)
    }

    fun distanceToBin(distanceMeters: Double): Double {
        return distanceToBeatFreq(distanceMeters) / FREQ_RESOLUTION_HZ
    }

    fun binToDistance(bin: Double): Double {
        return beatFreqToDistance(bin * FREQ_RESOLUTION_HZ)
    }
}
