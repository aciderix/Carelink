# APK CareLink 2.4.0

`CareLink-2.4.0.apk` : version 2.4.0 (code 10), nom affiché « CareLink », identifiant Android `com.aistudio.cradlelink.vxrtw.debug`.

Sur GitHub, ouvrir l’APK puis cliquer sur **Download raw file** pour le télécharger. Le ZIP de cette branche contient également ce fichier.

Pour vérifier son intégrité après téléchargement, placer le fichier `.sha256` dans le même dossier et exécuter :

```sh
sha256sum -c CareLink-2.4.0.apk.sha256
```

**Réinstallation nécessaire** : cet APK est signé avec une nouvelle clé (empreinte SHA-256 du certificat `859818e4…c96b6cc`). Android refuse de le poser par-dessus la 2.3.0 : désinstaller l’ancienne application sur chaque téléphone, installer celle-ci, puis refaire le choix du rôle et l’appairage des chambres. Le journal local de l’ancienne installation est supprimé avec elle.

Installer cette même version sur tous les téléphones (chambres et veilleurs). Lire les [consignes d’installation et d’appairage](../README.md#installer-et-appairer) et les [conditions de validation](../docs/VALIDATION.md).

La clé privée de signature n’est pas dans le dépôt.
