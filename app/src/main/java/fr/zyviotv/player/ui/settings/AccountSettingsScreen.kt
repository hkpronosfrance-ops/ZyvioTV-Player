package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import fr.zyviotv.player.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.ui.theme.ZyvioSpace
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.auth.SupabaseAuthRepository
import fr.zyviotv.player.shared.auth.AuthResult
import kotlinx.coroutines.launch

@Composable
fun AccountSettingsScreen(
    onSignedOut: () -> Unit,
    onOpenPlaylists: () -> Unit = {},
    onOpenDevices: () -> Unit = {},
    onOpenPlaybackData: () -> Unit = {},
    onOpenCache: () -> Unit = {},
    onOpenParentalControls: () -> Unit = {},
    onOpenProfiles: () -> Unit = {},
    onSwitchProfile: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember {
        SupabaseAuthRepository(
            sessionStore = SecureSessionStore(context.applicationContext),
        )
    }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ZyvioSpace.s4, vertical = ZyvioSpace.s6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.settings_account_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_account_storage),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            onClick = onOpenPlaylists,
        ) {
            Text(stringResource(R.string.playlist_title))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            onClick = onOpenDevices,
        ) {
            Text(stringResource(R.string.settings_devices))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            onClick = onOpenPlaybackData,
        ) {
            Text(stringResource(R.string.settings_playback_data))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            onClick = onOpenCache,
        ) {
            Text(stringResource(R.string.settings_data_cache))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            onClick = onOpenParentalControls,
        ) {
            Text(stringResource(R.string.settings_parental))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            onClick = onOpenProfiles,
        ) {
            Text(stringResource(R.string.settings_profiles))
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            onClick = onSwitchProfile,
        ) {
            Text(stringResource(R.string.settings_switch_profile))
        }

        Spacer(Modifier.height(12.dp))

        message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
        }

        Button(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            enabled = !isLoading,
            onClick = {
                scope.launch {
                    isLoading = true
                    when (val result = repository.signOut()) {
                        AuthResult.Success -> onSignedOut()
                        is AuthResult.Failure -> message = result.message
                    }
                    isLoading = false
                }
            },
        ) {
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.settings_sign_out))
            }
        }
    }
}
