package fr.zyviotv.player.shared.m3u

object M3uValidator {
    fun validate(source: M3uSource): M3uValidationResult {
        val value = source.url.trim()

        if (value.isBlank()) {
            return M3uValidationResult.Invalid("Saisissez l’adresse de la playlist M3U.")
        }

        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            return M3uValidationResult.Invalid("La playlist doit utiliser une adresse http:// ou https://.")
        }

        return M3uValidationResult.Valid
    }
}
