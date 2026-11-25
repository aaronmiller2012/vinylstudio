#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/{ExpandedPortraitStylesHostTest,V3SelectiveRidgePolishTest,V3SelectiveRidgeSvgCutTest}.java
java -ea -Xmx1000m -cp "$TMP" com.halftonestudio.ExpandedPortraitStylesHostTest
java -ea -Xmx1000m -cp "$TMP" com.halftonestudio.V3SelectiveRidgePolishTest
java -ea -Xmx1200m -cp "$TMP" com.halftonestudio.V3SelectiveRidgeSvgCutTest
printf '\nPASS selective organic ridge contour polish and export safety\n'
