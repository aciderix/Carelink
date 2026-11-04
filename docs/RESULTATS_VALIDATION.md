# Résultats de validation de CareLink 2.0.1-debug

Vérifications locales effectuées le 4 novembre 2026.

| Vérification | Résultat |
| --- | --- |
| Compilation de l’APK et de l’application de test | Réussite |
| Tests JVM / Robolectric | 35 réussis, aucun échec ni test ignoré |
| Compilation des tests instrumentés | 5 tests compilés |
| Exécution instrumentée Android 15 / API 35 | Non réalisée : les émulateurs cloud n’achèvent pas leur démarrage |
| Analyse Android Lint | Réussite sans erreur bloquante ; des avertissements subsistent |
| Signature de l’APK | Vérifiée ; même certificat de développement que la version 2.0.0 |

Les tests JVM couvrent notamment le choix du certificat pour les moteurs TLS et les sockets, les candidats d’adresses et les annonces malformées, la détection acoustique, la persistance des événements, l’appairage, la révocation et le contrôle du certificat. Les régressions de notification vérifient qu’une panne continue ne recrée pas d’alerte après clôture ou redémarrage et qu’une prise en charge distante coupe les rappels locaux pendant l’attente de confirmation.

Un nouveau test instrumenté fait communiquer deux identités et deux journaux avec le vrai RoomServer et MonitorClient par l’interface réseau locale. Il contrôle l’appairage, la télémétrie de démarrage et de capture, le nombre de veilleurs et la livraison d’un événement. Ce test est compilé ; son exécution reste à effectuer sur un appareil ou un émulateur opérationnel. Les résultats Android API 28 de la version précédente sont archivés dans `validation-results/2.0.0/` et ne constituent pas une validation du nouvel APK.

L’APK et son empreinte sont dans [downloads](../downloads/). Les comptes, métadonnées et états de vérification sont dans [validation-results](validation-results/). Les captures de [screenshots](screenshots/) proviennent du rendu des tests d’interface.

```sh
./gradlew --no-build-cache clean :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest # sur un appareil de test connecté
```

La correction de compatibilité TLS et les nouveaux diagnostics ne garantissent pas que le réseau d’établissement autorise les communications entre téléphones. Consulter le [guide de dépannage](DEPANNAGE_RESEAU.md), puis effectuer la [qualification technique et opérationnelle](VALIDATION.md) sur les appareils retenus. Aucun essai sur les deux modèles Redmi signalés ni essai prolongé en conditions réelles n’a été réalisé ici.
