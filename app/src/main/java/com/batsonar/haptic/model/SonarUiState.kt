package com.batsonar.haptic.model

/**
 * 聲納警示等級（對應高對比無障礙色碼與狀態提示）。
 */
enum class ProximityAlertLevel {
    IDLE,
    CALIBRATING,
    CLEAR,
    FAR,
    CAUTION,
    DANGER
}

/**
 * 主畫面不可變 UI 狀態。
 */
data class SonarUiState(
    val hasAudioPermission: Boolean = false,
    val isSonarRunning: Boolean = false,
    val isCalibrating: Boolean = false,
    val calibrationProgress: Float = 0f,
    val hasCalibration: Boolean = false,
    val obstacleDetected: Boolean = false,
    val distanceMeters: Float? = null,
    val echoStrength: Float = 0f,
    val snrRatio: Float = 0f,
    val maxDetectDistanceMeters: Float = 2.5f,
    val isSilentVibrationMode: Boolean = false,
    val isVoiceGuidanceEnabled: Boolean = true,
    val spectrumBins: List<Float> = emptyList(),
    val errorMessage: String? = null
) {
    val alertLevel: ProximityAlertLevel
        get() = when {
            !isSonarRunning -> ProximityAlertLevel.IDLE
            isCalibrating -> ProximityAlertLevel.CALIBRATING
            !obstacleDetected || distanceMeters == null -> ProximityAlertLevel.CLEAR
            distanceMeters <= 0.65f -> ProximityAlertLevel.DANGER
            distanceMeters <= 1.40f -> ProximityAlertLevel.CAUTION
            else -> ProximityAlertLevel.FAR
        }
}
