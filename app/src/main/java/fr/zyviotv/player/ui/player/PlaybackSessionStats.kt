package fr.zyviotv.player.ui.player

import kotlin.random.Random

/** Why the player entered BUFFERING after it had started (bloc #211). */
enum class BufferingCause(val logName: String) {
    /** Before the first READY of the session. */
    Startup("startup"),

    /** Right after a seek requested by the viewer. */
    Seek("seek"),

    /** Right after a reload (silent retry, Retry button, container switch, live window). */
    Reload("reload"),

    /** No known reason: the stream stalled while playing. */
    Stall("stall"),
}

/**
 * Aggregated, anonymous statistics of one playback (one Media3 player), logged
 * once when the player is released: no periodic logging.
 *
 * Holds counts, durations, codec names and MIME types only. It never receives
 * a URL, host, title, header or credential, and free-form values are
 * reduced to a short safe alphabet before they reach the log line.
 */
class PlaybackSessionStats(
    val sessionId: String,
    private val kind: String,
    private val clock: () -> Long,
) {
    private val startedAtMs = clock()
    private var container = "unknown"
    private var attempts = 0

    private var firstReadyMs: Long? = null
    private var firstFrameMs: Long? = null
    private var readyCount = 0

    private var bufferingStartedAtMs: Long? = null
    private var bufferingCause = BufferingCause.Startup
    private var pendingCause: BufferingCause? = null
    private val bufferingCounts = IntArray(BufferingCause.entries.size)
    private var stallTotalMs = 0L
    private var stallMaxMs = 0L

    private var playingSinceMs: Long? = null
    private var playingTotalMs = 0L

    private var seeks = 0
    private var silentReloads = 0
    private var manualRetries = 0
    private var behindLiveWindow = 0
    private var droppedFrames = 0L
    private val discontinuities = sortedMapOf<String, Int>()
    private val loadErrors = sortedMapOf<String, Int>()

    private var videoMime: String? = null
    private var videoSize: String? = null
    private var videoDecoder: String? = null
    private var audioMime: String? = null
    private var audioDecoder: String? = null
    private var lastBandwidthKbps: Long? = null
    private var maxBandwidthKbps: Long? = null

    fun onPrepare(containerName: String) {
        container = safeToken(containerName)
        attempts += 1
    }

    fun onBuffering() {
        if (bufferingStartedAtMs != null) return
        bufferingStartedAtMs = clock()
        bufferingCause = when {
            firstReadyMs == null -> BufferingCause.Startup
            else -> pendingCause ?: BufferingCause.Stall
        }
        pendingCause = null
        bufferingCounts[bufferingCause.ordinal] += 1
    }

    fun onReady() {
        val now = clock()
        readyCount += 1
        if (firstReadyMs == null) firstReadyMs = now - startedAtMs
        closeBuffering(now)
        pendingCause = null
    }

    /** ENDED or IDLE: an open buffering period stops counting. */
    fun onIdleOrEnded() {
        closeBuffering(clock())
    }

    fun onIsPlaying(isPlaying: Boolean) {
        val now = clock()
        if (isPlaying) {
            if (playingSinceMs == null) playingSinceMs = now
        } else {
            playingSinceMs?.let { playingTotalMs += now - it }
            playingSinceMs = null
        }
    }

    fun onFirstFrame() {
        if (firstFrameMs == null) firstFrameMs = clock() - startedAtMs
    }

    /**
     * A seek discontinuity. The seek that a reload performs to restore the
     * position belongs to that reload and is not counted as a viewer seek.
     */
    fun onSeekDiscontinuity() {
        if (pendingCause == BufferingCause.Reload) return
        seeks += 1
        pendingCause = BufferingCause.Seek
    }

    fun onSilentReload() {
        silentReloads += 1
        pendingCause = BufferingCause.Reload
    }

    fun onManualRetry() {
        manualRetries += 1
        pendingCause = BufferingCause.Reload
    }

    fun onContainerSwitch() {
        pendingCause = BufferingCause.Reload
    }

    fun onBehindLiveWindow() {
        behindLiveWindow += 1
        pendingCause = BufferingCause.Reload
    }

    fun onDiscontinuity(reason: String) {
        increment(discontinuities, safeToken(reason))
    }

    fun onDroppedFrames(count: Int) {
        if (count > 0) droppedFrames += count
    }

    fun onVideoFormat(mime: String?, width: Int, height: Int) {
        videoMime = mime?.let(::safeMime)
        videoSize = if (width > 0 && height > 0) "${width}x$height" else null
    }

    fun onVideoDecoder(name: String) {
        videoDecoder = safeToken(name)
    }

    fun onAudioFormat(mime: String?) {
        audioMime = mime?.let(::safeMime)
    }

    fun onAudioDecoder(name: String) {
        audioDecoder = safeToken(name)
    }

    fun onBandwidthEstimate(bitsPerSecond: Long) {
        if (bitsPerSecond <= 0L) return
        val kbps = bitsPerSecond / 1_000L
        lastBandwidthKbps = kbps
        maxBandwidthKbps = maxOf(maxBandwidthKbps ?: 0L, kbps)
    }

    /** [errorKind] is a class simple name or a classifier name, never a message. */
    fun onLoadError(errorKind: String) {
        increment(loadErrors, safeToken(errorKind))
    }

    fun bufferingCount(cause: BufferingCause): Int = bufferingCounts[cause.ordinal]

    fun summary(endReason: String): String {
        val now = clock()
        closeBuffering(now)
        val playing = playingTotalMs + (playingSinceMs?.let { now - it } ?: 0L)
        return buildString {
            append("session=").append(sessionId)
            append(" kind=").append(safeToken(kind))
            append(" end=").append(safeToken(endReason))
            append(" duration_s=").append((now - startedAtMs) / 1_000L)
            append(" playing_s=").append(playing / 1_000L)
            append(" container=").append(container)
            append(" attempts=").append(attempts)
            append(" first_ready_ms=").append(firstReadyMs ?: "none")
            append(" first_frame_ms=").append(firstFrameMs ?: "none")
            append(" ready=").append(readyCount)
            BufferingCause.entries.forEach { cause ->
                append(" buffering_").append(cause.logName).append('=').append(bufferingCounts[cause.ordinal])
            }
            append(" stall_ms_total=").append(stallTotalMs)
            append(" stall_ms_max=").append(stallMaxMs)
            append(" seeks=").append(seeks)
            append(" silent_reloads=").append(silentReloads)
            append(" manual_retries=").append(manualRetries)
            append(" behind_live_window=").append(behindLiveWindow)
            append(" discontinuities=").append(discontinuities.format())
            append(" dropped_frames=").append(droppedFrames)
            append(" video=").append(videoMime ?: "none")
            append(" video_size=").append(videoSize ?: "none")
            append(" video_decoder=").append(videoDecoder ?: "none")
            append(" audio=").append(audioMime ?: "none")
            append(" audio_decoder=").append(audioDecoder ?: "none")
            append(" bandwidth_kbps_last=").append(lastBandwidthKbps ?: "none")
            append(" bandwidth_kbps_max=").append(maxBandwidthKbps ?: "none")
            append(" load_errors=").append(loadErrors.format())
        }
    }

    private fun closeBuffering(now: Long) {
        val started = bufferingStartedAtMs ?: return
        bufferingStartedAtMs = null
        if (bufferingCause == BufferingCause.Stall) {
            val duration = (now - started).coerceAtLeast(0L)
            stallTotalMs += duration
            stallMaxMs = maxOf(stallMaxMs, duration)
        }
    }

    private fun increment(target: MutableMap<String, Int>, key: String) {
        // Bounded: unknown values beyond the limit are grouped.
        val bucket = if (key in target || target.size < MAX_DISTINCT_KEYS) key else "other"
        target[bucket] = (target[bucket] ?: 0) + 1
    }

    private fun Map<String, Int>.format(): String =
        if (isEmpty()) "none" else entries.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        private const val MAX_DISTINCT_KEYS = 8
        private const val MAX_TOKEN_LENGTH = 64

        fun newSessionId(random: Random = Random.Default): String =
            random.nextInt().toUInt().toString(16).padStart(8, '0')

        /**
         * Codec, class and reason names. Anything outside a short dotted ASCII
         * token, or containing a sensitive word, is reported as "other".
         */
        internal fun safeToken(value: String): String {
            val lower = value.lowercase()
            if (!TOKEN_PATTERN.matches(lower)) return "other"
            if (SENSITIVE_FRAGMENTS.any(lower::contains)) return "other"
            return lower
        }

        internal fun safeMime(value: String): String {
            val lower = value.lowercase()
            return if (MIME_PATTERN.matches(lower)) lower else "other"
        }

        private val TOKEN_PATTERN = Regex("[a-z0-9][a-z0-9._-]{0,${MAX_TOKEN_LENGTH - 1}}")
        private val MIME_PATTERN = Regex("(video|audio|text|application)/[a-z0-9.+-]{1,40}")
        private val SENSITIVE_FRAGMENTS = listOf("password", "passwd", "token", "authorization", "username", "secret")
    }
}
