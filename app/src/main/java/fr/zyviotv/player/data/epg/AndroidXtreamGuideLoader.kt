package fr.zyviotv.player.data.epg

import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.epg.EpgLoadResult
import fr.zyviotv.player.shared.epg.EpgProgramme
import fr.zyviotv.player.shared.epg.EpgWindow
import fr.zyviotv.player.shared.xtream.XtreamCredentials
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class GuideChannelData(
    val channelId: String,
    val programmes: List<EpgProgramme>,
)

sealed interface GuideLoadResult {
    data class Success(
        val channels: List<GuideChannelData>,
        val failedChannels: Int,
    ) : GuideLoadResult

    data class Failure(val message: String) : GuideLoadResult
}

class AndroidXtreamGuideLoader(
    private val credentials: XtreamCredentials,
) {
    suspend fun load(
        channels: List<CatalogLiveChannel>,
        window: EpgWindow,
        maxChannels: Int = MAX_CHANNELS,
    ): GuideLoadResult = coroutineScope {
        val bounded = channels.take(maxChannels.coerceIn(1, MAX_CHANNELS))
        if (bounded.isEmpty()) {
            return@coroutineScope GuideLoadResult.Success(
                channels = emptyList(),
                failedChannels = 0,
            )
        }

        val repository = AndroidXtreamEpgRepository(credentials)
        val loaded = mutableListOf<GuideChannelData>()
        var failures = 0

        bounded.chunked(MAX_CONCURRENT_REQUESTS).forEach { batch ->
            val results = batch.map { channel ->
                async {
                    channel to repository.load(channel.id, window)
                }
            }.awaitAll()

            results.forEach { (channel, result) ->
                when (result) {
                    is EpgLoadResult.Success -> loaded += GuideChannelData(
                        channelId = channel.id,
                        programmes = result.programmes,
                    )
                    is EpgLoadResult.Failure -> {
                        failures += 1
                        loaded += GuideChannelData(
                            channelId = channel.id,
                            programmes = emptyList(),
                        )
                    }
                }
            }
        }

        if (failures == bounded.size) {
            GuideLoadResult.Failure("Impossible de charger le guide TV du fournisseur.")
        } else {
            GuideLoadResult.Success(
                channels = loaded,
                failedChannels = failures,
            )
        }
    }

    private companion object {
        const val MAX_CHANNELS = 50
        const val MAX_CONCURRENT_REQUESTS = 8
    }
}
