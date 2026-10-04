package fr.zyviotv.player.ui.auth

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.shared.AppIdentity
import fr.zyviotv.player.shared.auth.AuthMode
import fr.zyviotv.player.shared.auth.AuthValidator

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
) {
    var mode by remember { mutableStateOf(AuthMode.SignIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = AppIdentity.name.removeSuffix(" Player").uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = "PLAYER",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = when (mode) {
                    AuthMode.SignIn -> "Connexion"
                    AuthMode.SignUp -> "Créer un compte"
                    AuthMode.ResetPassword -> "Mot de passe oublié"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = when (mode) {
                    AuthMode.SignIn -> "Retrouvez vos playlists et votre progression sur tous vos appareils."
                    AuthMode.SignUp -> "Créez votre compte ZyvioTV Player pour synchroniser vos appareils."
                    AuthMode.ResetPassword -> "Nous vous enverrons un lien pour réinitialiser votre mot de passe."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    message = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Adresse e-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )

            if (mode != AuthMode.ResetPassword) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }

            if (mode == AuthMode.SignUp) {
                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        message = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Confirmer le mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    when (mode) {
                        AuthMode.SignIn -> {
                            val result = AuthValidator.validateSignIn(email, password)
                            if (result.isValid) {
                                message = "Interface prête. Connexion serveur à configurer."
                            } else {
                                message = result.emailError ?: result.passwordError
                            }
                        }

                        AuthMode.SignUp -> {
                            val result = AuthValidator.validateSignUp(
                                email = email,
                                password = password,
                                confirmPassword = confirmPassword,
                            )
                            if (result.isValid) {
                                message = "Interface prête. Création de compte serveur à configurer."
                            } else {
                                message = result.emailError
                                    ?: result.passwordError
                                    ?: result.confirmPasswordError
                            }
                        }

                        AuthMode.ResetPassword -> {
                            val result = AuthValidator.validateReset(email)
                            message = if (result.isValid) {
                                "Interface prête. Envoi de l'e-mail serveur à configurer."
                            } else {
                                result.emailError
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(
                    text = when (mode) {
                        AuthMode.SignIn -> "Se connecter"
                        AuthMode.SignUp -> "Créer mon compte"
                        AuthMode.ResetPassword -> "Envoyer le lien"
                    },
                    fontWeight = FontWeight.Bold,
                )
            }

            if (mode == AuthMode.SignIn) {
                TextButton(onClick = { mode = AuthMode.ResetPassword }) {
                    Text("Mot de passe oublié ?")
                }
            }

            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            when (mode) {
                AuthMode.SignIn -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("Pas encore de compte ?")
                        TextButton(onClick = { mode = AuthMode.SignUp }) {
                            Text("S'inscrire")
                        }
                    }
                }

                AuthMode.SignUp,
                AuthMode.ResetPassword,
                -> {
                    OutlinedButton(
                        onClick = { mode = AuthMode.SignIn },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retour à la connexion")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = onAuthenticated) {
                Text("Aperçu de l'application")
            }
        }
    }
}
