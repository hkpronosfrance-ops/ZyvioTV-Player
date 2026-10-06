package fr.zyviotv.player.ui.player

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.settings.ParentalControlsRepository
import fr.zyviotv.player.data.settings.ParentalExceptionResult
import fr.zyviotv.player.data.settings.ProfilePreferences
import fr.zyviotv.player.shared.playback.PlaybackState
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun ParentalPlaybackGuard(
    streamUrl: String,
    playbackKind: String,
    playbackState: PlaybackState,
    isPlaying: Boolean,
    onBlockPlayback: () -> Unit,
    onResumePlayback: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val profilePreferences = remember(context) { ProfilePreferences(context) }
    val repository = remember(context) {
        ParentalControlsRepository(SecureSessionStore(context))
    }
    val scope = rememberCoroutineScope()

    val profileId = remember { profilePreferences.selectedProfileId() }
    val contentKey = remember(streamUrl, playbackKind) {
        playbackKind + ":" + sha256(streamUrl)
    }

    var blocked by remember(contentKey, profileId) { mutableStateOf(false) }
    var pin by remember(contentKey, profileId) { mutableStateOf("") }
    var error by remember(contentKey, profileId) { mutableStateOf<String?>(null) }
    var submitting by remember(contentKey, profileId) { mutableStateOf(false) }

    LaunchedEffect(profileId, contentKey) {
        if (profileId == null) return@LaunchedEffect
        repository.loadRuntimeState(profileId, contentKey)
            .getOrNull()
            ?.let { state ->
                if (state.parentalEnabled && state.isChild && state.blockedByTime) {
                    blocked = true
                    onBlockPlayback()
                }
            }
    }

    LaunchedEffect(
        profileId,
        contentKey,
        playbackState,
        isPlaying,
        blocked,
    ) {
        if (profileId == null) return@LaunchedEffect

        if (playbackState == PlaybackState.Ended) {
            repository.heartbeatScreenTime(
                profileId = profileId,
                playing = false,
                contentKey = contentKey,
            )
            repository.endRuntimeException(profileId, contentKey)
            return@LaunchedEffect
        }

        while (true) {
            val activelyPlaying =
                playbackState == PlaybackState.Ready &&
                    isPlaying &&
                    !blocked

            val heartbeat = repository.heartbeatScreenTime(
                profileId = profileId,
                playing = activelyPlaying,
                contentKey = contentKey,
            ).getOrNull()

            if (heartbeat?.blockedByTime == true) {
                blocked = true
                onBlockPlayback()
                break
            }

            delay(30_000L)
        }
    }

    if (blocked && profileId != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Temps d’écran atteint") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(4)
                        error = null
                    },
                    enabled = !submitting,
                    singleLine = true,
                    label = { Text("PIN parental") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                    ),
                    supportingText = {
                        error?.let { Text(it) }
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !submitting && pin.length == 4,
                    onClick = {
                        scope.launch {
                            submitting = true
                            when (
                                val result = repository.grantRuntimeException(
                                    profileId = profileId,
                                    pin = pin,
                                    contentKey = contentKey,
                                )
                            ) {
                                is ParentalExceptionResult.Granted -> {
                                    pin = ""
                                    error = null
                                    blocked = false
                                    onResumePlayback()
                                }
                                is ParentalExceptionResult.Failure -> {
                                    error = result.message
                                }
                            }
                            submitting = false
                        }
                    },
                ) {
                    Text("Continuer 30 min")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !submitting,
                    onClick = {
                        pin = ""
                        error = null
                    },
                ) {
                    Text("Rester en pause")
                }
            },
        )
    }
}

private fun sha256(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
