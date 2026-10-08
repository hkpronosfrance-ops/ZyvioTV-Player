package fr.zyviotv.player.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Claude Design D6 spacing/radius reference for Compose screens.
 * Use these canonical grid steps when aligning mobile/tablet layouts.
 */
object ZyvioSpace {
    val s1 = 4.dp
    val s2 = 8.dp
    val s3 = 12.dp
    val s4 = 16.dp
    val s5 = 20.dp
    val s6 = 24.dp
    val s7 = 28.dp
    val s8 = 32.dp
    val s10 = 40.dp
    val s12 = 48.dp
    val s16 = 64.dp
    val s24 = 96.dp
}

object ZyvioRadius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

object ZyvioMotion {
    const val pressMillis = 90
    const val focusMillis = 150
    const val rowScrollMillis = 220
    const val sidebarMillis = 220
    const val pageMillis = 260
    const val playerControlsHideMobileMillis = 4000
    const val playerControlsHideTvMillis = 5000
}
