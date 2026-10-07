package fr.zyviotv.player.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.AppIdentity
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.auth.SupabaseAuthRepository
import kotlinx.coroutines.delay

@Composable
fun StartupSplashScreen(
    onSessionReady: () -> Unit,
    onAuthRequired: () -> Unit,
) {
    val appContext = LocalContext.current.applicationContext
    val coordinator = remember(appContext) {
        StartupSessionCoordinator(
            SupabaseAuthRepository(
                sessionStore = SecureSessionStore(appContext),
            ),
        )
    }
    var attemptToken by remember { mutableIntStateOf(0) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var connectionRequired by remember { mutableStateOf(false) }

    LaunchedEffect(attemptToken) {
        connectionRequired = false
        statusText = null
        val statusJobStart = System.currentTimeMillis()

        val result = coordinator.restore()

        // No artificial minimum splash duration. The status copy below only
        // appears when restoration genuinely takes long enough.
        when (result) {
            StartupSessionResult.SessionReady -> onSessionReady()
            StartupSessionResult.AuthRequired -> onAuthRequired()
            StartupSessionResult.ConnectionRequired -> {
                connectionRequired = true
                statusText = "Connexion nécessaire"
            }
        }
    }

    LaunchedEffect(attemptToken, connectionRequired) {
        if (connectionRequired) return@LaunchedEffect
        delay(1_500L)
        if (!connectionRequired) statusText = "Connexion…"
        delay(2_500L)
        if (!connectionRequired) statusText = "Cela prend un peu plus de temps que prévu…"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = AppIdentity.name.removeSuffix(" Player").uppercase(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Black,
        )
        Text(
            text = "PLAYER",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = statusText ?: AppIdentity.tagline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        if (connectionRequired) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Les données locales disponibles ne suffisent pas encore pour ouvrir l’application hors ligne.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = { attemptToken += 1 }) {
                Text("Réessayer")
            }
        }
    }
}
