#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC58 gallery and style-picker checks =====\n'
python3 tests/check-v25424-interface.py
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D/StudioActivity.java" "$D/StudioProjectGalleryModel.java"
# The full project-library dependencies compile as pure Java with no Android runtime.
javac -sourcepath app/src/main/java -d "$T" "$D"/{StudioProjectGalleryModel,StudioProjectLibrary,StudioProjectBackup,StudioProjectArchiveWriter,ProjectArchiveReader}.java tests/StudioProjectGalleryModelTest.java
java -ea -cp "$T" com.halftonestudio.StudioProjectGalleryModelTest
printf '\n===== RC57 performance and inherited project/engine tests =====\n'
bash tests/run-v25423-performance-gate.sh
printf '\nPASS RC58 interface polish gate\n'
