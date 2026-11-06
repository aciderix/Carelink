package com.example.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.RoomAlertEvent
import com.example.model.RoomUnitState
import com.example.model.AlertEventType

/** Durable journal. Only resolved records expire; pending events are never silently evicted. */
class EventStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "carelink-events.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE events (side TEXT NOT NULL,id TEXT NOT NULL,body TEXT NOT NULL,resolved INTEGER NOT NULL,created INTEGER NOT NULL,PRIMARY KEY(side,id))")
        db.execSQL("CREATE TABLE receipts (event TEXT NOT NULL,device TEXT NOT NULL,revision INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(event,device))")
        db.execSQL("CREATE TABLE outbox (event TEXT PRIMARY KEY,room TEXT NOT NULL,action TEXT NOT NULL)")
        createIncidents(db)
    }
    private fun createIncidents(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS incidents (room TEXT NOT NULL,kind TEXT NOT NULL,event TEXT NOT NULL,PRIMARY KEY(room,kind))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { if (oldVersion < 2) createIncidents(db) }
    /** Latch the physical incident independently of a user's acknowledgement or closure. */
    @Synchronized fun incident(room: RoomUnitState, kind: AlertEventType, message: String): RoomAlertEvent? {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val latched = db.rawQuery("SELECT event FROM incidents WHERE room=? AND kind=?", arrayOf(room.id, kind.name)).use { it.moveToFirst() }
            if (latched) { db.setTransactionSuccessful(); return null }
            val existing = all("monitor").firstOrNull { !it.remote && !it.resolved && it.roomId == room.id && it.eventType == kind }
            val event = existing ?: RoomAlertEvent(roomId = room.id, roomName = room.name, eventType = kind, notes = message)
            if (existing == null) put("monitor", event)
            check(db.insertOrThrow("incidents", null, ContentValues().apply {
                put("room", room.id); put("kind", kind.name); put("event", event.eventId)
            }) != -1L)
            db.setTransactionSuccessful()
            return if (existing == null) event else null
        } finally { db.endTransaction() }
    }
    @Synchronized fun recoverIncident(roomId: String, kind: AlertEventType): List<RoomAlertEvent> {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("incidents", "room=? AND kind=?", arrayOf(roomId, kind.name))
            val recovered = all("monitor").filter { !it.remote && !it.resolved && it.roomId == roomId && it.eventType == kind }
                .mapNotNull { transition("monitor", it.eventId, "Système · état rétabli", true) }
            db.setTransactionSuccessful()
            return recovered
        } finally { db.endTransaction() }
    }
    @Synchronized fun put(side: String, event: RoomAlertEvent): Boolean {
        val previous = get(side, event.eventId)
        if (previous != null) {
            require(previous.roomId == event.roomId && previous.eventType == event.eventType && previous.timestamp == event.timestamp) { "Identité d’événement incohérente" }
            if (previous.revision > event.revision) return false
        }
        val merged = event.copy(isAcknowledged = event.isAcknowledged || previous?.isAcknowledged == true,
            resolved = event.resolved || previous?.resolved == true,
            resolvedAt = if ((previous?.resolvedAt ?: 0L) > 0) previous!!.resolvedAt else event.resolvedAt,
            acknowledgedBy = event.acknowledgedBy.ifBlank { previous?.acknowledgedBy.orEmpty() })
        if (side == "room" && previous != null && (previous.isAcknowledged != merged.isAcknowledged || previous.resolved != merged.resolved))
            writableDatabase.delete("receipts", "event=?", arrayOf(event.eventId))
        val result = writableDatabase.insertWithOnConflict("events", null, ContentValues().apply {
            put("side", side); put("id", merged.eventId); put("body", merged.toJson().toString())
            put("resolved", if (merged.resolved) 1 else 0); put("created", if (merged.resolved && merged.resolvedAt > 0) merged.resolvedAt else merged.timestamp)
        }, SQLiteDatabase.CONFLICT_REPLACE)
        check(result != -1L) { "Journal indisponible" }; return previous == null
    }
    /** True while an event of this kind is still waiting for a first acknowledgement. */
    @Synchronized fun hasUncheckedEvent(side: String, kind: AlertEventType): Boolean =
        all(side).any { it.eventType == kind && !it.resolved && !it.isAcknowledged }
    @Synchronized fun get(side: String, id: String): RoomAlertEvent? =
        readableDatabase.rawQuery("SELECT body FROM events WHERE side=? AND id=?", arrayOf(side, id)).use {
            if (it.moveToFirst()) RoomAlertEvent.fromJson(org.json.JSONObject(it.getString(0))) else null
        }
    @Synchronized fun all(side: String): List<RoomAlertEvent> =
        readableDatabase.rawQuery("SELECT body FROM events WHERE side=? ORDER BY created DESC", arrayOf(side)).use {
            buildList { while (it.moveToNext()) add(RoomAlertEvent.fromJson(org.json.JSONObject(it.getString(0)))) }
        }
    @Synchronized fun transition(side: String, id: String, actor: String, resolve: Boolean): RoomAlertEvent? {
        val old = get(side, id) ?: return null
        if (old.isAcknowledged && (!resolve || old.resolved)) return old
        val event = old.copy(isAcknowledged = true, acknowledgedBy = old.acknowledgedBy.ifBlank { actor }, resolved = old.resolved || resolve, revision = old.revision + 1,
            resolvedAt = if (resolve && !old.resolved) System.currentTimeMillis() else old.resolvedAt)
        put(side, event); return event
    }
    @Synchronized fun measurements(id: String, peak: Int, duration: Int) {
        val old = get("room", id) ?: return
        put("room", old.copy(levelReachedDb = maxOf(old.levelReachedDb, peak), durationSeconds = maxOf(old.durationSeconds, duration), revision = old.revision + 1))
    }
    @Synchronized fun received(eventId: String, device: String, revision: Int) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO receipts(event,device,revision) VALUES(?,?,MAX(?,COALESCE((SELECT revision FROM receipts WHERE event=? AND device=?),0)))", arrayOf<Any>(eventId, device, revision, eventId, device))
    }
    @Synchronized fun pending(device: String): List<RoomAlertEvent> {
        val received = readableDatabase.rawQuery("SELECT event,revision FROM receipts WHERE device=?", arrayOf(device)).use {
            buildMap { while (it.moveToNext()) put(it.getString(0), it.getInt(1)) }
        }
        return all("room").filter { (received[it.eventId] ?: -1) < it.revision }
    }
    @Synchronized fun request(id: String, room: String, action: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO outbox VALUES(?,?,?)", arrayOf(id, room, action))
    }
    /** Apply exactly the event IDs the operator saw, atomically within one service command. */
    @Synchronized fun monitorActions(ids: List<String>, actor: String, resolve: Boolean) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids.distinct()) {
                val event = get("monitor", id) ?: continue
                if (event.resolved) continue
                if (event.remote) request(id, event.roomId, if (resolve) "RESOLVE" else "ACK")
                else transition("monitor", id, actor, resolve)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun commands(room: String): List<Pair<String, String>> =
        readableDatabase.rawQuery("SELECT event,action FROM outbox WHERE room=?", arrayOf(room)).use {
            buildList { while (it.moveToNext()) add(it.getString(0) to it.getString(1)) }
        }
    @Synchronized fun pendingCommandIds(): Set<String> = readableDatabase.rawQuery("SELECT event FROM outbox", null).use {
        buildSet { while (it.moveToNext()) add(it.getString(0)) }
    }
    @Synchronized fun confirm(event: RoomAlertEvent) {
        val action = commands(event.roomId).find { it.first == event.eventId }?.second
        if ((action == "RESOLVE" && event.resolved) || (action == "ACK" && event.isAcknowledged))
            writableDatabase.delete("outbox", "event=?", arrayOf(event.eventId))
    }
    @Synchronized fun prune(now: Long = System.currentTimeMillis()) {
        val cutoff = now - 7L * 24 * 60 * 60 * 1000
        writableDatabase.execSQL("DELETE FROM receipts WHERE event IN (SELECT id FROM events WHERE side='room' AND resolved=1 AND created<?)", arrayOf(cutoff))
        writableDatabase.delete("events", "resolved=1 AND created<? AND id NOT IN (SELECT event FROM outbox)", arrayOf(cutoff.toString()))
    }
}
