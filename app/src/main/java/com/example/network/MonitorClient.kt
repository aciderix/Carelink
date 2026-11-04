package com.example.network

import android.content.Context
import com.example.audio.AudioPlayerEngine
import com.example.audio.TalkBackTransmitter
import com.example.data.EventStore
import com.example.model.*
import com.example.security.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class MonitorClient(private val context: Context, private val store: EventStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pairing = PairingStore(context)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val addresses = ConcurrentHashMap<String, String>()
    private val connections = ConcurrentHashMap<String, SecureSession>()
    private var audioJob: Job? = null
    @Volatile private var audioSession: SecureSession? = null
    val talkBackTransmitter = TalkBackTransmitter(context)
    var onRoomTelemetryReceived: ((RoomUnitState) -> Unit)? = null
    var onRoomAlertReceived: ((RoomAlertEvent) -> Unit)? = null
    var onRoomDisconnected: ((String, String) -> Unit)? = null
    var onListeningChanged: ((String?, String?) -> Unit)? = null
    var onSettingsApplied: ((String, Int) -> Unit)? = null
    @Synchronized fun connectToRoom(roomId: String, ipAddress: String, controlPort: Int = 9877) {
        if (pairing.rooms().none { it.id == roomId }) return
        if (connections.containsKey(roomId)) return // An unauthenticated beacon cannot redirect an established session.
        if (ipAddress.isNotBlank()) addresses[roomId] = ipAddress
        if (jobs[roomId]?.isActive == true) return
        jobs[roomId] = scope.launch {
            var backoff = 1000L
            while (isActive && pairing.rooms().any { it.id == roomId }) {
                var session: SecureSession? = null
                try {
                    val room = pairing.rooms().first { it.id == roomId }.let { it.copy(address = addresses[roomId] ?: it.address) }
                    session = SecureTransport(context).connect(room, "control", controlPort)
                    connections[roomId] = session; backoff = 1000
                    coroutineScope {
                        val activeSession = session
                        val sender = launch {
                            try {
                                while (isActive) {
                                    activeSession.send(JSONObject().put("cmd", "PING"))
                                    for ((id, action) in store.commands(roomId)) activeSession.send(JSONObject().put("cmd", action).put("eventId", id))
                                    delay(2000)
                                }
                            } catch (_: Exception) { activeSession.close() }
                        }
                        try {
                            while (isActive) {
                                val j = activeSession.receiveJson()
                                when (j.getString("type")) {
                                    "PONG" -> Unit // PONG does not establish capture health.
                                    "EVENT" -> {
                                        val event = RoomAlertEvent.fromJson(j).copy(remote = true)
                                        require(event.roomId == roomId)
                                        onRoomAlertReceived?.invoke(event) // Persists before the receipt is sent.
                                        activeSession.send(JSONObject().put("cmd", "RECEIVED").put("eventId", event.eventId).put("revision", event.revision))
                                    }
                                    "SETTINGS_APPLIED" -> onSettingsApplied?.invoke(roomId, j.getInt("threshold"))
                                    "ROOM_TELEMETRY" -> {
                                        require(j.getInt("version") == 2 && j.getString("id") == roomId)
                                        val age = j.getLong("captureAgeMs")
                                        val operational = j.getBoolean("isMicOperational") && age < 2000
                                        val reported = RoomStatus.valueOf(j.getString("status"))
                                        val status = if (!operational && reported != RoomStatus.DEGRADED) RoomStatus.MIC_ERROR else reported
                                        onRoomTelemetryReceived?.invoke(RoomUnitState(id = roomId, name = j.getString("name").take(80),
                                            ipAddress = room.address, status = status, currentDecibels = j.getInt("decibels"),
                                            noiseThresholdDb = j.getInt("threshold"), batteryPercent = j.getInt("battery"),
                                            isCharging = j.getBoolean("isCharging"), isMicOperational = operational,
                                            lastSeenTimestamp = System.currentTimeMillis(), captureAgeMs = age,
                                            samplesCaptured = j.getLong("samples"), diagnostic = j.optString("diagnostic"),
                                            temperatureC = if (j.has("temperatureC")) j.getDouble("temperatureC").toFloat() else null))
                                    }
                                    else -> throw IllegalArgumentException("Protocole incompatible")
                                }
                            }
                        } finally { sender.cancel(); activeSession.close() }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { onRoomDisconnected?.invoke(roomId, "Connexion interrompue ou identité non vérifiée") }
                finally { session?.let { connections.remove(roomId, it); it.close() } }
                delay(backoff); backoff = (backoff * 2).coerceAtMost(15000)
            }
        }
    }
    @Synchronized fun startListeningToAudio(roomId: String) {
        val previous = audioJob
        stopListeningToAudio()
        audioJob = scope.launch {
            previous?.join()
            val player = AudioPlayerEngine(context)
            var local: SecureSession? = null
            try {
                val room = pairing.rooms().first { it.id == roomId }.let { it.copy(address = addresses[roomId] ?: it.address) }
                check(!room.enrolling)
                local = SecureTransport(context).connect(room, "audio", 9878); audioSession = local
                player.start()
                var first = true
                while (isActive) {
                    player.writeAudio(local.receive())
                    if (first) { first = false; onListeningChanged?.invoke(roomId, null) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { onListeningChanged?.invoke(null, e.message ?: "Écoute interrompue") }
            finally { local?.close(); player.close(); onListeningChanged?.invoke(null, null) }
        }
    }
    @Synchronized fun stopListeningToAudio() { audioJob?.cancel(); audioSession?.close(); audioSession = null }
    fun sendThresholdUpdate(roomId: String, thresholdDb: Int) {
        require(thresholdDb in 35..85)
        scope.launch { runCatching { connections[roomId]?.send(JSONObject().put("cmd", "SET_THRESHOLD").put("value", thresholdDb)) } }
    }
    fun startSpeaking(roomId: String) {
        stopListeningToAudio()
        val room = pairing.rooms().first { it.id == roomId }
        talkBackTransmitter.startTransmitting(room.copy(address = addresses[roomId] ?: room.address))
    }
    fun stopSpeaking() = talkBackTransmitter.stopTransmitting()
    fun refresh() { pairing.rooms().forEach { connectToRoom(it.id, it.address) } }
    fun checkDeadlines() {
        connections.values.forEach { it.checkWriteDeadline() }; talkBackTransmitter.checkDeadline()
    }
    fun forget(id: String) { jobs.remove(id)?.cancel(); connections.remove(id)?.close(); addresses.remove(id) }
    fun disconnectAll() {
        stopListeningToAudio(); talkBackTransmitter.release(); connections.values.forEach { it.close() }
        connections.clear(); scope.cancel(); jobs.clear()
    }
}
