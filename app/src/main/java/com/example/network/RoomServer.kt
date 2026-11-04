package com.example.network

import android.content.Context
import android.os.SystemClock
import com.example.audio.AudioPlayerEngine
import com.example.data.EventStore
import com.example.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/** Each accepted connection owns its jobs/socket. No plaintext compatibility fallback. */
class RoomServer(private val context: Context, private val store: EventStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val transport = SecureTransport(context)
    private val pairing = PairingStore(context)
    private val listeners = mutableListOf<SSLServerSocket>()
    private val sessions = ConcurrentHashMap<String, SecureSession>()
    private val audio = ConcurrentHashMap<String, Channel<ByteArray>>()
    private val count = AtomicInteger()
    private val talking = AtomicInteger()
    val connectedMonitors get() = sessions.keys.count { it.startsWith("control:") }
    var telemetry: () -> JSONObject = { JSONObject() }
    var onCommand: ((String, JSONObject) -> Unit)? = null
    var onTalkBackStatusChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onConnectionDiagnostic: ((String?) -> Unit)? = null
    fun start() {
        try {
            for ((port, channel) in listOf(9877 to "control", 9878 to "audio", 9879 to "talk")) {
                val server = transport.server(port); listeners.add(server)
                scope.launch {
                    try {
                        while (isActive) {
                            val socket = server.accept() as SSLSocket
                            if (count.incrementAndGet() > 32) { count.decrementAndGet(); socket.close(); continue }
                            scope.launch { serve(socket, channel) }
                        }
                    } catch (e: Exception) { if (isActive) onError?.invoke("Serveur $channel indisponible") }
                }
            }
            scope.launch {
                while (isActive) {
                    delay(500)
                    for ((_, session) in sessions) {
                        session.checkWriteDeadline()
                        if (!pairing.stillAuthorized(session.deviceId, session.credential)) session.close()
                    }
                }
            }
        } catch (e: Exception) { stop(); throw e }
    }
    private suspend fun serve(socket: SSLSocket, channel: String) {
        var connection: SecureSession? = null
        var key: String? = null
        try {
            connection = transport.accept(socket, channel)
            onConnectionDiagnostic?.invoke(null)
            key = "$channel:${connection.deviceId}"
            sessions.put(key, connection)?.close()
            when (channel) {
                "control" -> control(connection)
                "audio" -> stream(connection)
                "talk" -> talk(connection)
            }
        } catch (e: Exception) {
            if (connection == null) onConnectionDiagnostic?.invoke(when (e) {
                is PairingRejected -> "Appairage refusé : QR expiré, annulé ou accès révoqué. Générez un nouveau QR."
                is javax.net.ssl.SSLException -> "Un téléphone a tenté une connexion, mais TLS a échoué. Vérifiez la version CareLink et la date des téléphones."
                else -> "Tentative de connexion interrompue avant authentification. Réessayez avec un nouveau QR."
            })
        } finally {
            if (key != null && connection != null) sessions.remove(key, connection)
            connection?.close(); runCatching { socket.close() }; count.decrementAndGet()
        }
    }
    private suspend fun control(connection: SecureSession) = coroutineScope {
        val sender = launch {
            try {
                while (isActive) {
                    connection.send(telemetry().put("type", "ROOM_TELEMETRY").put("version", 2))
                    for (event in store.pending(connection.deviceId).take(32))
                        connection.send(event.toJson().put("type", "EVENT"))
                    delay(1000)
                }
            } catch (_: Exception) { connection.close() }
        }
        try {
            while (currentCoroutineContext().isActive) {
                val command = connection.receiveJson()
                when (command.getString("cmd")) {
                    "PING" -> connection.send(JSONObject().put("type", "PONG"))
                    "RECEIVED" -> store.received(command.getString("eventId"), connection.deviceId, command.getInt("revision").coerceIn(0, 1000000))
                    "ACK", "RESOLVE" -> {
                        val event = store.transition("room", command.getString("eventId"), connection.deviceId,
                            command.getString("cmd") == "RESOLVE")
                        if (event != null) connection.send(event.toJson().put("type", "EVENT"))
                    }
                    "SET_THRESHOLD" -> {
                        val value = command.getInt("value"); require(value in 35..85)
                        onCommand?.invoke(connection.deviceId, command)
                        connection.send(JSONObject().put("type", "SETTINGS_APPLIED").put("threshold", value))
                    }
                    else -> throw IllegalArgumentException("Commande inconnue")
                }
            }
        } finally { sender.cancel(); connection.close() }
    }
    private suspend fun stream(connection: SecureSession) {
        val queue = Channel<ByteArray>(4, kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
        audio.put(connection.deviceId, queue)?.close()
        try {
            while (currentCoroutineContext().isActive) {
                val bytes = withTimeout(3000) { queue.receive() }
                connection.send(bytes)
            }
        } finally { audio.remove(connection.deviceId, queue); queue.close() }
    }
    private fun talk(connection: SecureSession) {
        if (!talking.compareAndSet(0, 1)) return
        val player = AudioPlayerEngine(context, speaker = true)
        val until = SystemClock.elapsedRealtime() + 30000
        try {
            player.start(); onTalkBackStatusChanged?.invoke(true)
            while (SystemClock.elapsedRealtime() < until && !connection.socket.isClosed) player.writeAudio(connection.receive())
        } finally {
            player.close(); talking.set(0); onTalkBackStatusChanged?.invoke(false)
        }
    }
    fun broadcastAudioPacket(bytes: ByteArray) { for (queue in audio.values) queue.trySend(bytes) }
    fun revoke(id: String) { pairing.revoke(id); sessions.values.filter { it.deviceId == id }.forEach { it.close() } }
    fun stop() {
        listeners.forEach { runCatching { it.close() } }; listeners.clear()
        sessions.values.forEach { it.close() }; sessions.clear()
        audio.values.forEach { it.close() }; audio.clear(); scope.cancel()
    }
}
