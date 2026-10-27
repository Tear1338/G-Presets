## 1.4.7

- Search and sort local presets, and save a separate JSON copy for sharing.
- Log a partial import report with resolved floor furniture and WIRED confirmations; wall furniture is excluded.
- Check WIRED configuration serialization locally before import; gameplay remains unverified.

## 1.4.6

- Show import phases and processed counts while furniture and WIRED are being handled. Counts do not indicate server confirmation.

## 1.4.5

- Add an on-demand floor-furniture availability check to the preview. Unknown items and wall items are identified separately.

## 1.4.4

- Improve assembled furniture preview and fit loaded images in view.
- Support larger presets with more distinct furniture images.

## 1.4.3

- Preview real furniture images from HabboAssets with schematic fallback when unavailable.
- Prevent personal preset directories from being included in the distribution.

## 1.4.2

- Restore G-Earth-compatible binary encoding for intercepted packet transport to prevent data corruption.

## 1.4.1

- Update the extension description.
- Move the preview setting fully into the visible Settings area.

## 1.4.0

- Set authors to sirjonasxx and 1desirable1337.
- Translate the preset preview to English and fix the clipped settings control.
- Include the standalone Windows UI dependencies and G-Earth manifest compatibility fixes.

## 1.4.0-rc4

- Corrige la detection Java et la lecture du manifeste par G-Earth 1.5.4-beta-29.

## 1.4.0-rc3

- Correction du demarrage : JavaFX et les dependances de l interface sont incluses dans le JAR Windows. Java 17 ou plus reste necessaire.
- Test de l interface avec le seul JAR distribue.

# Historique de cette édition

## 1.4.0-rc2

- Aperçu isométrique avant import, zoom, déplacement, recentrage et inspection des objets.
- Confirmation unique avant préparation/placement, annulation et rejet des contextes obsolètes.
- Commandes `:preview` et `:pp` ; conservation de l'origine explicite de `:ip x,y`.
- Couverture des tests portée à 1 203 assertions et chargement de l'interface JavaFX vérifié.
- Publication de cette édition sur Tear1338/G-Presets, documentation illustrée, crédits et lien HabbOne.
- Workflow de compilation et publication du JAR pour chaque nouvelle version.

## 1.4.0-rc1

- Adaptation des six familles WIRED, variables modernes et références de dépendances.
- Contrats structurés, préférences des coffres, liens, labels, régions et contexte de salle.
- Gestion du mobilier mural, propriétés spéciales, références internes et chaînes UTF-8.
- Validation explicite des données, sauvegarde atomique et fixtures des formats réseau.

Ces préversions n'ont pas encore été validées par un cycle complet export/import dans Habbo. Voir `COMPATIBILITE.md` pour les limites.
