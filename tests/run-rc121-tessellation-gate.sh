#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
printf '\n===== RC121 TESSELLATION TILES / NO MOTIFS =====\n'
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/TessellationTilesRC121Test.java tests/TessellationMotifRC120Test.java \
  tests/ProjectSettingsSchemaHostTest.java tests/GoldenGeometryHostTest.java
java -Xmx900m -cp "$TMP" com.halftonestudio.TessellationTilesRC121Test
java -Xmx900m -cp "$TMP" com.halftonestudio.TessellationMotifRC120Test
java -Xmx900m -cp "$TMP" com.halftonestudio.ProjectSettingsSchemaHostTest
java -Xmx900m -cp "$TMP" com.halftonestudio.GoldenGeometryHostTest
python3 tests/check-rc120-tessellation-integration.py
python3 tests/check-v25472-render-schema.py
javac -J-Xmx256m -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
java -Xmx256m -cp "$TMP" com.halftonestudio.JavaSyntaxCheck \
 app/src/main/java/com/halftonestudio/StudioActivity.java \
 app/src/main/java/com/halftonestudio/TessellationMotifEngine.java \
 app/src/main/java/com/halftonestudio/VinylStyleSuite.java
printf '\nPASS RC121 dedicated tessellation cut-geometry and integration gate\n'
