#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
echo '===== RC171 OUTPUT TARGET VALIDATION ====='
python3 tests/check-current-release.py
python3 tests/check-version-transition.py
python3 tests/check-rc171-output-targets.py
T="$(mktemp -d)"; trap 'rm -rf -- "$T"' EXIT
javac -d "$T" app/src/main/java/com/halftonestudio/OutputDeviceProfile.java tests/OutputDeviceProfileTest.java
java -ea -cp "$T" com.halftonestudio.OutputDeviceProfileTest
bash tests/run-rc170-integrity-tone-gate.sh
