package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.settings.ProfilePreferences
import fr.zyviotv.player.data.settings.ProfileRepository
import fr.zyviotv.player.shared.sync.PlayerProfile
import fr.zyviotv.player.shared.sync.PlayerProfileType
import fr.zyviotv.player.shared.sync.ProfileWriteResult
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect
import kotlinx.coroutines.launch

@Composable
fun ProfilesSettingsScreen(
    profile: DeviceProfile,
    onBack: () -> Unit,
    onProfileSelectionChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val repository = remember(context.applicationContext) {
        ProfileRepository(SecureSessionStore(context.applicationContext))
    }
    val preferences = remember(context.applicationContext) {
        ProfilePreferences(context.applicationContext)
    }
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var profiles by remember { mutableStateOf<List<PlayerProfile>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadToken by remember { mutableStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var child by remember { mutableStateOf(false) }
    var maxAge by remember { mutableStateOf<Int?>(12) }
    var avatarIndex by remember { mutableStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var defaultProfileId by remember { mutableStateOf(preferences.defaultProfileId()) }
    var editingProfile by remember { mutableStateOf<PlayerProfile?>(null) }
    var editName by remember { mutableStateOf("") }
    var editChild by remember { mutableStateOf(false) }
    var editMaxAge by remember { mutableStateOf<Int?>(12) }
    var editAvatarIndex by remember { mutableStateOf(1) }
    var editError by remember { mutableStateOf<String?>(null) }

    fun reload() {
        reloadToken += 1
    }

    fun startEditing(item: PlayerProfile) {
        editingProfile = item
        editName = item.name
        editChild = item.type == PlayerProfileType.Child
        editMaxAge = item.maxAge ?: 12
        editAvatarIndex = item.avatarKey
            .substringAfter("avatar_", "01")
            .toIntOrNull()
            ?.coerceIn(1, 16)
            ?: 1
        editError = null
        showCreate = false
    }

    LaunchedEffect(reloadToken) {
        loading = true
        error = null

        repository.ensurePrimaryProfile().getOrElse {
            error = "Impossible de préparer le profil principal."
            loading = false
            return@LaunchedEffect
        }

        profiles = repository.listProfiles().getOrElse {
            error = "Impossible de charger les profils."
            emptyList()
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                enabled = !busy,
                onClick = onBack,
            ) {
                Text("Retour")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Profils",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "${profiles.size} / 5",
                    color = ZyvioTextSecondary,
                )
            }
            TextButton(
                enabled = !busy && profiles.size < 5,
                onClick = { showCreate = !showCreate },
            ) {
                Text(if (showCreate) "Annuler" else "Ajouter")
            }
        }

        Spacer(Modifier.height(16.dp))

        editingProfile?.let { item ->
            ProfileEditPanel(
                deviceProfile = profile,
                profile = item,
                name = editName,
                child = editChild,
                maxAge = editMaxAge,
                avatarIndex = editAvatarIndex,
                isDefault = defaultProfileId == item.id,
                busy = busy,
                error = editError,
                onNameChange = {
                    editName = it
                    editError = null
                },
                onChildChange = {
                    if (!item.isPrimary) {
                        editChild = it
                        if (it && editMaxAge == null) editMaxAge = 12
                        editError = null
                    }
                },
                onMaxAgeChange = {
                    editMaxAge = it
                    editError = null
                },
                onAvatarIndexChange = {
                    editAvatarIndex = it.coerceIn(1, 16)
                    editError = null
                },
                onDefaultChange = { enabled ->
                    if (enabled) {
                        defaultProfileId = item.id
                        preferences.setDefaultProfileId(item.id)
                        preferences.setSelectedProfileId(item.id)
                        onProfileSelectionChanged()
                        message = "Profil par défaut enregistré sur cet appareil."
                    } else {
                        defaultProfileId = null
                        preferences.setDefaultProfileId(null)
                        message = "Profil par défaut désactivé sur cet appareil."
                    }
                },
                onSave = {
                    val cleanName = editName.trim()
                    val duplicate = profiles.any {
                        it.id != item.id && it.name.equals(cleanName, ignoreCase = true)
                    }
                    if (duplicate) {
                        editError = "Ce nom est déjà utilisé par un autre profil."
                    } else {
                        scope.launch {
                            busy = true
                            editError = null
                            val updated = item.copy(
                                name = cleanName,
                                avatarKey = "avatar_" + editAvatarIndex.toString().padStart(2, '0'),
                                type = if (item.isPrimary) {
                                    PlayerProfileType.Standard
                                } else if (editChild) {
                                    PlayerProfileType.Child
                                } else {
                                    PlayerProfileType.Standard
                                },
                                maxAge = if (!item.isPrimary && editChild) editMaxAge else null,
                            )
                            when (val result = repository.updateProfile(updated)) {
                                ProfileWriteResult.Success -> {
                                    editingProfile = null
                                    if (preferences.selectedProfileId() == item.id) {
                                        onProfileSelectionChanged()
                                    }
                                    message = "Profil modifié."
                                    reload()
                                }
                                is ProfileWriteResult.Failure -> {
                                    editError = result.message
                                }
                            }
                            busy = false
                        }
                    }
                },
                onCancel = {
                    editingProfile = null
                    editError = null
                },
            )
            Spacer(Modifier.height(14.dp))
        }

        if (showCreate) {
            Surface(
                color = ZyvioSurface1,
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Nom du profil") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

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
                                text = "Applique les restrictions d’âge du profil.",
                                color = ZyvioTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = child,
                            onCheckedChange = { child = it },
                        )
                    }

                    if (child) {
                        Spacer(Modifier.height(12.dp))
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
                                        onClick = { maxAge = age },
                                    ) {
                                        Text(age.toString())
                                    }
                                } else {
                                    OutlinedButton(
                                        modifier = Modifier.weight(1f),
                                        onClick = { maxAge = age },
                                    ) {
                                        Text(age.toString())
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Avatar ZYVIOTV ${avatarIndex.toString().padStart(2, '0')}",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            enabled = avatarIndex > 1,
                            onClick = { avatarIndex -= 1 },
                        ) {
                            Text("Précédent")
                        }
                        OutlinedButton(
                            enabled = avatarIndex < 16,
                            onClick = { avatarIndex += 1 },
                        ) {
                            Text("Suivant")
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        enabled = !busy && name.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val cleanName = name.trim()
                            if (profiles.any { it.name.equals(cleanName, ignoreCase = true) }) {
                                message = "Ce nom est déjà utilisé par un autre profil."
                            } else {
                                scope.launch {
                                    busy = true
                                    message = null
                                    val avatarKey = "avatar_" + avatarIndex.toString().padStart(2, '0')
                                    when (
                                        val result = repository.createProfile(
                                        name = cleanName,
                                        avatarKey = avatarKey,
                                        type = if (child) {
                                            PlayerProfileType.Child
                                        } else {
                                            PlayerProfileType.Standard
                                        },
                                        maxAge = if (child) maxAge else null,
                                    )
                                ) {
                                    ProfileWriteResult.Success -> {
                                        name = ""
                                        child = false
                                        maxAge = 12
                                        avatarIndex = 1
                                        showCreate = false
                                        reload()
                                    }
                                        is ProfileWriteResult.Failure -> message = result.message
                                    }
                                    busy = false
                                }
                            }
                        },
                    ) {
                        Text("Créer le profil")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }

        message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { reload() }) {
                        Text("Réessayer")
                    }
                }
            }

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(
                    items = profiles,
                    key = { it.id },
                ) { item ->
                    ProfileRow(
                        item = item,
                        isDefault = defaultProfileId == item.id,
                        isTelevision = profile == DeviceProfile.Television,
                        onEdit = { startEditing(item) },
                        onSetDefault = {
                            defaultProfileId = item.id
                            preferences.setDefaultProfileId(item.id)
                            preferences.setSelectedProfileId(item.id)
                            onProfileSelectionChanged()
                            message = "Profil par défaut enregistré sur cet appareil."
                        },
                        onDelete = {
                            scope.launch {
                                busy = true
                                when (val result = repository.deleteProfile(item)) {
                                    ProfileWriteResult.Success -> {
                                        if (defaultProfileId == item.id) {
                                            defaultProfileId = null
                                            preferences.setDefaultProfileId(null)
                                        }
                                        if (preferences.selectedProfileId() == item.id) {
                                            preferences.setSelectedProfileId(null)
                                            onProfileSelectionChanged()
                                        }
                                        reload()
                                    }
                                    is ProfileWriteResult.Failure -> message = result.message
                                }
                                busy = false
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(
    item: PlayerProfile,
    isDefault: Boolean,
    isTelevision: Boolean,
    onEdit: () -> Unit,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusEffect(isTelevision, cornerRadiusDp = 16),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (item.type == PlayerProfileType.Child) {
                    Icons.Default.ChildCare
                } else {
                    Icons.Default.Person
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = item.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = buildString {
                        append(if (item.type == PlayerProfileType.Child) "Enfant" else "Standard")
                        item.maxAge?.let {
                            append(" • ")
                            append(it)
                            append(" ans")
                        }
                        if (item.isPrimary) append(" • Principal")
                        if (isDefault) append(" • Par défaut")
                    },
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = item.avatarKey,
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextButton(onClick = onEdit) {
                Text("Modifier")
            }

            if (!isDefault) {
                TextButton(onClick = onSetDefault) {
                    Text("Par défaut")
                }
            }

            if (!item.isPrimary) {
                TextButton(onClick = onDelete) {
                    Text("Supprimer")
                }
            }
        }
    }
}
