package com.example.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/** Discovery supplies address candidates only. Every connection still requires the QR-pinned identity. */
class LanDiscoveryManager(private val context: Context) {
    data class DiscoveredRoomBeacon(val id: String, val ipAddress: String, val controlPort: Int = 9877,
        val audioPort: Int = 9878, val talkbackPort: Int = 9879)
    companion object {
        const val DISCOVERY_PORT = 9876
        const val SERVICE_TYPE = "_carelink._tcp."
        private fun validId(id: String) = id.length in 1..80 && id.matches(Regex("[A-Za-z0-9_-]+"))
        fun parseBeacon(json: JSONObject, address: String): DiscoveredRoomBeacon? {
            if (json.optString("type") != "CARELINK_ROOM_BEACON" || json.optInt("version", 2) != 2 ||
                !validId(json.optString("id")) || !RoomAddresses.isLocalAddress(address) || json.optInt("controlPort", 9877) != 9877) return null
            return DiscoveredRoomBeacon(json.getString("id"), address)
        }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val listeners = CopyOnWriteArrayList<(DiscoveredRoomBeacon) -> Unit>()
    private val resolved = ConcurrentHashMap<String, DiscoveredRoomBeacon>()
    private var watcher: Job? = null
    private var udp: Job? = null
    @Volatile private var socket: DatagramSocket? = null
    @Volatile private var generation = 0
    @Volatile private var mdnsStatus = "mDNS en cours"
    @Volatile private var udpStatus = "UDP en cours"
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    var onDiagnostic: ((String) -> Unit)? = null
    fun addBeaconListener(listener: (DiscoveredRoomBeacon) -> Unit) { listeners.add(listener) }
    fun removeBeaconListener(listener: (DiscoveredRoomBeacon) -> Unit) { listeners.remove(listener) }
    fun startBroadcasting(unitId: String) = start(unitId)
    fun startListening() = start(null)

    private fun start(unitId: String?) {
        stop()
        watcher = scope.launch {
            var previousIp: String? = null
            var previousNetwork: android.net.Network? = null
            while (isActive) {
                val ip = NetworkUtils.getLocalIpAddress(context)
                val network = NetworkUtils.localNetwork(context)
                if (ip != previousIp || network != previousNetwork) {
                    previousIp = ip
                    previousNetwork = network
                    stopEndpoints()
                    if (ip.isNotBlank()) {
                        val token = generation
                        if (unitId == null) listenUdp(token) else broadcastUdp(unitId, token)
                        withContext(Dispatchers.Main) { if (token == generation) startNsd(unitId, token) }
                    }
                }
                if (ip.isBlank()) onDiagnostic?.invoke("Aucun réseau local Wi-Fi ou Ethernet disponible")
                else {
                    onDiagnostic?.invoke("Réseau local · $ip · $mdnsStatus · $udpStatus")
                    resolved.values.forEach { beacon -> listeners.forEach { it(beacon) } }
                }
                delay(3000)
            }
        }
    }
    private fun broadcastUdp(id: String, token: Int) {
        udp = scope.launch {
            var local: DatagramSocket? = null
            try {
                local = DatagramSocket().apply { broadcast = true }
                socket = local
                NetworkUtils.localNetwork(context)?.bindSocket(local)
                udpStatus = "UDP actif"
                val bytes = JSONObject().put("type", "CARELINK_ROOM_BEACON").put("version", 2)
                    .put("id", id).put("controlPort", 9877).toString().toByteArray()
                while (isActive && token == generation) {
                    udpStatus = if (runCatching { local.send(DatagramPacket(bytes, bytes.size, NetworkUtils.getBroadcastAddress(context), DISCOVERY_PORT)) }.isSuccess)
                        "UDP actif" else "UDP indisponible"
                    delay(2000)
                }
            } catch (_: Exception) {
                if (token == generation) udpStatus = "UDP indisponible"
            } finally { local?.close() }
        }
    }
    private fun listenUdp(token: Int) {
        udp = scope.launch {
            var local: DatagramSocket? = null
            try {
                local = DatagramSocket(null)
                socket = local
                local.reuseAddress = true; local.bind(InetSocketAddress(DISCOVERY_PORT)); local.broadcast = true
                NetworkUtils.localNetwork(context)?.bindSocket(local)
                udpStatus = "UDP actif"
                val bytes = ByteArray(2048)
                while (isActive && token == generation) {
                    try {
                        val packet = DatagramPacket(bytes, bytes.size)
                        local.receive(packet)
                        val beacon = parseBeacon(JSONObject(String(packet.data, 0, packet.length, Charsets.UTF_8)), packet.address.hostAddress.orEmpty())
                        if (beacon != null && token == generation) listeners.forEach { it(beacon) }
                    } catch (e: Exception) { if (local.isClosed || !isActive) break }
                }
            } catch (_: Exception) {
                if (token == generation) udpStatus = "UDP indisponible"
            } finally { local?.close() }
        }
    }
    @Suppress("DEPRECATION")
    private fun startNsd(unitId: String?, token: Int) {
        val manager = nsd ?: run { mdnsStatus = "mDNS indisponible"; return }
        if (unitId != null) {
            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo) {
                    if (token != generation) runCatching { manager.unregisterService(this) } else mdnsStatus = "mDNS actif"
                }
                override fun onRegistrationFailed(info: NsdServiceInfo, code: Int) { if (token == generation) mdnsStatus = "mDNS indisponible" }
                override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
                override fun onUnregistrationFailed(info: NsdServiceInfo, code: Int) = Unit
            }
            registration = listener
            runCatching {
                manager.registerService(NsdServiceInfo().apply {
                    serviceName = "CareLink-$unitId"; serviceType = SERVICE_TYPE; port = 9877
                    setAttribute("id", unitId); setAttribute("version", "2")
                }, NsdManager.PROTOCOL_DNS_SD, listener)
            }.onFailure { if (token == generation) mdnsStatus = "mDNS indisponible" }
            return
        }
        val queue = ConcurrentLinkedQueue<NsdServiceInfo>()
        val resolving = AtomicBoolean(false)
        fun resolveNext() {
            if (token != generation || !resolving.compareAndSet(false, true)) return
            val info = queue.poll()
            if (info == null) { resolving.set(false); return }
            val callback = object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, code: Int) {
                    resolving.set(false); resolveNext()
                }
                override fun onServiceResolved(service: NsdServiceInfo) {
                    if (token == generation) {
                        val id = service.attributes["id"]?.toString(Charsets.UTF_8).orEmpty()
                        val version = service.attributes["version"]?.toString(Charsets.UTF_8)
                        val address = service.host?.hostAddress.orEmpty()
                        if (version == "2" && validId(id) && RoomAddresses.isLocalAddress(address) && service.port == 9877 && resolved.size < 128) {
                            val beacon = DiscoveredRoomBeacon(id, address)
                            resolved[info.serviceName] = beacon
                            listeners.forEach { it(beacon) }
                        }
                    }
                    resolving.set(false); resolveNext()
                }
            }
            try { manager.resolveService(info, callback) } catch (_: Exception) { resolving.set(false) }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {
                if (token != generation) runCatching { manager.stopServiceDiscovery(this) } else mdnsStatus = "mDNS actif"
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                if (token == generation && info.serviceName.startsWith("CareLink-") && info.serviceType.trimEnd('.') == SERVICE_TYPE.trimEnd('.') && queue.size < 128) {
                    queue.offer(info); resolveNext()
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) { if (token == generation) resolved.remove(info.serviceName) }
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { if (token == generation) mdnsStatus = "mDNS indisponible" }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
        }
        discovery = listener
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
            .onFailure { if (token == generation) mdnsStatus = "mDNS indisponible" }
    }
    private fun stopEndpoints() {
        generation++
        mdnsStatus = "mDNS en cours"; udpStatus = "UDP en cours"
        udp?.cancel(); udp = null
        runCatching { socket?.close() }; socket = null
        registration?.let { listener -> runCatching { nsd?.unregisterService(listener) } }; registration = null
        discovery?.let { listener -> runCatching { nsd?.stopServiceDiscovery(listener) } }; discovery = null
        resolved.clear()
    }
    fun stop() { watcher?.cancel(); watcher = null; stopEndpoints() }
    fun release() { stop(); scope.cancel() }
}
