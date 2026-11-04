# CareLink 🔔

**Système de surveillance sonore multi-chambres pour adultes dépendants / personnes handicapées.**

CareLink permet à un poste de surveillance (soignant, aidant, veilleur) de superviser simultanément plusieurs chambres équipées d'un appareil Android agissant comme capteur sonore, directement sur le réseau local Wi-Fi, sans aucun serveur cloud tiers ni abonnement.

---

## 🌟 Fonctionnalités principales

- **Surveillance Multi-Chambres Simultanée** : Visualisez l'ensemble des chambres actives sur un tableau de bord unifié en temps réel.
- **100% Réseau Local Wi-Fi (LAN)** : Découverte automatique des appareils de chambres via balises UDP (port 9876) et flux directs TCP. Aucune connexion internet requise.
- **Détection acoustique du bruit & Décibelmètre en direct** : Mesure continue en décibels (RMS) et réglage d'un seuil de sensibilité en dB par chambre.
- **Alertes instantanées** : Bannière prioritaire, vibration et signal sonore lors d'un dépassement de seuil (appels, chutes, bruits anormaux).
- **Écoute en direct** : Écoute audio en streaming local pour chaque chambre.
- **Interphone bidirectionnel (*Maintenir pour parler*)** : Permet au soignant de parler immédiatement dans la chambre pour rassurer la personne.
- **Télémétrie & État des appareils** : Affichage en continu du niveau de batterie (%), de l'état de charge et de la température de chaque chambre.
- **Historique des alertes** : Journal horodaté des alertes sonores avec niveau maximal atteint.

---

## 📱 Modes de fonctionnement

1. **Appareil Chambre (Émetteur micro)** : Placé dans la chambre du résident/adulte, il capte le son ambiant, transmet les alertes et diffuse la voix de l'interphone.
2. **Poste de Surveillance (Récepteur)** : Gardé auprès du soignant ou de l'aidant pour superviser toutes les chambres.

---

## 🛠️ Stack technique

- **Langage** : Kotlin
- **UI** : Jetpack Compose & Material 3 (Thème sombre optimisé OLED)
- **Architecture** : MVVM (StateFlow, Coroutines)
- **Audio** : Android `AudioRecord` (16 kHz PCM 16-bit) & `AudioTrack`
- **Réseau** : UDP Multicast / Broadcast pour la découverte + TCP Sockets pour le streaming audio et la télémétrie
