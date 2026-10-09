package fr.zyviotv.player.ui.profiles

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.settings.ParentalControlsRepository
import fr.zyviotv.player.data.settings.PinVerificationResult
import fr.zyviotv.player.data.settings.ProfilePreferences
import fr.zyviotv.player.data.settings.ProfileRepository
import fr.zyviotv.player.shared.sync.PlayerProfile
import fr.zyviotv.player.shared.sync.PlayerProfileType
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import fr.zyviotv.player.ui.tv.tvFocusEffect
import kotlinx.coroutines.launch

private sealed interface ProfileGateState {
    data object Loading : ProfileGateState
    data class Choose(val profiles: List<PlayerProfile>) : ProfileGateState
    data class Error(val message: String) : ProfileGateState
}

@Composable
fun WhoIsWatchingGate(
    deviceProfile: DeviceProfile,
    forceChooser: Boolean = false,
    onProfileSelected: (PlayerProfile) -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) {
        ProfileRepository(SecureSessionStore(appContext))
    }
    val preferences = remember(appContext) {
        ProfilePreferences(appContext)
    }
    val parentalRepository = remember(appContext) {
        ParentalControlsRepository(SecureSessionStore(appContext))
    }
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<ProfileGateState>(ProfileGateState.Loading) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var currentProfile by remember { mutableStateOf<PlayerProfile?>(null) }
    var pendingProfile by remember { mutableStateOf<PlayerProfile?>(null) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var verifyingPin by remember { mutableStateOf(false) }

    fun completeSelection(profile: PlayerProfile) {
        preferences.setSelectedProfileId(profile.id)
        onProfileSelected(profile)
    }

    fun requestSelection(profile: PlayerProfile) {
        val current = currentProfile
        if (
            current != null &&
            current.type == PlayerProfileType.Child &&
            current.id != profile.id
        ) {
            pendingProfile = profile
            pin = ""
            pinError = null
        } else {
            completeSelection(profile)
        }
    }

    LaunchedEffect(reloadToken) {
        state = ProfileGateState.Loading

        repository.ensurePrimaryProfile().getOrElse {
            state = ProfileGateState.Error("Impossible de préparer vos profils.")
            return@LaunchedEffect
        }

        val profiles = repository.listProfiles().getOrElse {
            android.util.Log.e("ZyvioProfiles", "Profile list failed: ${it.message?.takeIf { value -> value.matches(Regex("PROFILE_LIST_HTTP_[0-9]{3}")) } ?: it.javaClass.simpleName}")
            state = ProfileGateState.Error("Impossible de charger vos profils.")
            return@LaunchedEffect
        }

        if (profiles.isEmpty()) {
            state = ProfileGateState.Error("Aucun profil disponible.")
            return@LaunchedEffect
        }

        currentProfile = profiles.firstOrNull {
            it.id == preferences.selectedProfileId()
        }

        if (!forceChooser && profiles.size == 1) {
            completeSelection(profiles.first())
            return@LaunchedEffect
        }

        val defaultProfileId = preferences.defaultProfileId()
        val defaultProfile = profiles.firstOrNull { it.id == defaultProfileId }
        if (!forceChooser && defaultProfile != null) {
            completeSelection(defaultProfile)
            return@LaunchedEffect
        }

        if (defaultProfileId != null) {
            preferences.setDefaultProfileId(null)
        }

        state = ProfileGateState.Choose(profiles)
    }

    when (val current = state) {
        ProfileGateState.Loading -> LoadingProfilesScreen()
        is ProfileGateState.Error -> ProfileGateError(
            message = current.message,
            onRetry = { reloadToken += 1 },
        )
        is ProfileGateState.Choose -> WhoIsWatchingScreen(
            deviceProfile = deviceProfile,
            profiles = current.profiles,
            onSelect = ::requestSelection,
        )
    }

    val target = pendingProfile
    if (target != null) {
        AlertDialog(
            onDismissRequest = {
                if (!verifyingPin) {
                    pendingProfile = null
                    pin = ""
                    pinError = null
                }
            },
            title = { Text("Code PIN parental") },
            text = {
                Column {
                    Text("Saisissez le code PIN pour quitter le profil Enfant.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { value ->
                            pin = value.filter(Char::isDigit).take(4)
                            pinError = null
                        },
                        enabled = !verifyingPin,
                        singleLine = true,
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    pinError?.let { message ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !verifyingPin && pin.length == 4,
                    onClick = {
                        scope.launch {
                            verifyingPin = true
                            when (val result = parentalRepository.verifyPin(pin)) {
                                PinVerificationResult.Verified -> {
                                    pendingProfile = null
                                    pin = ""
                                    completeSelection(target)
                                }
                                is PinVerificationResult.Invalid -> {
                                    pinError = result.attemptsRemaining?.let {
                                        "Code incorrect. $it tentative(s) restante(s)."
                                    } ?: "Code PIN incorrect."
                                }
                                is PinVerificationResult.Blocked -> {
                                    pinError = "Trop de tentatives. Réessayez plus tard."
                                }
                                PinVerificationResult.NotConfigured -> {
                                    pinError = "Configurez d’abord le code PIN parental."
                                }
                                is PinVerificationResult.Failure -> {
                                    pinError = result.message
                                }
                            }
                            verifyingPin = false
                        }
                    },
                ) {
                    Text("Valider")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !verifyingPin,
                    onClick = {
                        pendingProfile = null
                        pin = ""
                        pinError = null
                    },
                ) {
                    Text("Annuler")
                }
            },
        )
    }
}

@Composable
private fun LoadingProfilesScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ProfileGateError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text("Réessayer")
            }
        }
    }
}

@Composable
private fun WhoIsWatchingScreen(
    deviceProfile: DeviceProfile,
    profiles: List<PlayerProfile>,
    onSelect: (PlayerProfile) -> Unit,
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
            modifier = Modifier.widthIn(max = if (isTv) 920.dp else 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
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
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Qui regarde ?",
                style = if (isTv) {
                    MaterialTheme.typography.displaySmall
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Choisissez votre profil pour retrouver vos favoris et votre progression.",
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(if (isTv) 34.dp else 24.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    items = profiles,
                    key = { it.id },
                ) { profile ->
                    ProfileChoiceCard(
                        profile = profile,
                        isTv = isTv,
                        onClick = { onSelect(profile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileChoiceCard(
    profile: PlayerProfile,
    isTv: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusEffect(isTv, cornerRadiusDp = 18),
        color = ZyvioSurface1,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(if (isTv) 20.dp else 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(min = if (isTv) 72.dp else 58.dp)
                    .height(if (isTv) 72.dp else 58.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (profile.type == PlayerProfileType.Child) {
                            Icons.Default.ChildCare
                        } else {
                            Icons.Default.Person
                        },
                        contentDescription = null,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = profile.name,
                    style = if (isTv) {
                        MaterialTheme.typography.headlineSmall
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(if (profile.type == PlayerProfileType.Child) "Enfant" else "Standard")
                        profile.maxAge?.let {
                            append(" • ")
                            append(it)
                            append(" ans max")
                        }
                        if (profile.isPrimary) append(" • Principal")
                    },
                    color = ZyvioTextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
