#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$T" tests/V3RenderPipelineOptimizationTest.java tests/PaletteReduction529Reference.java tests/ParseJavaSource.java
java -ea -Xmx850m -cp "$T" com.halftonestudio.V3RenderPipelineOptimizationTest
python3 tests/check-v3-organic-preview-rescue.py
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/StudioActivity.java
