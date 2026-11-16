package com.batsonar.haptic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batsonar.haptic.ui.SonarScreen
import com.batsonar.haptic.ui.theme.BatSonarHapticTheme
import com.batsonar.haptic.viewmodel.SonarViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: SonarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 檢查初始 RECORD_AUDIO 權限狀態
        val initiallyGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionUpdated(initiallyGranted)

        setContent {
            BatSonarHapticTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { granted ->
                    viewModel.onPermissionUpdated(granted)
                    if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                        // 若使用者勾選永久拒絕，引導前往系統應用程式設定頁面
                        val settingsIntent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", packageName, null)
                        )
                        startActivity(settingsIntent)
                    }
                }

                // 首次開啟自動請求 RECORD_AUDIO 權限
                LaunchedEffect(Unit) {
                    if (!initiallyGranted) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                // 聲納掃描期間保持螢幕恆亮，停止時恢復系統休眠設定
                DisposableEffect(uiState.isSonarRunning) {
                    if (uiState.isSonarRunning) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                SonarScreen(
                    uiState = uiState,
                    onRequestPermission = {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onToggleSonar = viewModel::toggleSonar,
                    onCalibrate = viewModel::triggerEnvironmentCalibration,
                    onMaxDistanceChange = viewModel::setMaxDetectDistance,
                    onSensitivityChange = viewModel::setSensitivity,
                    onSilentModeChange = viewModel::setSilentVibrationMode,
                    onVoiceGuidanceChange = viewModel::setVoiceGuidanceEnabled,
                    onDismissError = viewModel::clearError
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionUpdated(granted)
        viewModel.refreshMediaVolumeStatus()
    }

    override fun onStop() {
        super.onStop()
        // 避免離開背景後仍持續發射超聲波或耗電（依實際需求可隨時再點擊啟動）
        if (isFinishing) {
            viewModel.stopSonar()
        }
    }
}
