# G-Presets 1.6.5 — guide technique

Édition modifiée de [sirjonasxx/G-Presets](https://github.com/sirjonasxx/G-Presets), à partir de la version modifiée 1.3.7 fournie. Auteurs : **sirjonasxx & 1desirable1337** ; dépôt de cette édition : [Tear1338/G-Presets](https://github.com/Tear1338/G-Presets).

Consultez le [README principal](README.md) pour l'installation, les commandes et l'utilisation des fonctions. Téléchargements : [JAR 1.6.5](https://github.com/Tear1338/G-Presets/releases/download/v1.6.5/GPresets-1.6.5.jar) et [archive complète](https://github.com/Tear1338/G-Presets/releases/download/v1.6.5/GPresets-1.6.5-complet.zip).

## Environnement

- Distribution Windows 64 bits, Java 17+ ; compilation et vérifications effectuées avec Java 21.
- Intégration visant notamment G-Earth 1.5.4-beta-29, avec un catalogue de noms de messages adapté au client Habbo connecté.
- Référence de protocole examinée : client Flash français `WIN63-202610201032-107964540`, du 20 octobre 2026, et catalogue du 26 octobre 2026.
- Le build inclut les dépendances JavaFX Windows nécessaires au JAR. Les autres systèmes ne sont pas validés par cette distribution.

## Thème de l'interface

L'extension importe directement la feuille de style `gearth/ui/themes/G-Earth_Dark/styling.css` embarquée dans son runtime. La palette correspond à la capture de référence fournie : panneau #333333, en-tête #222222, champs #363636, boutons #262626, textes #f0f0f0 et onglets #5d83b4. Les accents de sélection utilisent les couleurs du thème G-Earth. Les fenêtres d'aperçu, de partage et de rapport reçoivent aussi cette feuille de style ; le canevas suit le fond anthracite.

## Aperçu et confirmation

`PresetPreviewModel` travaille sur une copie du preset et de ses options. Il applique les remplacements avant de calculer les empreintes. Les dimensions absentes sont représentées en 1×1 ; les mobis existants explicitement réutilisés sont comptés séparément et ne sont pas déplacés par l'aperçu.

`PresetPreviewSession` fige le preset, les options et le contexte de salle. La confirmation devient invalide lorsque ce contexte change. L'ouverture, l'annulation et le rejet d'une confirmation obsolète sont vérifiés sans envoyer de paquets au serveur Habbo.

L'interface propose zoom, panoramique, recentrage, rotation de vue, recherche, sélection, isolation et filtre de hauteur. Les images de mobilier demandent l'orientation et l'état disponibles à l'imager HabboAssets. Les points d'ancrage, animations et placements muraux ne reproduisent pas le moteur complet de Habbo ; les offsets muraux restent consultables dans les détails. Le sol de destination peut imposer un décalage de hauteur à l'import.

## Chargement et cache des images

Les deux fenêtres — aperçu et partage — utilisent `FurnitureSprites` et `FurnitureSpriteStore`. Le chargement est préamorcé après une sélection de preset stabilisée pendant 250 ms.

| Mécanisme | Comportement en 1.6.5 |
| :--- | :--- |
| Téléchargements | Quatre tâches simultanées en arrière-plan ; suppression du délai de 1,1 seconde par image |
| Déduplication | Une demande en cours partagée pour une même classe, orientation normalisée et état normalisé |
| Mémoire | Cache des images décodées, éviction LRU avec un budget de 64 MiB |
| Disque | Fichiers PNG sous le dossier de données `cache/furniture-v1`, noms dérivés de l'URL ; expiration après sept jours |
| Nettoyage | Nettoyage périodique du cache disque visant 128 MiB et 2 048 fichiers |
| Validation | Taille de réponse limitée à 2 000 000 octets, signature PNG, dimensions au plus 2 048×2 048 et décodage de l'image |
| Serveur occupé | Temporisation commune sur HTTP 429/503, respect de `Retry-After`, nombre d'essais limité |
| Panne ou fichier corrompu | Rechargement d'une entrée disque invalide ; court cache des échecs pour éviter les demandes répétées ; empreinte visible si l'image manque |
| Fermeture de fenêtre | Les tâches partagées peuvent terminer pour alimenter le cache, sans mettre à jour la fenêtre fermée |

Les images déjà en mémoire sont récupérées avant le calcul des limites de la vue afin d'être disponibles dès la première image. La lecture du disque, le réseau et le décodage sont exécutés en arrière-plan. Le premier téléchargement reste tributaire du réseau et du service HabboAssets ; le cache ne garantit pas un chargement instantané à froid.

L'aperçu limite ses nouvelles demandes à 256 variantes par fenêtre. Le préchargement de sélection limite aussi son ensemble initial à 256 variantes distinctes. Le compteur de chargement indique les images disponibles ou en échec. Les images publiques sont demandées par classe, orientation et état, sans identifiants de connexion Habbo.

## Données WIRED prises en charge

| Ensemble | Implémentation |
| :--- | :--- |
| Six familles | Déclencheurs, effets, conditions, add-ons, sélecteurs et variables ; variantes reconnues par famille |
| Champs des formulaires | Options, textes, deux sélections de mobilier, sources, références de variables, délais et quantificateurs |
| Variables modernes | Métadonnées de contexte, connecteurs de texte et valeur de secours, réponses fragmentées, identifiants sous forme de chaînes |
| Dépendances | Ordre topologique des définitions, remappage d'identifiants, contrôle des références et cycles |
| Contrats | Règles structurées de paiement, d'échange et de récompense ; types de mobilier traduits par classe, sauvegarde puis relecture |
| Coffres | Options, préférences, capacité configurée, présentation, accès et notifications ; aucun contenu ni solde copié, aucun achat automatique de capacité |
| Liens WIRED | Association à un réseau existant accessible, réception locale et relecture de la configuration |
| Labels et régions | Formats distincts, textes, présentation, rectangles et translation des régions |
| Propriétés et valeurs | Propriétés modifiables inspectées et valeurs personnalisées, remappées selon leur nature |
| Paramètres de salle | Contexte conservé ; application réservée aux presets de salle complète |
| Mobilier mural | Placement avant la configuration des sélections WIRED, gestion des variantes et décalages |

Les chaînes du protocole Habbo utilisent UTF-8. Le transport binaire vers G-Earth conserve les octets avec l'encodage ISO-8859-1 adapté à son enveloppe ; ce transport a fait l'objet de la correction du blocage de session en 1.4.2.

## Vérifications avant et après import

`AvailabilityAudit` examine la copie effective du preset et les réglages de source. Il distingue mobilier au sol et au mur, variantes d'affiches, quantité en inventaire, offre BC, manque et données inconnues. Une offre ne constitue pas une vérification du quota BC ou des droits de placement. La sélection de mobilier réutilisé dans la salle intervient lors de l'import.

`WiredDiagnostics` contrôle la sérialisation locale des configurations, les doublons d'identifiants, les cycles de variables et les références externes. Le contrôle interactif vérifie aussi les messages requis dans le catalogue G-Earth. Les erreurs locales détectées à la préparation bloquent le placement ; les références externes restent des avertissements et ne sont pas supprimées automatiquement.

La progression affiche les opérations traitées par phase. La pause est appliquée entre les opérations ; les réponses réseau continuent d'être traitées. La reprise concerne la même session d'import, sans reprise après redémarrage. Un changement de salle détecté aux points de contrôle interrompt le travail.

`ImportReport` conserve des suivis distincts pour les identifiants du sol et du mur. Il capture l'état observé, les positions attendues et observées, les orientations et états disponibles, les objets non observés et les confirmations des configurations. Une altitude murale non transmise reste inconnue. Les compteurs auxiliaires portent sur les contrats, coffres, liens, labels/régions, paramètres de salle, liens de salle et valeurs d'objets vérifiés par l'importeur.

Le rapport reflète un instant donné. Des réponses tardives peuvent modifier la salle et les propriétés non comparées restent à contrôler en jeu. Un accusé de sauvegarde ne prouve pas le fonctionnement d'un montage WIRED. Une interruption peut laisser du mobilier déjà placé.

## Fichiers, dossiers et partage

- Les presets restent des JSON. La sauvegarde des presets est atomique et les chemins sont contrôlés.
- Sous Windows, le dossier de données par défaut est `%APPDATA%/G-Presets` ; `gpresets.dataDir` permet de le remplacer.
- Les presets sont dans `presets/`. Les dossiers sont limités à un niveau ; un déplacement n'écrase pas un fichier existant.
- Le partage ajoute des métadonnées optionnelles : nom, description et image PNG encodée. Elles sont conservées lors de la relecture et restent séparées des sérialisations de paquets.
- Le partage et les rapports enregistrent de nouveaux fichiers sans écraser une destination existante.
- La distribution exclut les dossiers de presets personnels ; les données du cache ne sont pas incluses dans l'archive complète.

## Validation de la version 1.6.5

**1 413 assertions de régression passent**, ainsi que les vérifications de l'interface JavaFX et de ses ressources embarquées. Les 80 nouvelles assertions couvrent l’historique, la comparaison, les remplacements, la confidentialité, le placement et les garde-fous d’annulation. Le traitement réel d’annulation est aussi exercé avec une salle simulée, sans connexion réseau. Les tests comprennent les formats binaires, les fichiers, les correspondances d'identifiants, les diagnostics, la pause et les comportements du cache : parallélisme borné, demande unique partagée, cache disque après redémarrage, corruption et panne. Un contrôle JavaFX vérifie aussi qu'une image déjà décodée est disponible dès la première vue, avec les couleurs du thème sombre de G-Earth : panneaux #333333, champs #363636, boutons #262626 et textes clairs.

Le catalogue de référence comporte **284 classes liées aux WIRED**, dont **207 classes des six familles** ; les fixtures couvrent **188 codes de formulaires**. Ces tests utilisent des exemples contrôlés pour vérifier les données et ne constituent pas une validation de chaque règle métier.

**Aucun cycle complet export/import ni scénario WIRED en jeu n'a été validé pour cette édition.** La salle observée était vide. Les prototypes `wf_test_26secret1` et `wf_test_26secret2` ont un comportement configurable inconnu. Voir [COMPATIBILITE.md](COMPATIBILITE.md), [COUVERTURE-WIRED.csv](COUVERTURE-WIRED.csv) et le [rapport du build 1.6.5](https://github.com/Tear1338/G-Presets/releases/download/v1.6.5/build-report.json).

Les rapports sous `validation/1.4.0-rc2/` sont historiques et ne décrivent pas la validation de la version actuelle.

## Reconstruction et publication

JDK 17+ et Python 3 ; la vérification JavaFX de la distribution embarquée cible Windows :

```powershell
python scripts/build.py --java-home 'C:/Program Files/Java/jdk-21.0.10' --ui-smoke
python scripts/package.py
```

Le build recompile les sources applicatives et la classe `HPacket` adaptée. `lib/gpresets-runtime.jar` et `lib/dependencies` fournissent les autres dépendances. Les résultats locaux sont dans `target/build-report.json`, `target/test-results.txt` et `target/ui-test-results.txt`.

L'archive complète contient le JAR, les sources, dépendances, fixtures, documents et rapports sélectionnés. Le workflow GitHub recompile et vérifie les changements, puis publie une nouvelle version si son tag n'existe pas déjà. Une modification du README ne remplace pas une release existante. Consulter [RELEASING.md](RELEASING.md).


## Outils 1.6.5 et conservation des releases

L'onglet **Tools** regroupe les nouveaux outils. La 1.5.3 et les releases déjà publiées sont conservées sur GitHub ; les prochaines publications ne les suppriment pas.

### Annuler le dernier import

**Undo last import** propose de récupérer les mobis nouvellement posés et identifiés individuellement pendant le dernier import de cette session, y compris un import incomplet. Les objets déjà présents, réutilisés et les stacktiles temporaires sont exclus. Chaque récupération vérifie la salle, le type, le propriétaire et les droits, puis attend la disparition de l'objet dans l'état reçu du serveur. `:a` arrête la récupération ; les objets sans confirmation restent disponibles pour une nouvelle tentative.

L'annulation ne restaure pas les déplacements, états, WIRED ou réglages de salle modifiés sur des éléments existants. Elle ne persiste pas après fermeture de l'extension ou déconnexion. Les placements non identifiés ou arrivés trop tard sont exclus. Les objets BC nécessitent une attribution non ambiguë au compte connecté ; **Load Inventory** actualise aussi cette identité lorsque le message est connu de G-Earth. Les objets BC appartenant à un propriétaire non identifié ne sont pas récupérés automatiquement.

### Comparer deux presets

**Compare presets & history** permet de choisir A et B et d'afficher les ajouts, suppressions et modifications de mobilier, positions, états et configurations WIRED. Tous les champs sérialisés sont comparés. Les identifiants sol/mur sont distincts ; deux exports indépendants peuvent attribuer des identifiants différents aux mêmes objets.

### Remplacer le mobilier manquant

Sélectionnez votre preset, puis **Replace missing furniture**. Les classes manquantes peuvent être remplacées par une classe du même type sol/mur présente dans votre inventaire. Le changement s'applique à tous les objets de cette classe **pour la session uniquement**, en conservant leurs identifiants et les références WIRED. Les états sont remis à leur valeur par défaut. Les WIRED, affiches et objets porteurs de configurations spéciales sont protégés contre un remplacement incompatible. Un remplacement peut changer les dimensions ou le comportement attendu par les WIRED : relisez l'avertissement et vérifiez le nouvel aperçu. Les quantités restent contrôlées avant l'import.

### Historique local

Les sauvegardes de presets conservent des révisions locales, avec déduplication des contenus inchangés. **Save revision** enregistre aussi une copie du fichier courant, pratique après une modification dans un éditeur externe. Choisissez une révision pour **Compare revision to current** ou **Restore revision** ; le fichier remplacé est conservé dans l'historique. Le déplacement vers un dossier garde les révisions accessibles. L'historique se trouve dans `history` à côté du dossier `presets` et n'est jamais ajouté aux fichiers partagés. Resélectionnez un preset après restauration avant de l'importer.

### Contrôle du partage

Les cases du partage permettent d'exclure le nom, la description et l'image. La fenêtre **Privacy review - exact exported file** montre le contenu exact qui sera enregistré après validation. Elle rappelle les champs de configuration pouvant contenir des textes, liens ou destinations. Aucun envoi en ligne n'est effectué.

### Placement assisté

Dans **Preview preset**, activez **Choose origin here** et réglez **X** / **Y**. Le contrôle se rafraîchit avec la salle : les empreintes hors du sol utilisable ou chevauchant un mobi existant sont entourées en rouge. Ce contrôle géométrique en 2D reste indicatif : il ne simule ni les hauteurs d'empilement, ni les droits, ni les collisions physiques, ni le placement mural exact. La confirmation utilise l'origine choisie ; la préparation peut encore demander la zone technique des stacktiles dans Habbo.


## Import avec des éléments manquants (1.6.5)

`Allow building without all furni` permet de terminer la construction disponible. Le plan de configuration exclut les WIRED qui dépendent d'un mobi non posé, les instantanés incomplets et les dépendances de variables devenues indisponibles. Les paramètres des autres mobis continuent ; seules les valeurs de propriétés qui référencent un élément absent sont retirées. Le preset source reste intact et le rapport conserve les quantités initiales, les éléments ignorés et leurs raisons. Le résultat affiche `Import finished with omissions`.

Sans cette option, un mobi requis manquant reste bloquant. Une erreur de sauvegarde ou une absence de confirmation serveur reste bloquante dans les deux modes. `Prefer BC` garde la priorité BC au sol et au mur, puis utilise l'inventaire lorsqu'aucune offre BC n'existe pour le mobi ; cela n'achète aucun mobi.

## Ancrage des images (1.6.5)

La preview lit uniquement les métadonnées de géométrie des assets SWF officiels à la révision du furnidata, sans exécuter leur code. Elle utilise leurs origines et les rectangles des calques lorsque ces limites correspondent exactement au PNG affiché. Les symboles partagés, alias, miroirs horizontaux et états de base sont pris en charge ; une image incompatible conserve un ancrage approximatif indiqué à l'écran. Les empreintes suivent les centres des cases, la rotation de caméra conserve l'origine des mobis rectangulaires et le cadrage utilise les limites complètes des images. Les couches de fond sont dessinées avant les objets et la sélection tient compte de la transparence. Ce rendu de PNG n'est pas le moteur d'animations/composition par calque de Habbo ; les marqueurs muraux restent approximatifs.

Deux tâches de fond au maximum lisent les géométries, avec déduplication et un cache disque plafonné à 32 Mio. Les contrôles JavaFX exercent les ancrages, les hauteurs, les quatre orientations, le cadrage et les clics transparents. Des assets officiels de sable, eau, plateforme et arbre ont également été examinés localement pour confirmer les origines ; ces assets de diagnostic et les presets privés ne sont pas distribués.

## Compatibilité des anciennes liaisons (1.6.5)

`roomLinks.destination` a été incorrectement interprété comme un numéro d'appartement dans les versions précédentes. La propriété native `~103` désigne le téléporteur associé (`~teleport.target_id`). Un ancien identifiant absolu ne peut pas être appliqué à un nouveau mobi comme une destination de salle.

Les exports 1.6.5 ajoutent `destinationType` : `furniture` pour un identifiant de preset local, `externalFurniture` pour une association hors sélection, et `none` pour une absence de liaison explicite. Seuls les identifiants locaux résolus sont envoyés au serveur. En mode partiel, les anciennes liaisons ambiguës et les associations externes sont ignorées avec une raison détaillée ; leur liaison native actuelle reste intacte et le reste de la construction continue. En mode strict, ces réglages non transférables restent bloquants. Une nouvelle exportation permet de capturer une paire locale explicitement.
