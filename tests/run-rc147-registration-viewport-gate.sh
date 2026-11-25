#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc147-registration-viewport.py
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
S=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java -d "$TMP" \
  "$S/RegistrationViewportMath.java" "$S/RegistrationPreviewGeometry.java" \
  "$S/RegistrationMarks.java" tests/RegistrationViewportRC147Test.java
java -ea -cp "$TMP" com.halftonestudio.RegistrationViewportRC147Test
bash tests/run-rc146-reliability-gate.sh
printf '\nPASS RC147 screen transform / stable registration viewport / inherited RC146 reliability\n'
