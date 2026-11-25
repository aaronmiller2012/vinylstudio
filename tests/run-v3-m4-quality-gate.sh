#!/usr/bin/env bash
# M4 artwork quality: real engines on real (CC0/public-domain) photographs in tests/m4/photos.
# Colour (D12 palette, ten schemes), geometry (gap contract, shared edges, cut-prep
# reporting), fidelity (Linocut tone mapping) and D8 physical slider floors.
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
printf '\n===== M4 ARTWORK QUALITY (build 559) =====\n'
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/{M4ArtBench,M4SavedCmykReverseTest,M4PhotoPaletteTest,M4ColorSchemesTest,M4GapContractTest,M4SharedEdgeTest,M4CutRepairSummaryTest,M4LinocutToneTest,M4PhysicalSliderFloorTest}.java \
    2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx800m  -cp "$T" com.halftonestudio.M4PhotoPaletteTest
java -ea -Xmx512m  -cp "$T" com.halftonestudio.M4ColorSchemesTest
java -ea -Xmx256m  -cp "$T" com.halftonestudio.M4SavedCmykReverseTest
java -ea -Xmx1g    -cp "$T" com.halftonestudio.M4PhysicalSliderFloorTest
java -ea -Xmx1200m -cp "$T" com.halftonestudio.M4LinocutToneTest
java -ea -Xmx1200m -cp "$T" com.halftonestudio.M4GapContractTest
java -ea -Xmx1500m -cp "$T" com.halftonestudio.M4SharedEdgeTest
java -ea -Xmx1500m -cp "$T" com.halftonestudio.M4CutRepairSummaryTest
printf '\nPASS M4 artwork quality gate\n'
