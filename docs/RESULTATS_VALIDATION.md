# Résultats de validation de CareLink 2.1.0-debug

Vérifications locales effectuées le 5 novembre 2026.

| Vérification | Résultat |
| --- | --- |
| Compilation de l’APK et de l’application de test | Réussite |
| Tests JVM / Robolectric | 45 réussis, aucun échec ni test ignoré |
| Compilation des tests instrumentés | 5 tests compilés |
| Exécution instrumentée Android 15 / API 35 | Non réalisée ; la tentative antérieure sur émulateur n’a pas abouti au démarrage |
| Analyse Android Lint | Réussite sans erreur bloquante ; des avertissements subsistent |
| Signature de l’APK | Vérifiée ; même certificat de développement que les versions précédentes |

Les tests de regroupement vérifient la conservation de chaque épisode dans le journal, une seule notification par chambre/type, la prise en charge groupée, la réactivation au nouveau bruit, les actions en attente après reprise et la préservation d’un épisode arrivé après le clic de résolution. Les tests de tableau de bord vérifient les raccourcis sans navigation involontaire, le compteur d’événements pris en charge, les informations au toucher et à l’appui long.

Les tests d’interface vérifient le code actif, sa désactivation depuis les réglages, la mise à jour immédiate du bouton et l’ajout d’une chambre sans demande de code, y compris lorsqu’un code reste enregistré. Le rendu de l’icône réelle de l’application a été inspecté sur les captures.

Les tests JVM couvrent notamment le choix du certificat pour les moteurs TLS et les sockets, les candidats d’adresses et les annonces malformées, la détection acoustique, la persistance des événements, l’appairage, la révocation et le contrôle du certificat. Les régressions de notification vérifient qu’une panne continue ne recrée pas d’alerte après clôture ou redémarrage et qu’une prise en charge distante coupe les rappels locaux pendant l’attente de confirmation.

Un nouveau test instrumenté fait communiquer deux identités et deux journaux avec le vrai RoomServer et MonitorClient par l’interface réseau locale. Il contrôle l’appairage, la télémétrie de démarrage et de capture, le nombre de veilleurs et la livraison d’un événement. Ce test est compilé ; son exécution reste à effectuer sur un appareil ou un émulateur opérationnel. Les résultats précédents sont archivés dans les sous-dossiers des versions précédentes de `validation-results/` ; ils ne constituent pas une validation native du nouvel APK.

L’APK et son empreinte sont dans [downloads](../downloads/). Les comptes, métadonnées et états de vérification sont dans [validation-results](validation-results/). Les captures de [screenshots](screenshots/) proviennent du rendu des tests d’interface. `monitor-alerts.png` montre le composant de liste avec des données d’essai, sans l’en-tête et la navigation de l’activité.

```sh
./gradlew --no-build-cache clean :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest # sur un appareil de test connecté
```

La correction de compatibilité TLS et les nouveaux diagnostics ne garantissent pas que le réseau d’établissement autorise les communications entre téléphones. Consulter le [guide de dépannage](DEPANNAGE_RESEAU.md), puis effectuer la [qualification technique et opérationnelle](VALIDATION.md) sur les appareils retenus. Aucun essai sur les deux modèles Redmi signalés ni essai prolongé en conditions réelles n’a été réalisé ici.
