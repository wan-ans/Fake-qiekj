package com.fakepangguai.qiekj.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF222222),
    onPrimary = Color.White,
    secondary = Color(0xFF4E6E5D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE7E7E1),
    onSecondaryContainer = Color(0xFF202020),
    background = Color(0xFFFAFAF8),
    onBackground = Color(0xFF202020),
    surface = Color(0xFFFAFAF8),
    onSurface = Color(0xFF202020),
    surfaceVariant = Color(0xFFEFEFE9),
    onSurfaceVariant = Color(0xFF6F6F68),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F6F2),
    surfaceContainer = Color(0xFFF2F2ED),
    surfaceContainerHigh = Color(0xFFEDEDE7),
    surfaceContainerHighest = Color(0xFFE8E8E2),
    outline = Color(0xFFE0E0DA),
    outlineVariant = Color(0xFFE8E8E2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8E8E2),
    onPrimary = Color(0xFF1B1B19),
    secondary = Color(0xFF9CBFAB),
    onSecondary = Color(0xFF12281C),
    secondaryContainer = Color(0xFF2E2E29),
    onSecondaryContainer = Color(0xFFEDEDE8),
    background = Color(0xFF141414),
    onBackground = Color(0xFFEDEDE8),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFEDEDE8),
    surfaceVariant = Color(0xFF2A2A26),
    onSurfaceVariant = Color(0xFFA6A6A0),
    surfaceContainerLowest = Color(0xFF0F0F0E),
    surfaceContainerLow = Color(0xFF181817),
    surfaceContainer = Color(0xFF1C1C1A),
    surfaceContainerHigh = Color(0xFF242422),
    surfaceContainerHighest = Color(0xFF2C2C29),
    outline = Color(0xFF3A3A36),
    outlineVariant = Color(0xFF2A2A26),
)

@Composable
fun DeviceControlTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
