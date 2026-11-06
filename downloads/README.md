# APK CareLink 2.4.2

`CareLink-2.4.2.apk` : version 2.4.2 (code 12), nom affiché « CareLink », identifiant Android `com.aistudio.cradlelink.vxrtw.debug`.

Sur GitHub, ouvrir l’APK puis cliquer sur **Download raw file** pour le télécharger. Le ZIP de cette branche contient également ce fichier.

Pour vérifier son intégrité après téléchargement, placer le fichier `.sha256` dans le même dossier et exécuter :

```sh
sha256sum -c CareLink-2.4.2.apk.sha256
```

**Mise à jour** : signé avec la même clé que la 2.4.0 (empreinte SHA-256 du certificat `859818e4…c96b6cc`), il s’installe par-dessus la 2.4.0 ou la 2.4.1 sans perdre les appairages. Depuis la 2.3.0, désinstaller d’abord l’ancienne application puis refaire le choix du rôle et l’appairage.

Installer cette même version sur tous les téléphones (chambres et veilleurs). Lire les [consignes d’installation et d’appairage](../README.md#installer-et-appairer) et les [conditions de validation](../docs/VALIDATION.md).

La clé privée de signature n’est pas dans le dépôt.
