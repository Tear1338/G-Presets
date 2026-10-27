# Couverture vérifiée — 26 octobre 2026

| Élément | Implémentation | Validation obtenue |
|---|---|---|
| Six familles de WIRED | Import/export des enveloppes modernes et paramètres opaques | Comparaison binaire avec un codec indépendant |
| 188 codes de formulaires | Options, textes UTF-8, sources, deux sélections et variables conservés | 188 exemples entrée serveur → sauvegarde attendue |
| 207 classes de famille | Reconnaissance par préfixe, variantes de test et prototypes | Parcours du catalogue |
| Variables modernes | Nouveau drapeau de contexte, fallback, suppressions en chaînes et fragments | Exemples et cas d’erreur |
| Références et dépendances | Correspondance d’IDs, ordre topologique, références externes vérifiées | Remplacements, références manquantes et cycles |
| Contrats de paiement/échange/récompense | Règles imbriquées, types de mobi, textes et options | Exemples natifs des trois types, JSON et paquets tronqués |
| Coffres | Options, préférences, notifications et capacité configurée | Trois paquets comparés au codec natif ; différences de lecture détectées |
| Liens WIRED | Réseau existant et réception locale | Analyse de réponse, sauvegarde, disponibilité ; relecture intégrée |
| Labels | Discriminateur 0, ID entier, texte, couleur, visibilité, ouverture | Exemple binaire natif et sérialisation |
| Régions | Discriminateur 1, description, décalages, taille minimale, couleur et rectangles | Exemple mixte natif ; sauvegarde et translation |
| Propriétés de mobilier | Valeurs natives modifiables, couleurs, masques, compteurs et destinations | Correspondances signées, translation et valeurs immuables |
| Valeurs personnalisées | IDs de variables conservés ; Write/Create selon leur nature | Correspondance d’ID et refus des références absentes |
| Valeurs de salle | Variables définies par les boîtes du preset | Inspection globale native et opération Write |
| Paramètres de salle | Droits et fuseau ; application limitée au preset de salle complète | Exemple natif, JSON et contrôle de portée |
| Mobilier mural | Placement avant les WIRED et espaces d’IDs distincts | Vérification des identifiants de sol/mur identiques |
| Classements | Ancre d’affichage relative au preset | Translation et aller-retour JSON |
| Téléporteurs | Remplacement des références internes aux deux extrémités copiées | Correspondance d’ID et endpoint manquant |
| Texte réseau | UTF-8 ; limite de 65 535 octets encodés | Accents, emoji, remplacement et transport binaire G-Earth |
| Fichiers | Écriture atomique, contrôle des chemins, anciens presets | Non-régression et échecs de sérialisation |
| Apercu avant import | Plan isometrique, zoom, selection, recherche, confirmation et annulation | Tests du modele, des snapshots, de la commande :ip et des controles JavaFX ; aucun paquet serveur avant confirmation |
| Interface du JAR | FXML, liaisons des contrôles et CSS embarqués | Chargement JavaFX sans fenêtre ni connexion à Habbo |
| Objets auxiliaires | Placement, état et propriétés inspectables disponibles | Inventoriés ; gameplay non testé |
| Deux prototypes 26secret | Comportement configurable inconnu | Non validé |
| Export/import et gameplay réel | À valider avec des mobis et droits de test | Non testé : salle connectée vide |

Le chiffre des tests porte sur les assertions de transport, de persistance et de correspondance ; il ne mesure pas le nombre de comportements WIRED exécutés en jeu. Les 188 exemples utilisent des valeurs synthétiques pour vérifier l’intégrité des champs et non pour valider les règles métier de chaque formulaire.

Le client natif a aussi été examiné pour l’ordre des métadonnées modernes, le discriminateur labels/régions, le composeur des régions et la séparation des variables des dataframes. Les données texte des dataframes restent des couples clé/texte ; leurs IDs de variables sont transportés dans la liste dédiée.

SHA-256 du client de référence :
`8C098CF290F3637D310235D735F6BD7EDC90581A2E9480BF1518F715663AAEF3`

Le code dépend des noms de messages résolus par G-Earth pour le client connecté. Aucun en-tête numérique de paquets n’est figé dans cette mise à jour.


## Vérifications 1.5.0

Le contrôle WIRED recherche les doublons d'identifiants, les cycles de variables, les références hors du preset, les charges utiles non prises en charge et les messages requis absents du catalogue G-Earth. Il ne prouve pas la sémantique des formulaires ni le comportement en jeu. Les références externes sont des avertissements, pas des suppressions automatiques.

Le bilan avant import couvre le sol, le mur et les variantes d'affiches. Il distingue inventaire, offre BC, manque et données inconnues. Les quotas BC, droits et réutilisations de salle restent à vérifier au moment de l'import.

Le rapport après import observe le sol et le mur dans la salle active et conserve deux espaces d'identifiants pour son suivi. Il compare les coordonnées, rotations, états et décalages muraux quand ils sont lisibles. Une altitude murale absente des réponses reste inconnue. Les confirmations des configurations auxiliaires sont comptées après la vérification déjà effectuée par l'importeur. Aucun achat automatique de capacité de coffre n'est ajouté. Les propriétés supplémentaires non comparées par ce rapport restent à contrôler en jeu.

L'aperçu a été enrichi, mais ne contient pas le moteur complet de rendu Habbo : les ancrages d'images, les animations et certains placements muraux ne sont pas garantis identiques au jeu. Les images et leurs variantes dépendent de la disponibilité de l'imager public HabboAssets. Documentation utilisée : https://www.habboassets.com/api (imaging, direction, state, size).

La session connectée observée le 27 octobre 2026 ne contenait aucun mobi au sol ou au mur. Aucun cycle complet d'export/import ni test de gameplay n'a été réalisé pour cette version.
