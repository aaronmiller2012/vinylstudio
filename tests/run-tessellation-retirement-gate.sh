#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"
trap 'rm -rf -- "$TEMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests/android-host-stubs -d "$TEMP" \
  tests/android-host-stubs/android/graphics/{Bitmap,Canvas,Paint,Path}.java \
  tests/TessellationBeta8RetirementTest.java \
  tests/StyleRetirementRC166Test.java
java -Xmx512m -ea -cp "$TEMP" com.halftonestudio.TessellationBeta8RetirementTest
java -Xmx512m -ea -cp "$TEMP" com.halftonestudio.StyleRetirementRC166Test
python3 tests/check-beta8-tessellation-retirement.py
