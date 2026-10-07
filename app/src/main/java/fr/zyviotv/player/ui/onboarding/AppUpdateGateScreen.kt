package fr.zyviotv.player.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.settings.AppUpdateKind
import fr.zyviotv.player.data.settings.AppUpdatePolicy
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect

@Composable
fun AppUpdateGateScreen(
    deviceProfile: DeviceProfile,
    policy: AppUpdatePolicy,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onSupport: () -> Unit = {},
    onSignOut: () -> Unit = {},
) {
    val isTv = deviceProfile == DeviceProfile.Television
    val mandatory = policy.kind == AppUpdateKind.Mandatory

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = if (isTv) 64.dp else 20.dp,
                vertical = if (isTv) 48.dp else 28.dp,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = if (isTv) 760.dp else 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (mandatory) {
                    "Mise à jour requise"
                } else {
                    "Une mise à jour est disponible"
                },
                style = if (isTv) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (mandatory) {
                    "Installez la dernière version pour continuer à utiliser ZYVIOTV."
                } else {
                    "Une nouvelle version de ZYVIOTV est disponible. Vous pouvez l’installer maintenant ou plus tard."
                },
                color = ZyvioTextSecondary,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onUpdate,
                enabled = policy.storeUrl != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusEffect(isTv, cornerRadiusDp = 12),
            ) {
                Text("Mettre à jour")
            }

            if (!mandatory) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onLater,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusEffect(isTv, cornerRadiusDp = 12),
                ) {
                    Text("Plus tard")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Ce rappel ne sera pas réaffiché avant 7 jours.",
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSupport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusEffect(isTv, cornerRadiusDp = 12),
                ) {
                    Text("Contacter le support")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusEffect(isTv, cornerRadiusDp = 12),
                ) {
                    Text("Se déconnecter")
                }
            }
        }
    }
}
