<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="apk-inspector-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de gestionnaire de fichiers. Inspecter les fichiers APK, APKS, XAPK, APKM, APKZ et AAB sans les installer</p>

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
- Français [fr] # actuel
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/.readme/README-ar.md)

******

### Introduction

******

APK Inspector fournit l'action principale d'inspection des paquets Android dans le gestionnaire de fichiers. Il analyse un instantané privé et borné sans modifier ni installer le fichier source.

******

### Fonctions

******

- Enregistre une action principale Explorer Action v2 pour APK, APKS, XAPK, APKM, APKZ et AAB.
- Décode les Manifest APK texte ou binaires, les Manifest protobuf AAB et les métadonnées bundletool toc.pb.
- Affiche l'identité, la version, les SDK, les permissions, les composants, les splits adaptés, les OBB et les problèmes structurels.
- Détecte la présence des schémas de signature APK V1, V2 et V3 sans affirmer leur validité cryptographique.
- Affiche un Android Manifest formaté dans une vue séparée en lecture seule.
- Fournit une passerelle ACTION_VIEW séparée pour les types MIME Android dédiés.

******

### Formats pris en charge

******

L'action principale de l'Explorateur correspond exactement à ces extensions:

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

******

### Interface du plugin

******

L'hôte découvre et exécute le plugin avec les identités suivantes:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5269
```

La version 1 effectue uniquement l'inspection. Elle ne contient aucun bouton d'installation, permission d'installation, installateur, éditeur de source ou énumération de répertoire. Les flux d'installation de l'hôte restent indépendants. Si le plugin est indisponible, l'hôte utilise son action de repli.

La version 5269 ou ultérieure de l'hôte est requise.

******

### Sécurité

******

La passerelle Explorer protégée vérifie le protocole v2, la surface principale, l'identifiant d'action, la hiérarchie content URI, ClipData, le nom, l'extension, le type MIME, la taille et les autorisations en lecture seule. ACTION_VIEW accepte uniquement les MIME dédiés. Une copie privée, bornée et en lecture seule est créée avec son SHA-256. Le parent URI n'est jamais énuméré.

******

### Limites de sécurité

******

- Taille maximale d'entrée: 4 GiB.
- Un fichier cible par action et une réserve de cache d'au moins 128 MiB.
- Le nombre, les noms et tailles des entrées, l'analyse APK imbriquée, les métadonnées, protobuf et la sortie Manifest sont bornés.
- ACTION_VIEW externe refuse application/zip, application/octet-stream et les autorisations d'écriture, persistantes ou de préfixe.
- V1-V3 indiquent seulement la présence d'un schéma. V4 exige une entrée idsig séparée hors de ce protocole.
- Le plugin n'installe jamais de paquet et ne demande aucune permission de stockage, réseau ou installation.

******

### Historique des versions

******

# v1.0.1

###### 2026/08/08

* `Correctif` Renvoyer une liaison de service Explorer Action valide lors de l'activation depuis le centre des plugins
* `Amélioration` Raccourcir le nom et la description du plugin et rendre la documentation utilisateur plus naturelle

# v1.0.0

###### 2026/08/02

* `Fonctionnalité` Plugin APK Inspector avec l'ID `apk-inspector`, l'ID d'action `inspect-android-package`, le moteur `explorer-action` et la variante `default`
* `Fonctionnalité` Inspection principale en lecture seule par le protocole Explorer Action v2 pour les fichiers APK, APKS, XAPK, APKM, APKZ et AAB
* `Fonctionnalité` Décodage en lecture seule des manifestes APK texte et binaires, des manifestes protobuf AAB et des métadonnées bundletool `toc.pb`
* `Fonctionnalité` Détails du paquet, autorisations demandées, composants, APK fractionnés adaptés à l'appareil, éléments OBB, constats structurels, manifeste formaté et présence des signatures APK V1-V3
* `Fonctionnalité` Passerelles distinctes pour Explorer protégé et Android `ACTION_VIEW` à MIME exact, avec limite de 4 GiB et instantané privé borné en lecture seule calculé avec SHA-256
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

Les paramètres proviennent de version.properties. Le SDK minimal est 24 et le SDK cible est 36.

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

strings.xml localise les métadonnées et l'interface. plugin_instruction.md contient les instructions de l'hôte. .python/generate_markdown.py génère les README et journaux localisés depuis les sources JSON.

******

### Liens

******

- Documentation AutoJs6: https://docs.autojs6.com
- Partage de fichiers sécurisé Android: https://developer.android.com/training/secure-file-sharing
