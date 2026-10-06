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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.shared.sync.SyncResult
import fr.zyviotv.player.shared.sync.SyncedPlaylist
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect
import kotlinx.coroutines.launch

@Composable
fun PlaylistSettingsScreen(
    profile: DeviceProfile,
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        SupabaseCloudSyncRepository(
            sessionStore = SecureSessionStore(context.applicationContext),
        )
    }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var playlists by remember { mutableStateOf<List<SyncedPlaylist>>(emptyList()) }
    var reloadToken by remember { mutableStateOf(0) }

    fun reload() {
        reloadToken += 1
    }

    LaunchedEffect(reloadToken) {
        loading = true
        error = null
        playlists = repository.listPlaylists().getOrElse {
            error = "Impossible de charger vos playlists."
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
                Text("Retour")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "${playlists.size} / 10",
                    color = ZyvioTextSecondary,
                )
            }
            TextButton(onClick = onAddPlaylist) {
                Text("Ajouter")
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
                        Text("Réessayer")
                    }
                }
            }

            playlists.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Aucune playlist configurée.",
                    color = ZyvioTextSecondary,
                )
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = playlists.sortedBy { it.priority },
                    key = { it.id },
                ) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        isTelevision = profile == DeviceProfile.Television,
                        canMoveUp = playlist.priority > 1,
                        canMoveDown = playlist.priority < playlists.size.coerceAtMost(10),
                        onToggle = { enabled ->
                            scope.launch {
                                when (repository.updatePlaylistEnabled(playlist.id, enabled)) {
                                    SyncResult.Success -> {
                                        playlists = playlists.map {
                                            if (it.id == playlist.id) it.copy(isEnabled = enabled) else it
                                        }
                                        onChanged()
                                    }
                                    is SyncResult.Failure -> error = "Impossible de modifier cette playlist."
                                }
                            }
                        },
                        onMoveUp = {
                            val ordered = playlists.sortedBy { it.priority }
                            val index = ordered.indexOfFirst { it.id == playlist.id }
                            if (index > 0) {
                                val previous = ordered[index - 1]
                                scope.launch {
                                    val a = repository.updatePlaylistPriority(playlist.id, previous.priority)
                                    val b = repository.updatePlaylistPriority(previous.id, playlist.priority)
                                    if (a is SyncResult.Success && b is SyncResult.Success) {
                                        reload()
                                        onChanged()
                                    }
                                }
                            }
                        },
                        onMoveDown = {
                            val ordered = playlists.sortedBy { it.priority }
                            val index = ordered.indexOfFirst { it.id == playlist.id }
                            if (index >= 0 && index < ordered.lastIndex) {
                                val next = ordered[index + 1]
                                scope.launch {
                                    val a = repository.updatePlaylistPriority(playlist.id, next.priority)
                                    val b = repository.updatePlaylistPriority(next.id, playlist.priority)
                                    if (a is SyncResult.Success && b is SyncResult.Success) {
                                        reload()
                                        onChanged()
                                    }
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                when (repository.deletePlaylist(playlist.id)) {
                                    SyncResult.Success -> {
                                        reload()
                                        onChanged()
                                    }
                                    is SyncResult.Failure -> error = "Impossible de supprimer cette playlist."
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
private fun PlaylistRow(
    playlist: SyncedPlaylist,
    isTelevision: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Text(
                    text = "P${playlist.priority}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = playlist.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = buildString {
                        append(if (playlist.providerType == "xtream") "Xtream Codes" else "M3U")
                        append(" • ")
                        append(
                            if (playlist.secretStatus == "configured") {
                                if (playlist.isEnabled) "À jour" else "Désactivée"
                            } else {
                                "Identifiants invalides"
                            },
                        )
                    },
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            IconButton(
                enabled = canMoveUp,
                onClick = onMoveUp,
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Monter")
            }
            IconButton(
                enabled = canMoveDown,
                onClick = onMoveDown,
            ) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Descendre")
            }
            Switch(
                checked = playlist.isEnabled,
                onCheckedChange = onToggle,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer")
            }
        }
    }
}
