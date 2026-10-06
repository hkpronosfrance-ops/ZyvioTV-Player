package fr.zyviotv.player.ui.settings

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.settings.ParentalControlsRepository
import fr.zyviotv.player.data.settings.PinVerificationResult
import kotlinx.coroutines.launch

@Composable
fun ParentalUnlockDialog(
    visible: Boolean,
    title: String = "Contenu verrouillé",
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current.applicationContext
    val repository = remember(context) {
        ParentalControlsRepository(SecureSessionStore(context))
    }
    val scope = rememberCoroutineScope()

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            if (!busy) onDismiss()
        },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(4)
                    error = null
                },
                enabled = !busy,
                singleLine = true,
                label = { Text("PIN parental") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                ),
                supportingText = {
                    error?.let { Text(it) }
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy && pin.length == 4,
                onClick = {
                    scope.launch {
                        busy = true
                        when (val result = repository.verifyPin(pin)) {
                            PinVerificationResult.Verified -> {
                                pin = ""
                                error = null
                                onUnlocked()
                            }
                            is PinVerificationResult.Invalid -> {
                                error = result.attemptsRemaining?.let {
                                    "PIN incorrect · $it tentative(s) restante(s)."
                                } ?: "PIN incorrect."
                            }
                            is PinVerificationResult.Blocked -> {
                                error = "Trop de tentatives. Réessayez plus tard."
                            }
                            PinVerificationResult.NotConfigured -> {
                                error = "Aucun PIN parental n’est configuré."
                            }
                            is PinVerificationResult.Failure -> {
                                error = result.message
                            }
                        }
                        busy = false
                    }
                },
            ) {
                Text("Déverrouiller")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
            ) {
                Text("Annuler")
            }
        },
    )
}
