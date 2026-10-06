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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.settings.ParentalControlsRepository
import fr.zyviotv.player.data.settings.ParentalSettings
import fr.zyviotv.player.data.settings.ParentalWriteResult
import kotlinx.coroutines.launch

@Composable
fun ParentalControlsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        ParentalControlsRepository(
            SecureSessionStore(context.applicationContext),
        )
    }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf<ParentalSettings?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var newPin by remember { mutableStateOf("") }
    var currentPin by remember { mutableStateOf("") }
    var settingsPin by remember { mutableStateOf("") }
    var maxAge by remember { mutableStateOf<Int?>(null) }
    var hideLocked by remember { mutableStateOf(false) }
    var scheduleEnabled by remember { mutableStateOf(false) }
    var dailyLimit by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        val loaded = repository.loadSettings().getOrElse {
            error = "Impossible de charger le contrôle parental."
            loading = false
            return
        }
        settings = loaded
        maxAge = loaded.maxAge
        hideLocked = loaded.hideLocked
        scheduleEnabled = loaded.scheduleEnabled
        dailyLimit = loaded.dailyLimitMinutes?.toString().orEmpty()
        loading = false
    }

    LaunchedEffect(Unit) {
        load()
    }

    if (loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
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
                    text = "Contrôle parental",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Protégé par un PIN de compte à 4 chiffres",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { load() } }) {
                Text("Réessayer")
            }
            return
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = if (settings?.hasPin == true) "Modifier le PIN" else "Créer le PIN",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        if (settings?.hasPin == true) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = currentPin,
                onValueChange = { currentPin = it.filter(Char::isDigit).take(4) },
                label = { Text("PIN actuel") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = newPin,
            onValueChange = { newPin = it.filter(Char::isDigit).take(4) },
            label = { Text("Nouveau PIN") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            enabled = !busy && newPin.length == 4 &&
                (settings?.hasPin != true || currentPin.length == 4),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    busy = true
                    when (
                        val result = repository.setPin(
                            newPin = newPin,
                            currentPin = currentPin.takeIf { settings?.hasPin == true },
                        )
                    ) {
                        ParentalWriteResult.Success -> {
                            message = "PIN enregistré."
                            newPin = ""
                            currentPin = ""
                            load()
                        }
                        is ParentalWriteResult.Failure -> message = result.message
                    }
                    busy = false
                }
            },
        ) {
            Text("Enregistrer le PIN")
        }

        if (settings?.hasPin == true) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Restrictions",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))

            Text(
                text = "Âge maximum",
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf<Int?>(null, 7, 10, 12, 16, 18).forEach { age ->
                    val label = age?.toString() ?: "Tous"
                    if (maxAge == age) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { maxAge = age },
                        ) {
                            Text(label)
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { maxAge = age },
                        ) {
                            Text(label)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SettingToggle(
                title = "Masquer les contenus verrouillés",
                subtitle = "Sinon ils restent visibles avec un cadenas.",
                checked = hideLocked,
                onCheckedChange = { hideLocked = it },
            )

            SettingToggle(
                title = "Activer une limite quotidienne",
                subtitle = "La limite s’appliquera ensuite aux profils Enfant.",
                checked = scheduleEnabled,
                onCheckedChange = { scheduleEnabled = it },
            )

            if (scheduleEnabled) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = dailyLimit,
                    onValueChange = { dailyLimit = it.filter(Char::isDigit).take(4) },
                    label = { Text("Limite quotidienne en minutes") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = settingsPin,
                onValueChange = { settingsPin = it.filter(Char::isDigit).take(4) },
                label = { Text("PIN pour confirmer") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Button(
                enabled = !busy && settingsPin.length == 4,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true
                        val limit = dailyLimit.toIntOrNull()
                        when (
                            val result = repository.updateSettings(
                                pin = settingsPin,
                                maxAge = maxAge,
                                hideLocked = hideLocked,
                                scheduleEnabled = scheduleEnabled,
                                dailyLimitMinutes = if (scheduleEnabled) limit else null,
                            )
                        ) {
                            ParentalWriteResult.Success -> {
                                message = "Contrôle parental mis à jour."
                                settingsPin = ""
                                load()
                            }
                            is ParentalWriteResult.Failure -> message = result.message
                        }
                        busy = false
                    }
                },
            ) {
                Text("Enregistrer les restrictions")
            }
        }

        message?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "Après 5 codes PIN incorrects, les vérifications sont bloquées pendant 5 minutes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SettingToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
