#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
python3 tests/check-v3-release-consistency.py
javac -J-Xmx384m -d "$TMP" \
  app/src/main/java/com/halftonestudio/{CutSvgCompatibility,OutputDeviceProfile,LoadValueConstraints,ProjectTempJanitor}.java \
  tests/V3Fix7HardeningTest.java
java -ea -Xmx192m -cp "$TMP" com.halftonestudio.V3Fix7HardeningTest
printf 'PASS Fix7 current release and host hardening gate\n'
