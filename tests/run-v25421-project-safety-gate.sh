#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-v25421-project-safety.py
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" app/src/main/java/com/halftonestudio/{StudioProjectLibrary,StudioProjectBackup,StudioProjectArchiveWriter,ProjectArchiveReader,ProjectManifest,EngineBuild,SharedExportFiles}.java tests/StudioProjectLibraryTest.java
java -ea -cp "$T" com.halftonestudio.StudioProjectLibraryTest
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/StudioActivity.java app/src/main/java/com/halftonestudio/StudioProjectLibrary.java app/src/main/java/com/halftonestudio/StudioProjectBackup.java
bash tests/run-v25419-cleanup-gate.sh
printf 'PASS v2.54.21 RC55 project safety and inherited geometry/style gates\n'
