******

### Historique des versions

******

# v1.0.0

###### 2026/08/02

* `Fonctionnalité` Plugin APK Inspector avec l'ID `apk-inspector`, l'ID d'action `inspect-android-package`, le moteur `explorer-action` et la variante `default`
* `Fonctionnalité` Inspection principale en lecture seule par le protocole Explorer Action v2 pour les fichiers APK, APKS, XAPK, APKM, APKZ et AAB
* `Fonctionnalité` Décodage en lecture seule des manifestes APK texte et binaires, des manifestes protobuf AAB et des métadonnées bundletool `toc.pb`
* `Fonctionnalité` Détails du paquet, autorisations demandées, composants, APK fractionnés adaptés à l'appareil, éléments OBB, constats structurels, manifeste formaté et présence des signatures APK V1-V3
* `Fonctionnalité` Passerelles distinctes pour Explorer protégé et Android `ACTION_VIEW` à MIME exact, avec limite de 4 GiB et instantané privé borné en lecture seule calculé avec SHA-256
* `Fonctionnalité` Implémentation JVM pure sans bibliothèque native, ABI sans restriction via `supportedAbis = emptyArray()`, un APK indépendant de l'ABI et version hôte AutoJs6 5269 requise
* `Fonctionnalité` Métadonnées, interface, instructions, README et historiques localisés en espagnol, français, russe, arabe, japonais, coréen, anglais, chinois simplifié, chinois traditionnel de Hong Kong et chinois traditionnel de Taïwan
* `Dépendance` Ajout de Gson version 2.13.2
