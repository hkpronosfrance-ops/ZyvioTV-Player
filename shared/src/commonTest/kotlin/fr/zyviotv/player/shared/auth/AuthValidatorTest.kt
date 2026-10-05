package fr.zyviotv.player.shared.auth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthValidatorTest {
    @Test
    fun validSignInPasses() {
        val result = AuthValidator.validateSignIn(
            email = "user@example.com",
            password = "password123",
        )

        assertTrue(result.isValid)
        assertNull(result.emailError)
        assertNull(result.passwordError)
    }

    @Test
    fun shortPasswordFails() {
        val result = AuthValidator.validateSignIn(
            email = "user@example.com",
            password = "123",
        )

        assertFalse(result.isValid)
    }

    @Test
    fun signUpRequiresMatchingPasswords() {
        val result = AuthValidator.validateSignUp(
            email = "user@example.com",
            password = "password123",
            confirmPassword = "password456",
        )

        assertFalse(result.isValid)
    }
}
