#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx850m -nowarn -sourcepath app/src/main/java:tests -d "$TMP" tests/V3Fix8CutTuneTest.java
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.V3Fix8CutTuneTest
printf 'PASS Fix8 cut tuning gate\n'
