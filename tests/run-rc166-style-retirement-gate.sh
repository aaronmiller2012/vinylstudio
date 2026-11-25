#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
for cmd in java javac python3 bash; do command -v "$cmd" >/dev/null || { echo "Missing $cmd" >&2;exit 12; };done
printf '\n===== RC166 RETIRED STYLE ID / PICKER / THUMBNAIL GATE =====\n'
python3 tests/check-current-release.py
python3 tests/check-version-transition.py
python3 tests/check-rc166-style-retirement.py
TMP="$(mktemp -d)";trap 'rm -rf -- "$TMP"' EXIT
mkdir -p "$TMP/src/com/halftonestudio" "$TMP/classes"
# Real production style registry and generation are compiled, with a non-Android
# stub for an unrelated ContinuousLineModes enum used by the thumbnail mapping.
cat > "$TMP/src/com/halftonestudio/ContinuousLineModes.java" <<'JAVA'
package com.halftonestudio;
public final class ContinuousLineModes {public enum LineStyle{SPIRAL,ADAPTIVE_HILBERT,GOSPER,PEANO,WUNDERLICH,MOORE,COIL}}
JAVA
javac -J-Xmx900m -sourcepath "$TMP/src:app/src/main/java" -d "$TMP/classes" \
  "$TMP/src/com/halftonestudio/ContinuousLineModes.java" tests/StyleRetirementRC166Test.java
java -ea -Xmx900m -cp "$TMP/classes" com.halftonestudio.StyleRetirementRC166Test
bash tests/run-rc152-thumbnail-jvm.sh
# Real tessellation geometry and cut-sheet tests on the remaining layouts.
javac -J-Xmx900m -sourcepath app/src/main/java -d "$TMP/classes" \
  tests/{TessellationTilesRC121Test,TessellationMotifRC120Test,ExtendedTessellationHostTest}.java
java -ea -Xmx900m -cp "$TMP/classes" com.halftonestudio.TessellationTilesRC121Test
java -ea -Xmx900m -cp "$TMP/classes" com.halftonestudio.TessellationMotifRC120Test
java -ea -Xmx900m -cp "$TMP/classes" com.halftonestudio.ExtendedTessellationHostTest
# Camouflage swatches and unrejected nine-layer styles must remain stable.
javac -J-Xmx900m -sourcepath app/src/main/java -d "$TMP/classes" \
  tests/{CamoPaletteDefaultsRC165Test,CraftPatternRC164Test}.java
java -ea -Xmx900m -cp "$TMP/classes" com.halftonestudio.CamoPaletteDefaultsRC165Test
java -ea -Xmx900m -cp "$TMP/classes" com.halftonestudio.CraftPatternRC164Test
javac -d "$TMP/classes" tests/ParseJavaSource.java
java -cp "$TMP/classes" ParseJavaSource app/src/main/java/com/halftonestudio/{StudioActivity,VinylStyleSuite,TessellationMotifEngine,NewStyleVariantPolicy,StudioThumbnailRegistry,StudioThumbnailPalette}.java
printf '\nPASS RC166 style removal, saved-ID safety, thumbnails, tessellation cuts and retained camouflage tests\n'
