package com.batsonar.haptic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object SonarColors {
    val BackgroundBlack = Color(0xFF000000)
    val SurfaceCard = Color(0xFF0A1419)
    val SurfaceElevated = Color(0xFF102028)
    val BorderMuted = Color(0xFF21404D)

    val SonarCyan = Color(0xFF00FFAA)
    val SonarElectricBlue = Color(0xFF00E5FF)
    val AmberCaution = Color(0xFFFFB300)
    val CrimsonDanger = Color(0xFFFF3B30)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB8D4DE)
}

private val HighContrastDarkScheme = darkColorScheme(
    primary = SonarColors.SonarCyan,
    onPrimary = SonarColors.BackgroundBlack,
    secondary = SonarColors.SonarElectricBlue,
    onSecondary = SonarColors.BackgroundBlack,
    tertiary = SonarColors.AmberCaution,
    background = SonarColors.BackgroundBlack,
    onBackground = SonarColors.TextPrimary,
    surface = SonarColors.SurfaceCard,
    onSurface = SonarColors.TextPrimary,
    surfaceVariant = SonarColors.SurfaceElevated,
    onSurfaceVariant = SonarColors.TextSecondary,
    error = SonarColors.CrimsonDanger,
    onError = SonarColors.TextPrimary
)

private val AccessibleTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = SonarColors.TextPrimary
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = SonarColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        color = SonarColors.TextPrimary
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        color = SonarColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        color = SonarColors.TextSecondary
    )
)

@Composable
fun BatSonarHapticTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HighContrastDarkScheme,
        typography = AccessibleTypography,
        content = content
    )
}
