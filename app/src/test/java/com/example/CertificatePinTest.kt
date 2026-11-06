package com.example

import com.example.security.PinnedTrustManager
import org.junit.Assert.*
import org.junit.Test
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class CertificatePinTest {
    private fun cert(name: String) = CertificateFactory.getInstance("X.509").generateCertificate(
        javaClass.getResourceAsStream("/certificates/$name.pem")) as X509Certificate
    @Test fun onlyTheExplicitlyPairedCertificateIsTrusted() {
        val trusted = cert("room")
        val manager = PinnedTrustManager(trusted)
        manager.checkServerTrusted(arrayOf(trusted), "EC")
        assertTrue(runCatching { manager.checkServerTrusted(arrayOf(cert("stranger")), "EC") }.isFailure)
        assertTrue(runCatching { manager.checkServerTrusted(emptyArray(), "EC") }.isFailure)
    }
    @Test fun expiredCertificateIsRejectedEvenWhenPinned() {
        val expired = cert("expired")
        assertTrue(runCatching { PinnedTrustManager(expired).checkServerTrusted(arrayOf(expired), "EC") }.isFailure)
    }
}
