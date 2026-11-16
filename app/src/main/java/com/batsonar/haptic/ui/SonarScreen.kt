package com.batsonar.haptic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batsonar.haptic.model.ProximityAlertLevel
import com.batsonar.haptic.model.SonarUiState
import com.batsonar.haptic.ui.components.EchoStrengthBar
import com.batsonar.haptic.ui.components.SubmarineRadarCanvas
import com.batsonar.haptic.ui.theme.SonarColors
import java.util.Locale

@Composable
fun SonarScreen(
    uiState: SonarUiState,
    onRequestPermission: () -> Unit,
    onToggleSonar: () -> Unit,
    onCalibrate: () -> Unit,
    onMaxDistanceChange: (Float) -> Unit,
    onSensitivityChange: (Int) -> Unit,
    onSilentModeChange: (Boolean) -> Unit,
    onVoiceGuidanceChange: (Boolean) -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val statusColor = when (uiState.alertLevel) {
        ProximityAlertLevel.DANGER -> SonarColors.CrimsonDanger
        ProximityAlertLevel.CAUTION -> SonarColors.AmberCaution
        ProximityAlertLevel.CALIBRATING -> SonarColors.SonarElectricBlue
        ProximityAlertLevel.CLEAR, ProximityAlertLevel.FAR -> SonarColors.SonarCyan
        ProximityAlertLevel.IDLE -> SonarColors.TextSecondary
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SonarColors.BackgroundBlack)
            .safeDrawingPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 頂部標題與狀態膠囊
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = null,
                    tint = SonarColors.SonarCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "BatSonar Haptic",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SonarColors.TextPrimary,
                    modifier = Modifier.semantics { heading() }
                )
            }

            StatusBadge(uiState = uiState, accentColor = statusColor)
        }

        // 媒體音量過低警告（AudioTrack 需要足夠媒體音量才能發射 17-21 kHz 超聲波）
        if (uiState.isMediaVolumeLow) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SonarColors.AmberCaution.copy(alpha = 0.18f))
                    .border(1.5.dp, SonarColors.AmberCaution, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "⚠️ 媒體音量偏低：請將手機「媒體音量」調高至 70% 以上，確保揚聲器發出足夠強度的超聲波！",
                    color = SonarColors.AmberCaution,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 錯誤訊息橫幅（若有）
        if (uiState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SonarColors.CrimsonDanger.copy(alpha = 0.22f))
                    .border(1.5.dp, SonarColors.CrimsonDanger, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.errorMessage,
                        color = SonarColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = onDismissError) {
                        Text("關閉", color = SonarColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 麥克風權限引導卡片（未授權時顯示）
        if (!uiState.hasAudioPermission) {
            PermissionPromptCard(onRequestPermission = onRequestPermission)
        }

        // 仿潛艇聲納扇形雷達掃描圖
        SubmarineRadarCanvas(
            isSonarRunning = uiState.isSonarRunning,
            isCalibrating = uiState.isCalibrating,
            distanceMeters = uiState.distanceMeters,
            maxDistanceMeters = uiState.maxDetectDistanceMeters,
            echoStrength = uiState.echoStrength,
            alertLevel = uiState.alertLevel
        )

        // 回波強度條
        EchoStrengthBar(
            echoStrength = uiState.echoStrength,
            snrRatio = uiState.snrRatio
        )

        // 超大字體即時距離面板
        DistanceReadoutCard(uiState = uiState, accentColor = statusColor)

        // 主控按鈕列：啟動/停止聲納 + 一鍵環境校準
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onToggleSonar,
                enabled = uiState.hasAudioPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isSonarRunning) {
                        SonarColors.CrimsonDanger
                    } else {
                        SonarColors.SonarCyan
                    },
                    contentColor = if (uiState.isSonarRunning) {
                        SonarColors.TextPrimary
                    } else {
                        SonarColors.BackgroundBlack
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1.15f)
                    .height(62.dp)
            ) {
                Icon(
                    imageVector = if (uiState.isSonarRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.isSonarRunning) "停止聲納" else "啟動聲納",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            OutlinedButton(
                onClick = onCalibrate,
                enabled = uiState.hasAudioPermission && !uiState.isCalibrating,
                border = androidx.compose.foundation.BorderStroke(
                    width = 2.dp,
                    color = SonarColors.SonarElectricBlue
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(62.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = SonarColors.SonarElectricBlue,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (uiState.isCalibrating) "校準中" else "環境校準",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SonarColors.SonarElectricBlue
                )
            }
        }

        // 1 秒校準進度條
        if (uiState.isCalibrating) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SonarColors.SurfaceCard)
                    .border(1.dp, SonarColors.SonarElectricBlue, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "請將手機對空，正在採集 1 秒靜態機身串擾基線...",
                    color = SonarColors.SonarElectricBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uiState.calibrationProgress },
                    color = SonarColors.SonarElectricBlue,
                    trackColor = SonarColors.SurfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                )
            }
        }

        // 最大偵測距離滑桿（1m – 3m）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SonarColors.SurfaceCard)
                .border(1.dp, SonarColors.BorderMuted, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "最大偵測距離",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SonarColors.TextPrimary
                )
                Text(
                    text = String.format(Locale.US, "%.1f m", uiState.maxDetectDistanceMeters),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SonarColors.SonarCyan
                )
            }

            Slider(
                value = uiState.maxDetectDistanceMeters,
                onValueChange = { raw ->
                    val stepped = kotlin.math.round(raw * 10f) / 10f
                    onMaxDistanceChange(stepped)
                },
                valueRange = 1.0f..3.0f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = SonarColors.SonarCyan,
                    activeTrackColor = SonarColors.SonarCyan,
                    inactiveTrackColor = SonarColors.SurfaceElevated
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1.0 m", color = SonarColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("2.0 m", color = SonarColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("3.0 m", color = SonarColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 偵測靈敏度滑桿（1–10 級，預設 7）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SonarColors.SurfaceCard)
                .border(1.dp, SonarColors.BorderMuted, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "回波偵測靈敏度",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SonarColors.TextPrimary
                )
                Text(
                    text = "第 ${uiState.sensitivity} 級",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SonarColors.SonarElectricBlue
                )
            }

            Slider(
                value = uiState.sensitivity.toFloat(),
                onValueChange = { raw ->
                    onSensitivityChange(kotlin.math.round(raw).toInt().coerceIn(1, 10))
                },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = SonarColors.SonarElectricBlue,
                    activeTrackColor = SonarColors.SonarElectricBlue,
                    inactiveTrackColor = SonarColors.SurfaceElevated
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1 (抗噪)", color = SonarColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("7 (建議)", color = SonarColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("10 (極敏)", color = SonarColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // 無障礙模式切換：靜音純震動模式 & 語音距離播報
        AccessibleToggleRow(
            icon = if (uiState.isSilentVibrationMode) Icons.Filled.VolumeOff else Icons.Filled.Vibration,
            title = "靜音純震動模式",
            subtitle = "關閉語音播報，僅使用 X 軸線性馬達脈衝警示",
            checked = uiState.isSilentVibrationMode,
            onCheckedChange = onSilentModeChange
        )

        AccessibleToggleRow(
            icon = Icons.Filled.RecordVoiceOver,
            title = "語音距離播報 (TTS)",
            subtitle = if (uiState.isSilentVibrationMode) {
                "目前受「靜音純震動模式」抑制中"
            } else {
                "即時語音報讀前方障礙物距離"
            },
            checked = uiState.isVoiceGuidanceEnabled && !uiState.isSilentVibrationMode,
            onCheckedChange = onVoiceGuidanceChange
        )
    }
}

@Composable
private fun StatusBadge(
    uiState: SonarUiState,
    accentColor: Color
) {
    val label = when {
        !uiState.isSonarRunning -> "待命中"
        uiState.isCalibrating -> "校準中"
        uiState.isSilentVibrationMode -> "純震動掃描"
        else -> "聲納運作中"
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accentColor.copy(alpha = 0.18f))
            .border(1.5.dp, accentColor, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = accentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun DistanceReadoutCard(
    uiState: SonarUiState,
    accentColor: Color
) {
    val distanceText = when {
        !uiState.isSonarRunning -> "--.-- m"
        uiState.isCalibrating -> "校準中"
        uiState.distanceMeters != null -> String.format(Locale.US, "%.2f m", uiState.distanceMeters)
        else -> "> ${String.format(Locale.US, "%.1f", uiState.maxDetectDistanceMeters)} m"
    }

    val alertText = when (uiState.alertLevel) {
        ProximityAlertLevel.IDLE -> "點擊「啟動聲納」開始 17–21 kHz 超聲波避障"
        ProximityAlertLevel.CALIBRATING -> "正在建立靜態機身直達波消除基線"
        ProximityAlertLevel.CLEAR -> "前方偵測範圍內淨空"
        ProximityAlertLevel.FAR -> "遠距偵測到物體（輕微低頻觸覺提示）"
        ProximityAlertLevel.CAUTION -> "注意前方障礙物（中頻加速觸覺警示）"
        ProximityAlertLevel.DANGER -> "極近距離警告！請立即避讓"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SonarColors.SurfaceCard)
            .border(2.dp, accentColor.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
            .padding(vertical = 14.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "即時障礙物距離",
            color = SonarColors.TextSecondary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = distanceText,
            style = TextStyle(
                fontSize = 58.sp,
                fontWeight = FontWeight.Black,
                color = accentColor,
                fontFeatureSettings = "tnum"
            ),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = alertText,
            color = SonarColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AccessibleToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SonarColors.SurfaceCard)
            .border(1.dp, SonarColors.BorderMuted, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked) SonarColors.SonarCyan else SonarColors.TextSecondary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SonarColors.TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = SonarColors.TextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SonarColors.BackgroundBlack,
                checkedTrackColor = SonarColors.SonarCyan,
                uncheckedThumbColor = SonarColors.TextSecondary,
                uncheckedTrackColor = SonarColors.SurfaceElevated
            )
        )
    }
}

@Composable
private fun PermissionPromptCard(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SonarColors.SurfaceElevated)
            .border(2.dp, SonarColors.AmberCaution, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = SonarColors.AmberCaution,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "需要麥克風錄音權限",
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold,
                color = SonarColors.AmberCaution
            )
        }
        Text(
            text = "BatSonar Haptic 需要存取未經處理的 48 kHz 麥克風音訊（RECORD_AUDIO），才能接收超聲波回波進行 FMCW 測距。",
            fontSize = 15.sp,
            color = SonarColors.TextPrimary
        )
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = SonarColors.AmberCaution,
                contentColor = SonarColors.BackgroundBlack
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text(
                text = "授予麥克風權限",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}
