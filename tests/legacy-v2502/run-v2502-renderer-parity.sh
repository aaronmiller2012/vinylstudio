#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
EXPECTED_LINES='00eae9f7f49267e2cb29ebaeee6972a68939787b03e122c353c82dddab600a68'
EXPECTED_VINYL='97ca8338432d0e132dc454e76959edfa73144269f65b0f0c10ac257593c58263'
actual_lines="$(sha256sum app/src/main/java/com/halftonestudio/LineArtEngine.java | awk '{print $1}')"
actual_vinyl="$(sha256sum app/src/main/java/com/halftonestudio/VinylEngine.java | awk '{print $1}')"
[[ "$actual_lines" == "$EXPECTED_LINES" ]] || { echo 'FAIL: baseline LineArtEngine modified' >&2; exit 1; }
[[ "$actual_vinyl" == "$EXPECTED_VINYL" ]] || { echo 'FAIL: baseline CMYK/shape engine modified' >&2; exit 1; }
TEMP="$(mktemp -d)";trap 'rm -rf "$TEMP"' EXIT
javac -d "$TEMP" app/src/main/java/com/halftonestudio/{SnipMask,RegistrationMarks,CmykWhitePaperBalance,LineRibbonPolicy,LineArtEngine,StencilSeparationEngine,VinylEngine}.java tests/LineVisualParityFixture.java
java -Xmx512m -cp "$TEMP" com.halftonestudio.LineVisualParityFixture > "$TEMP/actual"
diff -u tests/line-v2500-visual-parity.expected "$TEMP/actual"
echo 'PASS: 144 SVG signature checks (12 line styles × 4 CMYK layers × 3 settings variants) match v2.50.0 exactly'
