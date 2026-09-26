package com.thirdeye.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = NexusBlue,
    secondary = NexusBlueDark,
    background = NexusBackground,
    surface = NexusSurface,
    onBackground = NexusTextPrimary,
    onSurface = NexusTextPrimary
)

private val DarkColors = darkColorScheme(
    primary = NexusBlue
)

@Composable
fun ThirdEyeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) {
            DarkColors
        } else {
            LightColors
        },
        typography = NexusEyeTypography,
        content = content
    )
}