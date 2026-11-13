package com.freddychoudja.vpntethering.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ColorScheme = lightColorScheme(
    primary = Blue,
    onPrimary = Mist,
    secondary = BlueDark,
    onSecondary = Mist,
    background = Mist,
    onBackground = Ink,
    surface = Mist,
    onSurface = Ink,
    error = Danger
)

@Composable
fun VpnTetheringTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = Typography,
        content = content
    )
}
