package com.example.network

import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException

class PairingRejected(val enrolling: Boolean) : Exception()

object ConnectionDiagnostic {
    fun describe(error: Exception, address: String, enrolling: Boolean): String {
        val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
        return when {
            causes.any { it is PairingRejected } -> if (enrolling)
                "QR expiré, annulé ou déjà utilisé. Ouvrez un nouveau QR sur la chambre et scannez-le à nouveau."
                else "Accès refusé par la chambre. Vérifiez la révocation puis refaites l’appairage."
            causes.any { it is SSLException || it is CertificateException } ->
                "Connexion TLS refusée : vérifiez la date des deux téléphones et installez la même version CareLink. Si la chambre a été réinstallée, refaites l’appairage."
            causes.any { it is SecurityException } ->
                "Accès réseau refusé par Android. Vérifiez les autorisations réseau local de CareLink et le VPN."
            causes.any { it is ConnectException || it is NoRouteToHostException || it is SocketTimeoutException } ->
                "Chambre injoignable à $address:9877. Gardez CareLink ouvert côté chambre. Vérifiez le Wi-Fi, le VPN et l’isolation des appareils sur le réseau invité."
            causes.any { it is EOFException } && enrolling ->
                "Appairage non confirmé par la chambre. Gardez son QR ouvert ; s’il a expiré, générez-en un nouveau."
            else -> "Connexion interrompue avec $address. Nouvelle tentative automatique ; vérifiez CareLink et le réseau côté chambre."
        }
    }
}
