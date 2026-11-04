package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.audio.AudioPlayerEngine
import com.example.audio.TalkBackTransmitter
import com.example.model.AlertEventType
import com.example.model.RoomAlertEvent
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class MonitorClient(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val roomConnections = ConcurrentHashMap<String, RoomConnectionSession>()
    private val audioSessions = ConcurrentHashMap<String, AudioStreamSession>()
    val talkBackTransmitter = TalkBackTransmitter(context)

    var onRoomTelemetryReceived: ((RoomUnitState) -> Unit)? = null
    var onRoomAlertReceived: ((RoomAlertEvent) -> Unit)? = null
    var onRoomDisconnected: ((String) -> Unit)? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private class RoomConnectionSession(
        val roomId: String,
        var ipAddress: String,
        val port: Int,
        var socket: Socket? = null,
        var writer: PrintWriter? = null,
        var connectionJob: Job? = null,
        var heartbeatJob: Job? = null,
        var lastSeenTimestamp: Long = 0L,
        var retryDelayMs: Long = 1000L
    )

    private class AudioStreamSession(
        val roomId: String,
        val player: AudioPlayerEngine,
        var socket: Socket? = null,
        var job: Job? = null
    )

    init {
        registerNetworkMonitoring()
    }

    private fun registerNetworkMonitoring() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val builder = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Network restored: trigger immediate reconnect for all registered rooms
                for ((_, session) in roomConnections) {
                    if (session.socket == null || session.socket?.isClosed == true) {
                        reconnectSession(session, immediate = true)
                    }
                }
            }

            override fun onLost(network: Network) {
                for ((roomId, session) in roomConnections) {
                    cleanSessionSockets(session)
                    onRoomDisconnected?.invoke(roomId)
                }
            }
        }

        try {
            cm.registerNetworkCallback(builder.build(), networkCallback!!)
        } catch (_: Exception) {}
    }

    fun connectToRoom(roomId: String, ipAddress: String, controlPort: Int = 9877) {
        val existing = roomConnections[roomId]
        if (existing != null) {
            existing.ipAddress = ipAddress
            if (existing.socket == null || existing.socket?.isClosed == true) {
                reconnectSession(existing, immediate = true)
            }
            return
        }

        val session = RoomConnectionSession(roomId, ipAddress, controlPort)
        roomConnections[roomId] = session
        startSessionLoop(session)
    }

    private fun startSessionLoop(session: RoomConnectionSession) {
        session.connectionJob?.cancel()
        session.connectionJob = scope.launch {
            while (isActive) {
                var socket: Socket? = null
                try {
                    socket = Socket()
                    socket.connect(InetSocketAddress(session.ipAddress, session.port), 3500)
                    session.socket = socket
                    session.writer = PrintWriter(socket.getOutputStream(), true)
                    session.lastSeenTimestamp = System.currentTimeMillis()
                    session.retryDelayMs = 1000L // Reset backoff on success

                    startHeartbeat(session)

                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                    while (isActive && !socket.isClosed) {
                        val line = reader.readLine() ?: break
                        handleIncomingControlMessage(session.roomId, session.ipAddress, line)
                    }
                } catch (e: Exception) {
                    Log.d("MonitorClient", "Room connection error ${session.roomId}: ${e.message}")
                } finally {
                    cleanSessionSockets(session)
                    onRoomDisconnected?.invoke(session.roomId)
                }

                if (!isActive) break

                // Exponential backoff reconnect: 1s, 2s, 4s, 8s, max 15s
                delay(session.retryDelayMs)
                session.retryDelayMs = (session.retryDelayMs * 2).coerceAtMost(15000L)
            }
        }
    }

    private fun startHeartbeat(session: RoomConnectionSession) {
        session.heartbeatJob?.cancel()
        session.heartbeatJob = scope.launch {
            while (isActive && session.socket?.isConnected == true && session.socket?.isClosed == false) {
                delay(3000)
                try {
                    val ping = JSONObject().apply {
                        put("cmd", "PING")
                        put("timestamp", System.currentTimeMillis())
                    }
                    session.writer?.println(ping.toString())

                    // Check heartbeat timeout (if no valid packet received for > 7.5 seconds)
                    val now = System.currentTimeMillis()
                    if (session.lastSeenTimestamp > 0 && (now - session.lastSeenTimestamp) > 7500) {
                        Log.d("MonitorClient", "Heartbeat timeout for room ${session.roomId}")
                        cleanSessionSockets(session)
                        onRoomDisconnected?.invoke(session.roomId)
                        break
                    }
                } catch (_: Exception) {
                    break
                }
            }
        }
    }

    private fun reconnectSession(session: RoomConnectionSession, immediate: Boolean) {
        if (immediate) {
            session.retryDelayMs = 500L
        }
        startSessionLoop(session)
    }

    private fun cleanSessionSockets(session: RoomConnectionSession) {
        session.heartbeatJob?.cancel()
        session.heartbeatJob = null
        try { session.socket?.close() } catch (_: Exception) {}
        session.socket = null
        session.writer = null
    }

    private fun handleIncomingControlMessage(roomId: String, ipAddress: String, line: String) {
        try {
            val json = JSONObject(line)
            val session = roomConnections[roomId]
            session?.lastSeenTimestamp = System.currentTimeMillis()

            val type = json.optString("type")
            when (type) {
                "PONG" -> {
                    // Heartbeat acknowledged
                }
                "ROOM_TELEMETRY" -> {
                    val statusStr = json.optString("status", "CALM")
                    val status = try {
                        RoomStatus.valueOf(statusStr)
                    } catch (_: Exception) {
                        RoomStatus.CALM
                    }

                    val unitState = RoomUnitState(
                        id = json.optString("id", roomId),
                        name = json.optString("name", "Chambre"),
                        ipAddress = ipAddress,
                        status = status,
                        currentDecibels = json.optInt("decibels", 0),
                        noiseThresholdDb = json.optInt("threshold", 55),
                        batteryPercent = json.optInt("battery", -1),
                        isCharging = json.optBoolean("isCharging", false),
                        wifiSignalDbm = json.optInt("wifiRssi", 0),
                        isMicOperational = json.optBoolean("isMicOperational", true),
                        lastSeenTimestamp = System.currentTimeMillis(),
                        isAudioStreaming = isListening(roomId)
                    )

                    onRoomTelemetryReceived?.invoke(unitState)
                }
                "ROOM_ALERT_EVENT" -> {
                    val eventTypeStr = json.optString("eventType", "SOUND_ALERT")
                    val eventType = try {
                        AlertEventType.valueOf(eventTypeStr)
                    } catch (_: Exception) {
                        AlertEventType.SOUND_ALERT
                    }

                    val event = RoomAlertEvent(
                        eventId = json.optString("eventId"),
                        roomId = json.optString("roomId", roomId),
                        roomName = json.optString("roomName", "Chambre"),
                        timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                        levelReachedDb = json.optInt("levelReachedDb", 0),
                        thresholdDb = json.optInt("thresholdDb", 55),
                        eventType = eventType,
                        durationSeconds = json.optInt("durationSeconds", 0),
                        notes = json.optString("notes", "")
                    )

                    onRoomAlertReceived?.invoke(event)
                }
            }
        } catch (_: Exception) {}
    }

    fun startListeningToAudio(roomId: String, ipAddress: String, audioPort: Int = 9878) {
        stopListeningToAudio(roomId)

        val player = AudioPlayerEngine(sampleRate = 16000)
        player.start()
        val session = AudioStreamSession(roomId, player)
        audioSessions[roomId] = session

        session.job = scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, audioPort), 3500)
                session.socket = socket

                val inputStream = socket.getInputStream()
                val buffer = ByteArray(2048)
                var bytesRead: Int

                while (isActive && !socket.isClosed) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    val chunk = buffer.copyOf(bytesRead)
                    player.writeAudio(chunk)
                }
            } catch (e: Exception) {
                Log.d("MonitorClient", "Audio stream error $roomId: ${e.message}")
            } finally {
                player.stop()
                audioSessions.remove(roomId)
            }
        }
    }

    fun stopListeningToAudio(roomId: String) {
        audioSessions[roomId]?.let { session ->
            session.job?.cancel()
            try { session.socket?.close() } catch (_: Exception) {}
            session.player.stop()
        }
        audioSessions.remove(roomId)
    }

    fun isListening(roomId: String): Boolean {
        return audioSessions.containsKey(roomId)
    }

    fun sendThresholdUpdate(roomId: String, thresholdDb: Int) {
        val json = JSONObject().apply {
            put("cmd", "SET_THRESHOLD")
            put("value", thresholdDb)
        }
        scope.launch {
            roomConnections[roomId]?.writer?.println(json.toString())
        }
    }

    fun startSpeaking(roomId: String, targetIp: String, targetPort: Int = 9879) {
        talkBackTransmitter.startTransmitting(targetIp, targetPort)
    }

    fun stopSpeaking() {
        talkBackTransmitter.stopTransmitting()
    }

    fun disconnectAll() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        networkCallback?.let { try { cm?.unregisterNetworkCallback(it) } catch (_: Exception) {} }
        networkCallback = null

        talkBackTransmitter.stopTransmitting()

        for ((_, session) in audioSessions) {
            session.job?.cancel()
            try { session.socket?.close() } catch (_: Exception) {}
            session.player.stop()
        }
        audioSessions.clear()

        for ((_, conn) in roomConnections) {
            conn.connectionJob?.cancel()
            conn.heartbeatJob?.cancel()
            try { conn.socket?.close() } catch (_: Exception) {}
        }
        roomConnections.clear()
    }
}
