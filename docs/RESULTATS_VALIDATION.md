# Résultats de validation de CareLink 2.2.1-debug

Vérifications locales effectuées le 5 novembre 2026.

| Vérification | Résultat |
| --- | --- |
| Compilation de l’APK et de l’application de test | Réussite |
| Tests JVM / Robolectric | 53 réussis, aucun échec ni test ignoré |
| Dispositions téléphone et tablette | Contrôles de rendu en portrait et paysage, sous Robolectric API 34 |
| Compilation des tests instrumentés | 5 tests compilés |
| Exécution instrumentée Android 15 / API 35 | Non réalisée ; la tentative antérieure sur émulateur n’a pas abouti au démarrage |
| Analyse Android Lint | Réussite sans erreur bloquante ; des avertissements subsistent |
| Signature de l’APK | Vérifiée ; même certificat de développement que les versions précédentes |

Les tests de disposition contrôlent l’ordre des indicateurs à gauche du titre, la grille sur téléphone 360 × 800 dp et tablette 800 × 1280 / 1280 × 800 dp, ainsi que la navigation latérale sur écran large et téléphone en paysage 800 × 360 dp. Ils vérifient les commandes principales des fiches, le repli des informations et de la sensibilité, l’accès immédiat aux actions de reprise lors d’une coupure et l’affichage du téléphone chambre sur tablette. Les contrôles de géométrie des deux vues de chambre vérifient aussi le titre et l’état sur une même ligne, puis les deux colonnes et leurs premières cartes alignées sous cet en-tête commun. Les captures ont été inspectées ; elles proviennent de données d’essai et du rendu Robolectric, sans essai sur une tablette physique. Voir [la matrice des contrôles](validation-results/layout-checks.json).

Les régressions existantes vérifient les raccourcis sans navigation involontaire, les informations au toucher et à l’appui long, le compteur de groupes pris en charge, la protection par code active/désactivée et la mise à jour immédiate de son état. Les tests de regroupement conservent chaque épisode dans le journal, une seule notification par chambre/type, la prise en charge groupée, la réactivation au nouveau bruit et la préservation d’un épisode arrivé après le clic de résolution.

Les tests JVM couvrent aussi le choix du certificat TLS, les candidats d’adresses, la détection acoustique, la persistance, l’appairage, la révocation et le contrôle du certificat. Le test instrumenté RoomServer/MonitorClient d’appairage, de télémétrie et d’événement est compilé, mais reste à exécuter sur un appareil ou un émulateur opérationnel. Les résultats antérieurs sont archivés dans les sous-dossiers de `validation-results/`.

L’APK et son empreinte sont dans [downloads](../downloads/). Les métadonnées sont dans [validation-results](validation-results/). Les captures de [screenshots](screenshots/) comprennent le tableau de surveillance complet, les vues de chambre et les rendus tablette portrait/paysage.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest # sur un appareil de test connecté
```

Les essais matériels, de tailles de texte agrandies et d’écran partagé restent à effectuer sur le parc retenu. Consulter le [protocole de qualification](VALIDATION.md) et le [guide réseau](DEPANNAGE_RESEAU.md) pour les essais en établissement. Aucun essai sur les modèles Redmi signalés ni essai prolongé en conditions réelles n’a été réalisé ici.
