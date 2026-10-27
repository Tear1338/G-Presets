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
