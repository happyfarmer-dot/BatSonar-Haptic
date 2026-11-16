package com.batsonar.haptic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batsonar.haptic.ui.theme.SonarColors
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 高對比分段式回波強度條（Echo Strength Bar），同步顯示回波強度百分比與訊噪比 SNR。
 */
@Composable
fun EchoStrengthBar(
    echoStrength: Float,
    snrRatio: Float,
    modifier: Modifier = Modifier
) {
    val clampedStrength = echoStrength.coerceIn(0f, 1f)
    val totalSegments = 16
    val activeSegments = (clampedStrength * totalSegments).roundToInt()
    val percent = (clampedStrength * 100f).roundToInt()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SonarColors.SurfaceCard)
            .border(1.dp, SonarColors.BorderMuted, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "回波強度",
                color = SonarColors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = String.format(Locale.US, "%d%%  (SNR %.1f)", percent, snrRatio),
                color = SonarColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (i in 0 until totalSegments) {
                val isLit = i < activeSegments
                val segColor: Color = when {
                    !isLit -> SonarColors.SurfaceElevated
                    i >= 12 -> SonarColors.CrimsonDanger
                    i >= 7 -> SonarColors.AmberCaution
                    else -> SonarColors.SonarCyan
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(segColor)
                )
            }
        }
    }
}
