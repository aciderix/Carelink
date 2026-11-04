# Deux téléphones sur le Wi-Fi, mais aucune connexion

Le scan du QR enregistre une demande d’accès. L’appairage est confirmé seulement après une connexion TLS authentifiée avec la chambre. Tant que cette première connexion n’a pas abouti, la chambre reste « Appairage à terminer » ; elle n’est pas présentée comme surveillée et ne déclenche pas d’alarme de coupure.

## Essayer la version 2.0.1

1. Installer le nouvel APK sur les deux téléphones par-dessus la version d’essai existante. Ouvrir CareLink sur les deux.
2. Côté chambre, vérifier que la capture fonctionne et relever l’adresse Wi-Fi affichée. Garder l’application visible pendant l’appairage.
3. Ouvrir un **nouveau QR** sur la chambre. Sur le veilleur, ouvrir la chambre en attente → **Scanner un nouveau QR**. Le QR expire au bout de deux minutes ou si l’application chambre passe en arrière-plan.
4. Attendre une capture récente côté veilleur et au moins un veilleur connecté côté chambre. Un QR lu ne suffit pas.
5. Si la connexion échoue, ouvrir la fiche de la chambre côté veilleur. Le diagnostic distingue notamment réseau injoignable, TLS refusé et QR expiré. Côté chambre, une tentative refusée avant authentification est également signalée.

Sur Xiaomi/HyperOS, vérifier dans les paramètres de CareLink que l’accès au Wi-Fi est autorisé. Pour les essais écran éteint, vérifier également les réglages de batterie et d’exécution en arrière-plan sur chacun des deux téléphones. Les noms de ces réglages dépendent de la version HyperOS. L’application doit être ouverte après un redémarrage pour reprendre et vérifier la surveillance.

## Découverte automatique

CareLink annonce et recherche les chambres avec mDNS/DNS-SD (`_carelink._tcp.`) et des annonces UDP. La recherche redémarre lors d’un changement d’adresse Wi-Fi ; les nouvelles adresses découvertes sont proposées aux chambres déjà appairées. Une adresse qui a été authentifiée avec succès est conservée pour les connexions suivantes.

Les chambres non appairées détectées apparaissent dans **Ajouter une chambre**, avec leur identifiant technique court et leur adresse. Le QR reste nécessaire pour autoriser le premier accès. Une annonce réseau ne donne jamais accès au microphone.

Si la découverte est filtrée mais que les connexions directes sont autorisées, la fiche de la chambre permet **Corriger l’adresse** en utilisant l’IP affichée sur son téléphone. Le certificat appairé reste contrôlé ; corriger une IP ne remplace pas l’identité de la chambre.

## Réseau d’établissement

Le même nom de Wi-Fi ne garantit pas que deux téléphones peuvent communiquer. Une isolation des clients, des VLAN distincts ou un pare-feu peuvent bloquer les connexions. La découverte automatique ne peut pas contourner ces règles.

Pour isoler la cause, effectuer un essai sur un autre réseau Wi-Fi autorisé où les communications entre téléphones sont permises. Si cela fonctionne, transmettre les besoins suivants à l’équipe informatique de l’établissement :

| Usage | Flux entre les appareils |
| --- | --- |
| Commandes et événements, indispensables | TCP 9877 vers les chambres |
| Écoute | TCP 9878 vers les chambres |
| Interphone | TCP 9879 vers les chambres |
| Découverte UDP | UDP 9876, diffusion locale |
| Découverte mDNS | UDP 5353, multicast local 224.0.0.251 / ff02::fb |

Aucune ouverture de ports sur Internet ni redirection depuis le routeur n’est nécessaire. Sans accès TCP entre les téléphones, un scan QR ou une correction d’IP ne peut pas établir la surveillance.

## Rappels et pannes continues

- **Prendre en charge** coupe immédiatement les rappels de cet événement, même pendant une coupure. Pour un événement distant, l’action reste marquée en attente jusqu’à confirmation par la chambre. L’incident reste visible.
- **Résoudre** clôt l’événement. Si la même panne persiste, elle ne crée pas une nouvelle alerte à chaque reconnexion ratée ou redémarrage. L’état de la chambre continue d’indiquer la panne.
- Après réception de nouvelles données authentifiées, l’incident local de coupure est clôturé automatiquement. Une coupure ultérieure crée un nouvel événement.
- Les événements sonores et les autres pannes restent indépendants : la reprise réseau ne clôt pas un événement sonore ni un problème de microphone encore présent.
