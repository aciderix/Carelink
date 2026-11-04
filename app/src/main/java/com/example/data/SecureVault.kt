package com.example.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface CredentialVault { fun read(name: String): String?; fun write(name: String, value: String) }

class SecureVault(context: Context) : CredentialVault {
    private val prefs = context.getSharedPreferences("carelink_credentials", Context.MODE_PRIVATE)
    private fun key(): SecretKey = synchronized(lock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("carelink-vault", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("carelink-vault", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun read(name: String): String? {
        val bytes = prefs.getString(name, null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD(name.toByteArray())
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }
    @Synchronized override fun write(name: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(name.toByteArray()) }
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray())
        check(prefs.edit().putString(name, Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    companion object { private val lock = Any() }
}
