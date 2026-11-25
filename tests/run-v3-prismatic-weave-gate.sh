#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -J-Xmx850m -sourcepath app/src/main/java:tests -d "$T" tests/V3RibbonLocalColorTest.java tests/PrismaticWeaveV3Test.java tests/PrismaticDiagonalRC149Test.java tests/PrismaticToneRC150Test.java tests/ParseJavaSource.java
java -ea -Xmx950m -cp "$T" com.halftonestudio.V3RibbonLocalColorTest
java -ea -Xmx950m -cp "$T" com.halftonestudio.PrismaticDiagonalRC149Test
java -ea -Xmx950m -cp "$T" com.halftonestudio.PrismaticToneRC150Test
java -ea -Xmx950m -cp "$T" com.halftonestudio.PrismaticWeaveV3Test
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/StudioActivity.java app/src/main/java/com/halftonestudio/StudioStylePreview.java app/src/main/java/com/halftonestudio/PrismaticDiagonalPortraitEngine.java
python3 tests/check-v3-prismatic-weave.py
