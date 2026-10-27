"""Package the validated build, sources, dependencies, fixtures and review patch."""
from pathlib import Path
import csv
import difflib
import hashlib
import json
import zipfile

ROOT=Path(__file__).resolve().parents[1]

def main():
    report=json.loads((ROOT/"target/build-report.json").read_text(encoding="utf-8"))
    version=report["version"]
    jar=ROOT/"dist"/f"GPresets-{version}.jar"
    if hashlib.sha256(jar.read_bytes()).hexdigest()!=report["artifact_sha256"]:
        raise SystemExit("JAR differs from the validated build report; rebuild first")
    if "PASS:" not in report["test_output"]:
        raise SystemExit("No successful test report")
    baseline=ROOT/"target/baseline-sources.zip"
    old={}
    if baseline.exists():
        with zipfile.ZipFile(baseline) as z:
            old={name:z.read(name).decode("utf-8-sig",errors="replace") for name in z.namelist()}
    files=sorted(p for folder in ["src/main/java","scripts","tests","docs",".github","validation"] for p in (ROOT/folder).rglob("*") if p.is_file() and "__pycache__" not in p.parts)
    files.append(ROOT/"src/main/java/gearth/protocol/HPacket.java")
    changes=[];patch=[]
    for p in files:
        name=p.relative_to(ROOT).as_posix()
        if p.suffix not in [".java",".fxml",".css"]: continue
        before=old.get(name,"");after=p.read_text(encoding="utf-8-sig")
        if before!=after:
            changes.append(name)
            patch.extend(difflib.unified_diff(before.splitlines(True),after.splitlines(True),fromfile="a/"+name,tofile="b/"+name))
    (ROOT/"changes.patch").write_text("".join(patch),encoding="utf-8")
    (ROOT/"target/changed-files.json").write_text(json.dumps(changes,indent=2),encoding="utf-8")
    # The original dependency's HPacket is retained in lib; include its recovered baseline for review.
    golden=json.loads((ROOT/"tests/fixtures/qx-reference.json").read_text(encoding="utf-8-sig"))
    catalog=json.loads(golden["CATALOG"])
    rows=[]
    import re
    for name in catalog:
        if re.fullmatch(r"wf_(?:(?:proto|ltdproto|test)_)?(trg|cnd|act|xtra|slc|var)(?:_.*)?",name): mode="six-family configuration envelope"
        elif name.startswith("wf_storage_"): mode="dedicated chest preferences/options/notifications"
        elif name.startswith("wf_contract_"): mode="dedicated structured contract rules"
        elif name=="wf_link": mode="existing network association and local reception"
        elif name=="wf_label": mode="typed label/region snapshot and setters"
        elif name=="wf_room_linker": mode="native writable room destination"
        elif name=="wf_screenseparator": mode="writable area-hide properties"
        elif name.startswith("wf_test_26secret"): mode="unpublished prototype: configurable behavior unverified"
        else: mode="placement, state and writable inspected properties where available"
        rows.append({"className":name,"implementation":mode,"live_gameplay_tested":"no"})
    with (ROOT/"COUVERTURE-WIRED.csv").open("w",encoding="utf-8",newline="") as f:
        writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
    files.extend(p for p in (ROOT/"lib").rglob("*") if p.is_file())
    files.extend(ROOT/p for p in ["VERSION","README.md","README.fr.md","ATTRIBUTION.md","CHANGELOG.md","RELEASING.md",".gitignore","COMPATIBILITE.md","COUVERTURE-WIRED.csv","changes.patch","target/build-report.json","target/compile.log","target/test-results.txt","target/changed-files.json"])
    if (ROOT/"target/ui-test-results.txt").exists(): files.append(ROOT/"target/ui-test-results.txt")
    if baseline.exists():files.append(baseline)
    files.extend([jar,jar.with_suffix(".jar.sha256")])
    output=ROOT/"dist"/f"GPresets-{version}-complet.zip"
    prefix=f"GPresets-{version}/"
    with zipfile.ZipFile(output,"w",zipfile.ZIP_DEFLATED) as z:
        for p in sorted(set(files)):
            if any(part.lower() == "presets" for part in p.relative_to(ROOT).parts): continue
            info=zipfile.ZipInfo(prefix+p.relative_to(ROOT).as_posix(),date_time=(2026,10,26,0,0,0))
            info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,p.read_bytes())
    with zipfile.ZipFile(output) as z:
        if z.testzip() is not None: raise SystemExit("Invalid archive")
        if hashlib.sha256(z.read(prefix+jar.relative_to(ROOT).as_posix())).hexdigest()!=report["artifact_sha256"]:raise SystemExit("Packaged JAR mismatch")
    digest=hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix(".zip.sha256").write_text(digest+"  "+output.name+"\n",encoding="ascii")
    print(f"Packaged {output} ({output.stat().st_size} bytes); {len(changes)} changed application/resource files; {len(catalog)} inventory rows")
    print("SHA-256 "+digest)

if __name__=="__main__":main()
