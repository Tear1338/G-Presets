# G-Presets 1.4.0

Mise à jour locale de l’extension Java G-Presets, préparée à partir de la version modifiée 1.3.7 fournie. Projet d’origine : https://github.com/sirjonasxx/G-Presets ; auteur conservé : sirjonasxx. Édition modifiée et publiée par 1desirable1337 (Tear1338 / Darkz) : https://github.com/Tear1338/G-Presets. Version indépendante du projet original. Voir README.md pour la présentation et les téléchargements.

## Installation et utilisation

1. Décompresser l’archive complète et conserver une copie de ses anciens presets.
2. Charger `dist/GPresets-1.4.0.jar` dans G-Earth, avec un catalogue de noms de paquets compatible avec le client utilisé.
3. Entrer dans la salle source après le démarrage de l’extension : le snapshot des labels/régions est envoyé à l’entrée.
4. Pour tout conserver, laisser décochées les options `Do not export wired`, `Do not export Furni Variables` et `Do not export wall items`. L’export des valeurs de variables est maintenant activé par défaut pour une nouvelle configuration ; les préférences existantes restent respectées.
5. Utiliser `:ep all` pour exporter la salle ou `:ep` pour sélectionner une zone, puis `:ip` pour importer. `:a` interrompt une opération.

Le mobilier requis doit être disponible dans l’inventaire, la salle ou le BC selon la source choisie. Les contrats, coffres, labels et liens nécessitent les droits correspondants. Une absence de message reconnu, de référence ou de confirmation serveur arrête l’import avec une erreur explicite. Des mobis déjà placés peuvent rester dans la salle après une interruption ; il n’y a pas de transaction globale côté Habbo.

## Mode preview avant import

Un bouton **Aperçu du preset** ouvre un plan isométrique du preset sélectionné. La commande `:preview` (ou `:pp`) ouvre la même fenêtre. Le plan fonctionne sans inventaire chargé ; sans données de dimensions des mobis, les empreintes inconnues sont signalées comme des repères 1×1.

- Zoom avec la molette ou le curseur ; glisser pour déplacer la vue ; **Recentrer** pour revenir au plan complet.
- Hauteurs du preset, orientations et empreintes des mobis ; distinction entre mobilier, WIRED et repères muraux.
- Recherche dans la liste et sélection pour consulter la classe, l'ID, les coordonnées, l'état et les offsets muraux.
- Les remplacements de classes sont appliqués au plan. Les mobis existants réutilisés sont comptés séparément, car leur position reste conservée.

L'option **Aperçu avant import (:ip)**, activée par défaut, ouvre cette fenêtre avant toute préparation ou placement. **Continuer vers l'import** confirme le preset et ses options figés, puis reprend le choix des cases dans Habbo. `:ip x,y` conserve l'origine demandée. **Annuler**, la fermeture de la fenêtre ou `:a` ne lancent aucun import. Un changement de salle, de preset ou d'options invalide l'ancienne confirmation ; un double clic ne peut pas lancer deux imports.

Ce mode est un **plan schématique**, pas un rendu des images originales des mobis dans Habbo. Les positions murales sont indicatives dans le plan ; leurs offsets exacts sont lisibles dans les détails. Les hauteurs affichées sont celles du preset ; le sol de destination peut imposer un décalage lors de l'import.

## Changements principaux

- Six familles de configurations : déclencheurs, effets, conditions, add-ons, sélecteurs et variables ; variantes de test et prototypes reconnues par famille.
- Transport des options, textes, deux sélections de mobilier, sources, références de variables, délais et quantificateurs. Cela couvre notamment pointeurs, classements, Variable FX, API Web, messages/dataframes, initialisation de salle, visibilité, surbrillance et contrats personnalisés.
- Métadonnées modernes des variables, contextes 7 et 8, connecteurs de texte avec valeur de secours, réponses en plusieurs morceaux et identifiants de suppressions sous forme de chaînes.
- Ordre des dépendances de variables, rejet des références non résolues et des cycles ; conservation des références externes identifiées comme telles par le serveur.
- Contrats de paiement, d’échange et de récompense : règles structurées, montants, types de mobi, textes et options ; traduction des types de mobi par nom de classe à l’import et relecture de la définition sauvegardée.
- Coffres : options, capacité configurée, nom, description, accès, apparence, aperçu, activation WIRED et notifications. Aucun contenu ni solde n’est copié ; les extensions de capacité achetées ne sont pas achetées automatiquement.
- Liens WIRED : association à un réseau existant et réception locale, avec validation de disponibilité et relecture.
- Labels et régions : formats typés distincts, texte, présentation, rectangles et décalage des coordonnées de région.
- Propriétés spéciales du mobilier : zones masquées, couleurs, compteurs, occupation et destinations ; valeurs des variables de mobilier par ID, sans confusion entre noms identiques. Les valeurs de variables de salle définies par les boîtes exportées sont également conservées.
- Mobilier mural placé avant les sélections WIRED ; identifiants signés de mur et de sol conservés dans des espaces distincts. Les références internes entre téléporteurs et les ancres de classement suivent la copie.
- Encodage UTF-8 des chaînes réseau pour les accents et emojis ; la sérialisation binaire de liaison à G-Earth reste inchangée.
- Suppression de la double sauvegarde des add-ons, meilleure gestion des reprises et validations ; écriture atomique des presets, contrôle des noms de fichier, gestion des JSON invalides et correction des fonds publicitaires.

Les paramètres WIRED de salle (droits et fuseau horaire) sont enregistrés ; leur application automatique est réservée aux presets qui incluent tout le mobilier de sol et de mur. Un preset de zone conserve ce contexte sans remplacer les paramètres de toute la salle.

## Validation et limites

**1 203 assertions de régression passent**, avec des exemples binaires indépendants pour les six familles et les 188 codes de formulaires recensés, ainsi que les contrats, coffres, labels, régions et paramètres de salle. Les tests couvrent aussi le plan, les snapshots, la confirmation unique, les commandes réelles de l'importer interceptées sans réseau, l'annulation et les contextes obsolètes. Le FXML et le CSS du JAR sont chargés sans fenêtre visible pour vérifier les ressources et les liaisons des contrôles.

L’inventaire comporte **284 classes liées aux WIRED**, dont **207 classes des six familles de configuration**. Le détail se trouve dans `COUVERTURE-WIRED.csv` et `COMPATIBILITE.md`. Un code de formulaire ou une classe reconnue n’est pas une preuve de fonctionnement de chaque scénario de jeu.

**Aucun cycle complet export/import ni scénario WIRED en jeu n’a été validé.** La salle connectée était vide lors de cette vérification. Les prototypes `wf_test_26secret1` et `wf_test_26secret2` n’ont pas de formulaire identifié ni de scénario validé ; leur comportement configurable reste inconnu. Une garantie « tous les bugs corrigés / rien ne manque » ne serait donc pas fondée.

Référence examinée : client Flash français `WIN63-202610201032-107964540`, fichier HabboAir.swf du 20 octobre 2026, catalogue chargé le 26 octobre 2026. Une évolution ultérieure de Habbo peut nécessiter une nouvelle adaptation.

## Reconstruction

JDK 17 ou ultérieur et Python 3 :

```powershell
python scripts/build.py --java-home 'C:/Program Files/Java/jdk-21.0.10' --ui-smoke
python scripts/package.py
```

Le build recompile tous les sources applicatifs et la classe HPacket adaptée. `lib/gpresets-runtime.jar` fournit les autres classes G-Earth et dépendances récupérées ; `lib/dependencies` contient les bibliothèques d’interface nécessaires. Le contrôle UI utilise les natives Windows de l’archive ; sans interface/native compatible, reconstruire sans `--ui-smoke`.

Les rapports se trouvent dans `target/build-report.json`, `target/test-results.txt` et `target/ui-test-results.txt`. `changes.patch` et `target/changed-files.json` permettent de relire les modifications ; `target/baseline-sources.zip` conserve les sources applicatives initiales.


## Nouveautés 1.5.0

- **Share preset** : nom, description et image de la vue actuelle dans un fichier JSON compatible avec les presets existants. Attendez le chargement des images avant de sauvegarder. L'original est conservé.
- **Avant import** : le résumé apparaît dans l'aperçu. **Check availability** détaille les objets au sol et au mur, les variantes d'affiches, l'inventaire et les offres BC. Une offre BC ne garantit ni le quota ni les droits de placement.
- **Check WIRED** : format, doublons, dépendances de variables et catalogue de messages G-Earth. Les références externes sont signalées ; elles peuvent être intentionnelles et doivent être vérifiées dans la salle de destination.
- **Après import** : fenêtre avec positions/états observés, objets non observés et confirmations de configurations. **Save report** exporte un JSON sans écraser un fichier existant. Les réponses serveur tardives peuvent modifier l'état réel après cette photographie.
- **Aperçu** : **Rotate view**, **Max height** et **Selected item only** aident à examiner les empilements. Zoom, panoramique et recentrage restent disponibles. Les images proviennent de l'imager public HabboAssets avec orientation et état demandés.

Le rendu des images reste approximatif (ancrages, variantes absentes, murs et animations). La rotation de l'aperçu ne modifie pas le preset ni les coordonnées d'import. Les images indisponibles restent représentées par leur empreinte. Les contrôles locaux et confirmations de sauvegarde ne valident pas les comportements WIRED en jeu ; un cycle réel export/import reste nécessaire.


### Chargement des images — 1.5.2

Les images sont préchargées dès la sélection d'un preset et partagées entre l'aperçu et **Share preset**. Les mobis déjà décodés réapparaissent dès la première image ; un cache sur disque évite de tout retélécharger au redémarrage. Les variantes d'orientation et d'état sont conservées séparément. Les nouveaux téléchargements se font à quatre en parallèle, sans la pause de 1,1 seconde entre chaque mobi.

Le compteur indique les images chargées et indisponibles. Au tout premier chargement, le délai dépend encore du réseau et du serveur HabboAssets ; ses demandes de temporisation sont respectées. Le cache expire après sept jours et reste limité en taille. Aucune donnée de connexion Habbo n'est envoyée au serveur d'images.
