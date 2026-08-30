# Historique des versions

## v1.1.0

_2026/08/30_

- `Note` Nécessite AutoJs6 avec le code de version 5277 ou ultérieur pour le protocole Explorer Action v22 et l’accès borné au fichier V4
- `Fonctionnalité` Ajout de statistiques bornées des composants du manifeste pour les activités/alias, services, récepteurs de diffusion et fournisseurs de contenu dans les splits APK sélectionnés et modules AAB analysés, regroupées par état android:exported explicite avec signalement des résultats partiels
- `Fonctionnalité` Ajout de la vérification cryptographique sur l’appareil des schémas APK V2, V3, V3.1, V4 et V4.1, y compris les condensats, preuves du signataire, racines fs-verity, arbres de Merkle intégrés et correspondance avec le schéma complémentaire
- `Fonctionnalité` Ajout des champs détaillés des certificats et des lignées de rotation vérifiées avec rôles ancien/actuel, indicateurs de capacité et empreintes SHA-256
- `Fonctionnalité` Ajout de la copie bornée de `.idsig` via un descripteur en lecture seule dérivé exactement par l’hôte ; l’énumération des répertoires et l’accès arbitraire aux voisins restent indisponibles
- `Fonctionnalité` Regroupement des autorisations demandées selon `protectionLevel` en exécution/dangereuses, signature/protégées et normales ; celles à l’exécution sont mises en avant avec une explication bornée sur une ligne, tandis que les niveaux indisponibles restent visibles et signalés
- `Amélioration` Renforcement de la validation Explorer Action v22 et des instantanés privés immuables, avec limites de 4 GiB pour le paquet et 40 MiB pour idsig, contrôles d’identité et fermeture rapide de la session
- `Amélioration` Ajout d’échantillons officiels Build Tools 37 `apksigner` couvrant signatures valides, altérées, multiples, rotations V3.1/V4.1, absences et formats incorrects

## v1.0.1

_2026/08/08_

- `Correctif` Correction de l'impossibilité pour l'hôte de se lier au service du plugin après son activation dans le centre de plugins ; l'action « Inspecter le paquet Android » fonctionne désormais immédiatement après l'activation
- `Amélioration` Nom et description du plugin simplifiés, documentation utilisateur plus naturelle à lire

## v1.0.0

_2026/08/02_

- `Note` Première version publique ; requiert AutoJs6 avec un code de version 5269 ou ultérieur
- `Fonctionnalité` Touchez un fichier APK, APKS, XAPK, APKM, APKZ ou AAB dans le gestionnaire de fichiers AutoJs6 pour ouvrir un rapport d'inspection en lecture seule (ID de plugin `apk-inspector`, ID d'action `inspect-android-package`)
- `Fonctionnalité` Le rapport montre le nom et l'icône de l'application, le nom du paquet, la version, la plage de SDK, les autorisations demandées, les splits et ressources OBB, les problèmes structurels et la présence des schémas de signature V1-V3
- `Fonctionnalité` Les manifestes APK texte et binaires, les manifestes protobuf AAB et les métadonnées bundletool `toc.pb` sont décodés automatiquement, avec une visionneuse dédiée pour le manifeste mis en forme
- `Fonctionnalité` D'autres applications peuvent transmettre un paquet via « Ouvrir avec » (ACTION_VIEW) avec les types MIME de paquets Android dédiés
- `Fonctionnalité` Avant l'inspection, le fichier est copié vers un instantané privé en lecture seule avec calcul du SHA-256 (limite de 4 GiB) ; le plugin ne demande aucune autorisation de stockage, de réseau ni d'installation
- `Fonctionnalité` Livré en 10 langues pour l'interface, les instructions, le README et le CHANGELOG : chinois simplifié, chinois traditionnel (Hong Kong et Taïwan), anglais, français, espagnol, japonais, coréen, russe et arabe
- `Dépendance` Ajout de Gson 2.13.2
