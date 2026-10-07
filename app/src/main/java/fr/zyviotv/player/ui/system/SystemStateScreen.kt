package fr.zyviotv.player.ui.system

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
import fr.zyviotv.player.data.system.SystemGateState
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect

@Composable
fun SystemStateScreen(
    deviceProfile: DeviceProfile,
    state: SystemGateState,
    onContinue: () -> Unit,
    onSupport: () -> Unit,
    onSignOut: () -> Unit,
) {
    val isTv = deviceProfile == DeviceProfile.Television
    val planned = state as? SystemGateState.PlannedMaintenance
    val blocking = state is SystemGateState.BlockingMaintenance ||
        state is SystemGateState.AccountSuspended

    val title = when (state) {
        is SystemGateState.PlannedMaintenance -> "Maintenance programmée"
        is SystemGateState.BlockingMaintenance -> "Maintenance en cours"
        is SystemGateState.AccountSuspended -> "Compte suspendu"
        SystemGateState.Normal -> ""
    }
    val fallbackMessage = when (state) {
        is SystemGateState.PlannedMaintenance ->
            "Une maintenance est prévue prochainement. Vous pouvez continuer à utiliser ZYVIOTV."
        is SystemGateState.BlockingMaintenance ->
            "Le service est momentanément indisponible pendant la maintenance."
        is SystemGateState.AccountSuspended ->
            "L’accès au service est actuellement suspendu pour ce compte."
        SystemGateState.Normal -> ""
    }
    val customMessage = when (state) {
        is SystemGateState.PlannedMaintenance -> state.message
        is SystemGateState.BlockingMaintenance -> state.message
        is SystemGateState.AccountSuspended -> state.message
        SystemGateState.Normal -> null
    }

    if (state == SystemGateState.Normal) return

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
                text = title,
                style = if (isTv) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = customMessage ?: fallbackMessage,
                color = ZyvioTextSecondary,
            )
            Spacer(Modifier.height(22.dp))

            if (planned != null) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvFocusEffect(isTv, cornerRadiusDp = 12),
                ) {
                    Text("Continuer")
                }
            }

            if (blocking) {
                Button(
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
