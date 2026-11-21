package com.zeno.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette sampled from UI Designs/Screens (purple set chosen per review decision D-06/C6).
private val Purple = Color(0xFF8B7CF6)
private val PurpleDeep = Color(0xFF5B4BC4)
private val Night = Color(0xFF0D0E1A)
private val NightSurface = Color(0xFF171A2B)
private val NightSurfaceHigh = Color(0xFF20243A)
private val Mist = Color(0xFFE6E4F5)
private val MistMuted = Color(0xFFA5A3BD)

/** Status green used only for states that Android actually reported as active. */
val StatusActive = Color(0xFF4CC38A)
val StatusWarning = Color(0xFFE8B04B)

private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color(0xFF14102E),
    primaryContainer = PurpleDeep,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF7FA8FF),
    background = Night,
    onBackground = Mist,
    surface = NightSurface,
    onSurface = Mist,
    surfaceVariant = NightSurfaceHigh,
    onSurfaceVariant = MistMuted,
    surfaceContainer = NightSurface,
    surfaceContainerHigh = NightSurfaceHigh,
    outline = Color(0xFF3A3E58),
    error = Color(0xFFFF7A7A),
)

private val LightColors = lightColorScheme(
    primary = PurpleDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E2FF),
    onPrimaryContainer = Color(0xFF1C1453),
    background = Color(0xFFFBFAFF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEDEBF7),
    onSurfaceVariant = Color(0xFF4A4860),
)

/**
 * Dark-first with light support (docs/07 "Design system"). The app always uses dark for now; a theme
 * setting (dark / light / follow system via [isSystemInDarkTheme]) is planned for M9 polish.
 */
@Composable
fun ZenoTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
