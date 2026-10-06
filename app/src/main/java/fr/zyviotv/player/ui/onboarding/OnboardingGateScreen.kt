package fr.zyviotv.player.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.catalog.ProviderCatalogState
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect

@Composable
fun OnboardingGateScreen(
    deviceProfile: DeviceProfile,
    state: ProviderCatalogState,
    hasConfiguredPlaylist: Boolean,
    resumed: Boolean,
    onAddPlaylist: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    val isTv = deviceProfile == DeviceProfile.Television

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTv) 64.dp else 20.dp,
                vertical = if (isTv) 48.dp else 28.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = if (isTv) 920.dp else 620.dp),
        ) {
            Text(
                text = "ZYVIOTV",
                color = MaterialTheme.colorScheme.primary,
                style = if (isTv) {
                    MaterialTheme.typography.headlineLarge
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Black,
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = if (resumed) "Reprenons là où vous en étiez" else "Préparons votre expérience",
                style = if (isTv) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                fontWeight = FontWeight.ExtraBold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Une seule famille de contenu prête suffit pour accéder à l’Accueil.",
                color = ZyvioTextSecondary,
            )

            Spacer(Modifier.height(24.dp))

            if (!hasConfiguredPlaylist) {
                EmptyPlaylistCard(
                    isTv = isTv,
                    onAddPlaylist = onAddPlaylist,
                    onContinue = onContinue,
                )
                return@Column
            }

            when (state) {
                ProviderCatalogState.Loading -> {
                    SyncCard(
                        title = "Synchronisation en cours",
                        body = "Nous préparons vos chaînes, films et séries.",
                        isTv = isTv,
                    )
                }

                is ProviderCatalogState.Error -> {
                    SyncErrorCard(
                        message = state.message,
                        isTv = isTv,
                        onRetry = onRetry,
                        onContinue = onContinue,
                    )
                }

                is ProviderCatalogState.Empty -> {
                    EmptyPlaylistCard(
                        isTv = isTv,
                        onAddPlaylist = onAddPlaylist,
                        onContinue = onContinue,
                    )
                }

                is ProviderCatalogState.Ready -> {
                    val hasLive = state.snapshot.liveChannels.isNotEmpty()
                    val hasMovies = state.snapshot.movies.isNotEmpty()
                    val hasSeries = state.snapshot.series.isNotEmpty()
                    val anyReady = hasLive || hasMovies || hasSeries

                    SyncStatusCard(
                        isTv = isTv,
                        liveReady = hasLive,
                        moviesReady = hasMovies,
                        seriesReady = hasSeries,
                        onContinue = onContinue,
                        allowContinue = anyReady || (
                            state.snapshot.liveChannels.isEmpty() &&
                                state.snapshot.movies.isEmpty() &&
                                state.snapshot.series.isEmpty()
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyPlaylistCard(
    isTv: Boolean,
    onAddPlaylist: () -> Unit,
    onContinue: () -> Unit,
) {
    Surface(
        color = ZyvioSurface1,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(if (isTv) 28.dp else 20.dp)) {
            Text(
                "Aucune playlist configurée",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Ajoutez une playlist Xtream Codes ou M3U. Vous pouvez aussi continuer vers un Accueil vide.",
                color = ZyvioTextSecondary,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusEffect(isTv, cornerRadiusDp = 12),
                    onClick = onAddPlaylist,
                ) {
                    Text("Ajouter une playlist")
                }
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusEffect(isTv, cornerRadiusDp = 12),
                    onClick = onContinue,
                ) {
                    Text("Continuer quand même")
                }
            }
        }
    }
}

@Composable
private fun SyncCard(
    title: String,
    body: String,
    isTv: Boolean,
) {
    Surface(
        color = ZyvioSurface1,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(if (isTv) 28.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = ZyvioTextSecondary)
        }
    }
}

@Composable
private fun SyncErrorCard(
    message: String,
    isTv: Boolean,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Surface(
        color = ZyvioSurface1,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(if (isTv) 28.dp else 20.dp)) {
            Text(
                "Synchronisation incomplète",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(message, color = ZyvioTextSecondary)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusEffect(isTv, cornerRadiusDp = 12),
                    onClick = onRetry,
                ) {
                    Text("Réessayer")
                }
                OutlinedButton(
                    modifier = Modifier
                        .weight(1f)
                        .tvFocusEffect(isTv, cornerRadiusDp = 12),
                    onClick = onContinue,
                ) {
                    Text("Accéder à l’Accueil")
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(
    isTv: Boolean,
    liveReady: Boolean,
    moviesReady: Boolean,
    seriesReady: Boolean,
    allowContinue: Boolean,
    onContinue: () -> Unit,
) {
    Surface(
        color = ZyvioSurface1,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(if (isTv) 28.dp else 20.dp)) {
            Text(
                "Votre catalogue se prépare",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(14.dp))

            StatusRow("Chaînes TV", liveReady)
            StatusRow("Films", moviesReady)
            StatusRow("Séries", seriesReady)

            Spacer(Modifier.height(18.dp))
            Button(
                enabled = allowContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusEffect(isTv, cornerRadiusDp = 12),
                onClick = onContinue,
            ) {
                Text(if (allowContinue) "Accéder à l’Accueil" else "Préparation…")
            }

            if (allowContinue) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Le reste continuera en arrière-plan si nécessaire.",
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    ready: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(
            if (ready) "Prêt" else "En cours",
            color = if (ready) MaterialTheme.colorScheme.primary else ZyvioTextSecondary,
        )
    }
}
