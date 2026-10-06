package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.sync.PlayerProfile
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect

@Composable
internal fun ProfileEditPanel(
    deviceProfile: DeviceProfile,
    profile: PlayerProfile,
    name: String,
    child: Boolean,
    maxAge: Int?,
    avatarIndex: Int,
    isDefault: Boolean,
    busy: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onChildChange: (Boolean) -> Unit,
    onMaxAgeChange: (Int) -> Unit,
    onAvatarIndexChange: (Int) -> Unit,
    onSetDefault: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusEffect(
                deviceProfile == DeviceProfile.Television,
                cornerRadiusDp = 18,
            ),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Modifier · \${profile.name}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = if (profile.isPrimary) {
                            "Profil principal · Standard"
                        } else {
                            if (child) "Profil Enfant" else "Profil Standard"
                        },
                        color = ZyvioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(
                    enabled = !busy,
                    onClick = onCancel,
                ) {
                    Text("Annuler")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Avatar ZYVIOTV \${avatarIndex.toString().padStart(2, '0')}",
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !busy && avatarIndex > 1,
                    onClick = { onAvatarIndexChange(avatarIndex - 1) },
                ) {
                    Text("Précédent")
                }
                OutlinedButton(
                    enabled = !busy && avatarIndex < 16,
                    onClick = { onAvatarIndexChange(avatarIndex + 1) },
                ) {
                    Text("Suivant")
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { onNameChange(it.take(40)) },
                enabled = !busy,
                label = { Text("Nom du profil") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            if (profile.isPrimary) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "Type · Standard",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Le profil principal ne peut pas devenir un profil Enfant.",
                            color = ZyvioTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Profil Enfant",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Active les restrictions propres à ce profil.",
                            color = ZyvioTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = child,
                        enabled = !busy,
                        onCheckedChange = onChildChange,
                    )
                }
            }

            if (!profile.isPrimary && child) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Âge maximum",
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(7, 10, 12, 16, 18).forEach { age ->
                        if (maxAge == age) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = !busy,
                                onClick = { onMaxAgeChange(age) },
                            ) {
                                Text(age.toString())
                            }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = !busy,
                                onClick = { onMaxAgeChange(age) },
                            ) {
                                Text(age.toString())
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Profil par défaut sur cet appareil",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (isDefault) {
                            "Ouverture directe avec ce profil."
                        } else {
                            "Le sélecteur reste utilisé si aucun profil par défaut n’est défini."
                        },
                        color = ZyvioTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = isDefault,
                    enabled = !busy && !isDefault,
                    onCheckedChange = {
                        if (it) onSetDefault()
                    },
                )
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !busy && name.trim().isNotEmpty(),
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Enregistrer")
            }
        }
    }
}
