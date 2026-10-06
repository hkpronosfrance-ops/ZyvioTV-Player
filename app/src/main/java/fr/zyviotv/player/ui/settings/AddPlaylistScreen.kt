package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.m3u.AndroidM3uClient
import fr.zyviotv.player.data.sync.SupabaseCloudSyncRepository
import fr.zyviotv.player.data.xtream.AndroidXtreamClient
import fr.zyviotv.player.shared.m3u.M3uImportResult
import fr.zyviotv.player.shared.m3u.M3uSource
import fr.zyviotv.player.shared.sync.PlaylistSecret
import fr.zyviotv.player.shared.sync.SyncResult
import fr.zyviotv.player.shared.xtream.XtreamConnectionResult
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import java.net.URL
import kotlinx.coroutines.launch

private enum class AddPlaylistType {
    Xtream,
    M3u,
}

@Composable
fun AddPlaylistScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        SupabaseCloudSyncRepository(
            sessionStore = SecureSessionStore(context.applicationContext),
        )
    }
    val xtreamClient = remember { AndroidXtreamClient() }
    val m3uClient = remember { AndroidM3uClient() }
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf(AddPlaylistType.Xtream) }
    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                enabled = !busy,
                onClick = onBack,
            ) {
                Text("Retour")
            }
            Text(
                text = "Ajouter une playlist",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (type == AddPlaylistType.Xtream) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { type = AddPlaylistType.Xtream },
                ) {
                    Text("Xtream Codes")
                }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { type = AddPlaylistType.M3u },
                ) {
                    Text("M3U")
                }
            } else {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = { type = AddPlaylistType.Xtream },
                ) {
                    Text("Xtream Codes")
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { type = AddPlaylistType.M3u },
                ) {
                    Text("M3U")
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            enabled = !busy,
            singleLine = true,
            label = { Text("Nom") },
            placeholder = { Text("Fournisseur principal") },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        when (type) {
            AddPlaylistType.Xtream -> {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Adresse du serveur") },
                    placeholder = { Text("https://") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Nom d’utilisateur") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("Mot de passe") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            AddPlaylistType.M3u -> {
                OutlinedTextField(
                    value = m3uUrl,
                    onValueChange = { m3uUrl = it },
                    enabled = !busy,
                    singleLine = true,
                    label = { Text("URL de la playlist M3U") },
                    placeholder = { Text("https://") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        message?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(18.dp))

        Button(
            enabled = !busy && name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    busy = true
                    message = null

                    val secret: PlaylistSecret
                    val providerType: String
                    val serverHost: String?
                    val urlHint: String?

                    when (type) {
                        AddPlaylistType.Xtream -> {
                            val credentials = XtreamCredentials(
                                serverUrl = serverUrl.trim(),
                                username = username.trim(),
                                password = password,
                            )
                            when (val result = xtreamClient.authenticate(credentials)) {
                                is XtreamConnectionResult.Failure -> {
                                    message = result.message
                                    busy = false
                                    return@launch
                                }
                                is XtreamConnectionResult.Success -> Unit
                            }

                            secret = PlaylistSecret.Xtream(
                                serverUrl = credentials.serverUrl,
                                username = credentials.username,
                                password = credentials.password,
                            )
                            providerType = "xtream"
                            serverHost = safeOrigin(credentials.serverUrl)
                            urlHint = null
                        }

                        AddPlaylistType.M3u -> {
                            val cleanUrl = m3uUrl.trim()
                            when (
                                val result = m3uClient.import(
                                    source = M3uSource(cleanUrl),
                                    maxEntries = 5,
                                )
                            ) {
                                is M3uImportResult.Failure -> {
                                    message = result.message
                                    busy = false
                                    return@launch
                                }
                                is M3uImportResult.Success -> Unit
                            }

                            secret = PlaylistSecret.M3u(cleanUrl)
                            providerType = "m3u"
                            serverHost = null
                            urlHint = safeHost(cleanUrl)
                        }
                    }

                    val existing = repository.listPlaylists().getOrElse {
                        message = "Impossible de préparer l’enregistrement de la playlist."
                        busy = false
                        return@launch
                    }
                    if (existing.size >= 10) {
                        message = "La limite de 10 playlists est atteinte."
                        busy = false
                        return@launch
                    }

                    val nextPriority = ((existing.maxOfOrNull { it.priority } ?: 0) + 1)
                        .coerceAtMost(10)
                    val playlistId = repository.createPlaylist(
                        name = name.trim(),
                        providerType = providerType,
                        priority = nextPriority,
                        serverHost = serverHost,
                        playlistUrlHint = urlHint,
                    ).getOrElse {
                        message = "Impossible d’enregistrer la playlist."
                        busy = false
                        return@launch
                    }

                    when (repository.setPlaylistSecret(playlistId, secret)) {
                        SyncResult.Success -> {
                            busy = false
                            onSaved()
                        }
                        is SyncResult.Failure -> {
                            repository.deletePlaylist(playlistId)
                            message = "La playlist a été testée, mais ses identifiants n’ont pas pu être sécurisés."
                            busy = false
                        }
                    }
                }
            },
        ) {
            if (busy) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text("Tester et enregistrer")
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = "Les identifiants sont testés puis enregistrés de façon sécurisée. Ils ne sont jamais affichés en clair dans les réglages.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun safeOrigin(value: String): String? =
    runCatching {
        val url = URL(value)
        buildString {
            append(url.protocol)
            append("://")
            append(url.host)
            if (url.port != -1 && url.port != url.defaultPort) {
                append(":")
                append(url.port)
            }
        }
    }.getOrNull()

private fun safeHost(value: String): String? =
    runCatching { URL(value).host }.getOrNull()
