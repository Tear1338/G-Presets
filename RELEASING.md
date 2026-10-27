# Publier une nouvelle version

1. Modifier `VERSION` et le champ `Version` de `src/main/java/extension/GPresets.java` avec la même valeur, par exemple `1.4.0-rc3`.
2. Mettre à jour `CHANGELOG.md`, la documentation et les assertions annoncées si leur nombre change.
3. Vérifier localement :

   ```powershell
   python scripts/build.py --ui-smoke
   python scripts/package.py
   ```

4. Pousser le commit sur `master`. Le workflow **Build and release** recompile, exécute les tests et crée le tag `v<VERSION>` ainsi qu'une release si ce tag n'existe pas encore. Le bouton **Run workflow** permet une relance manuelle.
5. Vérifier le résultat dans **Actions** et les fichiers de la release.

Chaque release contient :

- `GPresets-<version>.jar` et sa somme SHA-256 ;
- `GPresets-<version>-complet.zip` et sa somme SHA-256 ;
- le rapport `build-report.json` produit par ce build.

Un tag ou une release existants ne sont pas remplacés automatiquement. Une correction publiée utilise une nouvelle version. Une version contenant un suffixe comme `-rc2` ou `-beta1` est marquée comme préversion. Les simples mises à jour du README déclenchent aussi une vérification mais ne republient pas une version existante.

Le workflow s'exécute sur Windows, car les bibliothèques JavaFX embarquées incluent les natives Windows. Il n'atteste pas d'un test en jeu. Les rapports des nouvelles versions sont fournis avec les artefacts de leur propre exécution ; les rapports sous `validation/1.4.0-rc2` restent historiques.


## Conservation des versions

Conserver **toutes les releases et tous les tags déjà publiés**, notamment **v1.5.3**. Publier un nouveau numéro de version sans supprimer, remplacer ni réutiliser une release antérieure. Aucun workflow de nettoyage de releases ne doit être ajouté.
