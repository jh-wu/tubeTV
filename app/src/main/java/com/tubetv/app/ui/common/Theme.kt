package com.tubetv.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val colors = darkColorScheme(
    primary = Color(0xFFFF0033),
    onPrimary = Color.White,
    background = Color(0xFF0F0F0F),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFF1F1F1),
)

@Composable
fun TubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
