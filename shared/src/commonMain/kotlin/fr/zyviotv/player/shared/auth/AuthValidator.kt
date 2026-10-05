package fr.zyviotv.player.shared.auth

object AuthValidator {
    private val emailPattern = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun validateSignIn(email: String, password: String): AuthValidationResult {
        val emailError = validateEmail(email)
        val passwordError = if (password.length < 8) {
            "Le mot de passe doit contenir au moins 8 caractères."
        } else {
            null
        }

        return AuthValidationResult(
            isValid = emailError == null && passwordError == null,
            emailError = emailError,
            passwordError = passwordError,
        )
    }

    fun validateSignUp(
        email: String,
        password: String,
        confirmPassword: String,
    ): AuthValidationResult {
        val base = validateSignIn(email, password)
        val confirmPasswordError = when {
            confirmPassword.isBlank() -> "Confirmez votre mot de passe."
            confirmPassword != password -> "Les mots de passe ne correspondent pas."
            else -> null
        }

        return base.copy(
            isValid = base.isValid && confirmPasswordError == null,
            confirmPasswordError = confirmPasswordError,
        )
    }

    fun validateReset(email: String): AuthValidationResult {
        val emailError = validateEmail(email)
        return AuthValidationResult(
            isValid = emailError == null,
            emailError = emailError,
        )
    }

    private fun validateEmail(email: String): String? {
        val normalized = email.trim()
        return when {
            normalized.isBlank() -> "Saisissez votre adresse e-mail."
            !emailPattern.matches(normalized) -> "Adresse e-mail invalide."
            else -> null
        }
    }
}
