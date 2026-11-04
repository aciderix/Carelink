package com.example.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

class LanDiscoveryManager {

    companion object {
        const val DISCOVERY_PORT = 9876
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var broadcastJob: Job? = null
    private var listenJob: Job? = null
    private var socket: DatagramSocket? = null

    data class DiscoveredRoomBeacon(
        val id: String,
        val name: String,
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
     * Start broadcasting presence as a Room Unit over UDP
     */
    fun startBroadcasting(unitId: String, unitName: String) {
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
                        put("name", unitName)
                        put("controlPort", 9877)
                        put("audioPort", 9878)
                        put("talkbackPort", 9879)
                        put("timestamp", System.currentTimeMillis())
                    }.toString()

                    val bytes = beaconJson.toByteArray(Charsets.UTF_8)
                    val targetAddress = NetworkUtils.getBroadcastAddress()
                    val packet = DatagramPacket(bytes, bytes.size, targetAddress, DISCOVERY_PORT)

                    try {
                        broadcastSocket.send(packet)
                    } catch (e: Exception) {
                        Log.d("LanDiscovery", "Broadcast send error: ${e.message}")
                    }

                    kotlinx.coroutines.delay(1800)
                }
            } catch (e: Exception) {
                Log.d("LanDiscovery", "Broadcaster error: ${e.message}")
            } finally {
                broadcastSocket?.close()
            }
        }
    }

    /**
     * Start listening for Room Unit beacons as a Caregiver Monitor Unit
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
                        recvSocket.receive(packet)
                        val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val senderIp = packet.address.hostAddress ?: ""

                        val json = JSONObject(message)
                        if (json.optString("type") == "CARELINK_ROOM_BEACON") {
                            val beacon = DiscoveredRoomBeacon(
                                id = json.optString("id"),
                                name = json.optString("name", "Chambre"),
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
                socket?.close()
                socket = null
            }
        }
    }

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
