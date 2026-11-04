# Résultats de validation de CareLink 2.0.0-debug

Vérifications locales effectuées le 4 novembre 2026 sur le code livré avec cet APK.

| Vérification | Résultat |
| --- | --- |
| Compilation de l’APK et de l’application de test | Réussite |
| Tests JVM / Robolectric | 28 réussis, aucun échec ni test ignoré |
| Tests instrumentés sur émulateur Android API 28 | 4 réussis |
| Analyse Android Lint | Réussite sans erreur bloquante ; des avertissements subsistent |
| Signature de l’APK | Signature APK v2 vérifiée |

Les tests couvrent notamment la détection acoustique, la persistance et les transitions des événements, les reçus et retransmissions, l’appairage et sa révocation, le contrôle du certificat, le code professionnel, le transport TLS avec AndroidKeyStore et les parcours d’interface.

Les comptes et sorties sont disponibles dans [validation-results](validation-results/). L’APK et son empreinte sont dans [downloads](../downloads/). Les captures dans [screenshots](screenshots/) proviennent du rendu des tests d’interface.

Commandes Gradle utilisées pour la vérification complète :

```sh
./gradlew --no-build-cache clean :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
```

Les tests instrumentés ont aussi été installés et exécutés avec AndroidJUnitRunner sur l’émulateur API 28. Ces résultats ne constituent pas une qualification sur plusieurs téléphones physiques, sur les versions Android récentes ou sur une nuit complète. Effectuer la [qualification technique et opérationnelle](VALIDATION.md) avant un déploiement en établissement.
