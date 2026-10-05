package fr.zyviotv.player.ui

import android.content.res.Configuration
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceProfileTest {
    @Test
    fun phoneWidthUsesMobileProfile() {
        assertEquals(
            DeviceProfile.Mobile,
            resolveDeviceProfile(
                screenWidthDp = 390,
                uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
            ),
        )
    }

    @Test
    fun tabletWidthUsesTabletProfile() {
        assertEquals(
            DeviceProfile.Tablet,
            resolveDeviceProfile(
                screenWidthDp = 600,
                uiModeType = Configuration.UI_MODE_TYPE_NORMAL,
            ),
        )
    }

    @Test
    fun televisionModeWinsRegardlessOfWidth() {
        assertEquals(
            DeviceProfile.Television,
            resolveDeviceProfile(
                screenWidthDp = 540,
                uiModeType = Configuration.UI_MODE_TYPE_TELEVISION,
            ),
        )
    }
}
