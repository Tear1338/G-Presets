# Origine et portée des exemples

Référence : client français WIN63-202610201032-107964540, inventaire relevé le 26 octobre 2026.

Les exemples Base64 ont été composés hors réseau par les modèles du QX Scripter local. Leur génération n’a envoyé aucune transaction, achat, modification de coffre ou configuration WIRED à Habbo. Les définitions de variables disponibles ont également été lues dans la salle connectée.

- qx-reference.json : six sauvegardes de famille et catalogue de 284 classes.
- qx-incoming.json : six configurations entrantes avec contextes 7 et 8.
- wired-form-inventory.json : inventaire des 188 définitions et de leurs champs.
- all-form-codecs.json : un exemple entrant et la sauvegarde attendue pour chacun des 188 codes. Les valeurs sont synthétiques et testent le transport, pas les contraintes sémantiques de chaque formulaire.
- auxiliary-codecs.json : contrats et règles imbriquées, options, préférences et notifications des coffres.
- region-codecs.json : mélange label/région, texte UTF-8 et paramètres WIRED de salle.
- writable-builtins.json : métadonnées natives des propriétés de mobilier modifiables.

L’ordre des métadonnées modernes de variable, les types de label/région et le composeur SetFurnitureRegionData ont été vérifiés dans le HabboAir.swf installé. Les fixtures ne remplacent pas un test de gameplay ni une capture d’un cycle complet d’export/import.
