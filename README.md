# CareLink 2

Application Android de détection sonore et de communication sur réseau local, en complément des procédures humaines. Deux rôles : **chambre** (capture et événements) et **veilleur** (appareils attendus, événements, écoute et interphone).

## Télécharger l’application

L’APK est inclus dans le dépôt : **[CareLink 2.4.1 — télécharger](downloads/CareLink-2.4.1.apk)**. Depuis GitHub, ouvrir le fichier puis utiliser **Download raw file**. Le bouton **Code → Download ZIP** récupère aussi les sources et l’APK. Elle s’installe par-dessus la 2.4.0 ; depuis la 2.3.0, désinstaller d’abord l’ancienne CareLink puis refaire l’appairage (voir [downloads](downloads/README.md)).

- [Dossier de téléchargement](downloads/) et [empreinte SHA-256](downloads/CareLink-2.4.1.apk.sha256).
- [Résultats de validation](docs/RESULTATS_VALIDATION.md) et [consignes de qualification](docs/VALIDATION.md).
- [Aperçu de l’accueil](docs/screenshots/welcome.png) et [tableau de surveillance](docs/screenshots/monitor-empty.png), [tablette en paysage](docs/screenshots/monitor-tablet-landscape.png) et [fiche de chambre](docs/screenshots/room-tablet-landscape.png).

Sur Android, autoriser l’installation depuis la source utilisée pour télécharger l’APK. Consulter les étapes d’appairage ci-dessous et effectuer les essais sur les téléphones prévus avant utilisation en établissement.

## Ce qui change

- Appairage physique par QR ou code à usage unique, valable deux minutes. Chaque veilleur possède un accès distinct, révocable depuis la chambre.
- TLS 1.2/1.3 avec vérification exacte du certificat appairé. Aucun accès audio ou commande en clair, aucune compatibilité automatique avec les versions précédentes.
- Journal SQLite durable : retransmission jusqu’à réception, déduplication par événement, prise en charge et résolution séparées. Les actions distantes sont conservées jusqu’à confirmation de la chambre.
- Chambre attendue conservée après redémarrage du veilleur. Capture récente, état réseau et incidents affichés séparément.
- Services au premier plan indépendants des activités ; aucun type `dataSync` pour la permanence. Retour Android ne désarme plus la surveillance.
- Contrôles de fraîcheur, capture rendue silencieuse, silence numérique prolongé, erreurs et saturation. Le test de transmission ne prétend pas vérifier le microphone.
- Interface sombre ou claire, navigation Surveillance / Événements / Réglages, protection optionnelle des actions sensibles par code et notifications sans acquittement accidentel depuis l’écran verrouillé.
- Pas de SDK Firebase, pas de service cloud applicatif et pas d’enregistrement audio conservé.

## Version 2.4.1 : compatibilité Android 8 (tablettes anciennes)

- Détection de l’adresse Wi-Fi locale plus robuste. Avant, seul le premier réseau Wi-Fi annoncé par Android était examiné ; sur certaines tablettes (Galaxy Tab A 2016, Android 8), il n’avait pas d’adresse ou n’était pas trouvé. Résultat : « Connectez ce téléphone au Wi-Fi » en mode chambre et « Connexion interrompue » en mode veilleur. CareLink retient maintenant le premier réseau Wi-Fi/Ethernet qui porte réellement une adresse IPv4, puis se replie sur la liste des interfaces et sur le gestionnaire Wi-Fi. Les réseaux mobiles, VPN et Wi-Fi Direct restent exclus.
- Si Android refuse une connexion attachée au réseau Wi-Fi, une seconde tentative passe par la route par défaut du réseau local.
- Certificat de sécurité des nouvelles installations limité au 31/12/2037 (limite des systèmes 32 bits) ; une identité dont la date a été corrompue est régénérée.
- Messages de connexion plus précis (adresse Wi-Fi introuvable, type d’erreur).
- Tests d’interface et de réseau exécutés aussi en émulation Android 8 (API 26).

## Version 2.4.0 : nouveau design et ergonomie

**Interface**

- Nouveau système visuel sobre : surfaces graphite neutres, un seul accent bleu, couleurs d’état réservées au sens (vert normal, ambre attention, rouge critique, orange pause). Coins resserrés, bordures fines, liseré d’état sur les fiches, chiffres tabulaires.
- Thème **Sombre** (par défaut, pour la nuit), **Clair** ou **Système**, au choix dans **Réglages → Affichage**.
- En-tête simplifié : logo, nom du rôle et bouton **Pause / Reprendre** libellé. L’engrenage en double avec l’onglet **Réglages** est retiré.
- La surveillance s’ouvre sur une **vue d’ensemble** lisible sans toucher : *Captures récentes*, *À vérifier*, *Hors ligne*. Les badges d’icônes de l’en-tête disparaissent.
- Fiches de chambre : point et libellé d’état, type d’événement en cours, jauge de niveau avec repère du seuil, batterie faible signalée. Le bouton d’action est écrit en toutes lettres (**Prendre en charge**, **Résoudre**, **En attente**).
- Tri naturel des chambres (Chambre 2 avant Chambre 10), après le tri par urgence.
- Événements : sélecteur *En cours / Résolus*, icône par type, historique indiquant l’heure de résolution.
- Réglages regroupés en sections : Poste / Chambre, Alertes, Affichage, Sécurité, Maintenance.
- Ajout de chambre guidé en trois étapes ; QR d’appairage avec compte à rebours visuel.

**Ergonomie et fiabilité**

- La résolution depuis le tableau de surveillance demande désormais une confirmation (un appui involontaire ne clôture plus les alertes d’une chambre).
- Sans protection par code, ouvrir les réglages ou l’écran d’ajout ne demande plus de confirmation ; les actions réelles (révocation, retrait, test, seuil, arrêt) restent confirmées.
- Code professionnel : quatre essais libres, puis attente croissante de 30 s à 15 min, mémorisée même si l’application est fermée.
- Un microphone instable ne remplit plus le journal : un seul incident de capture « à vérifier » à la fois par chambre ; une nouvelle alerte n’est créée qu’après prise en charge.
- Une notification ouvre la chambre concernée sans recréer l’écran (plus de clignotement ni d’annulation d’un appairage en cours).
- Retour Android depuis Événements ou Réglages revient à la surveillance au lieu de quitter l’application.
- Fin automatique de l’interphone après 30 s : le service retire aussi son type « microphone ».
- Le message de fin d’appairage distingue un code utilisé d’un QR expiré.

Captures : [tableau sombre](docs/screenshots/design-dashboard-dark.png), [tableau clair](docs/screenshots/design-dashboard-light.png), [événements](docs/screenshots/design-events.png), [réglages](docs/screenshots/settings-monitor.png), [accueil](docs/screenshots/welcome.png), [chambre sur tablette](docs/screenshots/room-tablet-landscape.png).

## Pause du veilleur dans la version 2.3.0

- Le bouton **Pause**, à gauche des paramètres dans l’en-tête, suspend les alertes et rappels de ce veilleur. Un appui long affiche une courte aide ; le même bouton permet de reprendre.
- Les teintes orange, le bandeau **Surveillance en pause** et la notification permanente indiquent le mode. La pause reste mémorisée après fermeture, redémarrage ou mise à jour, jusqu’à reprise explicite.
- Les chambres et les autres veilleurs restent actifs. La connexion, le journal et les commandes manuelles d’écoute/interphone restent disponibles. La pause ne prend en charge ni ne résout automatiquement un événement.
- À la reprise, les groupes encore non résolus et non pris en charge retrouvent leurs alertes. Les actions déjà en attente de confirmation restent respectées.
- Si la protection par code est active, l’activation de la pause demande ce code. Sans protection, un toucher suffit ; la reprise reste directement accessible.
- Sur les petits écrans, les compteurs passent sur une seconde ligne lorsque la présence du bouton Retour ou le texte agrandi l’exige, pour conserver tous les contrôles accessibles.

## Alignement des fiches dans la version 2.2.1

Le titre de la chambre et son état sont réunis sur une ligne au-dessus des panneaux. Sur tablette, les deux colonnes commencent à la même hauteur sous cet en-tête, dans la fiche veilleur comme sur le téléphone chambre. Les libellés longs peuvent revenir à la ligne sur les petits écrans.

## Lisibilité et tablettes dans la version 2.2.0

- Les indicateurs **Captures récentes** et **À vérifier** sont dans l’en-tête, à gauche du titre et de l’icône CareLink. Leurs explications s’ouvrent au toucher.
- Les fiches de chambre mettent en avant l’état, le niveau sonore et les actions d’écoute ou d’interphone. La sensibilité, les informations de l’appareil et la connexion sont dans des sections repliables. Une connexion interrompue ouvre directement ses actions de reprise.
- Le téléphone chambre présente le niveau, les veilleurs connectés et l’appairage ; les informations techniques se déplient à la demande.
- La surveillance adapte le nombre de colonnes à la largeur. Les grandes fiches disposent de deux colonnes ; les écrans larges et les téléphones en paysage utilisent une barre de navigation latérale. Les formulaires restent centrés avec une largeur limitée.
- Les rendus sont vérifiés en portrait et en paysage par des tests d’interface ; les essais sur des tablettes physiques restent à effectuer.

## Surveillance dans la version 2.1.0

- Deux indicateurs compacts affichent les captures récentes et les alertes à vérifier. Toucher un indicateur affiche son explication.
- Une carte de chambre montre son titre et son état. Un appui long affiche le diagnostic, la batterie, la dernière donnée et le niveau relatif.
- Le bouton de la carte prend en charge les événements affichés de cette chambre, puis permet de les résoudre. Une action distante reste en attente de confirmation si la connexion est coupée.
- L’icône **Événements** porte le nombre de groupes en cours, y compris ceux pris en charge. Les épisodes d’un même type et d’une même chambre occupent un seul groupe et une seule notification. Le compteur d’occurrences apparaît dans les événements ; le journal conserve chaque épisode et son heure.
- Un nouveau bruit après prise en charge remet ce groupe à vérifier. Tant qu’un groupe est déjà à vérifier, ses nouvelles occurrences mettent à jour sa notification ; les rappels suivent le réglage du poste. Les actions portent sur les identifiants affichés au moment du clic : un épisode reçu ensuite reste à traiter.
- Le niveau sonore est relatif au microphone. Le seuil se règle dans la chambre ; l’aide au toucher explique ce réglage. Aucun étalonnage physique en dB SPL n’est proposé.

L’avertissement sur le mode Ne pas déranger est retiré de l’interface. La diffusion des notifications reste régie par les réglages Android.

## Interface et code dans la version 2.0.2

Le titre affiche l’icône de l’application installée. Le réglage de protection par code s’applique désormais aussi à l’ajout d’une chambre, à l’appairage, aux tests, aux révocations et aux réglages distants. Sa désactivation ne supprime pas le code enregistré et son état est immédiatement visible dans les réglages. La réactivation demande de créer ou de saisir le code ; les autres actions ne le demandent que lorsque la protection est active.

## Connexion et découverte dans la version 2.0.1

La recherche automatique utilise mDNS et UDP, et suit les changements d’adresse des chambres appairées. Les chambres détectées mais non autorisées sont affichées lors de l’ajout ; le QR reste nécessaire pour le premier accès.

Le scan est désormais présenté comme un appairage en attente tant que la chambre n’a pas confirmé la connexion. Une panne continue conserve le même incident, y compris après sa clôture ou un redémarrage, pour éviter les nouvelles alertes en boucle. La prise en charge coupe ses rappels. La reprise de la télémétrie clôt l’incident local de coupure.

Si deux téléphones ne se voient pas sur le Wi-Fi de l’établissement, consulter le [guide de dépannage réseau et Xiaomi/HyperOS](docs/DEPANNAGE_RESEAU.md), qui indique aussi les flux à faire vérifier par l’équipe informatique.

## Installer et appairer

1. Installer **la même version 2** sur les deux téléphones, connectés au réseau local autorisé.
2. Sur la chambre, choisir le rôle et autoriser le microphone. Définir un repère de chambre non nominatif, le niveau relatif et la durée de dépassement.
3. Toucher **Appairer un veilleur**. Si la protection par code est activée, créer ou saisir le code professionnel. Sur le veilleur, toucher **Ajouter une chambre → Scanner le QR**. Le scan est entièrement local. Le code peut aussi être collé via un canal de confiance ; il est confidentiel.
4. Attendre la connexion authentifiée et des échantillons récents. Vérifier les notifications, le volume et l’audibilité sur le poste veilleur.
5. Effectuer un test de transmission puis un véritable essai acoustique. Un test de transmission ne mesure ni la capture ni le besoin d’aide.
6. La protection par code professionnel est optionnelle dans **Réglages**. Activée, elle demande un code d’au moins six chiffres pour les réglages et les actions sensibles ; désactivée, ces actions ne demandent plus ce code. Le QR d’appairage reste nécessaire pour autoriser un nouveau téléphone. Le verrouillage Android se règle séparément.

Les autres téléphones du LAN ne sont pas ajoutés automatiquement. La révocation ferme leurs connexions ; un nouvel appairage physique est nécessaire pour rétablir un accès révoqué. Le code expire aussi lorsque l’application chambre passe en arrière-plan pendant l’appairage.

## Événements

- **À vérifier** : événement conservé, non pris en charge ; rappels selon les réglages.
- **Pris en charge** : transition durable confirmée, événement encore visible.
- **Résolu** : clôture humaine, ou retour technique confirmé pour un incident local, conservée sept jours après sa résolution avant nettoyage. Un événement non résolu n’est jamais supprimé par la rétention.
- Les événements sonores et de test proviennent de la chambre. Leur regroupement à l’écran conserve leurs identifiants et leurs états individuels. Les incidents de connexion/batterie/capture observés par un veilleur sont locaux à ce poste ; leur résolution ne répare pas l’état technique.
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

Le build debug (identifiant `.debug`) s’affiche désormais simplement « CareLink ». Arrêtez l’ancienne application avant les essais : deux serveurs chambre ne peuvent pas partager les mêmes ports sur un téléphone. Le build debug utilise une clé de développement ignorée par Git ; il n’est pas un livrable de production. Pour une livraison release, fournir `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` et la clé `upload` via le mécanisme sécurisé prévu, puis vérifier signature, manifeste et version. Ne jamais ajouter ces valeurs au dépôt. Aucun secret serveur ne doit être compilé dans l’APK.

Voir [qualification technique et opérationnelle](docs/VALIDATION.md) avant un pilote auprès des résidents.
