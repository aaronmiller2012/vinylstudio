#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx900m -sourcepath app/src/main/java:tests/android-host-stubs -d "$TMP" \
  tests/android-host-stubs/android/graphics/{Bitmap,Canvas,Paint,Path}.java \
  tests/TessellationReferenceAndroidTest.java \
  tests/TessellationHighDetailHostTest.java \
  tests/TessellationSecondReferenceAndroidTest.java
java -ea -Xmx700m -cp "$TMP" com.halftonestudio.TessellationReferenceAndroidTest
java -ea -Xmx700m -cp "$TMP" com.halftonestudio.TessellationHighDetailHostTest
java -ea -Xmx700m -cp "$TMP" com.halftonestudio.TessellationSecondReferenceAndroidTest
python3 tests/check-tessellation-reference-integration.py
bash tests/run-tessellation-retirement-gate.sh
