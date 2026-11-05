# Qualification de CareLink 2

## État de la validation

Les contrôles automatisés ne constituent pas une autorisation d’usage en MAS. Le rapport de livraison doit distinguer compilation, tests JVM, tests instrumentés, essais matériels et décisions de l’établissement. Ne pas réutiliser les anciens APK présents dans les versions Git antérieures.

## Essais indispensables sur le parc réel

- Version Android, constructeur et modèle identifiés ; capture et réception sur plusieurs nuits écran éteint, dont des vacations de plus de douze heures, avec économie d’énergie et politiques constructeur usuelles.
- Microphone refusé, révoqué, confidentialité système active, autre application de capture, appel, zéros continus, saturation et microphone couvert. Aucun état de capture récente ne doit prétendre prouver l’absence d’obstruction physique.
- Notifications refusées, canaux coupés, volumes nuls, silencieux, Ne pas déranger, casque et Bluetooth. Vérifier le signal réel et les indications dégradées. Aucun contournement de DND ou droit au plein écran n’est supposé.
- Téléphone débranché, batterie faible/défaillante, température et charge prolongée. Définir les conditions d’installation, placement, chargeur et maintenance.
- Coupure locale avec Wi-Fi encore associé, Wi-Fi sans Internet, redémarrage de borne, itinérance, adresse IP modifiée, réseau Ethernet et restrictions de broadcast. Les connexions TLS sont rattachées au réseau local retenu.
- Événement court pendant coupure, résolution pendant coupure, redémarrage des processus avant réception et avant confirmation. Aucun événement non résolu perdu ; aucune transition annulée par une retransmission ancienne.
- Plusieurs épisodes sonores avant puis après prise en charge : un groupe et une notification par chambre/type, compteur d’occurrences correct, réactivation au nouveau bruit après prise en charge. Vérifier que les événements bruts et leurs heures restent dans le journal.
- Boutons rapides de chambre : prise en charge puis résolution de tous les identifiants affichés, attente de confirmation en cas de coupure, nouveau bruit reçu pendant une résolution. Le nouvel épisode reste actif. Vérifier aussi le cas de plusieurs types d’incidents dans une chambre et les actions d’un second veilleur.
- Appui long sur une carte et toucher des indicateurs, textes accessibles, raccourci sans ouverture involontaire de la fiche. Tester sur les densités et tailles de texte des téléphones retenus.
- Plusieurs chambres, deux veilleurs avec déconnexion/reconnexion, changement d’équipe et perte d’un téléphone.
- Première association : QR lu sans accès TCP, code expiré et nouvelle lecture. Aucune alarme de coupure pour une chambre dont l’appairage n’a jamais été confirmé ; son absence de surveillance reste explicite.
- Panne continue : prendre en charge puis clôturer, laisser la panne persister et redémarrer le veilleur. Aucun nouvel événement pour la même panne. Confirmer une reprise puis provoquer une nouvelle coupure : un nouvel événement doit apparaître. Une action distante en attente de confirmation ne doit pas redéclencher les rappels locaux.
- Découverte : mDNS seul, UDP seul, les deux filtrés avec connexion TCP permise, puis isolation totale des clients. Vérifier la correction manuelle d’IP, les changements DHCP et le contrôle du certificat après chaque changement d’adresse.
- Client lent, perte d’un flux audio, interphone simultané, annulation de l’appui, appels/interruptions audio. Les états affichés doivent refléter les flux réels.
- Au nombre cible N de chambres et M de veilleurs, puis avec une marge : mesurer CPU, mémoire, threads/sockets, charge batterie, débit, délai événement→notification et latence audio. L’audio PCM représente environ 256 kbit/s par écoute avant surdébits. Ne pas annoncer une capacité maximale non mesurée.
- Redémarrage, arrêt forcé, arrêt Android du service, mise à jour signée, restauration et nouvelle installation. Prévoir une procédure de reprise explicite ; vérifier les identités et le journal.

## Protocole acoustique

Définir à l’avance les événements acoustiques cibles et les critères d’acceptation. Utiliser des scènes consenties ou synthétiques : appels faibles/forts, vocalisations brèves/répétées, télévision, ventilation, conversation, porte/couloir, bruits combinés, plusieurs distances et orientations. Mesurer taux de détection des scènes définies, faux positifs par nuit, événements manqués et distributions de délai local et bout en bout. Documenter téléphone, placement, seuil et durée pour chaque scène. Une détection sonore n’établit pas un réveil ou un besoin d’intervention.

## Contrôles de sécurité

- Appareil inconnu, certificat différent/expiré, code expiré ou déjà consommé, token incorrect, appareil révoqué, trames malformées/surdimensionnées et rejeu de transitions.
- TLS sans repli en clair sur les trois canaux. Aucun audio accessible avant authentification. Révocation effective des sessions ouvertes.
- Protection par code activée puis désactivée depuis les réglages : son état doit être visible immédiatement et l’ajout d’une chambre ne doit plus demander le code lorsqu’il est désactivé, même si un code est enregistré. Vérifier aussi la réactivation et son annulation.
- Secrets chiffrés et exclus des sauvegardes ; aucun nom ni code d’appairage dans les logs. Écran protégé contre les captures en production ; pas d’audio enregistré.
- Journal limité au nécessaire et accès Android protégé. Lorsqu’il est activé, le code professionnel protège des manipulations dans l’interface : il ne remplace pas un appareil géré ni un compte nominatif professionnel. L’acteur technique du journal est l’appareil authentifié.

## Procédure de vacation et escalade

Vérifier la liste des chambres attendues, l’état des captures et l’audibilité avant prise de poste. Effectuer un essai acoustique et un essai de notification. Les rappels et l’ancienneté des événements non pris en charge doivent être surveillés ; définir un délai d’escalade et un relais humain ou un second poste. CareLink ne contacte pas automatiquement un service d’urgence ni une personne extérieure. Aucun SMS/appel/cloud n’est envoyé.

La relève vérifie les événements en cours, la liste des appareils et les postes autorisés. Une résolution humaine est distincte d’un retour à un état technique valide. Prévoir le remplacement d’un veilleur éteint/perdu, sa révocation depuis les chambres et un repli sur les procédures humaines.

## Décisions avant pilote auprès des résidents

Direction et DPO : nécessité/proportionnalité, finalité, base juridique et condition applicable aux données sensibles, AIPD, information adaptée des résidents/représentants/visiteurs/professionnels, droits d’écoute par chambre, conservation, procédures d’accès et d’incident, information/consultation du personnel et gestion du parc. Un numéro de chambre peut identifier une personne et l’absence d’enregistrement ne supprime pas le traitement de données.

Destination et qualification : documenter les revendications et faire examiner le règlement UE 2017/745 et le guide MDCG 2019-11 révision 1 par une personne compétente. Aucun diagnostic ni détection certaine d’événement clinique ne doit être annoncé à partir de cet algorithme.

Les fonctionnalités de gestion centralisée de flotte, une identité nominative institutionnelle, une escalade extérieure et un éventuel coordinateur local supervisé nécessitent un choix d’architecture et une intégration propres à l’établissement ; ils ne sont pas simulés dans cette application.
