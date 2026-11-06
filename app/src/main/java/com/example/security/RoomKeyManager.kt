package com.example.security

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.X509ExtendedKeyManager

/** Android providers may negotiate SSLSockets through an SSLEngine internally. */
class RoomKeyManager(private val key: PrivateKey, private val chain: Array<X509Certificate>) : X509ExtendedKeyManager() {
    private fun alias(keyType: String?) = if (keyType == "EC") "carelink-tls-v2" else null
    override fun getPrivateKey(alias: String?) = if (alias == "carelink-tls-v2") key else null
    override fun getCertificateChain(alias: String?) = if (alias == "carelink-tls-v2") chain.clone() else null
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?) = alias(keyType)?.let { arrayOf(it) }
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?) = alias(keyType)
    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?) = alias(keyType)
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = null
    override fun chooseClientAlias(keyTypes: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = null
    override fun chooseEngineClientAlias(keyTypes: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?) = null
}
