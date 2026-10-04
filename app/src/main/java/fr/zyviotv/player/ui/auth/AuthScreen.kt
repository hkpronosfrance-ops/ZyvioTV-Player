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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.auth.SupabaseAuthRepository
import fr.zyviotv.player.shared.AppIdentity
import fr.zyviotv.player.shared.auth.AuthCredentials
import fr.zyviotv.player.shared.auth.AuthMode
import fr.zyviotv.player.shared.auth.AuthResult
import fr.zyviotv.player.shared.auth.AuthValidator
import fr.zyviotv.player.shared.auth.RegistrationCredentials
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
) {
    val context = LocalContext.current
    val repository = remember {
        SupabaseAuthRepository(
            sessionStore = SecureSessionStore(context.applicationContext),
        )
    }
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(AuthMode.SignIn) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (repository.hasStoredSession()) {
            onAuthenticated()
        }
    }

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
                enabled = !isLoading,
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
                    enabled = !isLoading,
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
                    enabled = !isLoading,
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
                    val validation = when (mode) {
                        AuthMode.SignIn -> AuthValidator.validateSignIn(email, password)
                        AuthMode.SignUp -> AuthValidator.validateSignUp(
                            email = email,
                            password = password,
                            confirmPassword = confirmPassword,
                        )
                        AuthMode.ResetPassword -> AuthValidator.validateReset(email)
                    }

                    if (!validation.isValid) {
                        message = validation.emailError
                            ?: validation.passwordError
                            ?: validation.confirmPasswordError
                        return@Button
                    }

                    scope.launch {
                        isLoading = true
                        message = null

                        val result = when (mode) {
                            AuthMode.SignIn -> repository.signIn(
                                AuthCredentials(
                                    email = email.trim(),
                                    password = password,
                                ),
                            )

                            AuthMode.SignUp -> repository.signUp(
                                RegistrationCredentials(
                                    email = email.trim(),
                                    password = password,
                                    confirmPassword = confirmPassword,
                                ),
                            )

                            AuthMode.ResetPassword -> repository.requestPasswordReset(email.trim())
                        }

                        when (result) {
                            AuthResult.Success -> {
                                when (mode) {
                                    AuthMode.SignIn -> onAuthenticated()
                                    AuthMode.SignUp -> {
                                        message = "Compte créé. Vérifiez votre e-mail si une confirmation est demandée."
                                        mode = AuthMode.SignIn
                                        password = ""
                                        confirmPassword = ""
                                    }
                                    AuthMode.ResetPassword -> {
                                        message = "E-mail envoyé. Consultez votre boîte de réception."
                                        mode = AuthMode.SignIn
                                    }
                                }
                            }

                            is AuthResult.Failure -> {
                                message = result.message
                            }
                        }

                        isLoading = false
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = when (mode) {
                            AuthMode.SignIn -> "Se connecter"
                            AuthMode.SignUp -> "Créer mon compte"
                            AuthMode.ResetPassword -> "Envoyer le lien"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (mode == AuthMode.SignIn) {
                TextButton(
                    enabled = !isLoading,
                    onClick = {
                        mode = AuthMode.ResetPassword
                        message = null
                    },
                ) {
                    Text("Mot de passe oublié ?")
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            when (mode) {
                AuthMode.SignIn -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("Pas encore de compte ?")
                        TextButton(
                            enabled = !isLoading,
                            onClick = {
                                mode = AuthMode.SignUp
                                message = null
                            },
                        ) {
                            Text("S'inscrire")
                        }
                    }
                }

                AuthMode.SignUp,
                AuthMode.ResetPassword,
                -> {
                    OutlinedButton(
                        enabled = !isLoading,
                        onClick = {
                            mode = AuthMode.SignIn
                            message = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Retour à la connexion")
                    }
                }
            }
        }
    }
}
