package com.batsonar.haptic.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batsonar.haptic.model.ProximityAlertLevel
import com.batsonar.haptic.ui.theme.SonarColors
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 仿潛艇聲納 120° 扇形雷達掃描圖（Canvas 繪製）。
 * 顯示距離同心弧線、動態掃描波束、障礙物距離弧與即時回波光點。
 */
@Composable
fun SubmarineRadarCanvas(
    isSonarRunning: Boolean,
    isCalibrating: Boolean,
    distanceMeters: Float?,
    maxDistanceMeters: Float,
    echoStrength: Float,
    alertLevel: ProximityAlertLevel,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sonar_sweep")
    val sweepProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blip_pulse"
    )

    val textMeasurer = rememberTextMeasurer()

    val activeAccent = when (alertLevel) {
        ProximityAlertLevel.DANGER -> SonarColors.CrimsonDanger
        ProximityAlertLevel.CAUTION -> SonarColors.AmberCaution
        ProximityAlertLevel.CALIBRATING -> SonarColors.SonarElectricBlue
        else -> SonarColors.SonarCyan
    }

    val accessibilityDesc = if (!isSonarRunning) {
        "潛艇聲納扇形雷達目前待命中"
    } else if (isCalibrating) {
        "潛艇聲納雷達目前正在進行 1 秒環境校準"
    } else if (distanceMeters != null) {
        String.format(Locale.US, "潛艇聲納雷達偵測到前方 %.2f 公尺障礙物", distanceMeters)
    } else {
        String.format(Locale.US, "潛艇聲納雷達掃描中，%.1f 公尺內無障礙物", maxDistanceMeters)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SonarColors.SurfaceCard)
            .border(1.5.dp, activeAccent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .semantics { contentDescription = accessibilityDesc }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val origin = Offset(x = size.width * 0.5f, y = size.height * 0.90f)
            val maxRadius = min(size.width * 0.46f, size.height * 0.80f)
            val startAngleDeg = -150f
            val sweepAngleDeg = 120f

            // 背景扇形微弱填充
            drawArc(
                brush = Brush.radialGradient(
                    colors = listOf(
                        activeAccent.copy(alpha = 0.14f),
                        activeAccent.copy(alpha = 0.03f),
                        Color.Transparent
                    ),
                    center = origin,
                    radius = maxRadius
                ),
                startAngle = startAngleDeg,
                sweepAngle = sweepAngleDeg,
                useCenter = true,
                topLeft = Offset(origin.x - maxRadius, origin.y - maxRadius),
                size = Size(maxRadius * 2f, maxRadius * 2f)
            )

            // 同心距離環（每 0.5 m 一環，並確保包含最大偵測距離外框）
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            val ringDistances = mutableListOf<Float>()
            var ringDist = 0.5f
            while (ringDist < maxDistanceMeters - 0.12f) {
                ringDistances.add(ringDist)
                ringDist += 0.5f
            }
            ringDistances.add(maxDistanceMeters)

            ringDistances.forEachIndexed { index, distVal ->
                val isOuter = index == ringDistances.lastIndex
                val r = maxRadius * (distVal / maxDistanceMeters).coerceIn(0f, 1f)
                drawArc(
                    color = if (isOuter) {
                        activeAccent.copy(alpha = 0.70f)
                    } else {
                        SonarColors.SonarCyan.copy(alpha = 0.28f)
                    },
                    startAngle = startAngleDeg,
                    sweepAngle = sweepAngleDeg,
                    useCenter = false,
                    topLeft = Offset(origin.x - r, origin.y - r),
                    size = Size(r * 2f, r * 2f),
                    style = Stroke(
                        width = if (isOuter) 3.dp.toPx() else 1.3.dp.toPx(),
                        pathEffect = if (isOuter) null else dashEffect
                    )
                )

                // 在右側邊緣繪製距離標尺文字（整數或最大距離）
                if (isOuter || (distVal * 2f).toInt() % 2 == 0) {
                    val label = String.format(Locale.US, "%.1fm", distVal)
                    val measured = textMeasurer.measure(
                        text = label,
                        style = TextStyle(
                            color = SonarColors.TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val labelAngleRad = (-30.0) * PI / 180.0
                    val lx = origin.x + (r * cos(labelAngleRad)).toFloat() + 6.dp.toPx()
                    val ly = origin.y + (r * sin(labelAngleRad)).toFloat() - measured.size.height / 2f
                    if (lx + measured.size.width <= size.width - 4.dp.toPx()) {
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(lx, ly)
                        )
                    }
                }
            }

            // 扇形輻射格線（-150°, -120°, -90°, -60°, -30°）
            val radialAngles = listOf(-150f, -120f, -90f, -60f, -30f)
            radialAngles.forEach { deg ->
                val rad = deg * PI / 180.0
                val end = Offset(
                    x = origin.x + (maxRadius * cos(rad)).toFloat(),
                    y = origin.y + (maxRadius * sin(rad)).toFloat()
                )
                val isEdge = deg == -150f || deg == -30f
                drawLine(
                    color = if (isEdge) {
                        activeAccent.copy(alpha = 0.70f)
                    } else {
                        SonarColors.SonarCyan.copy(alpha = 0.25f)
                    },
                    start = origin,
                    end = end,
                    strokeWidth = if (isEdge) 2.5.dp.toPx() else 1.2.dp.toPx()
                )
            }

            // 動態掃描光束（啟動時旋轉擺掃）
            if (isSonarRunning) {
                // 來回掃描或順時針掃描：-150° -> -30°
                val beamAngleDeg = startAngleDeg + sweepProgress * sweepAngleDeg
                val trailSweep = 26f
                val trailStart = (beamAngleDeg - trailSweep).coerceAtLeast(startAngleDeg)
                val trailSpan = (beamAngleDeg - trailStart).coerceAtLeast(1f)

                drawArc(
                    color = activeAccent.copy(alpha = 0.22f),
                    startAngle = trailStart,
                    sweepAngle = trailSpan,
                    useCenter = true,
                    topLeft = Offset(origin.x - maxRadius, origin.y - maxRadius),
                    size = Size(maxRadius * 2f, maxRadius * 2f)
                )

                val beamRad = beamAngleDeg * PI / 180.0
                val beamEnd = Offset(
                    x = origin.x + (maxRadius * cos(beamRad)).toFloat(),
                    y = origin.y + (maxRadius * sin(beamRad)).toFloat()
                )
                drawLine(
                    color = activeAccent,
                    start = origin,
                    end = beamEnd,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 偵測到障礙物時：繪製發光回波弧線與目標光點
            if (isSonarRunning && !isCalibrating && distanceMeters != null) {
                val ratio = (distanceMeters / maxDistanceMeters).coerceIn(0.06f, 1.0f)
                val echoRadius = maxRadius * ratio
                val arcAlpha = (0.55f + echoStrength * 0.45f) * pulseAlpha

                // 回波扇形反射弧
                drawArc(
                    color = activeAccent.copy(alpha = arcAlpha.coerceIn(0.3f, 1f)),
                    startAngle = -125f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(origin.x - echoRadius, origin.y - echoRadius),
                    size = Size(echoRadius * 2f, echoRadius * 2f),
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                )

                // 正前方目標發光圓點
                val blipCenter = Offset(origin.x, origin.y - echoRadius)
                drawCircle(
                    color = activeAccent.copy(alpha = 0.30f * pulseAlpha),
                    radius = 18.dp.toPx(),
                    center = blipCenter
                )
                drawCircle(
                    color = activeAccent,
                    radius = 7.dp.toPx(),
                    center = blipCenter
                )
            }

            // 底部手機發射源點
            drawCircle(
                color = SonarColors.TextPrimary,
                radius = 5.5.dp.toPx(),
                center = origin
            )
        }
    }
}
