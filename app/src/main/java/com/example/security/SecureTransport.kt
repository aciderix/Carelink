package com.example.security

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.data.PreferencesManager
import com.example.network.NetworkUtils
import com.example.network.PairingRejected
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

/** Exact certificate pinning: discovery and IP addresses never establish trust. */
class PinnedTrustManager(private val pinned: X509Certificate) : X509TrustManager {
    private val delegate: X509TrustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
        init(KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("paired-room", pinned) })
    }.trustManagers.filterIsInstance<X509TrustManager>().single()
    override fun getAcceptedIssuers() = arrayOf(pinned)
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) { throw CertificateException("Client certificate not supported") }
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Missing certificate")
        leaf.checkValidity()
        if (!MessageDigest.isEqual(leaf.encoded, pinned.encoded)) throw CertificateException("CareLink identity mismatch")
        delegate.checkServerTrusted(chain, authType)
    }
    companion object {
        fun decode(encoded: String) = CertificateFactory.getInstance("X.509").generateCertificate(
            Base64.decode(encoded, Base64.NO_WRAP).inputStream()) as X509Certificate
    }
}

class SecureSession(val socket: SSLSocket, val deviceId: String, val credential: String = "") : AutoCloseable {
    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(socket.outputStream)
    @Volatile var writingSince = 0L; private set
    @Synchronized fun send(bytes: ByteArray) {
        require(bytes.size in 1..32768)
        writingSince = SystemClock.elapsedRealtime()
        try { output.writeInt(bytes.size); output.write(bytes); output.flush() } finally { writingSince = 0 }
    }
    fun send(json: JSONObject) = send(json.toString().toByteArray(Charsets.UTF_8))
    fun receive(): ByteArray {
        val size = input.readInt(); require(size in 1..32768) { "Trame invalide" }
        return ByteArray(size).also { input.readFully(it) }
    }
    fun receiveJson() = JSONObject(receive().toString(Charsets.UTF_8))
    override fun close() { runCatching { socket.close() } }
    fun checkWriteDeadline() { if (writingSince != 0L && SystemClock.elapsedRealtime() - writingSince > 2000) close() }
}

class SecureTransport(private val context: Context) {
    private val pairing = PairingStore(context)
    private val prefs = PreferencesManager(context)
    private fun identity(): KeyStore = synchronized(identityLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        // 32-bit Android 8 keystores could wrap far-future dates; an unusable identity is replaced, not kept forever.
        if (store.containsAlias("carelink-tls-v2") && !usable(store.getCertificate("carelink-tls-v2") as? X509Certificate)) store.deleteEntry("carelink-tls-v2")
        if (!store.containsAlias("carelink-tls-v2")) {
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder("carelink-tls-v2", KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(java.security.spec.ECGenParameterSpec("secp256r1"))
                    // Conscrypt performs raw ECDSA signing of the already-hashed TLS transcript.
                    .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA384)
                    .setCertificateSubject(X500Principal("CN=CareLink"))
                    .setCertificateSerialNumber(java.math.BigInteger(80, java.security.SecureRandom()))
                    .setCertificateNotBefore(Date(1577836800000L)).setCertificateNotAfter(Date(CERTIFICATE_NOT_AFTER)).build())
            }.generateKeyPair()
            store.load(null)
        }
        store
    }
    fun certificate(): String = Base64.encodeToString(identity().getCertificate("carelink-tls-v2").encoded, Base64.NO_WRAP)
    fun server(port: Int): SSLServerSocket {
        val store = identity()
        val manager = RoomKeyManager(store.getKey("carelink-tls-v2", null) as PrivateKey,
            arrayOf(store.getCertificate("carelink-tls-v2") as X509Certificate))
        val ssl = SSLContext.getInstance("TLS").apply { init(arrayOf(manager), null, null) }
        return (ssl.serverSocketFactory.createServerSocket() as SSLServerSocket).apply {
            reuseAddress = true; enabledProtocols = supportedProtocols.filter { it == "TLSv1.2" || (android.os.Build.VERSION.SDK_INT >= 29 && it == "TLSv1.3") }.toTypedArray()
            bind(InetSocketAddress(port))
        }
    }
    fun accept(socket: SSLSocket, channel: String): SecureSession {
        socket.soTimeout = 5000; socket.tcpNoDelay = true; socket.sendBufferSize = 4096; socket.receiveBufferSize = 4096; socket.startHandshake()
        val session = SecureSession(socket, "")
        val hello = session.receiveJson()
        require(hello.getInt("version") == 2 && hello.getString("roomId") == prefs.installationId)
        require(hello.getString("channel") == channel)
        val id = hello.getString("deviceId")
        val enrolling = hello.optBoolean("enroll") && channel == "control"
        val credential = try { pairing.authorize(id, hello.getString("credential"), enrolling) }
        catch (_: IllegalArgumentException) {
            session.send(JSONObject().put("version", 2).put("type", "AUTH_ERROR"))
            throw PairingRejected(enrolling)
        }
        session.send(JSONObject().put("version", 2).put("credential", credential))
        return SecureSession(socket, id, credential)
    }
    fun connect(room: PairedRoom, channel: String, port: Int): SecureSession {
        require(room.address.isNotBlank()) { "Adresse de la chambre inconnue" }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(PinnedTrustManager(PinnedTrustManager.decode(room.certificate))), null) }
        val loopback = room.address == "127.0.0.1" || room.address == "::1"
        val link = if (loopback) null else NetworkUtils.localLink(context)
            ?: throw IllegalStateException("Réseau local Wi-Fi ou Ethernet indisponible")
        var raw = link?.network?.socketFactory?.createSocket() ?: Socket()
        var socket: SSLSocket? = null
        try {
            try { raw.connect(InetSocketAddress(room.address, port), 3500) }
            catch (e: java.io.IOException) {
                // Some OEM builds refuse sockets bound to a Network object: retry once on the default route.
                if (link?.network == null || e is java.net.ConnectException || e is java.net.SocketTimeoutException) throw e
                runCatching { raw.close() }
                raw = Socket(); raw.connect(InetSocketAddress(room.address, port), 3500)
            }
            socket = ssl.socketFactory.createSocket(raw, room.address, port, true) as SSLSocket
            socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.2" || (android.os.Build.VERSION.SDK_INT >= 29 && it == "TLSv1.3") }.toTypedArray()
            socket.soTimeout = 6000; socket.tcpNoDelay = true; socket.sendBufferSize = 4096; socket.receiveBufferSize = 4096
            socket.startHandshake()
            val session = SecureSession(socket, room.id)
            session.send(JSONObject().put("version", 2).put("roomId", room.id).put("deviceId", prefs.installationId)
                .put("channel", channel).put("credential", room.credential).put("enroll", room.enrolling))
            val response = session.receiveJson(); require(response.getInt("version") == 2)
            if (response.optString("type") == "AUTH_ERROR") throw PairingRejected(room.enrolling)
            if (room.enrolling) pairing.save(room.copy(credential = response.getString("credential"), enrolling = false))
            return session
        } catch (e: Exception) { runCatching { socket?.close() }; runCatching { raw.close() }; throw e }
    }
    companion object {
        private val identityLock = Any()
        /** 31 Dec 2037: stays below the 2038 limit of 32-bit time on older devices. */
        const val CERTIFICATE_NOT_AFTER = 2145830400000L
        /** Only an already expired identity (wrapped date) is replaced; a wrong clock in the past never discards a valid one. */
        fun usable(certificate: X509Certificate?, now: Date = Date()): Boolean = certificate != null && certificate.notAfter.after(now)
    }
}
