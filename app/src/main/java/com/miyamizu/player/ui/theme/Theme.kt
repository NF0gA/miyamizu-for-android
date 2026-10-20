package com.miyamizu.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MiyamizuDarkColors = darkColorScheme(
    primary = Color(0xFF35C9F3),
    onPrimary = Color(0xFF002B38),
    secondary = Color(0xFF8DB6D8),
    background = Color(0xFF0B1725),
    surface = Color(0xFF102235),
    onBackground = Color(0xFFE8F1FA),
    onSurface = Color(0xFFE8F1FA)
)

private val MiyamizuLightColors = lightColorScheme(
    primary = Color(0xFF006A86),
    secondary = Color(0xFF42677F)
)

@Composable
fun MiyamizuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) MiyamizuDarkColors else MiyamizuLightColors,
        content = content
    )
}
