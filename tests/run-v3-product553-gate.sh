#!/usr/bin/env bash
# Build 553 product-readiness gate (runs first in every pipeline; see tests/gates.manifest).
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
python3 tests/check-gate-manifest.py
python3 tests/check-v3-product553.py
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests -d "$T" \
    tests/{V3Product553LibraryTest,StudioProjectLibraryTest}.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -cp "$T" com.halftonestudio.V3Product553LibraryTest
java -ea -cp "$T" com.halftonestudio.StudioProjectLibraryTest
javac -nowarn -d "$T" "$D"/{BackupReminderPolicy,SharedExportFiles,EngineBuild,PhotoPairToken,PhotoRevisionStore}.java \
    tests/V3Product553PolicyTest.java 2>&1 | { grep -v '^Note:' || true; }
java -ea -cp "$T" com.halftonestudio.V3Product553PolicyTest
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,StudioProjectLibrary,SharedExportProvider,SharedExportFiles,SharedImageIntent,PlatformCompat,PhotoPairToken,ProjectPhotoStore,StartupPhotoLoader,VinylCrashReport,EngineBuild,BackupReminderPolicy,StudioProductFlows,GuideAssembler,AssemblyLayout}.java
printf 'PASS 553 product readiness gate\n'
