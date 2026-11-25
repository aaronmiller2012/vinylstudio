#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$TMP" tests/V3DenseCutSheetTest.java
java -Xmx1024m -ea -cp "$TMP" com.halftonestudio.V3DenseCutSheetTest
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$TMP" tests/V3DenseRealShapesTest.java
java -Xmx1024m -ea -cp "$TMP" com.halftonestudio.V3DenseRealShapesTest
