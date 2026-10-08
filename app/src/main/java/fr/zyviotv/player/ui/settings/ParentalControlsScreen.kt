package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import fr.zyviotv.player.R
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
import fr.zyviotv.player.data.settings.ParentalRecoveryResult
import fr.zyviotv.player.data.settings.ParentalSettings
import fr.zyviotv.player.data.settings.ParentalWriteResult
import fr.zyviotv.player.data.settings.ProfileParentalSettings
import fr.zyviotv.player.data.settings.ProfileRepository
import fr.zyviotv.player.ui.catalog.ProviderCatalogState
import fr.zyviotv.player.ui.catalog.rememberProviderCatalogSession
import fr.zyviotv.player.shared.sync.PlayerProfile
import kotlinx.coroutines.launch

@Composable
fun ParentalControlsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) {
        ParentalControlsRepository(SecureSessionStore(appContext))
    }
    val profileRepository = remember(appContext) {
        ProfileRepository(SecureSessionStore(appContext))
    }
    val scope = rememberCoroutineScope()
    val catalogSession = rememberProviderCatalogSession()
    val catalogState by catalogSession.state

    var loading by remember { mutableStateOf(true) }
    var settings by remember { mutableStateOf<ParentalSettings?>(null) }
    var profiles by remember { mutableStateOf<List<PlayerProfile>>(emptyList()) }
    var selectedProfile by remember { mutableStateOf<PlayerProfile?>(null) }
    var profileSettings by remember { mutableStateOf<ProfileParentalSettings?>(null) }
    var lockedCategoryKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var lockedContentKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var actionPin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    var maxAge by remember { mutableStateOf<Int?>(null) }
    var hideLocked by remember { mutableStateOf(false) }
    var dailyLimit by remember { mutableStateOf("") }
    var weekendLimit by remember { mutableStateOf("") }
    var warningMinutes by remember { mutableStateOf("10") }
    var scheduleEnabled by remember { mutableStateOf(false) }
    var scheduleWindows by remember {
        mutableStateOf<List<ParentalScheduleWindowUi>>(emptyList())
    }

    suspend fun loadAccount() {
        loading = true
        error = null
        val loadedSettings = repository.loadSettings().getOrElse {
            error = "Impossible de charger le contrôle parental."
            loading = false
            return
        }
        val loadedProfiles = profileRepository.listProfiles().getOrElse {
            error = "Impossible de charger les profils."
            loading = false
            return
        }
        settings = loadedSettings
        profiles = loadedProfiles
        if (selectedProfile == null) {
            selectedProfile = loadedProfiles.firstOrNull { !it.isPrimary } ?: loadedProfiles.firstOrNull()
        }
        loading = false
    }

    suspend fun loadSelectedProfile() {
        val profile = selectedProfile ?: return
        val loaded = repository.loadProfileSettings(profile.id).getOrElse {
            error = "Impossible de charger les restrictions du profil."
            return
        }
        profileSettings = loaded
        val locks = repository.loadContentLocks(profile.id).getOrElse {
            error = "Impossible de charger les verrouillages du profil."
            return
        }
        lockedCategoryKeys = locks.lockedCategoryKeys
        lockedContentKeys = locks.lockedContentKeys
        maxAge = loaded.maxAge
        hideLocked = loaded.hideLocked
        dailyLimit = loaded.dailyLimitMinutes?.toString().orEmpty()
        weekendLimit = loaded.weekendLimitMinutes?.toString().orEmpty()
        warningMinutes = loaded.warningMinutes.toString()
        scheduleEnabled = loaded.scheduleEnabled
        scheduleWindows = parseScheduleWindows(loaded.scheduleWindowsJson)
    }

    LaunchedEffect(Unit) { loadAccount() }
    LaunchedEffect(selectedProfile?.id) {
        if (selectedProfile != null) loadSelectedProfile()
    }

    if (loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(enabled = !busy, onClick = onBack) { Text(stringResource(R.string.nav_back)) }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_parental),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = stringResource(R.string.parental_header),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { loadAccount() } }) { Text(stringResource(R.string.device_retry)) }
            return@Column
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = if (settings?.hasPin == true) "Code PIN parental" else "Créer le code PIN parental",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )

        if (settings?.hasPin == true) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = currentPin,
                onValueChange = { currentPin = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.parental_current_pin)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(4.dp))
            TextButton(
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        message = when (val result = repository.requestPinRecoveryEmail()) {
                            is ParentalRecoveryResult.Sent ->
                                "Email de récupération envoyé à ${result.maskedEmail}."
                            is ParentalRecoveryResult.Failure -> result.message
                        }
                        busy = false
                    }
                },
            ) {
                Text(stringResource(R.string.parental_forgot_pin))
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newPin,
            onValueChange = { newPin = it.filter(Char::isDigit).take(4) },
            label = { Text(stringResource(R.string.parental_new_pin)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            enabled = !busy && newPin.length == 4 &&
                (settings?.hasPin != true || currentPin.length == 4),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    busy = true
                    when (val result = repository.setPin(
                        newPin = newPin,
                        currentPin = currentPin.takeIf { settings?.hasPin == true },
                    )) {
                        ParentalWriteResult.Success -> {
                            newPin = ""
                            currentPin = ""
                            message = "PIN enregistré."
                            loadAccount()
                        }
                        is ParentalWriteResult.Failure -> message = result.message
                    }
                    busy = false
                }
            },
        ) { Text(stringResource(R.string.parental_save_pin)) }

        if (settings?.hasPin == true) {
            Spacer(Modifier.height(20.dp))
            SettingToggle(
                title = "Contrôle parental du compte",
                subtitle = "Active les restrictions configurées sur les profils.",
                checked = settings?.enabled == true,
                onCheckedChange = { requested ->
                    if (actionPin.length != 4) {
                        message = "Saisissez le PIN pour modifier le contrôle parental."
                    } else {
                        scope.launch {
                            busy = true
                            when (val result = repository.setEnabled(actionPin, requested)) {
                                ParentalWriteResult.Success -> {
                                    actionPin = ""
                                    message = if (requested) "Contrôle parental activé." else "Contrôle parental désactivé."
                                    loadAccount()
                                }
                                is ParentalWriteResult.Failure -> message = result.message
                            }
                            busy = false
                        }
                    }
                },
            )

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = actionPin,
                onValueChange = { actionPin = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.parental_confirm_pin)) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.parental_per_profile),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                profiles.forEach { profile ->
                    if (selectedProfile?.id == profile.id) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { selectedProfile = profile },
                        ) { Text(profile.name) }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { selectedProfile = profile },
                        ) { Text(profile.name) }
                    }
                }
            }

            selectedProfile?.let { profile ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = stringResource(R.string.parental_profile_restrictions, profile.name),
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.profile_max_age), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf<Int?>(null, 7, 10, 12, 16, 18).forEach { age ->
                        val label = age?.toString() ?: "Tous"
                        val enabled = !profile.isPrimary || age == null
                        if (maxAge == age) {
                            Button(
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                onClick = { maxAge = age },
                            ) { Text(label) }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                enabled = enabled,
                                onClick = { maxAge = age },
                            ) { Text(label) }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                SettingToggle(
                    title = "Masquer complètement les contenus verrouillés",
                    subtitle = "Sinon ils restent visibles avec un cadenas.",
                    checked = hideLocked,
                    onCheckedChange = { hideLocked = it },
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dailyLimit,
                    onValueChange = { dailyLimit = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.parental_screen_time)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = weekendLimit,
                    onValueChange = { weekendLimit = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.parental_weekend)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = warningMinutes,
                    onValueChange = { warningMinutes = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.parental_warning)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                SettingToggle(
                    title = "Plages horaires",
                    subtitle = "Les règles en cache restent appliquées hors ligne.",
                    checked = scheduleEnabled,
                    onCheckedChange = { requested ->
                        scheduleEnabled = requested
                        if (requested && scheduleWindows.isEmpty()) {
                            scheduleWindows = listOf(
                                ParentalScheduleWindowUi(
                                    days = setOf(1, 2, 3, 4, 5),
                                    start = "16:30",
                                    end = "19:30",
                                ),
                            )
                        }
                    },
                )

                ParentalScheduleEditor(
                    windows = scheduleWindows,
                    enabled = scheduleEnabled,
                    onChange = { scheduleWindows = it },
                )

                if (profile.type.wireValue == "child") {
                    val readyCatalog = catalogState as? ProviderCatalogState.Ready
                    if (readyCatalog != null) {
                        ParentalContentLockEditor(
                            snapshot = readyCatalog.rawSnapshot,
                            lockedCategoryKeys = lockedCategoryKeys,
                            lockedContentKeys = lockedContentKeys,
                            onCategoryLocksChange = { lockedCategoryKeys = it },
                            onContentLocksChange = { lockedContentKeys = it },
                        )

                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            enabled = !busy && actionPin.length == 4,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    busy = true
                                    when (
                                        val result = repository.updateContentLocks(
                                            profileId = profile.id,
                                            pin = actionPin,
                                            lockedCategoryKeys = lockedCategoryKeys,
                                            lockedContentKeys = lockedContentKeys,
                                        )
                                    ) {
                                        ParentalWriteResult.Success -> {
                                            actionPin = ""
                                            message = "Verrouillages de ${profile.name} mis à jour."
                                            loadSelectedProfile()
                                            catalogSession.reload()
                                        }
                                        is ParentalWriteResult.Failure -> {
                                            message = result.message
                                        }
                                    }
                                    busy = false
                                }
                            },
                        ) {
                            Text(stringResource(R.string.parental_save_locks))
                        }
                    } else {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.parental_catalog_unavailable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = !busy && actionPin.length == 4,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val scheduleError = if (scheduleEnabled) {
                            validateScheduleWindows(scheduleWindows)
                        } else {
                            null
                        }
                        if (scheduleError != null) {
                            message = scheduleError
                            return@Button
                        }

                        scope.launch {
                            busy = true
                            when (val result = repository.updateProfileSettings(
                                profileId = profile.id,
                                pin = actionPin,
                                maxAge = maxAge,
                                hideLocked = hideLocked,
                                dailyLimitMinutes = dailyLimit.toIntOrNull(),
                                weekendLimitMinutes = weekendLimit.toIntOrNull(),
                                warningMinutes = warningMinutes.toIntOrNull() ?: 10,
                                scheduleEnabled = scheduleEnabled,
                                scheduleWindowsJson = if (scheduleEnabled) {
                                    encodeScheduleWindows(scheduleWindows)
                                } else {
                                    "[]"
                                },
                            )) {
                                ParentalWriteResult.Success -> {
                                    actionPin = ""
                                    message = "Restrictions de ${profile.name} mises à jour."
                                    loadSelectedProfile()
                                }
                                is ParentalWriteResult.Failure -> message = result.message
                            }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.parental_save_restrictions)) }
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(text = it, color = MaterialTheme.colorScheme.primary)
        }

        profileSettings?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.parental_pin_explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
