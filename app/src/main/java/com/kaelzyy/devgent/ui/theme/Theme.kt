package com.kaelzyy.devgent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DevGentDarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    secondary = AccentBlueLight,
    onSecondary = Color.Black,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Card,
    onSurfaceVariant = TextDim,
    outline = Border,
    outlineVariant = Border,
    scrim = Scrim,
)

@Composable
fun DevGentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DevGentDarkColorScheme,
        typography = DevGentTypography,
        content = content,
    )
}
