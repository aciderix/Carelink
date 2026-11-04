package com.example.network

import android.util.Log
import com.example.audio.AudioPlayerEngine
import com.example.model.RoomStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class RoomServer(
    private val controlPort: Int = 9877,
    private val audioPort: Int = 9878,
    private val talkbackPort: Int = 9879
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var controlServerJob: Job? = null
    private var audioServerJob: Job? = null
    private var talkbackServerJob: Job? = null

    private var controlServerSocket: ServerSocket? = null
    private var audioServerSocket: ServerSocket? = null
    private var talkbackServerSocket: ServerSocket? = null

    private val activeControlClients = CopyOnWriteArrayList<PrintWriter>()
    private val activeAudioClients = CopyOnWriteArrayList<Socket>()

    private val talkBackPlayer = AudioPlayerEngine(sampleRate = 16000)

    var onCommandReceived: ((JSONObject) -> Unit)? = null
    var onTalkBackStatusChanged: ((Boolean) -> Unit)? = null

    @Volatile
    var isRunning = false
        private set

    fun start() {
        if (isRunning) return
        isRunning = true

        startControlServer()
        startAudioServer()
        startTalkbackServer()
    }

    private fun startControlServer() {
        controlServerJob = scope.launch {
            try {
                controlServerSocket = ServerSocket(controlPort).apply {
                    reuseAddress = true
                }
                while (isActive && isRunning) {
                    val client = controlServerSocket?.accept() ?: break
                    handleControlClient(client)
                }
            } catch (e: Exception) {
                Log.d("RoomServer", "Control server stopped: ${e.message}")
            }
        }
    }

    private fun handleControlClient(socket: Socket) {
        scope.launch {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)
                activeControlClients.add(writer)

                while (isActive && isRunning && !socket.isClosed) {
                    val line = reader.readLine() ?: break
                    try {
                        val json = JSONObject(line)
                        onCommandReceived?.invoke(json)
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {
            } finally {
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    private fun startAudioServer() {
        audioServerJob = scope.launch {
            try {
                audioServerSocket = ServerSocket(audioPort).apply {
                    reuseAddress = true
                }
                while (isActive && isRunning) {
                    val client = audioServerSocket?.accept() ?: break
                    activeAudioClients.add(client)
                }
            } catch (e: Exception) {
                Log.d("RoomServer", "Audio server stopped: ${e.message}")
            }
        }
    }

    private fun startTalkbackServer() {
        talkbackServerJob = scope.launch {
            try {
                talkbackServerSocket = ServerSocket(talkbackPort).apply {
                    reuseAddress = true
                }
                while (isActive && isRunning) {
                    val client = talkbackServerSocket?.accept() ?: break
                    handleTalkbackClient(client)
                }
            } catch (e: Exception) {
                Log.d("RoomServer", "Talkback server stopped: ${e.message}")
            }
        }
    }

    private fun handleTalkbackClient(socket: Socket) {
        scope.launch {
            try {
                talkBackPlayer.start()
                onTalkBackStatusChanged?.invoke(true)
                val inputStream = socket.getInputStream()
                val buffer = ByteArray(2048)
                var bytesRead: Int

                while (isActive && isRunning) {
                    bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    val chunk = buffer.copyOf(bytesRead)
                    talkBackPlayer.writeAudio(chunk)
                }
            } catch (_: Exception) {
            } finally {
                talkBackPlayer.stop()
                onTalkBackStatusChanged?.invoke(false)
                try { socket.close() } catch (_: Exception) {}
            }
        }
    }

    fun broadcastAudioPacket(pcmBytes: ByteArray) {
        if (activeAudioClients.isEmpty()) return
        scope.launch {
            val toRemove = mutableListOf<Socket>()
            for (client in activeAudioClients) {
                try {
                    client.getOutputStream().write(pcmBytes)
                } catch (_: Exception) {
                    toRemove.add(client)
                }
            }
            activeAudioClients.removeAll(toRemove)
        }
    }

    fun broadcastTelemetry(
        unitId: String,
        unitName: String,
        decibels: Int,
        status: RoomStatus,
        batteryPercent: Int,
        isCharging: Boolean,
        temperature: Float,
        thresholdDb: Int
    ) {
        if (activeControlClients.isEmpty()) return
        scope.launch {
            val json = JSONObject().apply {
                put("type", "ROOM_TELEMETRY")
                put("id", unitId)
                put("name", unitName)
                put("decibels", decibels)
                put("status", status.name)
                put("battery", batteryPercent)
                put("isCharging", isCharging)
                put("temp", temperature)
                put("threshold", thresholdDb)
                put("timestamp", System.currentTimeMillis())
            }.toString()

            val deadWriters = mutableListOf<PrintWriter>()
            for (writer in activeControlClients) {
                try {
                    writer.println(json)
                } catch (_: Exception) {
                    deadWriters.add(writer)
                }
            }
            activeControlClients.removeAll(deadWriters)
        }
    }

    fun stop() {
        isRunning = false
        controlServerJob?.cancel()
        audioServerJob?.cancel()
        talkbackServerJob?.cancel()
        try { controlServerSocket?.close() } catch (_: Exception) {}
        try { audioServerSocket?.close() } catch (_: Exception) {}
        try { talkbackServerSocket?.close() } catch (_: Exception) {}
        talkBackPlayer.stop()
        activeControlClients.clear()
        activeAudioClients.clear()
    }
}
