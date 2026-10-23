#!/usr/bin/env python3
"""Package the signed APK, clean source, private signing backup, and SHA-256 sums."""
from pathlib import Path
import hashlib
import os
import shutil
import sys
import zipfile

root = Path(__file__).resolve().parents[1]
destination = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root.parent / "deliverables"
destination.mkdir(parents=True, exist_ok=True)
apk = root / "app/build/outputs/apk/release/app-release.apk"
acceptance = root / "docs/Acceptance.md"
if not apk.exists() or not acceptance.exists():
    raise SystemExit("Build the signed release and write docs/Acceptance.md before packaging.")

shutil.copy2(apk, destination / "EhBridge-1.0.0-universal.apk")
shutil.copy2(root / "README.md", destination / "EhBridge-README.md")
shutil.copy2(acceptance, destination / "EhBridge-Acceptance.md")

excluded_dirs = {"build", ".gradle", ".kotlin", ".idea", ".git", "release-signing", "__pycache__"}
excluded_files = {"local.properties", "signing.properties", "ehviewer_decoded.json"}
source_zip = destination / "EhBridge-1.0.0-source.zip"
with zipfile.ZipFile(source_zip, "w", zipfile.ZIP_DEFLATED) as archive:
    for directory, children, files in os.walk(root):
        children[:] = sorted(name for name in children if name not in excluded_dirs)
        for name in sorted(files):
            if name in excluded_files or name.endswith((".jks", ".p12", ".keystore")):
                continue
            path = Path(directory) / name
            archive.write(path, Path("EhBridge") / path.relative_to(root))

signing_zip = destination / "EhBridge-1.0.0-signing-backup.zip"
with zipfile.ZipFile(signing_zip, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.write(root / "release-signing/ehbridge.p12", "release-signing/ehbridge.p12")
    archive.write(root / "signing.properties", "signing.properties")
    archive.writestr("KEEP-PRIVATE.txt", "Keep this archive private. It contains the release signing key and its password.\nRestore both paths to the project root to build compatible updates.\n请私下保管此签名备份，切勿上传公开仓库。\n")
os.chmod(signing_zip, 0o600)

for path in (source_zip, signing_zip):
    with zipfile.ZipFile(path) as archive:
        if archive.testzip() is not None:
            raise SystemExit(f"Archive integrity failed: {path.name}")

with (destination / "SHA256SUMS.txt").open("w") as output:
    for path in sorted(destination.iterdir()):
        if path.is_file() and path.name != "SHA256SUMS.txt":
            output.write(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n")

for path in sorted(destination.iterdir()):
    if path.is_file():
        print(f"{path.name}: {path.stat().st_size:,} bytes")
