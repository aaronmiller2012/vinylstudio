#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2519-gate.sh
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" \
  app/src/main/java/com/halftonestudio/SnipMask.java \
  app/src/main/java/com/halftonestudio/AutoPhotoTuner.java \
  app/src/main/java/com/halftonestudio/VinylPlainLanguage.java \
  tests/AutoPhotoTunerTest.java
java -cp "$TMP" AutoPhotoTunerTest
python3 tests/check-v2520-ui.py
printf 'PASS v2.52.0 Auto Photo Tune RC21 complete source gate\n'
