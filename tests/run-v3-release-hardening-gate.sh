#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"; trap 'rm -rf -- "$TEMP"' EXIT
python3 tests/test-v3-release-archive-contract.py
python3 tests/check-v3-release-consistency.py
javac -J-Xmx820m -sourcepath app/src/main/java:tests -d "$TEMP" tests/V3ReleaseHardeningTest.java
java -Xmx800m -ea -cp "$TEMP" com.halftonestudio.V3ReleaseHardeningTest
