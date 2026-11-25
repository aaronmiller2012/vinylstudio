#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$T" tests/SmartPaletteReductionTest.java tests/TessellationTonalAssociationTest.java tests/OpaqueOverlayPaletteMappingTest.java tests/ParseJavaSource.java
java -ea -Xmx950m -cp "$T" com.halftonestudio.SmartPaletteReductionTest
java -ea -Xmx950m -cp "$T" com.halftonestudio.TessellationTonalAssociationTest
java -ea -Xmx950m -cp "$T" com.halftonestudio.OpaqueOverlayPaletteMappingTest
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/StudioActivity.java
