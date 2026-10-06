package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import fr.zyviotv.player.data.auth.SupabaseAuthRepository
import fr.zyviotv.player.data.settings.ParentalControlsRepository
import fr.zyviotv.player.data.settings.ParentalWriteResult
import fr.zyviotv.player.shared.auth.AuthResult
import kotlinx.coroutines.launch

@Composable
fun ParentalPinRecoveryScreen(
    deepLink: String,
    onCompleted: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val sessionStore = remember(context) { SecureSessionStore(context) }
    val authRepository = remember(context) {
        SupabaseAuthRepository(sessionStore)
    }
    val parentalRepository = remember(context) {
        ParentalControlsRepository(sessionStore)
    }
    val scope = rememberCoroutineScope()

    var linkReady by remember(deepLink) { mutableStateOf(false) }
    var loadingLink by remember(deepLink) { mutableStateOf(true) }
    var error by remember(deepLink) { mutableStateOf<String?>(null) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(deepLink) {
        loadingLink = true
        when (val result = authRepository.consumeMagicLink(deepLink)) {
            AuthResult.Success -> {
                linkReady = true
                error = null
            }
            is AuthResult.Failure -> {
                linkReady = false
                error = result.message
            }
        }
        loadingLink = false
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Réinitialiser le code PIN",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Créez un nouveau code PIN parental à 4 chiffres.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        if (loadingLink) {
            CircularProgressIndicator()
            return@Column
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            return@Column
        }

        if (linkReady) {
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it.filter(Char::isDigit).take(4) },
                label = { Text("Nouveau PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = confirmPin,
                onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) },
                label = { Text("Confirmer le PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                enabled = !busy &&
                    newPin.length == 4 &&
                    confirmPin.length == 4 &&
                    newPin == confirmPin,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        busy = true
                        when (val result = parentalRepository.resetPinAfterRecentAuth(newPin)) {
                            ParentalWriteResult.Success -> {
                                newPin = ""
                                confirmPin = ""
                                onCompleted()
                            }
                            is ParentalWriteResult.Failure -> {
                                error = result.message
                            }
                        }
                        busy = false
                    }
                },
            ) {
                Text(if (busy) "Enregistrement…" else "Enregistrer le nouveau PIN")
            }

            if (
                newPin.length == 4 &&
                confirmPin.length == 4 &&
                newPin != confirmPin
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Les deux codes PIN ne correspondent pas.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
