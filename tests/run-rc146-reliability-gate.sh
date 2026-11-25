#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
S=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java -d "$TMP" \
    "$S/StudioProjectLibrary.java" "$S/StudioProjectArchiveWriter.java" \
    "$S/ProjectArchiveReader.java" "$S/JsonSyntaxVerifier.java" \
    "$S/StagedCutZip.java" "$S/StagedDocumentFile.java" "$S/StudioRenderController.java" \
    tests/RC146ReliabilityHostTest.java
java -ea -cp "$TMP" com.halftonestudio.RC146ReliabilityHostTest
javac -sourcepath app/src/main/java -d "$TMP" tests/ParseJavaSource.java
java -cp "$TMP" ParseJavaSource "$S/StudioActivity.java" "$S/StudioProjectLibrary.java" "$S/StagedCutZip.java" "$S/StagedDocumentFile.java" "$S/JsonSyntaxVerifier.java"
python3 tests/check-rc146-wiring.py
bash tests/run-rc145-registration-stability-gate.sh
printf '\nPASS RC146 consolidated host and inherited registration gates\n'
