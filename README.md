# CareLink 2

Application Android de détection sonore et de communication sur réseau local, en complément des procédures humaines. Deux rôles : **chambre** (capture et événements) et **veilleur** (appareils attendus, événements, écoute et interphone).

## Télécharger la version d’essai

L’APK d’essai est inclus dans cette branche : **[CareLink 2.0.1-debug — télécharger](https://github.com/aciderix/Carelink/raw/refs/heads/carelink-v2-ameliorations-apk/downloads/CareLink-2.0.1-debug.apk)**. Depuis GitHub, ouvrir le fichier puis utiliser **Download raw file** si nécessaire. Le bouton **Code → Download ZIP** récupère aussi les sources et l’APK.

- [Dossier de téléchargement](downloads/) et [empreinte SHA-256](downloads/CareLink-2.0.1-debug.apk.sha256).
- [Résultats de validation](docs/RESULTATS_VALIDATION.md) et [consignes de qualification](docs/VALIDATION.md).
- [Aperçu de l’accueil](docs/screenshots/welcome.png) et [tableau de surveillance](docs/screenshots/monitor-empty.png).

Il s’agit d’une version d’essai distincte de l’application de production. Sur Android, autoriser l’installation depuis la source utilisée pour télécharger l’APK. Consulter les étapes d’appairage ci-dessous et effectuer les essais sur les téléphones prévus avant utilisation en établissement.

## Ce qui change

- Appairage physique par QR ou code à usage unique, valable deux minutes. Chaque veilleur possède un accès distinct, révocable depuis la chambre.
- TLS 1.2/1.3 avec vérification exacte du certificat appairé. Aucun accès audio ou commande en clair, aucune compatibilité automatique avec les versions précédentes.
- Journal SQLite durable : retransmission jusqu’à réception, déduplication par événement, prise en charge et résolution séparées. Les actions distantes sont conservées jusqu’à confirmation de la chambre.
- Chambre attendue conservée après redémarrage du veilleur. Capture récente, état réseau et incidents affichés séparément.
- Services au premier plan indépendants des activités ; aucun type `dataSync` pour la permanence. Retour Android ne désarme plus la surveillance.
- Contrôles de fraîcheur, capture rendue silencieuse, silence numérique prolongé, erreurs et saturation. Le test de transmission ne prétend pas vérifier le microphone.
- Interface sombre, navigation Surveillance / Événements / Réglages, protection des actions sensibles par code et notifications sans acquittement accidentel depuis l’écran verrouillé.
- Pas de SDK Firebase, pas de service cloud applicatif et pas d’enregistrement audio conservé.

## Connexion et découverte dans la version 2.0.1

La recherche automatique utilise mDNS et UDP, et suit les changements d’adresse des chambres appairées. Les chambres détectées mais non autorisées sont affichées lors de l’ajout ; le QR reste nécessaire pour le premier accès.

Le scan est désormais présenté comme un appairage en attente tant que la chambre n’a pas confirmé la connexion. Une panne continue conserve le même incident, y compris après sa clôture ou un redémarrage, pour éviter les nouvelles alertes en boucle. La prise en charge coupe ses rappels. La reprise de la télémétrie clôt l’incident local de coupure.

Si deux téléphones ne se voient pas sur le Wi-Fi de l’établissement, consulter le [guide de dépannage réseau et Xiaomi/HyperOS](docs/DEPANNAGE_RESEAU.md), qui indique aussi les flux à faire vérifier par l’équipe informatique.

## Installer et appairer

1. Installer **la même version 2** sur les deux téléphones, connectés au réseau local autorisé.
2. Sur la chambre, choisir le rôle et autoriser le microphone. Définir un repère de chambre non nominatif, le niveau relatif et la durée de dépassement.
3. Toucher **Appairer un veilleur** et créer ou saisir le code professionnel. Sur le veilleur, toucher **Ajouter une chambre → Scanner le QR**. Le scan est entièrement local. Le code peut aussi être collé via un canal de confiance ; il est confidentiel.
4. Attendre la connexion authentifiée et des échantillons récents. Vérifier les notifications, le volume et l’audibilité sur le poste veilleur.
5. Effectuer un test de transmission puis un véritable essai acoustique. Un test de transmission ne mesure ni la capture ni le besoin d’aide.
6. Protéger les réglages par un code professionnel d’au moins six chiffres et verrouiller le téléphone Android. Conserver ce code selon la procédure du service.

Les autres téléphones du LAN ne sont pas ajoutés automatiquement. La révocation ferme leurs connexions ; un nouvel appairage physique est nécessaire pour rétablir un accès révoqué. Le code expire aussi lorsque l’application chambre passe en arrière-plan pendant l’appairage.

## Événements

- **À vérifier** : événement conservé, non pris en charge ; rappels selon les réglages.
- **Pris en charge** : transition durable confirmée, événement encore visible.
- **Résolu** : clôture humaine, ou retour technique confirmé pour un incident local, conservée sept jours après sa résolution avant nettoyage. Un événement non résolu n’est jamais supprimé par la rétention.
- Les événements sonores et de test proviennent de la chambre. Les incidents de connexion/batterie/capture observés par un veilleur sont locaux à ce poste ; leur résolution ne répare pas l’état technique.
- Les prises en charge des événements distants sont partagées avec les veilleurs appairés via la chambre. Pendant une coupure, elles restent dans la file locale et sont confirmées à la reconnexion.

Une panne du téléphone chambre empêche la coordination distante jusqu’à sa reprise. Un téléphone veilleur arrêté ne peut pas signaler lui-même son arrêt : prévoir un relais humain ou un autre poste.

## Limites explicites

Le niveau affiché est **relatif aux échantillons PCM**, non une mesure calibrée en dB SPL. Le seuil ne distingue pas un appel d’une télévision, ne détecte pas avec certitude un réveil, une chute ou un besoin d’aide et dépend du placement et du matériel.

L’interphone est limité à 30 secondes par appui ; l’écoute est coupée côté veilleur et l’analyse sonore est signalée comme suspendue dans la chambre pendant la parole. Une autorisation ou un flux de capture ne prouve pas que le microphone est dégagé.

Après redémarrage, mise à jour ou arrêt forcé, ouvrir CareLink pour reprendre et vérifier la surveillance. Le système peut masquer le rappel si les notifications sont interdites. Les réglages constructeur, le mode Ne pas déranger, les volumes, la route Bluetooth et les conditions Wi-Fi doivent être qualifiés sur les appareils retenus.

Le journal est dans le stockage privé Android ; les identifiants d’accès sont chiffrés avec AndroidKeyStore. Sauvegarde et transfert Android sont exclus. Désinstallation ou perte du stockage détruisent les données locales : elles ne constituent pas une sauvegarde institutionnelle. Une restauration d’ancienne sauvegarde doit être traitée comme un nouvel enrôlement. Aucune conformité juridique ou qualification de dispositif médical n’est revendiquée.

## Développement

JDK 21, Android SDK 36.1, build-tools 36.0.0 et Gradle 9.3.1. Avec le wrapper :

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest # appareil ou émulateur connecté
```

Dans l’environnement cloud préparé : `source /workspace/.carelink-tools/env.sh`. Ne pas désactiver la vérification TLS du proxy. Le script Gradle local de cet environnement transmet le proxy aux tests Robolectric.

Le build debug est une application distincte (`.debug`, « CareLink · Essai »). Arrêtez l’ancienne application avant les essais : deux serveurs chambre ne peuvent pas partager les mêmes ports sur un téléphone. Le build debug utilise une clé de développement ignorée par Git ; il n’est pas un livrable de production. Pour une livraison release, fournir `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` et la clé `upload` via le mécanisme sécurisé prévu, puis vérifier signature, manifeste et version. Ne jamais ajouter ces valeurs au dépôt. Aucun secret serveur ne doit être compilé dans l’APK.

Voir [qualification technique et opérationnelle](docs/VALIDATION.md) avant un pilote auprès des résidents.
