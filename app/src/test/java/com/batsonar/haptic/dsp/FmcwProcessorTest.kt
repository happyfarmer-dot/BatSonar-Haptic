package com.batsonar.haptic.dsp

import com.batsonar.haptic.audio.ChirpGenerator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class FmcwProcessorTest {

    @Test
    fun `crosstalk calibration suppresses direct wave and measures 1_5m synthetic echo`() {
        val processor = FmcwProcessor()
        val refChirp = ChirpGenerator.generateReferenceChirp()
        val frameSamples = ChirpGenerator.FRAME_SAMPLES
        val hardwareOffset = 320 // 模擬裝置固定播放與錄音管線延遲

        // 步驟 1：啟動 1 秒對空校準（僅含機身直達串擾）
        processor.startCalibration()
        val continuousStream = DoubleArray(frameSamples * 30) { 0.0 }
        for (frameIdx in 0 until 30) {
            val base = frameIdx * frameSamples + hardwareOffset
            for (n in refChirp.indices) {
                if (base + n < continuousStream.size) {
                    continuousStream[base + n] += 0.45 * refChirp[n]
                }
            }
        }

        var lastCalibResult: FmcwMeasurement? = null
        // 輸入前 22 幀完成暖機 + 20 幀校準
        for (frameIdx in 0 until 22) {
            val frame = continuousStream.copyOfRange(
                frameIdx * frameSamples,
                (frameIdx + 1) * frameSamples
            )
            lastCalibResult = processor.processFrame(frame, maxDistanceMeters = 3.0)
        }

        assertNotNull(lastCalibResult)
        assertTrue("Expected calibration complete", lastCalibResult!!.hasCalibration)
        assertFalse("Should not report obstacle during calibration", lastCalibResult.isCalibrating)

        // 步驟 2：加入距離 1.50 m 的模擬障礙物回波（往返延遲 2*1.5/343 = 8.746 ms ≈ 420 samples）
        val targetDistanceMeters = 1.50
        val roundTripDelaySamples = ((2.0 * targetDistanceMeters / FmcwProcessor.SPEED_OF_SOUND) *
            FmcwProcessor.SAMPLE_RATE).roundToInt()

        val echoStream = continuousStream.copyOf()
        for (frameIdx in 22 until 30) {
            val base = frameIdx * frameSamples + hardwareOffset + roundTripDelaySamples
            for (n in refChirp.indices) {
                if (base + n < echoStream.size) {
                    echoStream[base + n] += 0.04 * refChirp[n]
                }
            }
        }

        var measured: FmcwMeasurement? = null
        for (frameIdx in 22 until 28) {
            val frame = echoStream.copyOfRange(
                frameIdx * frameSamples,
                (frameIdx + 1) * frameSamples
            )
            measured = processor.processFrame(frame, maxDistanceMeters = 3.0)
        }

        assertNotNull(measured)
        assertTrue("Expected obstacle detected at 1.50m", measured!!.obstacleDetected)
        val dist = measured.distanceMeters!!
        assertTrue(
            "Measured distance $dist m should be within 0.08m of $targetDistanceMeters m",
            abs(dist - targetDistanceMeters) < 0.08
        )
    }
}
