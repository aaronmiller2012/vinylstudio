#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '
===== RC100: Perceptual photo palettes, physical layer contracts and saved colors =====
'
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx512m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/RC100PaletteOrganizationTest.java tests/PaletteHarmonyRC81Test.java \
  tests/ColorWorkspacePaletteTest.java tests/NewStylePalettePresetsTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.RC100PaletteOrganizationTest
java -Xmx512m -cp "$TMP" com.halftonestudio.PaletteHarmonyRC81Test
java -Xmx512m -cp "$TMP" com.halftonestudio.ColorWorkspacePaletteTest
java -Xmx512m -cp "$TMP" com.halftonestudio.NewStylePalettePresetsTest
python3 tests/check-v25466-colors.py
printf '
===== RC99: Retained styles, legacy project migration, all output colors =====
'
bash tests/run-v25465-style-cleanup-gate.sh
printf '
PASS RC100 mandatory palette + retained style gate
'
