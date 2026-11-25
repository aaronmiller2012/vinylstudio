#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests -d "$TMP" \
    tests/V3SliderOverprintRepairTest.java
java -ea -Xmx950m -cp "$TMP" com.halftonestudio.V3SliderOverprintRepairTest
python3 tests/check-v3-slider-overprint-repair.py
