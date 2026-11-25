#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
echo '===== RC99: Retired typography and retained style behavioral gate ====='
python3 tests/check-v25465-style-cleanup.py
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx512m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/RemovedTypographyStyleTest.java \
  tests/EightStyleIntegrationTest.java \
  tests/EightStyleVariantsTest.java \
  tests/EightStyleExportTest.java \
  tests/StyleColorsIntegrationTest.java \
  tests/StyleLayerOperationsTest.java \
  tests/NewStylePalettePresetsTest.java
for test in RemovedTypographyStyleTest EightStyleIntegrationTest EightStyleVariantsTest \
    EightStyleExportTest StyleColorsIntegrationTest StyleLayerOperationsTest NewStylePalettePresetsTest; do
  echo "===== $test ====="
  java -Xmx512m -cp "$TMP" "com.halftonestudio.$test"
done
python3 tests/check-v2548-eight-styles.py
python3 tests/check-v2549-style-colors.py
python3 tests/check-v25410-style-navigation.py
python3 tests/check-v25412-palette-ui.py
javac -J-Xmx256m -d "$TMP" tests/ParseJavaSource.java
java -Xmx256m -cp "$TMP" ParseJavaSource \
  app/src/main/java/com/halftonestudio/StudioActivity.java \
  app/src/main/java/com/halftonestudio/VinylStyleSuite.java \
  app/src/main/java/com/halftonestudio/NewStyleColorSlots.java \
  app/src/main/java/com/halftonestudio/NewStylePalettePresets.java
printf '\nPASS RC99 mandatory style-removal and behavioral regression gate\n'
