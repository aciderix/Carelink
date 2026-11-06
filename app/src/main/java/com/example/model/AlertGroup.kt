package com.example.model

/** A presentation/notification group. Original event identities and acknowledgements stay intact. */
data class AlertGroup(val roomId: String, val eventType: AlertEventType, val events: List<RoomAlertEvent>) {
    val key get() = "$roomId:${eventType.name}"
    val roomName get() = events.last().roomName
    val occurrences get() = events.size
    val isAcknowledged get() = events.all { it.isAcknowledged }
    val firstTimestamp get() = events.first().timestamp
    val lastTimestamp get() = events.last().timestamp
    fun needsAttention(pending: Set<String> = emptySet()) = events.any { !it.isAcknowledged && it.eventId !in pending }
    companion object {
        fun active(events: List<RoomAlertEvent>): List<AlertGroup> = events.filterNot { it.resolved }
            .groupBy { it.roomId to it.eventType }
            .map { (key, members) -> AlertGroup(key.first, key.second, members.sortedWith(compareBy<RoomAlertEvent> { it.timestamp }.thenBy { it.eventId })) }
            .sortedWith(compareBy<AlertGroup> { it.isAcknowledged }.thenByDescending { it.lastTimestamp })
    }
}
