package com.batsonar.haptic.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Process
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 48,000 Hz 全雙工主動超聲波發射與接收引擎：
 * - AudioTrack（MODE_STREAM）每 50 ms 連續發射一段含 40 ms Chirp 的音訊幀
 * - AudioRecord 使用 MediaRecorder.AudioSource.UNPROCESSED 同步錄音（無 AGC/降噪破壞相位）
 */
class SonarAudioEngine(
    private val context: Context,
    private val onFrameCaptured: (DoubleArray) -> Unit,
    private val onError: (String) -> Unit
) {
    private val isRunning = AtomicBoolean(false)

    private var audioTrack: AudioTrack? = null
    private var audioRecord: AudioRecord? = null
    private var txThread: Thread? = null
    private var rxThread: Thread? = null

    val isActive: Boolean
        get() = isRunning.get()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (isRunning.get()) return true

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            onError("缺少 RECORD_AUDIO 麥克風權限，無法啟動超聲波聲納。")
            return false
        }

        val track = createAudioTrack()
        val record = createUnprocessedAudioRecord()

        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            record.release()
            onError("AudioTrack 初始化失敗，無法發射超聲波。")
            return false
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            track.release()
            record.release()
            onError("AudioRecord 初始化失敗（UNPROCESSED 音訊源不可用）。")
            return false
        }

        audioTrack = track
        audioRecord = record
        isRunning.set(true)

        val txFrame = ChirpGenerator.generateTxFramePcm16(amplitudeScale = 0.95)

        txThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            try {
                track.play()
                while (isRunning.get()) {
                    val written = track.write(
                        txFrame,
                        0,
                        txFrame.size,
                        AudioTrack.WRITE_BLOCKING
                    )
                    if (written < 0) {
                        if (isRunning.get()) {
                            onError("AudioTrack 寫入錯誤碼：$written")
                        }
                        break
                    }
                }
            } catch (t: Throwable) {
                if (isRunning.get()) {
                    onError("發射執行緒異常：${t.localizedMessage ?: "未知錯誤"}")
                }
            }
        }, "BatSonar-TxThread").apply { start() }

        rxThread = Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val rxShortBuffer = ShortArray(ChirpGenerator.FRAME_SAMPLES)
            val rxDoubleBuffer = DoubleArray(ChirpGenerator.FRAME_SAMPLES)
            val invScale = 1.0 / 32768.0

            try {
                record.startRecording()
                while (isRunning.get()) {
                    var totalRead = 0
                    while (totalRead < rxShortBuffer.size && isRunning.get()) {
                        val read = record.read(
                            rxShortBuffer,
                            totalRead,
                            rxShortBuffer.size - totalRead,
                            AudioRecord.READ_BLOCKING
                        )
                        if (read < 0) {
                            if (isRunning.get()) {
                                onError("AudioRecord 讀取錯誤碼：$read")
                            }
                            break
                        }
                        totalRead += read
                    }

                    if (totalRead == rxShortBuffer.size && isRunning.get()) {
                        for (i in rxShortBuffer.indices) {
                            rxDoubleBuffer[i] = rxShortBuffer[i] * invScale
                        }
                        onFrameCaptured(rxDoubleBuffer)
                    }
                }
            } catch (t: Throwable) {
                if (isRunning.get()) {
                    onError("接收執行緒異常：${t.localizedMessage ?: "未知錯誤"}")
                }
            }
        }, "BatSonar-RxThread").apply { start() }

        return true
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return

        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
            }
        } catch (_: Throwable) {
        }

        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    record.stop()
                }
            }
        } catch (_: Throwable) {
        }

        try {
            txThread?.join(400)
        } catch (_: InterruptedException) {
        }
        try {
            rxThread?.join(400)
        } catch (_: InterruptedException) {
        }

        txThread = null
        rxThread = null

        try {
            audioTrack?.release()
        } catch (_: Throwable) {
        }
        try {
            audioRecord?.release()
        } catch (_: Throwable) {
        }

        audioTrack = null
        audioRecord = null
    }

    private fun createAudioTrack(): AudioTrack {
        val minBufBytes = AudioTrack.getMinBufferSize(
            ChirpGenerator.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(ChirpGenerator.FRAME_SAMPLES * 2)

        val bufferBytes = maxOf(minBufBytes, ChirpGenerator.FRAME_SAMPLES * 2 * 4)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ChirpGenerator.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    @SuppressLint("MissingPermission")
    private fun createUnprocessedAudioRecord(): AudioRecord {
        val minBufBytes = AudioRecord.getMinBufferSize(
            ChirpGenerator.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(ChirpGenerator.FRAME_SAMPLES * 2)

        val bufferBytes = maxOf(minBufBytes, ChirpGenerator.FRAME_SAMPLES * 2 * 6)

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(ChirpGenerator.SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val unprocessedRecord = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.UNPROCESSED)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferBytes)
            .build()

        if (unprocessedRecord.state == AudioRecord.STATE_INITIALIZED) {
            return unprocessedRecord
        }
        unprocessedRecord.release()

        // 若少數模擬器不支援 UNPROCESSED，安全退回 VOICE_RECOGNITION 避免崩潰
        return AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufferBytes)
            .build()
    }
}
