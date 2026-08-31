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

Le rapport comprend quatre sections : « Détails du paquet » affiche le nom de l'application, l'icône, le nom du paquet, la version, la plage de SDK, la vérification des signatures et la lignée des certificats de signature, la taille du fichier et l'empreinte SHA-256 ; « Composants » liste chaque APK divisé (split) et chaque ressource OBB du paquet, marque les parties correspondant à cet appareil (pour un AAB, les modules), regroupe les activités/alias, services, récepteurs et fournisseurs déclarés dans le manifeste par nombre et état exported explicite, résume les bibliothèques natives .so par ABI, taille non compressée et compatibilité avec l’appareil, et liste les fichiers classes*.dex standard avec leur taille non compressée ; « Autorisations demandées » regroupe les autorisations système par niveau de protection et met en avant les autorisations dangereuses à l’exécution avec une brève explication ; « Constats de sécurité et de compatibilité » résume les problèmes structurels et le verdict de compatibilité. Le bouton « Afficher le manifeste » ouvre l'AndroidManifest complet et mis en forme.

### Points forts

- Mise en page pour grands caractères et écrans compacts : aux facteurs 1,5×/2,0×, l’en-tête du rapport s’empile si nécessaire, les titres compacts restent visibles, les SHA-256 et noms d’autorisations longs reviennent à la ligne sans ellipse, et la recherche du manifeste en paysage évite le mode d’extraction plein écran du clavier.
- Apparence adaptative : le rapport et la visionneuse du manifeste suivent le mode clair/sombre du système ; sous Android 12 ou version ultérieure, Material You dérive aussi la palette du fond d’écran, tandis que les couleurs sémantiques et les icônes de barres système à contraste adapté préservent la lisibilité.
- Métadonnées de conteneur : lit celles des SAI APKS, XAPK et APKMirror APKM dans une limite de 1 Mio, affiche l’outil/version de format, la version d’application déclarée et une entrée d’icône existante sans remplacer les faits du manifeste APK.
- Une pression suffit : le rapport s'ouvre directement depuis le gestionnaire de fichiers AutoJs6, sans installation, extraction ni accès réseau.
- Six formats : APK standard, formats à splits multiples (APKS, XAPK, APKM, APKZ) et format de distribution AAB ; APKS couvre les exports bundletool et SAI.
- Version et compatibilité : nom du paquet, nom et code de version, SDK min/cible/max, comparés à la version Android de l'appareil avant toute installation.
- Transparence des autorisations : regroupement par niveau de protection (exécution/dangereuses, signature/protégées et normales), avec les autorisations à l’exécution mises en avant et expliquées en une ligne ; les niveaux indisponibles restent visibles et signalés.
- Analyse des splits : liste chaque entrée APK et chaque ressource OBB d'un bundle et marque les splits retenus pour cet appareil (base, langue, densité d'écran, ABI).
- Simulation de configuration : changez la langue, la densité d’écran et l’ABI dans le rapport d’un bundle ; le plugin réexécute localement le même sélecteur borné sur l’instantané privé et affiche les APK ajoutés ou retirés par rapport à l’appareil réel.
- Configuration et distribution AAB : décode les réglages bornés de BundleConfig.pb et annote les modules base, feature, asset, ML, AI et SDK avec les métadonnées à l’installation, conditionnelles, à la demande, fast-follow, de fusion et amovibles ; une configuration ou un manifeste endommagé ou trop volumineux ne dégrade que son annotation.
- Repli d’identité via les ressources : quand Android ne peut pas charger directement un AAB ou un bundle trop volumineux, résout le libellé de l’application et une icône matricielle pour la langue et la densité actuelles depuis resources.pb (AAB) ou resources.arsc (APK), sans extraire tout l’APK imbriqué.
- Exposition des composants du manifeste : compte les activités/alias, services, récepteurs de diffusion et fournisseurs de contenu dans les splits APK sélectionnés ou les modules AAB analysés, en regroupant les valeurs android:exported explicites comme exporté, non exporté ou non précisé/non résolu.
- Vue des bibliothèques natives : regroupe les fichiers .so des splits APK sélectionnés ou modules AAB par ABI et taille non compressée, en signalant l’ABI préférée de l’appareil, les solutions de repli compatibles et les architectures non prises en charge, sans extraire leur contenu.
- Vue DEX : liste dans l’ordre naturel les fichiers classes*.dex standard des splits APK sélectionnés ou modules AAB, avec les tailles non compressées par fichier et totale, sans extraire, décoder ni décompiler leur contenu.
- Réutilisation du rapport : appuyez longuement sur une ligne principale des détails du paquet pour copier sa valeur brute, ou partagez le texte exact affiché en `text/plain` via la feuille de partage Android ; le texte reste en mémoire, sans fichier ni autorisation de stockage.
- Vérification des signatures et certificats : vérifie cryptographiquement les schémas APK V2, V3 et V3.1 ; signale la présence de V1 ; affiche chaque certificat actuel et la lignée de rotation vérifiée avec les rôles ancien/nouveau et les empreintes SHA-256.
- Vérification des fichiers annexes V4/V4.1 : AutoJs6 dérive uniquement le fichier voisin exact `<nom de l’APK>.idsig` et accorde un descripteur borné en lecture seule ; le plugin vérifie les données signées, le certificat et la clé publique, le condensat APK V2/V3 correspondant, la racine fs-verity, l’arbre de Merkle intégré et tout signataire de rotation V3.1.
- Manifestes lisibles : les manifestes APK binaires et protobuf AAB sont décodés en XML dans une visionneuse séparée en lecture seule, avec numéros de ligne, coloration syntaxique sémantique et recherche bornée insensible à la casse, surlignage des résultats et navigation précédent/suivant.
- Accessibilité TalkBack et RTL : les sections du rapport exposent une sémantique de titre, chaque action par icône possède un libellé vocal, les lignes interactives personnalisées offrent une cible de 48 dp, les mises à jour sont annoncées et l’interface arabe est inversée selon la langue.
- Contrôle d'intégrité : le SHA-256 est calculé pendant la lecture du fichier, prêt à être comparé aux empreintes publiées officiellement.
- Bilan structurel : détecte l'absence d'APK de base, les splits en double ou sans dépendance, les incohérences de version ou de paquet, chaque constat étant marqué bloquant [!] ou informatif [i].

### Mode d'emploi

1. Téléchargez et installez APK Inspector, puis activez-le dans le centre de plugins d'AutoJs6 (code de version AutoJs6 5277 ou ultérieur requis).
2. Ouvrez le gestionnaire de fichiers d'AutoJs6 et repérez le paquet à examiner (APK, APKS, XAPK, APKM, APKZ ou AAB).
3. Touchez le fichier, ou choisissez « Inspecter le paquet Android » dans son menu ; le rapport apparaît après un instant.
4. Parcourez le rapport de haut en bas : icône et nom de l'application, détails du paquet, composants, autorisations demandées, constats de sécurité et de compatibilité.
5. Pour un APKS, XAPK, APKM ou APKZ, choisissez la langue, la densité d’écran et l’ABI, puis appliquez la simulation pour comparer les APK sélectionnés à l’appareil réel.
6. Appuyez longuement sur une ligne des « Détails du paquet » pour copier sa valeur, ou touchez « Partager le rapport » dans la barre d’outils pour envoyer le texte exact affiché via la feuille de partage Android.
7. Touchez « Afficher le manifeste » pour lire l'AndroidManifest complet, puis revenez en arrière pour retrouver le gestionnaire de fichiers.

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

L'AAB est un format de distribution destiné aux boutiques d'applications ; un appareil Android ne peut pas l'installer directement. Le plugin décode son manifeste protobuf et sa structure de modules, et utilise resources.pb pour résoudre le libellé localisé et une icône matricielle adaptée à la densité, mais l'installation exige toujours une conversion en APK(s) avec un outil comme bundletool.

### Autorisations et sécurité

Le plugin ne demande aucune autorisation de stockage, de réseau ni d'installation de paquets. Il n'atteint le paquet sélectionné que par l'URI content temporaire en lecture seule accordée par l'hôte ; un `.idsig` facultatif n'est disponible que par un descripteur hôte borné pour le voisin exact `<nom de l’APK>.idsig`, sans énumération du répertoire ni chemin voisin arbitraire. Avant l'inspection, les deux entrées sont copiées vers des instantanés en lecture seule du cache privé de l'application (le SHA-256 est calculé pendant la copie du paquet) et toute l'analyse se fait sur ces instantanés ; `.idsig` est limité à 40 MiB et les instantanés périmés sont nettoyés sous 24 heures. Les requêtes du gestionnaire de fichiers sont validées champ par champ, notamment la version du protocole, les identifiants de requête et d'action, les métadonnées de la cible, la version de l'hôte, la forme de l'URI, le nom, la taille, les autorisations en lecture seule et le Binder de session. Les requêtes « Ouvrir avec » ne sont acceptées qu'avec les types MIME dédiés ; application/zip, application/octet-stream et toute autorisation d'écriture, persistante ou par préfixe sont refusés.

Pour empêcher des fichiers forgés d'épuiser les ressources de l'appareil, l'analyse est bornée comme suit, et tout fichier hors borne est rejeté avec un message:

- Un fichier ne peut dépasser 4 GiB, au moins 128 MiB d'espace de cache doivent rester libres pendant la copie, et chaque action traite exactement un fichier cible.
- Au plus 16384 entrées d'archive sont analysées, au plus 512 entrées APK par bundle sont parcourues, et les noms d'entrée sont limités à 1024 caractères.
- La taille déclarée d'une entrée ne peut dépasser 4 GiB, et le total déclaré ne peut dépasser 8 GiB.
- L'analyse des manifestes d'APK imbriqués est plafonnée à 256 MiB, les métadonnées de bundle à 1 MiB, et l'APK temporaire servant à charger l'icône et le libellé à 512 MiB.
- Le fichier AAB BundleConfig.pb est limité à 1 MiB. Les annotations de distribution partagent l’analyse AAB de 128 manifestes / 16 MiB et conservent au plus 128 valeurs de condition par module ; limites et métadonnées incorrectes restent isolées et signalées.
- Le repli via les ressources lit au plus 32 MiB par table et 4 MiB par icône, avec un budget partagé de 512 MiB pour localiser table et icône dans un APK imbriqué ; une limite, une ressource incorrecte ou une référence non résolue désactive uniquement ce repli et est clairement signalée.
- La classification examine au plus 2048 demandes, affiche jusqu’à 512 noms sûrs et uniques et limite chaque explication chargée à 240 caractères ; les omissions et niveaux indisponibles sont clairement signalés.
- Les statistiques de composants analysent au plus 4096 déclarations par manifeste et 128 manifestes de modules AAB avec un budget d’entrée partagé de 16 MiB ; les omissions, valeurs exported non résolues et échecs par manifeste sont clairement signalés.
- Les statistiques natives conservent au plus 4096 entrées .so et affichent 64 répertoires ABI. Jusqu’à 512 APK imbriqués sélectionnés sont lus avec un budget partagé de 256 MiB, en retenant au plus 8 MiB de répertoire central par APK ; limites et échecs produisent des résultats partiels clairement signalés.
- Les statistiques DEX comptent toutes les entrées standard analysées, mais affichent au plus 128 chemins triés naturellement ; elles partagent le parcours borné du répertoire central avec la vue des bibliothèques natives, de sorte que le contenu DEX n’est jamais extrait, décodé ni décompilé.

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

Les capacités ci-dessus et les éléments cochés de la Roadmap reflètent l'existant ; les travaux prévus, tels que l’analyse approfondie des bundles et des AAB, sont suivis dans la Roadmap, et les éléments non cochés ne sont pas des capacités actuelles.

- [ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector/blob/master/ROADMAP.md)

### Historique des versions

#### v1.1.0

_2026/08/30_

- `Note` Nécessite AutoJs6 avec le code de version 5277 ou ultérieur pour le protocole Explorer Action v22 et l’accès borné au fichier V4
- `Fonctionnalité` Ajout de mises en page adaptatives pour les polices 1,5×/2,0×, le paysage et les écrans de 320 dp : en-tête empilé si nécessaire, titres compacts complets, retour à la ligne sans ellipse des SHA-256 et autorisations longues, et recherche du manifeste sans extraction IME plein écran
- `Fonctionnalité` Ajout de l’accessibilité TalkBack et RTL au rapport et à la visionneuse de manifeste : titres sémantiques compatibles, actions par icône nommées, cibles personnalisées de 48 dp, annonces des résultats dynamiques, disposition suivant la langue et navigation arabe inversée
- `Fonctionnalité` Ajout, dans la visionneuse de manifeste en lecture seule, des numéros de ligne, de la coloration XML adaptée au thème Material et d’une recherche bornée insensible à la casse, avec surlignage, navigation précédent/suivant en boucle et restauration de l’état
- `Fonctionnalité` Ajout de thèmes Material 3 clair/sombre suivant le système pour le rapport et la visionneuse du manifeste, de couleurs dynamiques Material You sous Android 12 ou version ultérieure et d’icônes de barres système adaptées au contraste
- `Fonctionnalité` Ajout de résumés bornés des métadonnées de conteneur pour SAI APKS `meta.sai_v1/v2.json`, XAPK `manifest.json` et APKMirror APKM `info.json`, affichant l’outil/version de format, la version d’application déclarée dans les métadonnées et une entrée d’icône existante ; les métadonnées endommagées ou supérieures à 1 Mio restent isolées et signalées
- `Fonctionnalité` Ajout d’une simulation de configuration dans les rapports APKS, XAPK, APKM et APKZ : changez la langue, la densité d’écran et l’ABI pour réexécuter localement la sélection bornée des splits sur le même instantané privé, avec état compatible/non valide et APK ajoutés ou retirés par rapport à l’appareil réel ; le rapport principal reste basé sur l’appareil réel
- `Fonctionnalité` Ajout de métadonnées bornées de configuration et de distribution AAB : décodage des réglages bundletool/type/split/compression/optimisation de BundleConfig.pb et annotation des modules base, feature, asset, ML, AI et SDK pour les distributions à l’installation, conditionnelle, à la demande, fast-follow, fusionnée et amovible ; les métadonnées incorrectes, trop volumineuses ou omises restent isolées et signalées
- `Fonctionnalité` Ajout d’un repli borné pour le libellé et l’icône matricielle via resources.pb (AAB) et resources.arsc (APK), résolus selon la langue et la densité actuelles sans extraire les APK imbriqués trop volumineux ; les échecs de table, d’icône et de limite de parcours restent isolés et clairement signalés
- `Fonctionnalité` Ajout de la copie par appui long des principales valeurs du paquet et du partage du rapport texte exact via la feuille de partage Android ; le contenu reste en mémoire, sans autorisation de stockage ni création de fichier
- `Fonctionnalité` Ajout d’une vue DEX bornée qui liste dans l’ordre naturel les fichiers classes*.dex standard des splits APK sélectionnés et modules AAB, avec les tailles non compressées par fichier et totale, en partageant le parcours du répertoire central des bibliothèques natives sans extraire, décoder ni décompiler le contenu DEX
- `Fonctionnalité` Ajout d’une vue bornée des bibliothèques natives regroupant les fichiers .so des splits APK sélectionnés et modules AAB par ABI et taille non compressée, avec marquage des ABI préférées, compatibles de repli et non prises en charge sans extraire leur contenu
- `Fonctionnalité` Ajout de statistiques bornées des composants du manifeste pour les activités/alias, services, récepteurs de diffusion et fournisseurs de contenu dans les splits APK sélectionnés et modules AAB analysés, regroupées par état android:exported explicite avec signalement des résultats partiels
- `Fonctionnalité` Ajout de la vérification cryptographique sur l’appareil des schémas APK V2, V3, V3.1, V4 et V4.1, y compris les condensats, preuves du signataire, racines fs-verity, arbres de Merkle intégrés et correspondance avec le schéma complémentaire
- `Fonctionnalité` Ajout des champs détaillés des certificats et des lignées de rotation vérifiées avec rôles ancien/actuel, indicateurs de capacité et empreintes SHA-256
- `Fonctionnalité` Ajout de la copie bornée de `.idsig` via un descripteur en lecture seule dérivé exactement par l’hôte ; l’énumération des répertoires et l’accès arbitraire aux voisins restent indisponibles
- `Fonctionnalité` Regroupement des autorisations demandées selon `protectionLevel` en exécution/dangereuses, signature/protégées et normales ; celles à l’exécution sont mises en avant avec une explication bornée sur une ligne, tandis que les niveaux indisponibles restent visibles et signalés
- `Correctif` Correction de la disparition de l’action d’affichage du manifeste après un changement de langue, de thème ou une autre recréation d’activité, grâce au remplacement sûr de l’ancien instantané privé en lecture seule
- `Correctif` Correction de l’inaccessibilité du panneau de recherche du manifeste lorsque le clavier logiciel réduisait un écran compact ou utilisant de grands caractères ; le clavier n’apparaît désormais qu’après toucher du champ de recherche déjà ciblé
- `Amélioration` Renforcement de la validation Explorer Action v22 et des instantanés privés immuables, avec limites de 4 GiB pour le paquet et 40 MiB pour idsig, contrôles d’identité et fermeture rapide de la session
- `Amélioration` Ajout d’échantillons officiels Build Tools 37 `apksigner` couvrant signatures valides, altérées, multiples, rotations V3.1/V4.1, absences et formats incorrects
- `Amélioration` Ajout d’une matrice d’isolation par section sur de vrais paquets, couvrant les limites de production des autorisations, composants de manifeste, bibliothèques natives et DEX, ainsi que les dépassements de table de ressources et les répertoires centraux imbriqués endommagés ; chaque échantillon vérifie que les sections non touchées restent complètes et que les avis partiels sont conservés dans le partage texte
- `Amélioration` Ajout d’une matrice de référence reproductible pour la sélection bundletool 1.18.2 : `build-apks` traite un AAB minimal anonymisé, puis l’`ExtractApksCommand` partagé par `install-apks` enregistre les APK d’installation pour trois profils langue/densité/ABI ; les tests unitaires vérifient chaque ensemble simulé, l’absence de doublons et la stabilité entre exécutions
- `Amélioration` Ajout d’une matrice reproductible de revue de captures d’écran comportant 36 cas : portraits 411/320 dp et paysage compact, polices 1,0x/1,5x/2,0x, thèmes clair/sombre et LTR/RTL ; son pilote ADB préserve l’état de l’appareil, audite l’accessibilité des contrôles, l’ordre de lecture et les cibles tactiles de 48 dp, puis produit des preuves PNG/XML et une planche-contact
- `Amélioration` Ajout d’une matrice déterministe de 24 échantillons sans données privées couvrant APK/APKS/XAPK/APKM/APKZ/AAB avec des entrées normales, structurellement endommagées, hors limite et incompatibles avec l’appareil ; un générateur fondé sur la bibliothèque standard, un manifeste SHA-256 et des tests de contrat JVM vérifient la reproduction octet par octet, les résultats d’analyse, la taille compacte et l’absence de code, de matériel de signature et de données utilisateur

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
