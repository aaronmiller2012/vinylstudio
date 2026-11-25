#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '
===== RC102: opaque CMYK line portrait and cuttable continuity =====
'
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx512m -sourcepath app/src/main/java:tests -d "$TMP" \
  app/src/main/java/com/halftonestudio/CmykPortraitDefaults.java \
  tests/LineCoverageRC101Test.java tests/LineCoverage11InchTest.java tests/OpaqueLinePortraitRC102Test.java \
  tests/LineV2504CMYKContinuityTest.java tests/LineV2505PortraitRecoveryTest.java \
  tests/LineV2521HighlightRetentionTest.java tests/LineArtEngineTest.java \
  tests/ClassicLineV2523Test.java tests/LineCutReleaseTest.java \
  tests/SmartHalftonePresetsTest.java tests/ClassicV224GeometryFixture.java
for test in OpaqueLinePortraitRC102Test LineCoverageRC101Test LineCoverage11InchTest LineV2504CMYKContinuityTest \
  LineV2505PortraitRecoveryTest LineV2521HighlightRetentionTest LineArtEngineTest \
  ClassicLineV2523Test LineCutReleaseTest SmartHalftonePresetsTest; do
    printf '
===== %s =====
' "$test"
    java -Xmx512m -cp "$TMP" "com.halftonestudio.$test"
done
printf '
===== Frozen RC100 Classic CMYK geometry parity =====
'
java -Xmx512m -cp "$TMP" com.halftonestudio.ClassicV224GeometryFixture > "$TMP/classic.actual"
diff -u tests/rc100-classic-geometry.expected "$TMP/classic.actual"
printf 'PASS 48 Classic CMYK per-layer geometry signatures unchanged
'
python3 tests/check-v25468-opaque-portrait.py
printf '
===== Inherited RC100 palette + all eight specialty styles =====
'
bash tests/run-v25466-color-organization-gate.sh
printf '
PASS RC102 opaque line portrait, cut geometry and inherited style regression gate
'
