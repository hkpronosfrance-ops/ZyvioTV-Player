package fr.zyviotv.player.ui.catalog

import androidx.compose.ui.unit.Dp
import fr.zyviotv.player.ui.DeviceProfile
import androidx.compose.ui.unit.dp

/**
 * Claude D6 catalog poster sizing. GridCells.Adaptive uses the actual available
 * width, including split-screen/tablet windows and the TV navigation rail.
 * Keep the 2:3 poster ratio in the card itself.
 */
internal fun catalogPosterMinimumWidth(profile: DeviceProfile): Dp = when (profile) {
    DeviceProfile.Mobile -> 128.dp
    DeviceProfile.Tablet -> 168.dp
    DeviceProfile.Television -> 228.dp
}
