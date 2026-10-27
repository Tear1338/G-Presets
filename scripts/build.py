"""Compile recovered application sources, run regression tests, and package the extension.
The original JAR supplies only third-party runtime classes; every application source is compiled.
"""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = (ROOT / "VERSION").read_text(encoding="utf-8").strip()
if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.-]+)?", VERSION):
    raise SystemExit("Invalid VERSION")
SOURCE_ROOT = ROOT / "src" / "main" / "java"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ui-smoke", action="store_true", help="Load the packaged JavaFX UI without a visible window or hotel connection")
    parser.add_argument("--runtime-jar", type=Path)
    parser.add_argument("--java-home", type=Path)
    parser.add_argument("--dependency-dir", type=Path, action="append", default=[])
    args = parser.parse_args()
    metadata = (SOURCE_ROOT / "extension/GPresets.java").read_text(encoding="utf-8")
    if not re.search(r'Version\s*=\s*"' + re.escape(VERSION) + r'"', metadata):
        parser.error("VERSION and the extension metadata must match")
    candidates = [ROOT / "lib" / "gpresets-runtime.jar", ROOT.parent / "GPresets-v1.3.7-mod-by-th.jar"]
    runtime = args.runtime_jar or next((p for p in candidates if p.is_file()), None)
    if runtime is None or not runtime.is_file():
        parser.error("Supply --runtime-jar with the original GPresets 1.3.7 JAR")
    runtime = runtime.resolve()
    java_home = args.java_home or (Path(os.environ["JAVA_HOME"]) if os.environ.get("JAVA_HOME") else None)
    suffix = ".exe" if os.name == "nt" else ""
    javac = str(java_home / "bin" / ("javac" + suffix)) if java_home else shutil.which("javac")
    if not javac:
        parser.error("A JDK (17 or newer) is required")
    java = str(Path(javac).with_name("java" + suffix))
    deps = [runtime]
    cache = Path.home() / ".m2" / "repository"
    for group, version in [("org/openjfx", "17"), ("org/fxmisc/richtext", "0.10.9"),
                           ("org/reactfx", None), ("org/fxmisc/flowless", None),
                           ("org/fxmisc/wellbehaved", None), ("org/fxmisc/undo", None)]:
        for p in sorted((cache / group).rglob("*.jar")):
            if version is None or version in p.parts:
                deps.append(p)
    for directory in [ROOT / "lib" / "dependencies", *args.dependency_dir]:
        deps.extend(sorted(directory.rglob("*.jar")))
    deps = list(dict.fromkeys(p.resolve() for p in deps))
    classpath = os.pathsep.join(str(p) for p in deps)
    target = ROOT / "target"
    classes = target / "classes"
    if classes.exists():
        # This exact, fixed directory contains only compiler output.
        shutil.rmtree(classes)
    classes.mkdir(parents=True)
    sources = sorted(p for directory in ["extension", "furnidata", "game", "utils"]
                     for p in (SOURCE_ROOT / directory).rglob("*.java"))
    sources.append(SOURCE_ROOT / "gearth" / "protocol" / "HPacket.java")
    compile_args = ["-encoding", "UTF-8", "--release", "17", "-sourcepath", "",
                    "-cp", classpath, "-d", str(classes)] + [str(p) for p in sources]
    argfile = target / "javac.args"
    argfile.write_text("\n".join(json.dumps(a.replace(chr(92), "/")) for a in compile_args), encoding="utf-8")
    compiled = subprocess.run([javac, "@" + str(argfile)], cwd=ROOT, capture_output=True, text=True)
    (target / "compile.log").write_text(compiled.stdout + compiled.stderr, encoding="utf-8")
    print(compiled.stdout + compiled.stderr, end="")
    if compiled.returncode:
        raise SystemExit(compiled.returncode)
    test_sources = sorted((ROOT / "tests").rglob("*.java"))
    if not test_sources:
        raise SystemExit("No regression tests found; build not packaged")
    test_classes = target / "test-classes"
    if test_classes.exists():
        shutil.rmtree(test_classes)
    test_classes.mkdir()
    test_cp = str(classes) + os.pathsep + classpath
    subprocess.run([javac, "-encoding", "UTF-8", "--release", "17", "-sourcepath", "",
                    "-cp", test_cp, "-d", str(test_classes)] + [str(p) for p in test_sources], check=True, cwd=ROOT)
    tested = subprocess.run([java, "-ea", "-cp", str(test_classes) + os.pathsep + test_cp,
                             "tests.RegressionTests"], cwd=ROOT, capture_output=True, text=True)
    print(tested.stdout + tested.stderr, end="")
    (target / "test-results.txt").write_text(tested.stdout + tested.stderr, encoding="utf-8")
    if tested.returncode:
        raise SystemExit(tested.returncode)
    output = ROOT / "dist" / f"GPresets-{VERSION}.jar"
    output.parent.mkdir(exist_ok=True)
    if output.resolve() == runtime:
        raise SystemExit("Output cannot overwrite the runtime JAR")
    entries = {}
    with zipfile.ZipFile(runtime) as base:
        for name in base.namelist():
            if not name.endswith("/") and not any(part.lower() == "presets" for part in Path(name).parts) and not name.upper().endswith((".SF", ".RSA", ".DSA")):
                entries[name] = base.read(name)
    # Bundle the pinned Windows UI runtime so `java -jar` works in G-Earth.
    dependency_dir = ROOT / "lib" / "dependencies"
    for dependency in sorted(dependency_dir.glob("*.jar")):
        with zipfile.ZipFile(dependency) as archive:
            for name in archive.namelist():
                upper = name.upper()
                if name.endswith("/") or name == "module-info.class" or upper.startswith("META-INF/"):
                    continue
                entries.setdefault(name, archive.read(name))
    for p in classes.rglob("*.class"):
        entries[p.relative_to(classes).as_posix()] = p.read_bytes()
    for p in (SOURCE_ROOT / "extension").rglob("*"):
        if p.suffix in (".fxml", ".css"):
            entries[p.relative_to(SOURCE_ROOT).as_posix()] = p.read_bytes()
    entries["META-INF/MANIFEST.MF"] = ("Manifest-Version: 1.0\r\n"
        "Main-Class: extension.GPresetsLauncher\r\n"
        "Build-Jdk: 21\r\n"
        f"Implementation-Version: {VERSION}\r\n\r\n").encode()
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in sorted(entries, key=lambda item: (item != "META-INF/MANIFEST.MF", item)):
            data = entries[name]
            info = zipfile.ZipInfo(name, date_time=(2026, 10, 26, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix(".jar.sha256").write_text(digest + "  " + output.name + "\n", encoding="ascii")
    smoke_output = None
    if args.ui_smoke:
        smoke_cp = os.pathsep.join([str(test_classes), str(output)])
        smoke = subprocess.run([java, "-Dprism.order=sw", "-Djavafx.cachedir=" + str(target / "javafx-cache"),
                                "-cp", smoke_cp, "tests.UiResourceSmoke"], cwd=ROOT, capture_output=True,
                               text=True, timeout=30)
        smoke_output = smoke.stdout + smoke.stderr
        (target / "ui-test-results.txt").write_text(smoke_output, encoding="utf-8")
        print(smoke_output, end="")
        if smoke.returncode:
            raise SystemExit(smoke.returncode)
    report = {"version": VERSION, "runtime_sha256": hashlib.sha256(runtime.read_bytes()).hexdigest(),
              "artifact_sha256": digest, "sources_compiled": len(sources),
              "test_output": tested.stdout, "ui_resource_smoke_tested": args.ui_smoke,
              "ui_test_output": smoke_output, "live_habbo_import_tested": False}
    (target / "build-report.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(f"Built {output} ({output.stat().st_size} bytes)\nSHA-256 {digest}")


if __name__ == "__main__":
    main()
