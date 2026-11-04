package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object AdminPin {
    fun hash(pin: String): String {
        require(pin.length >= 6 && pin.all { it.isDigit() })
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(derive(pin, salt), Base64.NO_WRAP)
    }
    fun verify(pin: String, encoded: String): Boolean = runCatching {
        val parts = encoded.split(':'); parts.size == 2 && MessageDigest.isEqual(derive(pin, Base64.decode(parts[0], Base64.NO_WRAP)), Base64.decode(parts[1], Base64.NO_WRAP))
    }.getOrDefault(false)
    // SHA-1 PBKDF2 is available on every supported Android version; 160-bit output with a random salt.
    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 160)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
