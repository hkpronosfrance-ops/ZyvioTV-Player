package fr.zyviotv.player.shared.m3u

data class M3uSource(
    val url: String,
)

data class M3uEntry(
    val name: String,
    val streamUrl: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
)

sealed interface M3uValidationResult {
    data object Valid : M3uValidationResult
    data class Invalid(val message: String) : M3uValidationResult
}
