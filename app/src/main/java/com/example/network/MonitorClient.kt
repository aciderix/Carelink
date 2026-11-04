package com.example.network

import android.util.Log
import com.example.audio.AudioPlayerEngine
import com.example.model.RoomStatus
import com.example.model.RoomUnitState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

class MonitorClient {

    private val scope = CoroutineScope(Dispatchers.IO)

    private val controlConnections = ConcurrentHashMap<String, UnitConnection>()
    private val audioPlayers = ConcurrentHashMap<String, AudioStreamSession>()

    var onRoomTelemetryReceived: ((RoomUnitState) -> Unit)? = null

    private class UnitConnection(
        val unitId: String,
        var socket: Socket? = null,
        var writer: PrintWriter? = null,
        var job: Job? = null
    )

    private class AudioStreamSession(
        val unitId: String,
        val player: AudioPlayerEngine,
        var socket: Socket? = null,
        var job: Job? = null
    )

    fun connectToRoom(unitId: String, ipAddress: String, controlPort: Int = 9877) {
        if (controlConnections.containsKey(unitId)) return

        val conn = UnitConnection(unitId)
        controlConnections[unitId] = conn

        conn.job = scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, controlPort), 4000)
                conn.socket = socket
                conn.writer = PrintWriter(socket.getOutputStream(), true)

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                while (isActive && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    try {
                        val json = JSONObject(line)
                        if (json.optString("type") == "ROOM_TELEMETRY") {
                            val statusStr = json.optString("status", "CALM")
                            val status = try {
                                RoomStatus.valueOf(statusStr)
                            } catch (_: Exception) {
                                RoomStatus.CALM
                            }

                            val unitState = RoomUnitState(
                                id = json.optString("id", unitId),
                                name = json.optString("name", "Chambre"),
                                ipAddress = ipAddress,
                                port = controlPort,
                                status = status,
                                currentDecibels = json.optInt("decibels", 22),
                                noiseThresholdDb = json.optInt("threshold", 55),
                                batteryPercent = json.optInt("battery", 85),
                                isCharging = json.optBoolean("isCharging", true),
                                temperatureCelsius = json.optDouble("temp", 20.5).toFloat(),
                                lastPingTimestamp = System.currentTimeMillis()
                            )

                            onRoomTelemetryReceived?.invoke(unitState)
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.d("MonitorClient", "Connection error to $unitId: ${e.message}")
            } finally {
                controlConnections.remove(unitId)
            }
        }
    }

    fun startListeningToAudio(unitId: String, ipAddress: String, audioPort: Int = 9878) {
        stopListeningToAudio(unitId)

        val player = AudioPlayerEngine(sampleRate = 16000)
        player.start()
        val session = AudioStreamSession(unitId, player)
        audioPlayers[unitId] = session

        session.job = scope.launch {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ipAddress, audioPort), 4000)
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
                Log.d("MonitorClient", "Audio stream error $unitId: ${e.message}")
            } finally {
                player.stop()
                audioPlayers.remove(unitId)
            }
        }
    }

    fun stopListeningToAudio(unitId: String) {
        audioPlayers[unitId]?.let { session ->
            session.job?.cancel()
            try { session.socket?.close() } catch (_: Exception) {}
            session.player.stop()
        }
        audioPlayers.remove(unitId)
    }

    fun isListening(unitId: String): Boolean {
        return audioPlayers.containsKey(unitId)
    }

    fun sendCommand(unitId: String, commandJson: JSONObject) {
        scope.launch {
            controlConnections[unitId]?.writer?.println(commandJson.toString())
        }
    }

    fun sendThresholdUpdate(unitId: String, thresholdDb: Int) {
        val json = JSONObject().apply {
            put("cmd", "SET_THRESHOLD")
            put("value", thresholdDb)
        }
        sendCommand(unitId, json)
    }

    fun disconnectAll() {
        for ((_, session) in audioPlayers) {
            session.job?.cancel()
            try { session.socket?.close() } catch (_: Exception) {}
            session.player.stop()
        }
        audioPlayers.clear()

        for ((_, conn) in controlConnections) {
            conn.job?.cancel()
            try { conn.socket?.close() } catch (_: Exception) {}
        }
        controlConnections.clear()
    }
}
