#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc161-android-stability.py
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -d "$T" app/src/main/java/com/halftonestudio/LatestRequestWorker.java tests/LatestRequestWorkerRC161Test.java
java -ea -cp "$T" com.halftonestudio.LatestRequestWorkerRC161Test
# Java parser is compiled on host even without Android platform jars.
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/StudioActivity.java \
 app/src/main/java/com/halftonestudio/LatestRequestWorker.java
# Comprehensive previous release's compiled host regression suite remains mandatory.
bash tests/run-rc160-lambda-gate.sh
printf '\nPASS RC161 Android stability and inherited release regressions\n'
