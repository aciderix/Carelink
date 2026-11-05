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
    private val notified = mutableSetOf<Pair<String, AlertEventType>>()
    init {
        CareLinkNotificationHelper.createNotificationChannels(context)
        // Clear notifications left by the former one-notification-per-episode implementation.
        _active.value.forEach { CareLinkNotificationHelper.cancel(context, it.eventId) }
        _events.value.map { it.roomId to it.eventType }.distinct().forEach {
            CareLinkNotificationHelper.cancelGroup(context, it.first, it.second)
        }
        refreshNotifications()
        scope.launch {
            var since = android.os.SystemClock.elapsedRealtime()
            while (isActive) {
                delay(1000)
                val now = android.os.SystemClock.elapsedRealtime()
                if (!prefs.isAlertRepeatEnabled) { since = now; continue }
                if (now - since >= prefs.alertRepeatIntervalSeconds.coerceIn(10, 120) * 1000L) {
                    refreshNotifications(remind = true)
                    since = now
                }
            }
        }
    }
    @Synchronized fun triggerAlert(event: RoomAlertEvent) {
        val known = _events.value.any { it.eventId == event.eventId }
        val old = store.get("monitor", event.eventId)
        val fresh = store.put("monitor", event) // Receipt is permitted only after this returns.
        store.confirm(event)
        publish()
        if (!known || fresh || old?.isAcknowledged != event.isAcknowledged || old?.resolved != event.resolved) refreshNotifications()
    }
    @Synchronized fun incident(room: RoomUnitState, kind: AlertEventType, message: String) {
        val event = store.incident(room, kind, message) ?: return
        publish(); refreshNotifications()
    }
    @Synchronized fun recoverIncident(roomId: String, kind: AlertEventType) {
        val recovered = store.recoverIncident(roomId, kind)
        if (recovered.isNotEmpty()) {
            publish(); refreshNotifications()
        }
    }
    fun transition(eventId: String, resolve: Boolean) = transitionMany(listOf(eventId), resolve)
    @Synchronized fun transitionMany(eventIds: List<String>, resolve: Boolean) {
        store.monitorActions(eventIds, prefs.deviceLabel, resolve)
        publish(); refreshNotifications()
    }
    @Synchronized private fun refreshNotifications(remind: Boolean = false) {
        if (!scope.isActive) return
        val pending = store.pendingCommandIds()
        val groups = AlertGroup.active(_events.value)
        val current = groups.map { it.roomId to it.eventType }.toSet()
        (notified - current).forEach { CareLinkNotificationHelper.cancelGroup(context, it.first, it.second) }
        groups.forEach { CareLinkNotificationHelper.postGroup(context, it, pending, remind) }
        notified.clear(); notified.addAll(current)
    }
    fun publish() {
        _events.value = store.all("monitor")
        _active.value = _events.value.filterNot { it.resolved }
    }
    @Synchronized fun release() { scope.cancel() }
}
