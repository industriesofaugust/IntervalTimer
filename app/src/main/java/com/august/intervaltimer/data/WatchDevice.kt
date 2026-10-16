package com.august.intervaltimer.data

import android.content.Context
import android.net.Uri
import androidx.core.content.pm.PackageInfoCompat
import com.august.intervaltimer.sync.LivePaths
import com.august.intervaltimer.sync.WatchInfo
import com.august.intervaltimer.sync.WatchInfoCodec
import com.august.intervaltimer.sync.WatchInfoSync
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** How well the phone can talk to the watch, from worst to best. */
enum class WatchConnection {
    /** No watch is paired, or none is known to this phone. */
    NO_WATCH,

    /** A watch is connected but Interval Timer is not installed on it. */
    APP_MISSING,

    /** The watch has the app but cannot be reached right now. */
    NOT_REACHABLE,

    CONNECTED,
}

/**
 * Works out the connection from what Google Play services reports. A watch
 * that has the app but is out of range counts as NOT_REACHABLE even though it
 * is no longer a connected node: the phone still remembers it has the app.
 */
fun watchConnection(
    hasConnectedNode: Boolean,
    hasNodeWithApp: Boolean,
    hasReachableNodeWithApp: Boolean,
): WatchConnection = when {
    hasReachableNodeWithApp -> WatchConnection.CONNECTED
    hasNodeWithApp -> WatchConnection.NOT_REACHABLE
    hasConnectedNode -> WatchConnection.APP_MISSING
    else -> WatchConnection.NO_WATCH
}

data class WatchDeviceState(
    val connection: WatchConnection = WatchConnection.NO_WATCH,
    /** Null when no watch is known. */
    val name: String? = null,
    /** False means connected through the cloud rather than directly by Bluetooth. */
    val isNearby: Boolean = true,
    /** What the watch app last reported; null if it never has. */
    val info: WatchInfo? = null,
    val phoneVersionName: String = "",
    val phoneVersionCode: Long = 0,
) {
    /** Both apps should be the same build, or sync can misbehave. */
    val versionMismatch: Boolean
        get() = info != null && info.appVersionCode != phoneVersionCode
}

/**
 * What the phone knows about the watch, kept for the whole app process: the
 * connected device, whether our app is on it, whether it can be reached, and
 * what the watch app last reported about itself. One of these lives in
 * [com.august.intervaltimer.TimerApp]; screens call [refresh] when they
 * appear, and Play services tells us about changes while they are open.
 *
 * [publishTemplates] sends the current templates to the watch (for SYNC NOW).
 */
class WatchDevice(
    private val context: Context,
    private val scope: CoroutineScope,
    private val publishTemplates: suspend () -> Unit,
) : DataClient.OnDataChangedListener {

    private val dataClient = Wearable.getDataClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)

    private val watchInfoUri = Uri.Builder().scheme("wear").path(WatchInfoSync.PATH).build()

    private val _state = MutableStateFlow(readPhoneVersion())
    val state: StateFlow<WatchDeviceState> = _state.asStateFlow()

    private val _syncing = MutableStateFlow(false)

    /** True from tapping SYNC NOW until the watch reports back (or we give up). */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // Both only say that something changed, so each just checks again.
        runCatching {
            val listener = CapabilityClient.OnCapabilityChangedListener { refresh() }
            capabilityClient.addListener(listener, LivePaths.WATCH_CAPABILITY)
        }
        runCatching { dataClient.addListener(this, watchInfoUri, DataClient.FILTER_LITERAL) }
        refresh()
    }

    private fun readPhoneVersion(): WatchDeviceState {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return WatchDeviceState(
            phoneVersionName = info.versionName ?: "?",
            phoneVersionCode = PackageInfoCompat.getLongVersionCode(info),
        )
    }

    /** Asks Play services again. Safe to call as often as a screen appears. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val found = runCatching { findWatch() }.getOrNull()
            val infoRead = runCatching { readWatchInfo() }
            // runCatching also swallows cancellation; a newer refresh owns the result.
            ensureActive()
            _state.update { old ->
                old.copy(
                    connection = found?.connection ?: WatchConnection.NO_WATCH,
                    name = found?.name,
                    isNearby = found?.isNearby ?: true,
                    // A failed read keeps what we had; a read that found nothing clears it.
                    info = if (infoRead.isSuccess) infoRead.getOrNull() else old.info,
                )
            }
        }
    }

    private class FoundWatch(val connection: WatchConnection, val name: String?, val isNearby: Boolean)

    private suspend fun findWatch(): FoundWatch {
        val connected = nodeClient().connectedNodes.await()
        val withApp = capabilityClient
            .getCapability(LivePaths.WATCH_CAPABILITY, CapabilityClient.FILTER_ALL).await().nodes
        val reachable = capabilityClient
            .getCapability(LivePaths.WATCH_CAPABILITY, CapabilityClient.FILTER_REACHABLE).await().nodes

        val connection = watchConnection(
            hasConnectedNode = connected.isNotEmpty(),
            hasNodeWithApp = withApp.isNotEmpty(),
            hasReachableNodeWithApp = reachable.isNotEmpty(),
        )
        // Prefer the watch that has our app, then any connected watch.
        val node = reachable.firstOrNull() ?: withApp.firstOrNull() ?: connected.firstOrNull()
        return FoundWatch(connection, node?.displayName, node?.isNearby ?: true)
    }

    private fun nodeClient() = Wearable.getNodeClient(context)

    private suspend fun readWatchInfo(): WatchInfo? {
        val buffer = dataClient.getDataItems(watchInfoUri, DataClient.FILTER_LITERAL).await()
        try {
            return buffer.firstNotNullOfOrNull { decodeInfo(it) }
        } finally {
            buffer.release()
        }
    }

    private fun decodeInfo(item: DataItem): WatchInfo? {
        if (item.uri.path != WatchInfoSync.PATH) return null
        val json = DataMapItem.fromDataItem(item).dataMap.getString(WatchInfoSync.KEY)
        return json?.let { WatchInfoCodec.decode(it) }
    }

    /** The watch published new info, so this is also the "templates arrived" signal. */
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type != DataEvent.TYPE_CHANGED) return@forEach
            val info = runCatching { decodeInfo(event.dataItem) }.getOrNull() ?: return@forEach
            _state.update { it.copy(info = info) }
        }
    }

    /**
     * Sends the templates again right now. [syncing] stays true until the
     * watch reports a new receive time, or [SYNC_WAIT_MILLIS] pass.
     */
    fun syncNow() {
        if (_syncing.value) return
        _syncing.value = true
        scope.launch {
            try {
                val before = _state.value.info?.templatesReceivedAtEpochMillis
                val sent = runCatching { publishTemplates() }.isSuccess
                if (sent) {
                    withTimeoutOrNull(SYNC_WAIT_MILLIS) {
                        _state.first { it.info?.templatesReceivedAtEpochMillis != before }
                    }
                }
            } finally {
                _syncing.value = false
            }
        }
    }

    private companion object {
        const val SYNC_WAIT_MILLIS = 10_000L
    }
}
