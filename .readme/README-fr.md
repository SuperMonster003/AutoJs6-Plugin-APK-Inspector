<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>Inspection approfondie et en lecture seule des APK, conteneurs de paquets fractionnes et Android App Bundles dans AutoJs6 Explorer</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Langues

******

Le fichier README.md actuel prend en charge les langues suivantes:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-en.md)
- Francais [fr] # actuel
- [Espanol [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Russkii [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### Introduction

******

Le plugin AutoJs6 APK Inspector fournit l'action principale d'inspection des paquets Android dans la page principale des fichiers. Il analyse un instantane prive et borne sans modifier ni installer le fichier source.

******

### Fonctions

******

- Enregistre une action principale Explorer Action v2 pour APK, APKS, XAPK, APKM, APKZ et AAB.
- Decode les Manifest APK texte ou binaires, les Manifest protobuf AAB et les metadonnees bundletool toc.pb.
- Affiche l'identite, la version, les SDK, les permissions, les composants, les splits adaptes, les OBB et les problemes structurels.
- Detecte la presence des schemas de signature APK V1, V2 et V3 sans affirmer leur validite cryptographique.
- Affiche un Android Manifest formate dans une vue separee en lecture seule.
- Fournit une passerelle ACTION_VIEW separee pour les types MIME Android dedies.

******

### Formats pris en charge

******

L'action principale Explorer correspond exactement a ces extensions:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### Interface du plugin

******

AutoJs6 decouvre et execute le plugin avec les identites suivantes:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5269
supported ABIs: unrestricted (supportedAbis = emptyArray())
```

La version 1 effectue uniquement l'inspection. Elle ne contient aucun bouton d'installation, permission d'installation, installateur, editeur de source ou enumeration de repertoire. Les flux d'installation de l'hote restent independants. Si le plugin est indisponible, AutoJs6 utilise son repli hote.

Le plugin est entierement implemente sur la JVM et ne contient aucune bibliotheque native. Il declare supportedAbis = emptyArray() et produit un APK independant de l'ABI. AutoJs6 build 5269 ou plus recent est requis.

******

### Securite

******

La passerelle Explorer protegee verifie le protocole v2, la surface principale, l'identifiant d'action, la hierarchie content URI, ClipData, le nom, l'extension, le type MIME, la taille et les autorisations en lecture seule. ACTION_VIEW accepte uniquement les MIME dedies. Une copie privee, bornee et en lecture seule est creee avec son SHA-256. Le parent URI n'est jamais enumere.

******

### Limites de securite

******

- Taille maximale d'entree: 4 GiB.
- Un fichier cible par action et une reserve de cache d'au moins 128 MiB.
- Le nombre, les noms et tailles des entrees, l'analyse APK imbriquee, les metadonnees, protobuf et la sortie Manifest sont bornes.
- ACTION_VIEW externe refuse application/zip, application/octet-stream et les autorisations d'ecriture, persistantes ou de prefixe.
- V1-V3 indiquent seulement la presence d'un schema. V4 exige une entree idsig separee hors de ce protocole.
- Le plugin n'installe jamais de paquet et ne demande aucune permission de stockage, reseau ou installation.

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

##### Pour plus de versions

* [CHANGELOG-fr.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-fr.md)

******

### Construction

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Construction de publication:

```powershell
.\gradlew.bat :app:assembleRelease
```

Les parametres proviennent de version.properties. Le SDK minimal est 24 et le SDK cible est 36.

******

### Structure des ressources

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
```

strings.xml localise les metadonnees et l'interface. plugin_instruction.md contient les instructions de l'hote. .python/generate_markdown.py genere les README et journaux localises depuis les sources JSON.

******

### Liens

******

- Documentation AutoJs6: https://docs.autojs6.com
- Partage de fichiers securise Android: https://developer.android.com/training/secure-file-sharing
