APK Inspector examine les paquets APK, APKS, XAPK, APKM, APKZ et AAB avant leur installation: nom de l'application, version, autorisations, composants, signatures, etc., et indique si le paquet peut être installé sur cet appareil. Il se contente de lire le fichier: il n'installe jamais le paquet et ne modifie jamais le fichier d'origine.

### Prise en main

1. Vérifiez que ce plugin est activé dans le "Centre des plugins" d'AutoJs6.
2. Ouvrez le gestionnaire de fichiers d'AutoJs6 et repérez le paquet à examiner.
3. Touchez le fichier, ou choisissez "Inspecter le paquet Android" dans son menu (dans AutoJs6 6.8.1 et versions ultérieures, il se trouve dans le menu de premier niveau "APK Inspector").
4. Parcourez le rapport de haut en bas: "Détails du paquet", "Composants", "Autorisations demandées" et "Constats de sécurité et de compatibilité".

### Fonctionnalités principales

- **Détails du paquet**: affiche le nom du paquet, la version et les SDK minimal et cible, comparés à cet appareil; appuyez longuement sur un champ pour copier sa valeur.
- **Vérification des signatures**: vérifie les schémas de signature V2, V3, etc. et affiche les empreintes des certificats de signature.
- **Autorisations demandées**: classe les autorisations en trois groupes (exécution, signature et normales), les autorisations d'exécution en tête avec une courte explication.
- **Composants**: récapitule les activities, services, receivers et providers et indique si chacun est exporté.
- **Constats de sécurité et de compatibilité**: signale des problèmes comme un APK de base manquant, des splits en double ou des versions ou noms de paquet incohérents, en distinguant les blocages des simples indications.
- **Simulation de configuration de l'appareil**: pour les ensembles comme APKS, changez la langue, la densité d'écran et l'ABI, puis touchez "Appliquer la configuration simulée" pour comparer les splits choisis avec cet appareil.
- **Afficher le manifeste**: affiche AndroidManifest en XML avec numéros de ligne et coloration syntaxique; utilisez "Rechercher dans le manifeste" pour y chercher du texte.
- **Partager le rapport**: envoie un rapport texte identique à l'écran via le menu de partage du système, sans créer de fichier.

### Prérequis

- Android 7.0 ou version ultérieure.
- AutoJs6 6.8.0 ou version ultérieure.
- Aucune autorisation de stockage, de réseau ou d'installation n'est nécessaire; le plugin lit uniquement le fichier sélectionné dans le gestionnaire de fichiers.

### Questions fréquentes

**Raisons pour lesquelles le plugin n'installe pas les paquets**

Le plugin sert à examiner un paquet avant son installation; il ne demande donc aucune autorisation d'installation et n'offre aucun bouton d'installation. Installez les paquets avec l'installateur du système ou la procédure d'installation d'AutoJs6.

**Raisons pour lesquelles certains fichiers ne peuvent pas être examinés**

Causes fréquentes: un fichier de plus de 8 GiB, moins de 128 MiB d'espace libre dans le cache, un fichier modifié par une autre application pendant la lecture ou un fichier endommagé. Le message d'erreur de la page du rapport indique la cause.

**Signification d'une signature valide**

Une signature valide montre seulement que le fichier n'a pas changé depuis sa signature; elle ne prouve pas que le signataire ou l'application soient dignes de confiance. Comparez l'empreinte du certificat ou le SHA-256 avec les valeurs publiées par la source officielle.

**Raisons pour lesquelles les fichiers AAB ne s'installent pas directement**

L'AAB est un format de distribution destiné aux boutiques d'applications, qu'un appareil Android ne peut pas installer directement. Le plugin affiche son manifeste et sa structure de modules; convertissez-le en APK ou APKS avec un outil comme bundletool avant de l'installer.

Plus de détails sur la [page du projet](https://github.com/SuperMonster003/AutoJs6-Plugin-APK-Inspector).
