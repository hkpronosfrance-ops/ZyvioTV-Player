package fr.zyviotv.player.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.settings.DevicePreferencesSnapshot
import fr.zyviotv.player.data.settings.ProfileMediaPreferencesSnapshot
import fr.zyviotv.player.shared.sync.PlayerProfile
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect

@Composable
fun OnboardingPreferencesScreen(
    deviceProfile: DeviceProfile,
    profiles: List<PlayerProfile>,
    selectedDefaultProfileId: String?,
    selectedProfileId: String?,
    initialProfilePreferences: ProfileMediaPreferencesSnapshot,
    initialDevicePreferences: DevicePreferencesSnapshot,
    onDefaultProfileChanged: (String) -> Unit,
    onProfilePreferencesSaved: (String, ProfileMediaPreferencesSnapshot) -> Unit,
    onDevicePreferencesSaved: (DevicePreferencesSnapshot) -> Unit,
    onFinished: () -> Unit,
) {
    val isTv = deviceProfile == DeviceProfile.Television
    val activeProfile = profiles.firstOrNull { it.id == selectedProfileId }
        ?: profiles.firstOrNull()

    var audio by remember(activeProfile?.id, initialProfilePreferences) {
        mutableStateOf(initialProfilePreferences.audioLanguage)
    }
    var subtitles by remember(activeProfile?.id, initialProfilePreferences) {
        mutableStateOf(initialProfilePreferences.subtitleLanguage)
    }
    var autoNext by remember(activeProfile?.id, initialProfilePreferences) {
        mutableStateOf(initialProfilePreferences.autoNextEpisode)
    }
    var quality by remember(initialDevicePreferences) {
        mutableStateOf(initialDevicePreferences.playbackQuality)
    }
    var interfaceLanguage by remember(initialDevicePreferences) {
        mutableStateOf(initialDevicePreferences.interfaceLanguage)
    }

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
            modifier = Modifier.widthIn(max = if (isTv) 980.dp else 680.dp),
        ) {
            Text(
                text = "Finalisons votre expérience",
                style = if (isTv) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Ces réglages pourront être modifiés plus tard.",
                color = ZyvioTextSecondary,
            )
            Spacer(Modifier.height(22.dp))

            SetupSection(title = "Profil par défaut sur cet appareil") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profiles.take(5).forEach { profile ->
                        FilterChip(
                            selected = selectedDefaultProfileId == profile.id,
                            onClick = { onDefaultProfileChanged(profile.id) },
                            label = { Text(profile.name) },
                            modifier = Modifier.tvFocusEffect(isTv, cornerRadiusDp = 10),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (activeProfile != null) {
                SetupSection(title = "Préférences de ${activeProfile.name}") {
                    ChoiceRow(
                        label = "Audio",
                        values = listOf("Auto", "Français", "Original"),
                        selected = audio,
                        isTv = isTv,
                        onSelected = { audio = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    ChoiceRow(
                        label = "Sous-titres",
                        values = listOf("Auto", "Français", "Désactivés"),
                        selected = subtitles,
                        isTv = isTv,
                        onSelected = { subtitles = it },
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Épisode suivant automatique", fontWeight = FontWeight.SemiBold)
                            Text(
                                "Lancer automatiquement l’épisode suivant.",
                                color = ZyvioTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = autoNext,
                            onCheckedChange = { autoNext = it },
                            modifier = Modifier.tvFocusEffect(isTv, cornerRadiusDp = 12),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            SetupSection(title = "Préférences de cet appareil") {
                ChoiceRow(
                    label = "Qualité",
                    values = listOf("Auto", "4K", "FHD", "HD", "SD"),
                    selected = quality,
                    isTv = isTv,
                    onSelected = { quality = it },
                )
                Spacer(Modifier.height(10.dp))
                ChoiceRow(
                    label = "Langue de l’interface",
                    values = listOf("Système", "Français", "English"),
                    selected = interfaceLanguage,
                    isTv = isTv,
                    onSelected = { interfaceLanguage = it },
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusEffect(isTv, cornerRadiusDp = 12),
                onClick = {
                    if (activeProfile != null) {
                        onProfilePreferencesSaved(
                            activeProfile.id,
                            ProfileMediaPreferencesSnapshot(
                                audioLanguage = audio,
                                subtitleLanguage = subtitles,
                                autoNextEpisode = autoNext,
                            ),
                        )
                    }
                    onDevicePreferencesSaved(
                        DevicePreferencesSnapshot(
                            playbackQuality = quality,
                            interfaceLanguage = interfaceLanguage,
                        ),
                    )
                    onFinished()
                },
            ) {
                Text("Terminer")
            }
        }
    }
}

@Composable
private fun SetupSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = ZyvioSurface1,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ChoiceRow(
    label: String,
    values: List<String>,
    selected: String,
    isTv: Boolean,
    onSelected: (String) -> Unit,
) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            values.forEach { value ->
                if (value == selected) {
                    Button(
                        onClick = { onSelected(value) },
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusEffect(isTv, cornerRadiusDp = 10),
                    ) {
                        Text(value)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onSelected(value) },
                        modifier = Modifier
                            .weight(1f)
                            .tvFocusEffect(isTv, cornerRadiusDp = 10),
                    ) {
                        Text(value)
                    }
                }
            }
        }
    }
}
