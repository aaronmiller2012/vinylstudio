#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
printf '\n===== RC120 TESSELLATION + RC119 INHERITED RELEASE GATE =====\n'
bash tests/run-rc119-fish-scale-gate.sh
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/TessellationMotifRC120Test.java tests/ProjectSettingsSchemaHostTest.java \
  tests/GoldenGeometryHostTest.java
java -Xmx900m -cp "$TMP" com.halftonestudio.TessellationMotifRC120Test
java -Xmx900m -cp "$TMP" com.halftonestudio.ProjectSettingsSchemaHostTest
java -Xmx900m -cp "$TMP" com.halftonestudio.GoldenGeometryHostTest
python3 tests/check-rc120-tessellation-integration.py
javac -J-Xmx256m -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
java -Xmx256m -cp "$TMP" com.halftonestudio.JavaSyntaxCheck \
  app/src/main/java/com/halftonestudio/StudioActivity.java \
  app/src/main/java/com/halftonestudio/TessellationMotifEngine.java \
  app/src/main/java/com/halftonestudio/VinylStyleSuite.java
printf '\nPASS RC120 tessellation release gate\n'
