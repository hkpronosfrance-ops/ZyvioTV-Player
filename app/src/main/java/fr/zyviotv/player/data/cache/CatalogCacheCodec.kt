package fr.zyviotv.player.data.cache

import fr.zyviotv.player.data.catalog.SeriesDetailSource
import fr.zyviotv.player.data.catalog.SeriesEpisodeSource
import fr.zyviotv.player.shared.catalog.CatalogCategory
import fr.zyviotv.player.shared.catalog.CatalogLiveChannel
import fr.zyviotv.player.shared.catalog.CatalogMovie
import fr.zyviotv.player.shared.catalog.CatalogSeries
import fr.zyviotv.player.shared.catalog.CatalogSnapshot
import java.io.DataInputStream
import java.io.DataOutputStream

internal object CatalogCacheCodec {
    fun write(output: DataOutputStream, catalog: CachedCatalog) {
        output.writeInt(VERSION)
        output.writeString(catalog.playlistId)
        output.writeString(catalog.playlistName)
        with(catalog.snapshot) {
            output.writeList(liveCategories) { category -> output.writeCategory(category) }
            output.writeList(liveChannels) { channel ->
                output.writeString(channel.id)
                output.writeString(channel.name)
                output.writeNullableString(channel.categoryId)
                output.writeNullableString(channel.logoUrl)
                output.writeString(channel.streamUrl)
                output.writeNullableString(channel.epgId)
            }
            output.writeList(movieCategories) { category -> output.writeCategory(category) }
            output.writeList(movies) { movie ->
                output.writeString(movie.id)
                output.writeString(movie.title)
                output.writeNullableString(movie.categoryId)
                output.writeNullableString(movie.posterUrl)
                output.writeString(movie.streamUrl)
                output.writeString(movie.containerExtension)
                output.writeNullableLong(movie.addedAtEpochSeconds)
            }
            output.writeList(seriesCategories) { category -> output.writeCategory(category) }
            output.writeList(series) { series ->
                output.writeString(series.id)
                output.writeString(series.title)
                output.writeNullableString(series.categoryId)
                output.writeNullableString(series.posterUrl)
                output.writeNullableLong(series.addedAtEpochSeconds)
            }
        }
        output.writeInt(catalog.seriesDetails.size)
        catalog.seriesDetails.forEach { (seriesId, detail) ->
            output.writeString(seriesId)
            output.writeNullableString(detail.title)
            output.writeNullableString(detail.year)
            output.writeNullableString(detail.synopsis)
            output.writeList(detail.genres) { output.writeString(it) }
            output.writeList(detail.episodes) { episode ->
                output.writeString(episode.id)
                output.writeInt(episode.season)
                output.writeInt(episode.number)
                output.writeString(episode.title)
                output.writeNullableString(episode.synopsis)
                output.writeString(episode.streamUrl)
            }
        }
    }

    fun read(input: DataInputStream): CachedCatalog {
        require(input.readInt() == VERSION) { "Unsupported catalog cache version" }
        val playlistId = input.readString()
        val playlistName = input.readString()
        val liveCategories = input.readList { input.readCategory() }
        val liveChannels = input.readList {
            CatalogLiveChannel(
                id = input.readString(),
                name = input.readString(),
                categoryId = input.readNullableString(),
                logoUrl = input.readNullableString(),
                streamUrl = input.readString(),
                epgId = input.readNullableString(),
            )
        }
        val movieCategories = input.readList { input.readCategory() }
        val movies = input.readList {
            CatalogMovie(
                id = input.readString(),
                title = input.readString(),
                categoryId = input.readNullableString(),
                posterUrl = input.readNullableString(),
                streamUrl = input.readString(),
                containerExtension = input.readString(),
                addedAtEpochSeconds = input.readNullableLong(),
            )
        }
        val seriesCategories = input.readList { input.readCategory() }
        val series = input.readList {
            CatalogSeries(
                id = input.readString(),
                title = input.readString(),
                categoryId = input.readNullableString(),
                posterUrl = input.readNullableString(),
                addedAtEpochSeconds = input.readNullableLong(),
            )
        }
        val details = LinkedHashMap<String, SeriesDetailSource>()
        repeat(input.readCount()) {
            val seriesId = input.readString()
            details[seriesId] = SeriesDetailSource(
                title = input.readNullableString(),
                year = input.readNullableString(),
                synopsis = input.readNullableString(),
                genres = input.readList { input.readString() },
                episodes = input.readList {
                    SeriesEpisodeSource(
                        id = input.readString(),
                        season = input.readInt(),
                        number = input.readInt(),
                        title = input.readString(),
                        synopsis = input.readNullableString(),
                        streamUrl = input.readString(),
                    )
                },
            )
        }
        return CachedCatalog(
            playlistId = playlistId,
            playlistName = playlistName,
            snapshot = CatalogSnapshot(
                liveCategories = liveCategories,
                liveChannels = liveChannels,
                movieCategories = movieCategories,
                movies = movies,
                seriesCategories = seriesCategories,
                series = series,
            ),
            seriesDetails = details,
        )
    }

    private fun DataOutputStream.writeCategory(value: CatalogCategory) {
        writeString(value.id)
        writeString(value.name)
    }

    private fun DataInputStream.readCategory() = CatalogCategory(
        id = readString(),
        name = readString(),
    )

    private fun <T> DataOutputStream.writeList(values: List<T>, writeValue: (T) -> Unit) {
        writeInt(values.size)
        values.forEach(writeValue)
    }

    private fun <T> DataInputStream.readList(readValue: () -> T): List<T> {
        val count = readCount()
        return ArrayList<T>(count).also { values ->
            repeat(count) { values += readValue() }
        }
    }

    private fun DataInputStream.readCount(): Int {
        val count = readInt()
        require(count in 0..MAX_COLLECTION_SIZE) { "Invalid catalog collection size" }
        return count
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_STRING_BYTES)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val length = readInt()
        require(length in 0..MAX_STRING_BYTES) { "Invalid catalog string size" }
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun DataOutputStream.writeNullableString(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeString(value)
    }

    private fun DataInputStream.readNullableString(): String? =
        if (readBoolean()) readString() else null

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readNullableLong(): Long? =
        if (readBoolean()) readLong() else null

    private const val VERSION = 1
    private const val MAX_COLLECTION_SIZE = 1_000_000
    private const val MAX_STRING_BYTES = 2 * 1024 * 1024
}
