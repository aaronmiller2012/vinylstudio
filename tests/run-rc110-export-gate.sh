#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC110: PNG/PDF artwork document + security gate =====\n'
bash tests/run-rc109-security-gate.sh
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx256m -sourcepath app/src/main/java:tests -d "$TMP" tests/ArtworkExportLayoutHostTest.java
java -cp "$TMP" com.halftonestudio.ArtworkExportLayoutHostTest
javac -J-Xmx256m -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
java -cp "$TMP" com.halftonestudio.JavaSyntaxCheck \
  app/src/main/java/com/halftonestudio/StudioActivity.java \
  app/src/main/java/com/halftonestudio/ArtworkExportLayout.java \
  app/src/main/java/com/halftonestudio/ArtworkDocumentExport.java
python3 tests/check-rc110-export.py
printf '\nPASS RC110 PNG/PDF artwork export release gate\n'
