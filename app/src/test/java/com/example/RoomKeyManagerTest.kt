package com.example

import com.example.security.RoomKeyManager
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class RoomKeyManagerTest {
    @Test fun bothEngineAndSocketProvidersCanSelectTheRoomCertificate() {
        val key = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().private
        val cert = CertificateFactory.getInstance("X.509").generateCertificate(javaClass.getResourceAsStream("/certificates/room.pem")) as X509Certificate
        val manager = RoomKeyManager(key, arrayOf(cert))
        val socketAlias = manager.chooseServerAlias("EC", null, null)
        val engineAlias = manager.chooseEngineServerAlias("EC", null, null)
        assertNotNull(engineAlias)
        assertEquals(socketAlias, engineAlias)
        assertEquals(key, manager.getPrivateKey(engineAlias))
        assertArrayEquals(arrayOf(cert), manager.getCertificateChain(engineAlias))
        assertNull(manager.chooseEngineServerAlias("RSA", null, null))
        assertNull(manager.getPrivateKey("unknown"))
    }
}
