package com.batsonar.haptic.feedback

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.math.abs

/**
 * 語音距離播報控制器（TextToSpeech）。
 * 支援即時播報前方障礙物距離，並於啟用「靜音純震動模式」時自動靜音。
 */
class VoiceGuidanceController(context: Context) : TextToSpeech.OnInitListener {

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var isInitialized = false

    private var lastSpokenTimeMs = 0L
    private var lastSpokenDistance = -1f

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val twResult = tts.setLanguage(Locale.TRADITIONAL_CHINESE)
            if (twResult == TextToSpeech.LANG_MISSING_DATA ||
                twResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                tts.setLanguage(Locale.getDefault())
            }
            tts.setSpeechRate(1.15f)
            isInitialized = true
        }
    }

    fun announceIfDue(
        distanceMeters: Float?,
        isVoiceEnabled: Boolean,
        isSilentVibrationOnly: Boolean
    ) {
        if (!isInitialized) return
        if (!isVoiceEnabled || isSilentVibrationOnly || distanceMeters == null) {
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastSpokenTimeMs
        val deltaDist = if (lastSpokenDistance < 0f) Float.MAX_VALUE else abs(distanceMeters - lastSpokenDistance)

        val minIntervalMs = if (distanceMeters <= 0.65f) 1150L else 1900L
        val significantChange = deltaDist >= 0.25f && elapsed >= 900L

        if (elapsed >= minIntervalMs || significantChange) {
            val message = if (distanceMeters <= 0.55f) {
                String.format(Locale.US, "注意，%.1f 公尺", distanceMeters)
            } else {
                String.format(Locale.US, "%.1f 公尺", distanceMeters)
            }

            tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "batsonar_distance")
            lastSpokenTimeMs = now
            lastSpokenDistance = distanceMeters
        }
    }

    fun stopSpeaking() {
        if (isInitialized) {
            tts.stop()
        }
    }

    fun shutdown() {
        if (isInitialized) {
            tts.stop()
        }
        tts.shutdown()
        isInitialized = false
    }
}
