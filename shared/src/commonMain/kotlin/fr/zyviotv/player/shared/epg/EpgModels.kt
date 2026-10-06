package fr.zyviotv.player.shared.epg

data class EpgProgramme(
    val channelId: String,
    val title: String,
    val description: String? = null,
    val startEpochSeconds: Long,
    val endEpochSeconds: Long,
)

data class EpgNowNext(
    val now: EpgProgramme?,
    val next: EpgProgramme?,
)

object EpgTimeline {
    fun nowNext(
        programmes: List<EpgProgramme>,
        nowEpochSeconds: Long,
    ): EpgNowNext {
        val ordered = programmes
            .filter { it.endEpochSeconds > it.startEpochSeconds }
            .sortedBy { it.startEpochSeconds }

        val current = ordered.firstOrNull {
            nowEpochSeconds >= it.startEpochSeconds &&
                nowEpochSeconds < it.endEpochSeconds
        }

        val next = ordered.firstOrNull {
            it.startEpochSeconds >= (current?.endEpochSeconds ?: nowEpochSeconds)
        }

        return EpgNowNext(
            now = current,
            next = next,
        )
    }

    fun progress(
        programme: EpgProgramme,
        nowEpochSeconds: Long,
    ): Float {
        val duration = programme.endEpochSeconds - programme.startEpochSeconds
        if (duration <= 0L) return 0f

        val elapsed = nowEpochSeconds - programme.startEpochSeconds
        return (elapsed.toDouble() / duration.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
    }
}


data class EpgWindow(
    val fromEpochSeconds: Long,
    val toEpochSeconds: Long,
) {
    init {
        require(toEpochSeconds > fromEpochSeconds)
    }

    fun contains(programme: EpgProgramme): Boolean =
        programme.endEpochSeconds > fromEpochSeconds &&
            programme.startEpochSeconds < toEpochSeconds

    companion object {
        fun around(
            nowEpochSeconds: Long,
            beforeSeconds: Long = 3 * 60 * 60,
            afterSeconds: Long = 6 * 60 * 60,
        ): EpgWindow = EpgWindow(
            fromEpochSeconds = nowEpochSeconds - beforeSeconds,
            toEpochSeconds = nowEpochSeconds + afterSeconds,
        )
    }
}

sealed interface EpgLoadResult {
    data class Success(val programmes: List<EpgProgramme>) : EpgLoadResult
    data class Failure(val message: String) : EpgLoadResult
}

interface EpgRepository {
    suspend fun load(
        channelId: String,
        window: EpgWindow,
    ): EpgLoadResult
}
