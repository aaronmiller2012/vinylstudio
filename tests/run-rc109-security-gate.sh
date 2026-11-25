#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC109 mandatory security + compatibility gate =====\n'
bash tests/run-rc107-reliability-gate.sh
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/StageOneSecurityTest.java tests/RC85SecurityTest.java \
  tests/RC109SecurityHostTest.java tests/StudioProjectLibraryTest.java
java -Xmx512m -cp "$TMP" com.halftonestudio.StageOneSecurityTest
java -Xmx512m -cp "$TMP" com.halftonestudio.RC85SecurityTest
java -Xmx512m -cp "$TMP" com.halftonestudio.RC109SecurityHostTest
java -Xmx768m -cp "$TMP" com.halftonestudio.StudioProjectLibraryTest
bash tests/run-v25451-security-gate.sh
python3 tests/check-rc109-security.py
bash -n BUILD_ON_NAS.sh
bash -n tests/run-rc109-security-gate.sh
printf '\nPASS RC109 mandatory security + compatibility gate\n'
