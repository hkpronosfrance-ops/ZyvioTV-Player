package fr.zyviotv.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ZyvioColorScheme = darkColorScheme(
    primary = ZyvioRed,
    onPrimary = ZyvioWhite,
    secondary = ZyvioRedBright,
    background = ZyvioBlack,
    onBackground = ZyvioWhite,
    surface = ZyvioSurface,
    onSurface = ZyvioWhite,
    surfaceVariant = ZyvioSurfaceElevated,
    onSurfaceVariant = ZyvioGray,
)

@Composable
fun ZyvioTVTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZyvioColorScheme,
        typography = ZyvioTypography,
        content = content,
    )
}
