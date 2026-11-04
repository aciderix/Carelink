package com.example.security

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.example.data.PreferencesManager
import com.example.data.SecureVault
import com.example.data.CredentialVault
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

data class PairedRoom(val id: String, val name: String, val certificate: String, val credential: String,
    val address: String = "", val enrolling: Boolean = false) {
    fun json(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("certificate", certificate); put("credential", credential)
        put("address", address); put("enrolling", enrolling)
    }
    companion object {
        fun parse(j: JSONObject): PairedRoom = PairedRoom(j.getString("id"), j.getString("name").take(80),
            j.getString("certificate"), j.getString("credential"), j.optString("address"), j.optBoolean("enrolling"))
    }
}

/** Single-use, two-minute enrollment. Each enrolled monitor receives a separate revocable credential. */
class PairingStore(context: Context, private val vault: CredentialVault = SecureVault(context)) {
    private val prefs = PreferencesManager(context)
    fun rooms(): List<PairedRoom> = synchronized(lock) {
        val array = JSONArray(vault.read("rooms") ?: "[]")
        List(array.length()) { PairedRoom.parse(array.getJSONObject(it)) }
    }
    fun save(room: PairedRoom): Unit = synchronized(lock) {
        val rooms = rooms().filterNot { it.id == room.id } + room
        vault.write("rooms", JSONArray(rooms.map { it.json() }).toString())
    }
    fun forget(id: String): Unit = synchronized(lock) {
        vault.write("rooms", JSONArray(rooms().filterNot { it.id == id }.map { it.json() }).toString())
    }
    fun importCode(code: String) {
        require(code.startsWith("CL2.")) { "Code CareLink 2 attendu" }
        require(code.length < 12000)
        val j = JSONObject(String(Base64.decode(code.removePrefix("CL2."), Base64.URL_SAFE or Base64.NO_WRAP)))
        require(j.getInt("version") == 2)
        val room = PairedRoom.parse(j)
        require(room.id.length in 1..80 && room.credential.length == 43)
        PinnedTrustManager.decode(room.certificate).checkValidity()
        save(room.copy(enrolling = true))
    }
    fun createCode(certificate: String, address: String): String = synchronized(lock) {
        enrollment = randomToken(); enrollmentExpiry = SystemClock.elapsedRealtime() + 120000
        val room = PairedRoom(prefs.installationId, prefs.roomName, certificate, enrollment!!, address, true)
        val json = room.json().put("version", 2).toString()
        "CL2." + Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    fun enrollmentOpen(): Boolean = synchronized(lock) { enrollment != null && android.os.SystemClock.elapsedRealtime() < enrollmentExpiry }
    fun cancelEnrollment() { synchronized(lock) { enrollment = null; enrollmentExpiry = 0L } }
    fun monitors(): List<String> = synchronized(lock) {
        val j = JSONObject(vault.read("monitors") ?: "{}")
        j.keys().asSequence().toList()
    }
    fun revoke(id: String): Unit = synchronized(lock) {
        val j = JSONObject(vault.read("monitors") ?: "{}"); j.remove(id); vault.write("monitors", j.toString())
    }
    fun authorize(id: String, credential: String, enroll: Boolean): String = synchronized(lock) {
        require(id.length in 1..80 && credential.length == 43) { "Identité invalide" }
        val j = JSONObject(vault.read("monitors") ?: "{}")
        if (enroll) {
            require(enrollment != null && SystemClock.elapsedRealtime() < enrollmentExpiry && equal(enrollment!!, credential)) { "Appairage expiré ou déjà utilisé" }
            val token = randomToken(); j.put(id, token); vault.write("monitors", j.toString())
            enrollment = null; enrollmentExpiry = 0; token
        } else {
            require(j.has(id) && equal(j.getString(id), credential)) { "Appareil non autorisé" }
            credential
        }
    }
    fun stillAuthorized(id: String, credential: String): Boolean = synchronized(lock) {
        val j = JSONObject(vault.read("monitors") ?: "{}")
        j.has(id) && equal(j.getString(id), credential)
    }
    companion object {
        private val lock = Any()
        private var enrollment: String? = null
        private var enrollmentExpiry = 0L
        private fun equal(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
        private fun randomToken(): String = Base64.encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) },
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
