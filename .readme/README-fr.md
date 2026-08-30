<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="APK Inspector" width="128" />
  </p>

  <h1>APK Inspector</h1>

  <p>Plugin du gestionnaire de fichiers AutoJs6 : touchez un fichier APK ou AAB pour voir sa version, ses autorisations, ses signatures et sa compatibilité, sans l'installer</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-APK-Inspector?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-APK-Inspector?color=534BAE&label=License"/></a>
  </p>
</div>

### Langues (Languages)

Ce README est disponible dans les langues suivantes:

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

### Présentation

APK Inspector est un plugin compagnon du gestionnaire de fichiers AutoJs6. Touchez un fichier APK, APKS, XAPK, APKM, APKZ ou AAB dans le gestionnaire de fichiers : un rapport d'inspection s'ouvre aussitôt et montre, sur un seul écran, le nom de l'application, sa version, les autorisations demandées et si elle peut être installée sur cet appareil. Le paquet n'est jamais installé et le fichier source n'est jamais modifié.

Le rapport comprend quatre sections : « Détails du paquet » affiche le nom de l'application, l'icône, le nom du paquet, la version, la plage de SDK, la vérification des signatures et la lignée des certificats de signature, la taille du fichier et l'empreinte SHA-256 ; « Composants » liste chaque APK divisé (split) et chaque ressource OBB du paquet en marquant les parties correspondant à cet appareil (pour un AAB, les modules) ; « Autorisations demandées » regroupe les autorisations système par niveau de protection et met en avant les autorisations dangereuses à l’exécution avec une brève explication ; « Constats de sécurité et de compatibilité » résume les problèmes structurels et le verdict de compatibilité. Le bouton « Afficher le manifeste » ouvre l'AndroidManifest complet et mis en forme.

### Points forts

- Une pression suffit : le rapport s'ouvre directement depuis le gestionnaire de fichiers AutoJs6, sans installation, extraction ni accès réseau.
- Six formats : APK standard, formats à splits multiples (APKS, XAPK, APKM, APKZ) et format de distribution AAB ; APKS couvre les exports bundletool et SAI.
- Version et compatibilité : nom du paquet, nom et code de version, SDK min/cible/max, comparés à la version Android de l'appareil avant toute installation.
- Transparence des autorisations : regroupement par niveau de protection (exécution/dangereuses, signature/protégées et normales), avec les autorisations à l’exécution mises en avant et expliquées en une ligne ; les niveaux indisponibles restent visibles et signalés.
- Analyse des splits : liste chaque entrée APK et chaque ressource OBB d'un bundle et marque les splits retenus pour cet appareil (base, langue, densité d'écran, ABI).
- Vérification des signatures et certificats : vérifie cryptographiquement les schémas APK V2, V3 et V3.1 ; signale la présence de V1 ; affiche chaque certificat actuel et la lignée de rotation vérifiée avec les rôles ancien/nouveau et les empreintes SHA-256.
- Vérification des fichiers annexes V4/V4.1 : AutoJs6 dérive uniquement le fichier voisin exact `<nom de l’APK>.idsig` et accorde un descripteur borné en lecture seule ; le plugin vérifie les données signées, le certificat et la clé publique, le condensat APK V2/V3 correspondant, la racine fs-verity, l’arbre de Merkle intégré et tout signataire de rotation V3.1.
- Manifestes lisibles : les manifestes APK binaires et les manifestes protobuf AAB sont décodés en XML lisible, dans une visionneuse séparée en lecture seule.
- Contrôle d'intégrité : le SHA-256 est calculé pendant la lecture du fichier, prêt à être comparé aux empreintes publiées officiellement.
- Bilan structurel : détecte l'absence d'APK de base, les splits en double ou sans dépendance, les incohérences de version ou de paquet, chaque constat étant marqué bloquant [!] ou informatif [i].

### Mode d'emploi

1. Téléchargez et installez APK Inspector, puis activez-le dans le centre de plugins d'AutoJs6 (code de version AutoJs6 5277 ou ultérieur requis).
2. Ouvrez le gestionnaire de fichiers d'AutoJs6 et repérez le paquet à examiner (APK, APKS, XAPK, APKM, APKZ ou AAB).
3. Touchez le fichier, ou choisissez « Inspecter le paquet Android » dans son menu ; le rapport apparaît après un instant.
4. Parcourez le rapport de haut en bas : icône et nom de l'application, détails du paquet, composants, autorisations demandées, constats de sécurité et de compatibilité.
5. Touchez « Afficher le manifeste » pour lire l'AndroidManifest complet, puis revenez en arrière pour retrouver le gestionnaire de fichiers.

> D'autres applications peuvent aussi confier un paquet à APK Inspector via « Ouvrir avec » (ACTION_VIEW), à condition d'utiliser une URI content avec un type MIME de paquet Android dédié. Le plugin est strictement en lecture seule et n'offre aucun point d'entrée d'installation.

### Formats pris en charge

L'action principale du gestionnaire de fichiers correspond exactement aux extensions suivantes (sans distinction de casse):

```text
APK, APKS, XAPK, APKM, APKZ, AAB
```

APKS, XAPK, APKM et APKZ sont des formats conteneurs regroupant plusieurs APK divisés ; AAB est le format App Bundle destiné aux boutiques d'applications : il peut être inspecté ici, mais doit être converti avec un outil comme bundletool avant installation.

### FAQ

#### Ce plugin peut-il installer des APK ?

Non, et c'est voulu. Le plugin ne demande aucune autorisation d'installation et n'a aucun bouton d'installation ; son rôle est de montrer le contenu d'un paquet avant son installation. L'installation reste du ressort de l'installateur système ou du flux propre de l'hôte.

#### Pourquoi certains fichiers ne peuvent-ils pas être inspectés ?

Raisons courantes : le fichier dépasse la limite de 4 GiB ; le cache de l'appareil manque d'espace (au moins 128 MiB doivent rester libres) ; le bundle dépasse les bornes d'analyse en nombre ou en taille d'entrées ; le fichier a été modifié par une autre application pendant la lecture ; ou le fichier est structurellement corrompu. Le message d'erreur précise la raison.

#### La détection de signature prouve-t-elle qu'un paquet est sûr ?

Non. Le plugin vérifie cryptographiquement l'intégrité et les preuves du signataire pour V2, V3, V3.1, V4 et V4.1, et affiche les empreintes et la lignée de rotation ; mais une signature valide prouve seulement que le paquet n'a pas changé depuis sa signature, pas que le signataire ou l'application est digne de confiance. Comparez l'empreinte et le SHA-256 avec une source officielle.

#### Pourquoi les fichiers AAB indiquent-ils qu'ils « nécessitent une conversion avant installation » ?

L'AAB est un format de distribution destiné aux boutiques d'applications ; un appareil Android ne peut pas l'installer directement. Le plugin décode son manifeste protobuf et sa structure de modules pour consultation, mais l'installation exige d'abord une conversion en APK(s) avec un outil comme bundletool.

### Autorisations et sécurité

Le plugin ne demande aucune autorisation de stockage, de réseau ni d'installation de paquets. Il n'atteint le paquet sélectionné que par l'URI content temporaire en lecture seule accordée par l'hôte ; un `.idsig` facultatif n'est disponible que par un descripteur hôte borné pour le voisin exact `<nom de l’APK>.idsig`, sans énumération du répertoire ni chemin voisin arbitraire. Avant l'inspection, les deux entrées sont copiées vers des instantanés en lecture seule du cache privé de l'application (le SHA-256 est calculé pendant la copie du paquet) et toute l'analyse se fait sur ces instantanés ; `.idsig` est limité à 40 MiB et les instantanés périmés sont nettoyés sous 24 heures. Les requêtes du gestionnaire de fichiers sont validées champ par champ, notamment la version du protocole, les identifiants de requête et d'action, les métadonnées de la cible, la version de l'hôte, la forme de l'URI, le nom, la taille, les autorisations en lecture seule et le Binder de session. Les requêtes « Ouvrir avec » ne sont acceptées qu'avec les types MIME dédiés ; application/zip, application/octet-stream et toute autorisation d'écriture, persistante ou par préfixe sont refusés.

Pour empêcher des fichiers forgés d'épuiser les ressources de l'appareil, l'analyse est bornée comme suit, et tout fichier hors borne est rejeté avec un message:

- Un fichier ne peut dépasser 4 GiB, au moins 128 MiB d'espace de cache doivent rester libres pendant la copie, et chaque action traite exactement un fichier cible.
- Au plus 16384 entrées d'archive sont analysées, au plus 512 entrées APK par bundle sont parcourues, et les noms d'entrée sont limités à 1024 caractères.
- La taille déclarée d'une entrée ne peut dépasser 4 GiB, et le total déclaré ne peut dépasser 8 GiB.
- L'analyse des manifestes d'APK imbriqués est plafonnée à 256 MiB, les métadonnées de bundle à 1 MiB, et l'APK temporaire servant à charger l'icône et le libellé à 512 MiB.
- La classification examine au plus 2048 demandes, affiche jusqu’à 512 noms sûrs et uniques et limite chaque explication chargée à 240 caractères ; les omissions et niveaux indisponibles sont clairement signalés.

### Interface du plugin

L'hôte (AutoJs6) découvre et invoque le plugin via les identités suivantes, fournies pour les développeurs de plugins et d'hôtes:

```text
service action: org.autojs.plugin.EXPLORER_ACTION
execute action: org.autojs.plugin.EXPLORER_ACTION_EXECUTE
plugin id: apk-inspector
engine: explorer-action
variant: default
Explorer action id: inspect-android-package
MIME type: Explorer: extension-only; ACTION_VIEW: dedicated Android package MIME types
required host build: 5277
```

La version actuelle n'effectue qu'une inspection en lecture seule : aucun bouton d'installation, aucune autorisation d'installation, aucun installateur de paquets ; le fichier source n'est jamais modifié et aucun répertoire n'est énuméré. V4 utilise uniquement le candidat `.idsig` exact dérivé par l'hôte, copie son descripteur borné en lecture seule dans un instantané privé, puis ferme immédiatement la session hôte. Si le plugin est absent ou désactivé, l'hôte revient silencieusement à son action par défaut.

### Roadmap

Les capacités ci-dessus et les éléments cochés de la Roadmap reflètent l'existant ; les travaux prévus, tels que l'export de rapport et l'analyse des ressources et bibliothèques natives, sont suivis dans la Roadmap, et les éléments non cochés ne sont pas des capacités actuelles.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### Historique des versions

#### v1.1.0

_2026/08/30_

- `Note` Nécessite AutoJs6 avec le code de version 5277 ou ultérieur pour le protocole Explorer Action v22 et l’accès borné au fichier V4
- `Fonctionnalité` Ajout de la vérification cryptographique sur l’appareil des schémas APK V2, V3, V3.1, V4 et V4.1, y compris les condensats, preuves du signataire, racines fs-verity, arbres de Merkle intégrés et correspondance avec le schéma complémentaire
- `Fonctionnalité` Ajout des champs détaillés des certificats et des lignées de rotation vérifiées avec rôles ancien/actuel, indicateurs de capacité et empreintes SHA-256
- `Fonctionnalité` Ajout de la copie bornée de `.idsig` via un descripteur en lecture seule dérivé exactement par l’hôte ; l’énumération des répertoires et l’accès arbitraire aux voisins restent indisponibles
- `Fonctionnalité` Regroupement des autorisations demandées selon `protectionLevel` en exécution/dangereuses, signature/protégées et normales ; celles à l’exécution sont mises en avant avec une explication bornée sur une ligne, tandis que les niveaux indisponibles restent visibles et signalés
- `Amélioration` Renforcement de la validation Explorer Action v22 et des instantanés privés immuables, avec limites de 4 GiB pour le paquet et 40 MiB pour idsig, contrôles d’identité et fermeture rapide de la session
- `Amélioration` Ajout d’échantillons officiels Build Tools 37 `apksigner` couvrant signatures valides, altérées, multiples, rotations V3.1/V4.1, absences et formats incorrects

#### v1.0.1

_2026/08/08_

- `Correctif` Correction de l'impossibilité pour l'hôte de se lier au service du plugin après son activation dans le centre de plugins ; l'action « Inspecter le paquet Android » fonctionne désormais immédiatement après l'activation
- `Amélioration` Nom et description du plugin simplifiés, documentation utilisateur plus naturelle à lire

#### v1.0.0

_2026/08/02_

- `Note` Première version publique ; requiert AutoJs6 avec un code de version 5269 ou ultérieur
- `Fonctionnalité` Touchez un fichier APK, APKS, XAPK, APKM, APKZ ou AAB dans le gestionnaire de fichiers AutoJs6 pour ouvrir un rapport d'inspection en lecture seule (ID de plugin `apk-inspector`, ID d'action `inspect-android-package`)
- `Fonctionnalité` Le rapport montre le nom et l'icône de l'application, le nom du paquet, la version, la plage de SDK, les autorisations demandées, les splits et ressources OBB, les problèmes structurels et la présence des schémas de signature V1-V3
- `Fonctionnalité` Les manifestes APK texte et binaires, les manifestes protobuf AAB et les métadonnées bundletool `toc.pb` sont décodés automatiquement, avec une visionneuse dédiée pour le manifeste mis en forme
- `Fonctionnalité` D'autres applications peuvent transmettre un paquet via « Ouvrir avec » (ACTION_VIEW) avec les types MIME de paquets Android dédiés
- `Fonctionnalité` Avant l'inspection, le fichier est copié vers un instantané privé en lecture seule avec calcul du SHA-256 (limite de 4 GiB) ; le plugin ne demande aucune autorisation de stockage, de réseau ni d'installation
- `Fonctionnalité` Livré en 10 langues pour l'interface, les instructions, le README et le CHANGELOG : chinois simplifié, chinois traditionnel (Hong Kong et Taïwan), anglais, français, espagnol, japonais, coréen, russe et arabe
- `Dépendance` Ajout de Gson 2.13.2

##### Historique complet

- [CHANGELOG-fr.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/app/src/main/assets/doc/CHANGELOG-fr.md)

### Compilation

```powershell
.\gradlew.bat :app:assembleDebug
```

Compilation release:

```powershell
.\gradlew.bat :app:assembleRelease
```

Les paramètres de compilation et de signature proviennent de version.properties et sign.properties ; le minimum actuel est Android 7.0 (SDK 24) avec un SDK cible 36.

Les fichiers README et CHANGELOG sont générés par .python/generate_markdown.py à partir des sources JSON et des modèles situés sous .readme/ et .changelog/ (10 langues). Pour modifier la documentation, éditez les sources JSON puis relancez le script au lieu de modifier le Markdown généré.

### Liens

- Documentation AutoJs6: https://docs.autojs6.com
- Partage de fichiers sécurisé Android: https://developer.android.com/training/secure-file-sharing
