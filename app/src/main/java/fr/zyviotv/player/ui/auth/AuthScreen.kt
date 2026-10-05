package fr.zyviotv.player.ui.auth

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.zyviotv.player.data.auth.SecureSessionStore
import fr.zyviotv.player.data.auth.SupabaseAuthRepository
import fr.zyviotv.player.shared.AppIdentity
import fr.zyviotv.player.shared.auth.AuthCredentials
import fr.zyviotv.player.shared.auth.AuthMode
import fr.zyviotv.player.shared.auth.AuthResult
import fr.zyviotv.player.shared.auth.AuthValidator
import fr.zyviotv.player.shared.auth.RegistrationCredentials
import fr.zyviotv.player.ui.DeviceProfile
import fr.zyviotv.player.ui.theme.ZyvioRedTint
import fr.zyviotv.player.ui.theme.ZyvioSurface1
import fr.zyviotv.player.ui.theme.ZyvioSurface2
import fr.zyviotv.player.ui.theme.ZyvioTextSecondary
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    profile: DeviceProfile,
    onAuthenticated: () -> Unit,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
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

    val isTabletLandscape = profile == DeviceProfile.Tablet &&
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
    ) {
        when {
            profile == DeviceProfile.Television -> {
                TelevisionAuthLayout(
                    mode = mode,
                    email = email,
                    password = password,
                    confirmPassword = confirmPassword,
                    message = message,
                    isLoading = isLoading,
                    onModeChange = {
                        mode = it
                        message = null
                    },
                    onEmailChange = {
                        email = it
                        message = null
                    },
                    onPasswordChange = {
                        password = it
                        message = null
                    },
                    onConfirmPasswordChange = {
                        confirmPassword = it
                        message = null
                    },
                    onSubmit = {
                        submitAuth(
                            mode = mode,
                            email = email,
                            password = password,
                            confirmPassword = confirmPassword,
                            repository = repository,
                            setLoading = { isLoading = it },
                            setMessage = { message = it },
                            onModeChange = { mode = it },
                            clearPasswords = {
                                password = ""
                                confirmPassword = ""
                            },
                            onAuthenticated = onAuthenticated,
                            launch = { block -> scope.launch { block() } },
                        )
                    },
                )
            }

            isTabletLandscape -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AuthAmbientPanel(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    AuthFormCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        mode = mode,
                        email = email,
                        password = password,
                        confirmPassword = confirmPassword,
                        message = message,
                        isLoading = isLoading,
                        onModeChange = {
                            mode = it
                            message = null
                        },
                        onEmailChange = {
                            email = it
                            message = null
                        },
                        onPasswordChange = {
                            password = it
                            message = null
                        },
                        onConfirmPasswordChange = {
                            confirmPassword = it
                            message = null
                        },
                        onSubmit = {
                            submitAuth(
                                mode = mode,
                                email = email,
                                password = password,
                                confirmPassword = confirmPassword,
                                repository = repository,
                                setLoading = { isLoading = it },
                                setMessage = { message = it },
                                onModeChange = { mode = it },
                                clearPasswords = {
                                    password = ""
                                    confirmPassword = ""
                                },
                                onAuthenticated = onAuthenticated,
                                launch = { block -> scope.launch { block() } },
                            )
                        },
                    )
                }
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AuthFormCard(
                        modifier = Modifier.widthIn(max = 480.dp),
                        mode = mode,
                        email = email,
                        password = password,
                        confirmPassword = confirmPassword,
                        message = message,
                        isLoading = isLoading,
                        onModeChange = {
                            mode = it
                            message = null
                        },
                        onEmailChange = {
                            email = it
                            message = null
                        },
                        onPasswordChange = {
                            password = it
                            message = null
                        },
                        onConfirmPasswordChange = {
                            confirmPassword = it
                            message = null
                        },
                        onSubmit = {
                            submitAuth(
                                mode = mode,
                                email = email,
                                password = password,
                                confirmPassword = confirmPassword,
                                repository = repository,
                                setLoading = { isLoading = it },
                                setMessage = { message = it },
                                onModeChange = { mode = it },
                                clearPasswords = {
                                    password = ""
                                    confirmPassword = ""
                                },
                                onAuthenticated = onAuthenticated,
                                launch = { block -> scope.launch { block() } },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthAmbientPanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            ZyvioWordmark()
            Spacer(Modifier.width(1.dp))
            Text(
                text = "Vos chaînes, films et séries.\nUne seule expérience.",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Synchronisez vos appareils, vos favoris et votre progression avec votre compte ZYVIOTV.",
                modifier = Modifier.padding(top = 16.dp),
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun AuthFormCard(
    modifier: Modifier = Modifier,
    mode: AuthMode,
    email: String,
    password: String,
    confirmPassword: String,
    message: String?,
    isLoading: Boolean,
    onModeChange: (AuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Surface(
        modifier = modifier,
        color = ZyvioSurface1,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 32.dp),
        ) {
            ZyvioWordmark()

            Text(
                text = when (mode) {
                    AuthMode.SignIn -> "Bon retour"
                    AuthMode.SignUp -> "Créer votre compte"
                    AuthMode.ResetPassword -> "Réinitialiser le mot de passe"
                },
                modifier = Modifier.padding(top = 28.dp),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
            )

            Text(
                text = when (mode) {
                    AuthMode.SignIn -> "Connectez-vous pour retrouver vos playlists et votre progression."
                    AuthMode.SignUp -> "Un seul compte pour retrouver ZYVIOTV sur tous vos appareils."
                    AuthMode.ResetPassword -> "Saisissez votre adresse e-mail pour recevoir les instructions."
                },
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )

            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Adresse e-mail") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
            )

            if (mode != AuthMode.ResetPassword) {
                OutlinedTextField(
                    value = password,
                    onValueChange = onPasswordChange,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            if (mode == AuthMode.SignUp) {
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    label = { Text("Confirmer le mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(12.dp),
                )
            }

            message?.let {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    color = ZyvioRedTint,
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Button(
                onClick = onSubmit,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
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
                    onClick = { onModeChange(AuthMode.ResetPassword) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Mot de passe oublié ?")
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = ZyvioSurface2,
            )

            when (mode) {
                AuthMode.SignIn -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Pas encore de compte ?",
                            color = ZyvioTextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            enabled = !isLoading,
                            onClick = { onModeChange(AuthMode.SignUp) },
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
                        onClick = { onModeChange(AuthMode.SignIn) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Retour à la connexion")
                    }
                }
            }
        }
    }
}

@Composable
private fun TelevisionAuthLayout(
    mode: AuthMode,
    email: String,
    password: String,
    confirmPassword: String,
    message: String?,
    isLoading: Boolean,
    onModeChange: (AuthMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 72.dp, vertical = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 56.dp),
        ) {
            ZyvioWordmark()
            Text(
                text = "Bienvenue sur\nZYVIOTV",
                modifier = Modifier.padding(top = 28.dp),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "Connectez-vous avec votre compte ZYVIOTV pour accéder à votre expérience sur cet écran.",
                modifier = Modifier
                    .padding(top = 16.dp)
                    .widthIn(max = 520.dp),
                color = ZyvioTextSecondary,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        AuthFormCard(
            modifier = Modifier
                .width(560.dp)
                .verticalScroll(rememberScrollState()),
            mode = mode,
            email = email,
            password = password,
            confirmPassword = confirmPassword,
            message = message,
            isLoading = isLoading,
            onModeChange = onModeChange,
            onEmailChange = onEmailChange,
            onPasswordChange = onPasswordChange,
            onConfirmPasswordChange = onConfirmPasswordChange,
            onSubmit = onSubmit,
        )
    }
}

@Composable
private fun ZyvioWordmark() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ZYVIO",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "TV",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

private fun submitAuth(
    mode: AuthMode,
    email: String,
    password: String,
    confirmPassword: String,
    repository: SupabaseAuthRepository,
    setLoading: (Boolean) -> Unit,
    setMessage: (String?) -> Unit,
    onModeChange: (AuthMode) -> Unit,
    clearPasswords: () -> Unit,
    onAuthenticated: () -> Unit,
    launch: (suspend () -> Unit) -> Unit,
) {
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
        setMessage(
            validation.emailError
                ?: validation.passwordError
                ?: validation.confirmPasswordError,
        )
        return
    }

    launch {
        setLoading(true)
        setMessage(null)

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
                        setMessage("Compte créé. Vérifiez votre e-mail si une confirmation est demandée.")
                        onModeChange(AuthMode.SignIn)
                        clearPasswords()
                    }
                    AuthMode.ResetPassword -> {
                        setMessage("Si un compte existe pour cette adresse, un e-mail de réinitialisation a été envoyé.")
                        onModeChange(AuthMode.SignIn)
                    }
                }
            }

            is AuthResult.Failure -> {
                setMessage(result.message)
            }
        }

        setLoading(false)
    }
}
