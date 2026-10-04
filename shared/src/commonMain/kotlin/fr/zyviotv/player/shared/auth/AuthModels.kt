package fr.zyviotv.player.shared.auth

enum class AuthMode {
    SignIn,
    SignUp,
    ResetPassword,
}

data class AuthCredentials(
    val email: String,
    val password: String,
)

data class RegistrationCredentials(
    val email: String,
    val password: String,
    val confirmPassword: String,
)

data class AuthValidationResult(
    val isValid: Boolean,
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
)

sealed interface AuthResult {
    data object Success : AuthResult
    data class Failure(val message: String) : AuthResult
}

interface AuthRepository {
    suspend fun signIn(credentials: AuthCredentials): AuthResult
    suspend fun signUp(credentials: RegistrationCredentials): AuthResult
    suspend fun requestPasswordReset(email: String): AuthResult
    suspend fun signOut(): AuthResult
}
