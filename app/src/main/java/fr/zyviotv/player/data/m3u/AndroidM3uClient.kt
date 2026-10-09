package fr.zyviotv.player.data.m3u

import fr.zyviotv.player.data.network.NetworkDiagnostics
import fr.zyviotv.player.data.catalog.CatalogPerformanceDiagnostics
import fr.zyviotv.player.shared.m3u.M3uClient
import fr.zyviotv.player.shared.m3u.M3uEntry
import fr.zyviotv.player.shared.m3u.M3uImportResult
import fr.zyviotv.player.shared.m3u.M3uParser
import fr.zyviotv.player.shared.m3u.M3uSource
import fr.zyviotv.player.shared.m3u.M3uValidationResult
import fr.zyviotv.player.shared.m3u.M3uValidator
import java.io.EOFException
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

class AndroidM3uClient(
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
    private val tempDirectory: File? = null,
) : M3uClient {
    override suspend fun import(
        source: M3uSource,
        maxEntries: Int,
    ): M3uImportResult = withContext(Dispatchers.IO) {
        when (val validation = M3uValidator.validate(source)) {
            is M3uValidationResult.Invalid -> return@withContext M3uImportResult.Failure(validation.message)
            M3uValidationResult.Valid -> Unit
        }
        if (maxEntries <= 0) {
            return@withContext M3uImportResult.Success(emptyList(), totalParsed = 0)
        }

        val url = source.url.trim()
        val job = currentCoroutineContext()[Job]
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return@withContext download(url, maxEntries, job, attempt)
            } catch (error: CancellationException) {
                NetworkDiagnostics.failure("m3u", url, error, terminal = true)
                throw error
            } catch (error: M3uHttpStatusException) {
                val retry = attempt < MAX_ATTEMPTS && error.statusCode in RETRYABLE_HTTP_CODES
                if (retry) continue
                return@withContext M3uImportResult.Failure(
                    "Le serveur M3U a répondu avec le code ${error.statusCode}.",
                )
            } catch (error: IOException) {
                val retry = attempt < MAX_ATTEMPTS && error.isRetryableReadFailure()
                NetworkDiagnostics.failure("m3u", url, error, terminal = !retry)
                if (retry) continue
                return@withContext M3uImportResult.Failure(error.userMessage())
            } catch (error: Exception) {
                NetworkDiagnostics.failure("m3u", url, error)
                return@withContext M3uImportResult.Failure(
                    "Impossible d’ouvrir la playlist M3U. Vérifiez l’adresse et votre connexion.",
                )
            }
        }

        M3uImportResult.Failure("Impossible de télécharger complètement la playlist M3U.")
    }

    /**
     * Full-catalog path: finish and validate the download before emitting the first entry.
     * This prevents retaining both a huge M3U entry list and the mapped catalog in memory.
     */
    suspend fun importStreaming(
        source: M3uSource,
        onEntry: (M3uEntry) -> Unit,
    ): M3uStreamingResult = withContext(Dispatchers.IO) {
        when (val validation = M3uValidator.validate(source)) {
            is M3uValidationResult.Invalid ->
                return@withContext M3uStreamingResult.Failure(validation.message)
            M3uValidationResult.Valid -> Unit
        }

        val url = source.url.trim()
        val job = currentCoroutineContext()[Job]
        var downloaded: File? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                downloaded = downloadCompleteFile(url, job, attempt)
                break
            } catch (error: CancellationException) {
                NetworkDiagnostics.failure("m3u", url, error, terminal = true)
                throw error
            } catch (error: M3uHttpStatusException) {
                val retry = attempt < MAX_ATTEMPTS && error.statusCode in RETRYABLE_HTTP_CODES
                if (retry) continue
                return@withContext M3uStreamingResult.Failure(
                    "Le serveur M3U a répondu avec le code ${error.statusCode}.",
                )
            } catch (error: IOException) {
                val retry = attempt < MAX_ATTEMPTS && error.isRetryableReadFailure()
                NetworkDiagnostics.failure("m3u", url, error, terminal = !retry)
                if (retry) continue
                return@withContext M3uStreamingResult.Failure(error.userMessage())
            } catch (error: Exception) {
                NetworkDiagnostics.failure("m3u", url, error)
                return@withContext M3uStreamingResult.Failure(
                    "Impossible d’ouvrir la playlist M3U. Vérifiez l’adresse et votre connexion.",
                )
            }
        }

        val file = downloaded
            ?: return@withContext M3uStreamingResult.Failure(
                "Impossible de télécharger complètement la playlist M3U.",
            )
        try {
            val parseStartedAt = CatalogPerformanceDiagnostics.startedAt()
            val report = file.bufferedReader(Charsets.UTF_8).use { reader ->
                val lines = sequence {
                    var lineNumber = 0
                    while (true) {
                        if (lineNumber % CANCELLATION_CHECK_INTERVAL == 0 && job?.isActive == false) {
                            throw CancellationException("M3U import cancelled")
                        }
                        val line = reader.readLine() ?: break
                        yield(line)
                        lineNumber += 1
                    }
                }
                M3uParser.parseLinesDetailed(lines, Int.MAX_VALUE, onEntry)
            }
            CatalogPerformanceDiagnostics.phase("m3u_parse", parseStartedAt, report.emitted)
            if (report.danglingMetadata) {
                return@withContext M3uStreamingResult.Failure(
                    "Le téléchargement M3U s’est terminé avant la fin du contenu. Réessayez.",
                )
            }
            if (!report.headerSeen || report.emitted == 0) {
                return@withContext M3uStreamingResult.Failure("La playlist M3U est vide ou invalide.")
            }
            M3uStreamingResult.Success(report.emitted)
        } finally {
            file.delete()
        }
    }

    private fun downloadCompleteFile(
        url: String,
        job: Job?,
        attempt: Int,
    ): File {
        val startedAt = CatalogPerformanceDiagnostics.startedAt()
        val file = File.createTempFile("zyviotv-m3u-", ".tmp", tempDirectory)
        val connection = open(url)
        try {
            val code = connection.responseCode
            NetworkDiagnostics.response("m3u", url, code, attempt)
            if (code !in 200..299) throw M3uHttpStatusException(code)

            val expectedBytes = connection.contentLengthLong
            val counted = CountingInputStream(connection.inputStream)
            val decoded: InputStream = if (
                connection.contentEncoding.equals("gzip", ignoreCase = true)
            ) {
                GZIPInputStream(counted)
            } else {
                counted
            }
            decoded.use { input ->
                FileOutputStream(file).buffered().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        if (job?.isActive == false) throw CancellationException("M3U import cancelled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
            if (expectedBytes >= 0L && counted.count < expectedBytes) {
                throw M3uTruncatedException()
            }
            CatalogPerformanceDiagnostics.phase("m3u_download", startedAt)
            return file
        } catch (error: Throwable) {
            file.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun download(
        url: String,
        maxEntries: Int,
        job: Job?,
        attempt: Int,
    ): M3uImportResult {
        val connection = open(url)
        try {
            val code = connection.responseCode
            NetworkDiagnostics.response("m3u", url, code, attempt)
            if (code !in 200..299) throw M3uHttpStatusException(code)

            val expectedBytes = connection.contentLengthLong
            val counted = CountingInputStream(connection.inputStream)
            val decoded: InputStream = if (
                connection.contentEncoding.equals("gzip", ignoreCase = true)
            ) {
                GZIPInputStream(counted)
            } else {
                counted
            }

            val entries = ArrayList<M3uEntry>(minOf(maxEntries, INITIAL_CAPACITY))
            val report = decoded.bufferedReader(Charsets.UTF_8).use { reader ->
                val lines = sequence {
                    var lineNumber = 0
                    while (true) {
                        if (lineNumber % CANCELLATION_CHECK_INTERVAL == 0 && job?.isActive == false) {
                            throw CancellationException("M3U import cancelled")
                        }
                        val line = reader.readLine() ?: break
                        yield(line)
                        lineNumber += 1
                    }
                }
                M3uParser.parseLinesDetailed(lines, maxEntries) { entry -> entries += entry }
            }

            val sampled = maxEntries != Int.MAX_VALUE && report.reachedLimit
            if (!sampled && expectedBytes >= 0L && counted.count < expectedBytes) {
                throw M3uTruncatedException()
            }
            if (!sampled && report.danglingMetadata) {
                throw M3uTruncatedException()
            }
            if (!report.headerSeen || report.emitted == 0) {
                return M3uImportResult.Failure("La playlist M3U est vide ou invalide.")
            }

            return M3uImportResult.Success(entries = entries, totalParsed = report.emitted)
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doInput = true
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/x-mpegURL, audio/x-mpegurl, text/plain, */*")
            setRequestProperty("Accept-Encoding", "gzip")
            setRequestProperty("User-Agent", PRIMARY_USER_AGENT)
        }

    private fun IOException.isRetryableReadFailure(): Boolean =
        this is SocketTimeoutException ||
            this is EOFException ||
            this is ProtocolException ||
            this is SocketException

    private fun IOException.userMessage(): String = when (this) {
        is SocketTimeoutException -> "Le serveur M3U a interrompu la lecture par dépassement du délai. Réessayez."
        is EOFException, is ProtocolException ->
            "Le téléchargement M3U s’est terminé avant la fin du contenu. Réessayez."
        is SocketException -> "La connexion M3U a été interrompue pendant le téléchargement. Réessayez."
        else -> "La playlist M3U a été reçue, mais sa lecture a échoué. Réessayez."
    }

    private class M3uHttpStatusException(val statusCode: Int) : IOException()
    private class M3uTruncatedException : EOFException()

    private class CountingInputStream(input: InputStream) : FilterInputStream(input) {
        var count: Long = 0L
            private set

        override fun read(): Int = super.read().also { if (it >= 0) count += 1 }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) count += it }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_ATTEMPTS = 2
        const val INITIAL_CAPACITY = 256
        const val CANCELLATION_CHECK_INTERVAL = 256
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val PRIMARY_USER_AGENT = "ZYVIOTV-Player/0.1 (Android)"
        val RETRYABLE_HTTP_CODES = setOf(408, 425, 429, 500, 502, 503, 504)
    }
}

sealed interface M3uStreamingResult {
    data class Success(val totalParsed: Int) : M3uStreamingResult
    data class Failure(val message: String) : M3uStreamingResult
}
