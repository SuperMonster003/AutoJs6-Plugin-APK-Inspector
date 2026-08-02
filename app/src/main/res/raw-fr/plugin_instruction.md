# APK Inspector

APK Inspector fournit action principale de vérification en lecture seule dans Explorer principal de AutoJs6 pour les fichiers APK, APKS, XAPK, APKM, APKZ et AAB.

Il affiche les détails du paquet, les autorisations demandées, les composants, les APK fractionnés adaptés à appareil, les ressources OBB, les constats structurels, la présence des signatures APK V1-V3 et un manifeste Android formaté.

Le plugin exige AutoJs6 build 5269+. Il est entièrement implémenté sur la JVM et ne dépend pas de ABI du périphérique.

Limites de sécurité et de confidentialité:

- La source est ouverte avec un accès temporaire en lecture seule par URI `content`.
- La source est copiée une seule fois dans un instantané privé en lecture seule, limité à 4 GiB, avec calcul SHA-256.
- Le plugin ne demande aucune autorisation de stockage, de réseau ou installation de paquets.
- Le plugin ne modifie ni installe de paquet. Les schémas de signature sont uniquement des contrôles de présence.
