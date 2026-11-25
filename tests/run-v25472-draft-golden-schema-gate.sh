#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC106: draft, schema, and 162-case golden regression =====\n'
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/RenderDraftPolicyHostTest.java \
  tests/ProjectSettingsSchemaHostTest.java \
  tests/GoldenGeometryHostTest.java
java -Xmx900m -cp "$TMP" com.halftonestudio.RenderDraftPolicyHostTest
java -Xmx900m -cp "$TMP" com.halftonestudio.ProjectSettingsSchemaHostTest
java -Xmx900m -cp "$TMP" com.halftonestudio.GoldenGeometryHostTest
python3 tests/check-v25472-render-schema.py
javac -J-Xmx256m -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
java -Xmx256m -cp "$TMP" com.halftonestudio.JavaSyntaxCheck \
  app/src/main/java/com/halftonestudio/StudioActivity.java \
  app/src/main/java/com/halftonestudio/RenderDraftPolicy.java \
  app/src/main/java/com/halftonestudio/ProjectSettingsSchema.java \
  app/src/main/java/com/halftonestudio/ProjectSettingsMigration.java
printf '\n===== RC105 inherited Banknote engine and historical style-ID checks =====\n'
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/BanknoteEngravingHostTest.java tests/RemovedTypographyStyleTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.BanknoteEngravingHostTest
java -Xmx768m -cp "$TMP" com.halftonestudio.RemovedTypographyStyleTest
python3 tests/check-v25471-banknote.py
# The full RC105 / RC103 line-continuity and legacy render suite remains
# available under FULL_TESTS=1 in BUILD_ON_NAS.sh.
printf '\nPASS RC106 preview, schema and golden NAS release gate\n'
