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
