package fr.zyviotv.player.data.epg

import java.text.SimpleDateFormat
import java.util.Locale

internal object EpgTimeParsing {
    fun xmlTvEpochSeconds(raw: String): Long? {
        val normalized = raw
            .trim()
            .replace(Regex("""(\d{12,14})([+-]\d{4})$"""), "$1 $2")

        val candidates = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmm Z",
            "yyyyMMddHHmmssX",
            "yyyyMMddHHmmX",
        )

        for (pattern in candidates) {
            try {
                val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                return formatter.parse(normalized)?.time?.div(1000L)
            } catch (_: Exception) {
                Unit
            }
        }
        return null
    }

    fun epochSeconds(value: Long): Long? {
        if (value == Long.MIN_VALUE || value <= 0L) return null
        return if (value >= MILLIS_EPOCH_THRESHOLD) value / 1000L else value
    }

    private const val MILLIS_EPOCH_THRESHOLD = 100_000_000_000L
}
