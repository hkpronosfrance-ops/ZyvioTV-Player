package fr.zyviotv.player.ui.diagnostics

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.FrameMetrics
import android.view.Window

/**
 * Feeds [FrameStatsAggregator] from the window's FrameMetrics (API 24+, no
 * extra dependency) and logs one line per screen visit under `ZyvioUi`.
 */
object FrameStatsMonitor {
    private const val TAG = "ZyvioUi"
    private val aggregator = FrameStatsAggregator()
    private var thread: HandlerThread? = null
    private var listener: Window.OnFrameMetricsAvailableListener? = null

    fun attach(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || listener != null) return
        val handlerThread = HandlerThread("zyvio-frame-stats").also { it.start() }
        val frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            aggregator.onFrame(metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
        runCatching {
            activity.window.addOnFrameMetricsAvailableListener(frameListener, Handler(handlerThread.looper))
        }.onSuccess {
            thread = handlerThread
            listener = frameListener
        }.onFailure {
            handlerThread.quitSafely()
        }
    }

    fun detach(activity: Activity) {
        val frameListener = listener ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { activity.window.removeOnFrameMetricsAvailableListener(frameListener) }
        }
        listener = null
        thread?.quitSafely()
        thread = null
        flush()
    }

    /** Called on navigation: reports the screen being left. */
    fun setScreen(route: String?) {
        aggregator.switchTo(route)?.let { Log.i(TAG, it) }
    }

    /** Called when the app goes to the background. */
    fun flush() {
        aggregator.summaryOrNull()?.let { Log.i(TAG, it) }
        aggregator.switchTo(null)
    }
}
