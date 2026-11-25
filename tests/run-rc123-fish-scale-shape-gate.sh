#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
printf '\n===== RC123: FISH SCALE SHAPE & CUT SAFETY =====\n'
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/FishScaleRC123GeometryTest.java tests/FishScaleRC119Test.java
java -Xmx768m -cp "$TMP" com.halftonestudio.FishScaleRC123GeometryTest
java -Xmx768m -cp "$TMP" com.halftonestudio.FishScaleRC119Test
bash tests/run-rc122-extended-tiling-gate.sh
python3 tests/check-rc109-security.py
python3 tests/check-rc115-play.py
bash -n BUILD_ON_NAS.sh
bash -n BUILD_PLAY_BUNDLE_ON_NAS.sh
printf '\nPASS RC123 fish-scale geometry + inherited tilings and security checks\n'
