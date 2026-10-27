"""Print release notes for VERSION from its changelog section."""
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
version = (root / "VERSION").read_text(encoding="utf-8").strip()
changelog = (root / "CHANGELOG.md").read_text(encoding="utf-8")
section = re.search(r"^## " + re.escape(version) + r"\s*\n(.*?)(?=^## |\Z)", changelog, re.M | re.S)
if not section:
    raise SystemExit("Add a CHANGELOG.md section for " + version)
print("Version modifiée de [sirjonasxx/G-Presets](https://github.com/sirjonasxx/G-Presets), par **Tear1338 / Darkz**.\n")
print(section.group(1).strip())
print("\nTéléchargez le **.jar** pour G-Earth, ou l'archive **-complet.zip** pour les sources et dépendances. Les fichiers `.sha256` permettent de contrôler leur intégrité.")
print("\n**Validation :** tests de régression et interface JavaFX vérifiés au build. Aucun cycle complet export/import ni scénario WIRED en jeu n'a été validé. Voir le rapport joint et COMPATIBILITE.md.")
print("\nÀ découvrir : [HabbOne · Habbos](https://www.habbone.fr/habbos).")
