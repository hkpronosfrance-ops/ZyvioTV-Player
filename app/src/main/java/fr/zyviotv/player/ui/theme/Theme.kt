package fr.zyviotv.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ZyvioColorScheme = darkColorScheme(
    primary = ZyvioRed,
    onPrimary = ZyvioTextPrimary,
    primaryContainer = ZyvioRedTint,
    onPrimaryContainer = ZyvioTextPrimary,
    secondary = ZyvioRedBright,
    onSecondary = ZyvioTextPrimary,
    background = ZyvioCanvas,
    onBackground = ZyvioTextPrimary,
    surface = ZyvioSurface1,
    onSurface = ZyvioTextPrimary,
    surfaceVariant = ZyvioSurface2,
    onSurfaceVariant = ZyvioTextSecondary,
    outline = ZyvioTextTertiary,
    error = ZyvioRedBright,
)

@Composable
fun ZyvioTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZyvioColorScheme,
        typography = ZyvioTypography,
        content = content,
    )
}
