package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import fr.zyviotv.player.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.sync.AndroidDeviceDescriptor
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.shared.sync.SyncResult
import fr.zyviotv.player.shared.sync.SyncedDevice
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSpace
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect
import kotlinx.coroutines.launch

@Composable
fun DevicesSettingsScreen(
    profile: DeviceProfile,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        SupabaseCloudSyncRepository(
            sessionStore = SecureSessionStore(context.applicationContext),
        )
    }
    val currentDevice = remember(context.applicationContext) {
        AndroidDeviceDescriptor.current(context.applicationContext)
    }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var devices by remember { mutableStateOf<List<SyncedDevice>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingName by remember { mutableStateOf("") }

    fun reload() {
        reloadToken += 1
    }

    LaunchedEffect(reloadToken) {
        loading = true
        error = null
        devices = repository.listDevices().getOrElse {
            error = "Impossible de charger vos appareils."
            emptyList()
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.nav_back))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Appareils",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "${devices.size} appareil" + if (devices.size > 1) "s" else "",
                    color = ZyvioTextSecondary,
                )
            }
            IconButton(onClick = { reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualiser")
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error.orEmpty(),
                        color = ZyvioTextSecondary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { reload() }) {
                        Text(stringResource(R.string.device_retry))
                    }
                }
            }

            devices.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.device_empty),
                    color = ZyvioTextSecondary,
                )
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = devices,
                    key = { it.id },
                ) { device ->
                    DeviceRow(
                        device = device,
                        isCurrent = device.deviceUid == currentDevice.deviceUid,
                        isTelevision = profile == DeviceProfile.Television,
                        isEditing = editingId == device.id,
                        editingName = editingName,
                        onEditingNameChanged = { editingName = it },
                        onStartEdit = {
                            editingId = device.id
                            editingName = device.displayName
                        },
                        onCancelEdit = {
                            editingId = null
                            editingName = ""
                        },
                        onSaveEdit = {
                            scope.launch {
                                when (repository.renameDevice(device.id, editingName)) {
                                    SyncResult.Success -> {
                                        editingId = null
                                        editingName = ""
                                        reload()
                                    }
                                    is SyncResult.Failure -> {
                                        error = "Impossible de renommer cet appareil."
                                    }
                                }
                            }
                        },
                        onDelete = {
                            if (!device.deviceUid.equals(currentDevice.deviceUid)) {
                                scope.launch {
                                    when (repository.deleteDevice(device.id)) {
                                        SyncResult.Success -> reload()
                                        is SyncResult.Failure -> {
                                            error = context.getString(R.string.device_disconnect_error)
                                        }
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: SyncedDevice,
    isCurrent: Boolean,
    isTelevision: Boolean,
    isEditing: Boolean,
    editingName: String,
    onEditingNameChanged: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    onSaveEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusEffect(isTelevision, cornerRadiusDp = 16),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(ZyvioSpace.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = ZyvioSpace.s3),
            ) {
                if (isEditing) {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = onEditingNameChanged,
                        singleLine = true,
                        label = { Text(stringResource(R.string.device_name)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.padding(top = ZyvioSpace.s2),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(onClick = onCancelEdit) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            enabled = editingName.isNotBlank(),
                            onClick = onSaveEdit,
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                } else {
                    Text(
                        text = device.displayName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = buildString {
                            append(device.platform.name)
                            device.appVersion?.let {
                                append(" • v")
                                append(it)
                            }
                            if (isCurrent) {
                                append(" • Cet appareil")
                            }
                        },
                        color = ZyvioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (!isEditing) {
                IconButton(onClick = onStartEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.device_rename))
                }
                IconButton(
                    enabled = !isCurrent,
                    onClick = onDelete,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.device_disconnect))
                }
            }
        }
    }
}
