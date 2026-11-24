package com.nesperoudelmonico.projectvideo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Paleta oscura tipo visor de cámara: fondo casi negro y acento naranja "REC". */
private val CameraColors = darkColorScheme(
    primary = Color(0xFFFF5A1F),
    onPrimary = Color(0xFF1A0A00),
    secondary = Color(0xFF7DD3FC),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0E1116),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1B222C),
    onSurfaceVariant = Color(0xFF9DA7B3),
    surfaceContainer = Color(0xFF161B22),
    error = Color(0xFFFF6B6B),
)

@Composable
fun ProjectVideoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CameraColors, content = content)
}
