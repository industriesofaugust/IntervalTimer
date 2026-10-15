package com.august.intervaltimer.sync

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Watch-side receiver: reads the templates the phone published to the Data
 * Layer once, then keeps listening so edits on the phone appear live. There
 * is one of these for the whole app (see WatchStores), so opening a screen
 * never has to ask the Data Layer again.
 */
class SyncedTemplatesStore(context: Context, scope: CoroutineScope) :
    DataClient.OnDataChangedListener {

    // No host in the address means "from any device", so this matches only
    // the templates item, whoever published it.
    private val templatesUri = Uri.Builder()
        .scheme("wear")
        .path(SyncPaths.TEMPLATES_PATH)
        .build()

    private val _templates = MutableStateFlow<List<SyncedTemplate>>(emptyList())
    val templates: StateFlow<List<SyncedTemplate>> = _templates.asStateFlow()

    init {
        // In the background, so starting the app never waits for Google Play services.
        scope.launch {
            val dataClient = Wearable.getDataClient(context)
            // Listen first, so a change that lands during the load isn't missed.
            runCatching {
                dataClient.addListener(this@SyncedTemplatesStore, templatesUri, DataClient.FILTER_LITERAL)
                    .await()
            }
            runCatching { loadCurrentTemplates(dataClient) }
        }
    }

    /** Reads whatever the phone last published, even if it arrived days ago. */
    private suspend fun loadCurrentTemplates(dataClient: DataClient) {
        val buffer = dataClient.getDataItems(templatesUri, DataClient.FILTER_LITERAL).await()
        try {
            buffer.forEach { item -> readItem(item) }
        } finally {
            buffer.release()
        }
    }

    /** Called by the system the moment the phone publishes a change. */
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) readItem(event.dataItem)
        }
    }

    private fun readItem(item: DataItem) {
        if (item.uri.path != SyncPaths.TEMPLATES_PATH) return
        runCatching {
            val json = DataMapItem.fromDataItem(item).dataMap.getString(SyncPaths.TEMPLATES_KEY)
            if (json != null) {
                _templates.value = TemplateSyncCodec.decode(json).templates
            }
        }
    }
}
