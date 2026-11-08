package com.tim.d2a.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Ink = Color(0xFF172033)
private val Muted = Color(0xFF68748A)
private val Blue = Color(0xFF315CE8)
private val BlueSoft = Color(0xFFE7EDFF)
private val Canvas = Color(0xFFF8F9FC)
private val Surface = Color(0xFFFFFFFF)
private val Green = Color(0xFF14804A)
private val GreenSoft = Color(0xFFE6F6EE)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = BlueSoft,
    onPrimaryContainer = Color(0xFF18398F),
    secondary = Green,
    onSecondary = Color.White,
    secondaryContainer = GreenSoft,
    onSecondaryContainer = Color(0xFF07532D),
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Color(0xFFF0F2F7),
    onSurfaceVariant = Muted,
    outline = Color(0xFFD8DFEB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C5FF),
    onPrimary = Color(0xFF132B84),
    primaryContainer = Color(0xFF2944A8),
    onPrimaryContainer = Color(0xFFE0E6FF),
    secondary = Color(0xFF8DE1B3),
    onSecondary = Color(0xFF00391D),
    background = Color(0xFF101522),
    onBackground = Color(0xFFE9EDF6),
    surface = Color(0xFF171D2A),
    onSurface = Color(0xFFE9EDF6),
    surfaceVariant = Color(0xFF252C3B),
    onSurfaceVariant = Color(0xFFB3BDCF),
    outline = Color(0xFF3D475B),
)

@Composable
fun DictAnkiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

