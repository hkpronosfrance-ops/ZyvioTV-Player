package fr.zyviotv.player.shared.xtream

object XtreamValidator {
    fun validate(credentials: XtreamCredentials): XtreamValidationResult {
        val server = credentials.serverUrl.trim()
        val username = credentials.username.trim()
        val password = credentials.password

        if (server.isBlank()) {
            return XtreamValidationResult.Invalid("Saisissez l’adresse du serveur.")
        }

        if (!server.startsWith("http://") && !server.startsWith("https://")) {
            return XtreamValidationResult.Invalid("L’adresse du serveur doit commencer par http:// ou https://.")
        }

        if (username.isBlank()) {
            return XtreamValidationResult.Invalid("Saisissez votre identifiant Xtream.")
        }

        if (password.isBlank()) {
            return XtreamValidationResult.Invalid("Saisissez votre mot de passe Xtream.")
        }

        return XtreamValidationResult.Valid
    }
}
