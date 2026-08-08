# APK Inspector

APK Inspector fournit l'action principale de vérification en lecture seule dans le gestionnaire de fichiers pour les fichiers APK, APKS, XAPK, APKM, APKZ et AAB.

Il affiche les détails du paquet, les autorisations demandées, les composants, les APK fractionnés adaptés à appareil, les ressources OBB, les constats structurels, la présence des signatures APK V1-V3 et un manifeste Android formaté.

Le plugin exige la version 5269 ou ultérieure de l'hôte.

Limites de sécurité et de confidentialité:

- La source est ouverte avec un accès temporaire en lecture seule par URI `content`.
- La source est copiée une seule fois dans un instantané privé en lecture seule, limité à 4 GiB, avec calcul SHA-256.
- Le plugin ne demande aucune autorisation de stockage, de réseau ou installation de paquets.
- Le plugin ne modifie ni installe de paquet. Les schémas de signature sont uniquement des contrôles de présence.
