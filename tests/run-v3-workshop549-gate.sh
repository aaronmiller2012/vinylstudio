#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$T" \
  tests/assembly-path-stubs/android/graphics/{Path,PathMeasure}.java \
  "$D"/{AssemblyPathMetrics,ArtworkCutMetrics,ProjectReviewProgress}.java \
  tests/{V3Workshop549Test,ParseJavaSource}.java
java -ea -cp "$T" com.halftonestudio.V3Workshop549Test
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,AssemblyGuideDocument,FootprintClearance,AssemblyPathMetrics,ProjectReviewProgress}.java
python3 tests/check-v3-workshop549-wiring.py
