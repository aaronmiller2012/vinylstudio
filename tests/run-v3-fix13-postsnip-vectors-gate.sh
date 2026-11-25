#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$TMP" \
    tests/V3CropSnipCutParityTest.java tests/V3LateSnipClipTest.java
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.V3LateSnipClipTest
printf 'PASS Fix13: full-photo ribbon generation then cutter-safe Snip segment clipping\n'
