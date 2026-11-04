package com.example.service

import android.content.Context
import com.example.data.EventStore
import com.example.data.PreferencesManager
import com.example.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlertManager(private val context: Context, private val store: EventStore = EventStore(context)) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = PreferencesManager(context)
    private val _events = MutableStateFlow(store.all("monitor"))
    val history = _events.asStateFlow()
    private val _active = MutableStateFlow(_events.value.filterNot { it.resolved })
    val activeAlertsFlow = _active.asStateFlow()
    init {
        CareLinkNotificationHelper.createNotificationChannels(context)
        _active.value.filterNot { it.eventId in store.pendingCommandIds() }.forEach { CareLinkNotificationHelper.post(context, it) }
        scope.launch {
            var since = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1000)
                val now = android.os.SystemClock.elapsedRealtime()
                if (!prefs.isAlertRepeatEnabled) { since = now; continue }
                if (now - since >= prefs.alertRepeatIntervalSeconds.coerceIn(10, 120) * 1000L) {
                    val pending = store.pendingCommandIds()
                    _active.value.filterNot { it.isAcknowledged || it.eventId in pending }.forEach { CareLinkNotificationHelper.post(context, it, true) }
                    since = now
                }
            }
        }
    }
    @Synchronized fun triggerAlert(event: RoomAlertEvent) {
        val old = store.get("monitor", event.eventId)
        val fresh = store.put("monitor", event) // Receipt is permitted only after this returns.
        store.confirm(event)
        publish()
        if (event.eventId !in store.pendingCommandIds() && (fresh || old?.isAcknowledged != event.isAcknowledged || old?.resolved != event.resolved))
            CareLinkNotificationHelper.post(context, store.get("monitor", event.eventId)!!)
    }
    @Synchronized fun incident(room: RoomUnitState, kind: AlertEventType, message: String) {
        val event = store.incident(room, kind, message) ?: return
        publish(); CareLinkNotificationHelper.post(context, event)
    }
    @Synchronized fun recoverIncident(roomId: String, kind: AlertEventType) {
        val recovered = store.recoverIncident(roomId, kind)
        if (recovered.isNotEmpty()) {
            recovered.forEach { CareLinkNotificationHelper.cancel(context, it.eventId) }
            publish()
        }
    }
    @Synchronized fun transition(eventId: String, resolve: Boolean) {
        val event = store.get("monitor", eventId) ?: return
        if (event.remote) {
            store.request(event.eventId, event.roomId, if (resolve) "RESOLVE" else "ACK")
            CareLinkNotificationHelper.cancel(context, event.eventId)
        } else {
            val changed = store.transition("monitor", eventId, prefs.deviceLabel, resolve) ?: return
            CareLinkNotificationHelper.post(context, changed); publish()
        }
    }
    fun publish() {
        _events.value = store.all("monitor")
        _active.value = _events.value.filterNot { it.resolved }
    }
    fun release() { scope.cancel() }
}
