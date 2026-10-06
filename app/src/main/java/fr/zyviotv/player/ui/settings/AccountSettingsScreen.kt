package fr.zyviotv.player.ui.settings

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
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Mon compte",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Votre session ZyvioTV Player est stockée de façon chiffrée sur cet appareil.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(0.6f),
            onClick = onOpenPlaylists,
        ) {
            Text("Playlists")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(0.6f),
            onClick = onOpenDevices,
        ) {
            Text("Appareils")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(0.6f),
            onClick = onOpenPlaybackData,
        ) {
            Text("Lecture et données")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(0.6f),
            onClick = onOpenCache,
        ) {
            Text("Données et cache")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(0.6f),
            onClick = onOpenParentalControls,
        ) {
            Text("Contrôle parental")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(0.6f),
            onClick = onOpenProfiles,
        ) {
            Text("Profils")
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(0.6f),
            onClick = onSwitchProfile,
        ) {
            Text("Changer de profil")
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
            modifier = Modifier.fillMaxWidth(0.6f),
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
                Text("Se déconnecter")
            }
        }
    }
}
