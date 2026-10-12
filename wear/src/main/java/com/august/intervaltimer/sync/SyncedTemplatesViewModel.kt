package com.august.intervaltimer.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Watch-side receiver: reads the templates the phone published to the Data
 * Layer, and keeps listening so edits on the phone appear here live.
 */
class SyncedTemplatesViewModel(app: Application) :
    AndroidViewModel(app), DataClient.OnDataChangedListener {

    private val dataClient = Wearable.getDataClient(app)

    private val _templates = MutableStateFlow<List<SyncedTemplate>>(emptyList())
    val templates: StateFlow<List<SyncedTemplate>> = _templates.asStateFlow()

    init {
        dataClient.addListener(this)
        viewModelScope.launch { loadCurrentTemplates() }
    }

    /** Reads whatever the phone last published, even if it arrived days ago. */
    private suspend fun loadCurrentTemplates() {
        runCatching {
            val buffer = dataClient.dataItems.await()
            try {
                buffer.forEach { item -> readItem(item) }
            } finally {
                buffer.release()
            }
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

    override fun onCleared() {
        dataClient.removeListener(this)
    }
}
