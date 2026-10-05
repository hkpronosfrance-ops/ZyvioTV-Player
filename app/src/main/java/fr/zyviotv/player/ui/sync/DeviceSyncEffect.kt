package fr.zyviotv.player.ui.sync

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.sync.AndroidDeviceDescriptor
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository

@Composable
fun DeviceSyncEffect() {
    val context = LocalContext.current
    val repository = remember {
        SupabaseCloudSyncRepository(
            sessionStore = SecureSessionStore(context.applicationContext),
        )
    }
    val device = remember {
        AndroidDeviceDescriptor.current(context.applicationContext)
    }

    LaunchedEffect(device.deviceUid) {
        repository.registerDevice(device)
    }
}
