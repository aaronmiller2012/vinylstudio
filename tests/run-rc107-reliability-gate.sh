#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
printf '\n===== RC107 reliability and golden NAS build gate =====\n'
bash tests/run-v25472-draft-golden-schema-gate.sh
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
    tests/RC107ReliabilityHostTest.java tests/BanknoteEngravingHostTest.java \
    tests/RemovedTypographyStyleTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.RC107ReliabilityHostTest
java -Xmx768m -cp "$TMP" com.halftonestudio.BanknoteEngravingHostTest
java -Xmx768m -cp "$TMP" com.halftonestudio.RemovedTypographyStyleTest
python3 tests/check-rc107-integration.py
javac -J-Xmx256m -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
java -Xmx256m -cp "$TMP" com.halftonestudio.JavaSyntaxCheck \
    app/src/main/java/com/halftonestudio/StudioActivity.java \
    app/src/main/java/com/halftonestudio/NewStyleVariantPolicy.java \
    app/src/main/java/com/halftonestudio/VinylStyleSuite.java \
    app/src/main/java/com/halftonestudio/BanknoteEngravingEngine.java
printf '\nPASS RC107 release candidate gate\n'
