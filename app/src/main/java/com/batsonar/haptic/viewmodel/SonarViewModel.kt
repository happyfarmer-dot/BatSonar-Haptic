package com.batsonar.haptic.viewmodel

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batsonar.haptic.audio.SonarAudioEngine
import com.batsonar.haptic.dsp.FmcwProcessor
import com.batsonar.haptic.feedback.HapticSonarController
import com.batsonar.haptic.feedback.VoiceGuidanceController
import com.batsonar.haptic.model.SonarUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 協調主動超聲波引擎、FMCW 演算法、X 軸線性馬達觸覺回饋、TTS 語音播報與 WakeLock 的核心 ViewModel。
 */
class SonarViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SonarUiState())
    val uiState: StateFlow<SonarUiState> = _uiState.asStateFlow()

    private val fmcwProcessor = FmcwProcessor()
    private val hapticController = HapticSonarController(application, viewModelScope)
    private val voiceController = VoiceGuidanceController(application)

    private val audioManager = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val audioEngine = SonarAudioEngine(
        context = application,
        onFrameCaptured = { frameSamples ->
            handleCapturedFrame(frameSamples)
        },
        onError = { message ->
            viewModelScope.launch {
                stopSonar()
                _uiState.update { it.copy(errorMessage = message) }
            }
        }
    )

    fun onPermissionUpdated(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasAudioPermission = granted,
                isMediaVolumeLow = isMediaVolumeInsufficient(),
                errorMessage = null
            )
        }
        if (!granted && _uiState.value.isSonarRunning) {
            stopSonar()
        }
    }

    fun refreshMediaVolumeStatus() {
        _uiState.update { it.copy(isMediaVolumeLow = isMediaVolumeInsufficient()) }
    }

    fun toggleSonar() {
        if (_uiState.value.isSonarRunning) {
            stopSonar()
        } else {
            startSonar()
        }
    }

    fun startSonar() {
        val current = _uiState.value
        if (!current.hasAudioPermission) {
            _uiState.update { it.copy(errorMessage = "請先授予麥克風錄音權限") }
            return
        }
        if (current.isSonarRunning) return

        acquireWakeLock()
        fmcwProcessor.reset()
        fmcwProcessor.startCalibration()

        val started = audioEngine.start()
        if (!started) {
            releaseWakeLock()
            return
        }

        hapticController.start()
        _uiState.update {
            it.copy(
                isSonarRunning = true,
                isCalibrating = true,
                calibrationProgress = 0f,
                hasCalibration = false,
                obstacleDetected = false,
                distanceMeters = null,
                echoStrength = 0f,
                snrRatio = 0f,
                rxRms = 0f,
                isMediaVolumeLow = isMediaVolumeInsufficient(),
                errorMessage = null
            )
        }
    }

    fun stopSonar() {
        audioEngine.stop()
        hapticController.stop()
        voiceController.stopSpeaking()
        fmcwProcessor.reset()
        releaseWakeLock()

        _uiState.update {
            it.copy(
                isSonarRunning = false,
                isCalibrating = false,
                calibrationProgress = 0f,
                obstacleDetected = false,
                distanceMeters = null,
                echoStrength = 0f,
                snrRatio = 0f,
                rxRms = 0f,
                spectrumBins = emptyList()
            )
        }
    }

    fun triggerEnvironmentCalibration() {
        if (!_uiState.value.hasAudioPermission) {
            _uiState.update { it.copy(errorMessage = "請先授予麥克風錄音權限") }
            return
        }
        if (_uiState.value.isSonarRunning) {
            hapticController.updateMeasurement(null, _uiState.value.maxDetectDistanceMeters)
            fmcwProcessor.startCalibration()
            _uiState.update {
                it.copy(
                    isCalibrating = true,
                    calibrationProgress = 0f,
                    hasCalibration = false,
                    obstacleDetected = false,
                    distanceMeters = null,
                    echoStrength = 0f,
                    isMediaVolumeLow = isMediaVolumeInsufficient()
                )
            }
        } else {
            startSonar()
        }
    }

    fun setMaxDetectDistance(meters: Float) {
        val clamped = meters.coerceIn(1.0f, 3.0f)
        _uiState.update { it.copy(maxDetectDistanceMeters = clamped) }
        hapticController.updateMeasurement(_uiState.value.distanceMeters, clamped)
    }

    fun setSensitivity(level: Int) {
        _uiState.update { it.copy(sensitivity = level.coerceIn(1, 10)) }
    }

    fun setSilentVibrationMode(enabled: Boolean) {
        if (enabled) {
            voiceController.stopSpeaking()
        }
        _uiState.update { it.copy(isSilentVibrationMode = enabled) }
    }

    fun setVoiceGuidanceEnabled(enabled: Boolean) {
        if (!enabled) {
            voiceController.stopSpeaking()
            _uiState.update { it.copy(isVoiceGuidanceEnabled = false) }
        } else {
            _uiState.update {
                it.copy(
                    isVoiceGuidanceEnabled = true,
                    isSilentVibrationMode = false
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun handleCapturedFrame(frameSamples: DoubleArray) {
        val stateSnapshot = _uiState.value
        val result = fmcwProcessor.processFrame(
            rxFrame2400 = frameSamples,
            maxDistanceMeters = stateSnapshot.maxDetectDistanceMeters.toDouble(),
            sensitivity = stateSnapshot.sensitivity
        )

        val dist = if (result.obstacleDetected) result.distanceMeters else null
        hapticController.updateMeasurement(dist, stateSnapshot.maxDetectDistanceMeters)

        if (!result.isCalibrating && result.hasCalibration) {
            voiceController.announceIfDue(
                distanceMeters = dist,
                isVoiceEnabled = stateSnapshot.isVoiceGuidanceEnabled,
                isSilentVibrationOnly = stateSnapshot.isSilentVibrationMode
            )
        }

        _uiState.update {
            it.copy(
                isCalibrating = result.isCalibrating,
                calibrationProgress = result.calibrationProgress,
                hasCalibration = result.hasCalibration,
                obstacleDetected = result.obstacleDetected,
                distanceMeters = dist,
                echoStrength = result.echoStrength,
                snrRatio = result.snrRatio,
                rxRms = result.rxRms,
                spectrumBins = result.spectrumSlice.toList()
            )
        }
    }

    private fun isMediaVolumeInsufficient(): Boolean {
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return currentVol.toFloat() / maxVol.toFloat() < 0.50f
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BatSonarHaptic::SonarWakeLock"
            ).apply {
                setReferenceCounted(false)
            }
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Throwable) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopSonar()
        voiceController.shutdown()
    }
}
