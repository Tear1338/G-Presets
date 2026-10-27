## 1.6.3

- Fix the observed room-linker import stop: native property ~103 is a linked furniture ID (teleport.target_id), not a room ID. Never send an old absolute source furniture ID as an apartment destination.
- Preserve legacy JSON without rewriting private presets. In partial mode, report and omit unsupported legacy/external pairings while continuing WIRED and final placement. Keep native pairings intact; strict imports still stop on non-portable settings.
- Export explicit local, external and unpaired furniture destinations; remap both endpoints before a portable local pairing is saved and verified. Reject runtime-only resolved IDs supplied by preset JSON.
- Exercise the actual WIRED-setup-to-final-placement continuation, source preservation, pair remapping, legacy/external guards and inspection diagnostics. Live read-only inspection confirmed the property semantics and actual failing target; full private-preset remapping/encoding tested offline. A complete live import with this build is not yet validated.
- In partial mode, report an unconfirmed live clock pulse counter without stopping the remaining import when all other holder properties match. Missing inspection replies and other mismatches still stop; the counter is not counted as confirmed.
- Keep every earlier GitHub release and the 1.6.2 preview fixes.

## 1.6.2

- Align preview sprites using official revisioned furniture asset origins and bitmap bounds instead of one fixed bottom offset. Correct sand, overhanging water, rectangular platforms and tall decorations; retain explicit approximate anchors for unsupported or mismatched variants.
- Align footprint overlays with room cell centers, preserve rectangular object origins through camera quarter-turns, fit complete sprite bounds, keep background layers behind furniture and ignore transparent margins during picking.
- Load geometry on two bounded background workers with shared requests and a 32 MiB disk cache. Parse geometry only, without executing SWF code. Keep existing bounded image caching; wall markers and unsupported animation/layer semantics remain approximate.
- Add geometry parser, shared-symbol, mirrored-origin, camera, height, real JavaFX placement/bounds/selection and cache regression checks. Verify four official furniture asset origins locally; do not distribute diagnostic assets or personal presets.
- Preserve all previous releases and the 1.6.1 partial-import fixes.

## 1.6.1

- Finish remaining import stages when Allow building without all furni is enabled. Report unavailable floor/wall furniture and skip configuration with missing owners, selected furniture, snapshots or preset variable dependencies; continue independent WIRED, auxiliary settings and valid object properties.
- Preserve source presets and the full requested totals in reports. Distinguish completion with omissions from a complete import; include omission details and exact fatal reasons in exported text reports. Strict mode still stops for required missing furniture; transport/save/confirmation failures are not suppressed.
- Apply BC-first, inventory-fallback source selection consistently to floor and wall placements. Include collected floor BC catalog offers in placement and preflight checks.
- Add missing-teleporter, dependent variable/selection, source priority, immutable-source, namespace and real importer completion/report regression checks. Local regression and JavaFX smoke tests; no claim of a complete live Habbo import.
- Preserve all previous GitHub releases, including v1.5.3.

## 1.6.0

- Add Tools: recover individually identified new floor/wall placements from the last import in the same session/room, with per-item type/owner/permission checks, server-state acknowledgement and abort/retry support. Existing/reused furniture and helper tiles are excluded; unconfirmed or unattributable BC placements are excluded. Configuration changes on existing items are not reverted.
- Compare every preset field, identifying additions/removals and WIRED changes; keep floor and wall ID namespaces separate.
- Save append-only local revisions on preset save, deduplicate unchanged content, compare/restore revisions and retain history when moving presets to folders.
- Replace missing ordinary floor/wall furniture with inventory classes for the current session, retain IDs and references, clear incompatible states and protect WIRED/configured furniture and poster variants.
- Add independent name/description/image privacy switches and a review of the exact shared JSON before local export. Gameplay text, links and variables remain visible for review.
- Choose import origin in the preview and highlight out-of-floor/overlapping room footprints. This is an advisory 2D check, not a physics/stacking or wall-placement simulation.
- Preserve v1.5.3 and every existing/future GitHub release. No release pruning workflow.
- Extend regression/UI checks for the new tools. Live Habbo gameplay remains untested.

## 1.5.3

- Match the supplied G-Earth dark theme reference using its original bundled stylesheet: charcoal panels, dark controls, light text, blue tabs and cyan accents.
- Apply the same palette to the main extension, preview, sharing and report windows; keep existing image caching and import behavior.
- Refresh the README and technical guide for the current features and remove obsolete 1.4.0 instructions.

## 1.5.2

- Replace serialized image fetching and its 1.1-second per-image delay with four concurrent background downloads.
- Share one image cache between preview and sharing windows; deduplicate concurrent requests, furniture directions and states.
- Preload images after preset selection and reuse decoded images on the first frame when reopening a window.
- Keep a seven-day disk cache across restarts, bounded to 128 MiB / 2048 files, and a decoded memory cache bounded to 64 MiB. Repair corrupt cache entries and honor server Retry-After backoff.
- Show loaded/unavailable image counts and coalesce image redraws; closing a window leaves cache warming without updating the closed view.
- Verify parallel downloads, deduplication, persistent/offline cache behavior and first-frame warm rendering. Cold-cache latency still depends on the image provider.

## 1.5.1

- Use black text on light backgrounds throughout the extension, preview, sharing dialogs and reports, including when G-Earth uses a dark theme.
- Keep selected list/table rows, text selections, input prompts and log text readable; use a light preview canvas and button.

## 1.5.0

- Share a backward-compatible preset JSON containing a name, description and embedded PNG preview; show shared images when reopening a preset.
- Check floor and wall inventory availability before import, including poster variants, selected source and unknown BC catalog state. Catalog offers do not guarantee quota or room rights.
- Export detailed preflight and import reports. Compare observed floor/wall positions, directions and states; report missing observations, unknown wall heights and WIRED acknowledgements separately.
- Record confirmation counts for contracts, chests, links, labels, room settings and object variables.
- Diagnose duplicate IDs, WIRED variable cycles, external references, unsupported payloads and missing G-Earth message catalog entries. Block invalid local configurations before placement.
- Add preview camera rotation, height filtering, selected-item isolation, sprite hit selection, wall images and corrected height projection.
- Test metadata/image round trips, geometry, availability source rules, diagnostics and report comparisons. Full gameplay and pixel-exact preview alignment remain unverified.

## 1.4.9

- Pause and resume import between operations; abort if the room changes while paused.

## 1.4.8

- Organize presets into one-level folders without overwriting existing presets.

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
