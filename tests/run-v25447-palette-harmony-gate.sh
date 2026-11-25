#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC81 EIGHT-SHEET PALETTE HARMONY =====\n'
python3 tests/check-v25447-palette-harmony.py
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" tests/PaletteHarmonyRC81Test.java
java -Xmx512m -cp "$T" com.halftonestudio.PaletteHarmonyRC81Test
printf '\n===== RC80 RELEVANT PALETTE AND ENGINE REGRESSIONS =====\n'
for cls in ColorWorkspacePaletteTest PaletteExpansionTest PaletteOrientationRC69Test NewStylePalettePresetsTest PaletteHueShiftTest; do
  javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" "tests/$cls.java"
  if [[ "$cls" == PaletteHueShiftTest ]]; then java -Xmx512m -cp "$T" "$cls"; else java -Xmx512m -cp "$T" "com.halftonestudio.$cls"; fi
done
python3 tests/check-v25442-palette-tap.py
python3 tests/check-v25435-colors.py
printf '\n===== INHERITED RC80 STAINED-GLASS SAFETY + HISTORICAL CHECKS =====\n'
bash tests/run-v25446-glass-cutfix-gate.sh
printf '\nPASS RC81 PALETTE HARMONY GATE\n'
