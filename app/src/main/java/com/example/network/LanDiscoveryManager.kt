package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class LanDiscoveryManager(private val context: android.content.Context) {

    companion object {
        const val DISCOVERY_PORT = 9876
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var socket: DatagramSocket? = null

    data class DiscoveredRoomBeacon(
        val id: String,
        val ipAddress: String,
        val controlPort: Int = 9877,
        val audioPort: Int = 9878,
        val talkbackPort: Int = 9879
    )

    private val beaconListeners = CopyOnWriteArrayList<(DiscoveredRoomBeacon) -> Unit>()

    fun addBeaconListener(listener: (DiscoveredRoomBeacon) -> Unit) {
        beaconListeners.add(listener)
    }

    fun removeBeaconListener(listener: (DiscoveredRoomBeacon) -> Unit) {
        beaconListeners.remove(listener)
    }

    /**
     * Start broadcasting presence as a Room Unit over UDP.
     * Note: For privacy in care facilities, resident personal names are NOT included
     * in plaintext discovery broadcasts; only the technical room installation UUID is sent.
     */
    fun startBroadcasting(unitId: String) {
        stop()
        broadcastJob = scope.launch {
            var broadcastSocket: DatagramSocket? = null
            try {
                broadcastSocket = DatagramSocket()
                broadcastSocket.broadcast = true

                while (isActive) {
                    val beaconJson = JSONObject().apply {
                        put("type", "CARELINK_ROOM_BEACON")
                        put("id", unitId)
                        put("controlPort", 9877)
                        put("audioPort", 9878)
                        put("talkbackPort", 9879)
                        put("timestamp", System.currentTimeMillis())
                    }.toString()

                    val bytes = beaconJson.toByteArray(Charsets.UTF_8)
                    val targetAddress = NetworkUtils.getBroadcastAddress(context)
                    val packet = DatagramPacket(bytes, bytes.size, targetAddress, DISCOVERY_PORT)

                    try {
                        broadcastSocket.send(packet)
                    } catch (e: Exception) {
                        Log.d("LanDiscovery", "Broadcast send error: ${e.message}")
                    }

                    kotlinx.coroutines.delay(2000)
                }
            } catch (e: Exception) {
                Log.d("LanDiscovery", "Broadcaster error: ${e.message}")
            } finally {
                try { broadcastSocket?.close() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Start listening for Room Unit beacons on the caregiver monitor device.
     */
    fun startListening() {
        stop()
        listenJob = scope.launch {
            try {
                val recvSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                    broadcast = true
                }
                socket = recvSocket

                val buffer = ByteArray(2048)
                val packet = DatagramPacket(buffer, buffer.size)

                while (isActive) {
                    try {
                        packet.length = buffer.size
                        recvSocket.receive(packet)
                        val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val senderIp = packet.address.hostAddress ?: ""

                        val json = JSONObject(message)
                        if (json.optString("type") == "CARELINK_ROOM_BEACON") {
                            val beacon = DiscoveredRoomBeacon(
                                id = json.optString("id"),
                                ipAddress = senderIp,
                                controlPort = json.optInt("controlPort", 9877),
                                audioPort = json.optInt("audioPort", 9878),
                                talkbackPort = json.optInt("talkbackPort", 9879)
                            )
                            for (l in beaconListeners) {
                                l(beacon)
                            }
                        }
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (e: Exception) {
                Log.d("LanDiscovery", "Listener error: ${e.message}")
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                socket = null
            }
        }
    }

    fun release() { stop(); scope.cancel() }

    fun stop() {
        broadcastJob?.cancel()
        broadcastJob = null
        listenJob?.cancel()
        listenJob = null
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
    }
}
