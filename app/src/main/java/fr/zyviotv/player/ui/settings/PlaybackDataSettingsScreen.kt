package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.settings.PlayerPreferences

@Composable
fun PlaybackDataSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context.applicationContext) {
        PlayerPreferences(context.applicationContext)
    }
    val initial = remember { preferences.read() }

    var audioLanguage by remember { mutableStateOf(initial.preferredAudioLanguage.orEmpty()) }
    var subtitleLanguage by remember { mutableStateOf(initial.preferredSubtitleLanguage.orEmpty()) }
    var subtitlesEnabled by remember { mutableStateOf(initial.subtitlesEnabled) }
    var autoplayNext by remember { mutableStateOf(initial.autoplayNextEpisode) }
    var dataSaver by remember { mutableStateOf(initial.dataSaverEnabled) }
    var mobileQuality by remember { mutableStateOf(initial.mobileQualityLimit) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

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
                    text = "Lecture et données",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Préférences propres à cet appareil",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Lecture",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(12.dp))

        SettingSwitchRow(
            title = "Sous-titres activés par défaut",
            subtitle = "Vous pouvez toujours les modifier pendant la lecture.",
            checked = subtitlesEnabled,
            onCheckedChange = { subtitlesEnabled = it },
        )

        SettingSwitchRow(
            title = "Épisode suivant automatique",
            subtitle = "Prépare l’enchaînement automatique des épisodes.",
            checked = autoplayNext,
            onCheckedChange = { autoplayNext = it },
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = audioLanguage,
            onValueChange = { audioLanguage = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Langue audio préférée") },
            placeholder = { Text("ex. fr") },
        )

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = subtitleLanguage,
            onValueChange = { subtitleLanguage = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Langue de sous-titres préférée") },
            placeholder = { Text("ex. fr") },
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Données mobiles",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(12.dp))

        SettingSwitchRow(
            title = "Économiseur de données",
            subtitle = "Réduit l’usage réseau lorsque l’application est utilisée sur un réseau mobile.",
            checked = dataSaver,
            onCheckedChange = { dataSaver = it },
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Qualité maximale sur réseau mobile",
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Auto", "SD", "HD", "FHD").forEach { option ->
                if (mobileQuality == option) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { mobileQuality = option },
                    ) {
                        Text(option)
                    }
                } else {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { mobileQuality = option },
                    ) {
                        Text(option)
                    }
                }
            }
        }

        savedMessage?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(18.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                preferences.setPreferredAudioLanguage(audioLanguage.trim().ifBlank { null })
                preferences.setPreferredSubtitleLanguage(subtitleLanguage.trim().ifBlank { null })
                preferences.setSubtitlesEnabled(subtitlesEnabled)
                preferences.setAutoplayNextEpisode(autoplayNext)
                preferences.setDataSaverEnabled(dataSaver)
                preferences.setMobileQualityLimit(mobileQuality)
                savedMessage = "Préférences enregistrées."
            },
        ) {
            Text("Enregistrer")
        }
    }
}

@Composable
private fun SettingSwitchRow(
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
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
            )
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
