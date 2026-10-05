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


sealed interface M3uImportResult {
    data class Success(
        val entries: List<M3uEntry>,
        val totalParsed: Int,
    ) : M3uImportResult

    data class Failure(val message: String) : M3uImportResult
}

interface M3uClient {
    suspend fun import(
        source: M3uSource,
        maxEntries: Int = Int.MAX_VALUE,
    ): M3uImportResult
}
