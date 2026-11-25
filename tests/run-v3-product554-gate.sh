#!/usr/bin/env bash
# Build 554 gate: project format 2, kept cut sheets, weed score, camera policy, wiring.
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
python3 tests/check-gate-manifest.py
python3 tests/check-v3-product554.py
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests -d "$T" tests/V3Product554Test.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -cp "$T" com.halftonestudio.V3Product554Test
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,ProjectManifest,StudioProjectArchiveWriter,StudioProjectLibrary,LastVerifiedExport,WeedDifficulty,CutSheetZipWriter,SharedExportProvider,SharedExportFiles,StudioProductFlows,ProjectGalleryDialog,StudioUi}.java
printf 'PASS 554 product gate\n'
