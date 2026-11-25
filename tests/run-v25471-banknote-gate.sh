#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\n===== RC105: Banknote Engraving host geometry and registration =====\n'
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/{BanknoteEngravingHostTest,RemovedTypographyStyleTest}.java
java -Xmx768m -cp "$TMP" com.halftonestudio.BanknoteEngravingHostTest
java -Xmx768m -cp "$TMP" com.halftonestudio.RemovedTypographyStyleTest
python3 tests/check-v25471-banknote.py
printf '\n===== RC104 / RC103 inherited quality gates =====\n'
bash tests/run-v25470-defaults-colors-gate.sh
printf '\nPASS RC105 banknote engraving plus inherited tests\n'
