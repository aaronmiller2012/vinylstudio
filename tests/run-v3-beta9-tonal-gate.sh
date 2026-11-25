#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests/android-host-stubs -d "$TMP" \
  tests/android-host-stubs/android/graphics/{Bitmap,Canvas,Paint,Path}.java \
  tests/TessellationTonalAssociationTest.java
java -ea -Xmx700m -cp "$TMP" com.halftonestudio.TessellationTonalAssociationTest
