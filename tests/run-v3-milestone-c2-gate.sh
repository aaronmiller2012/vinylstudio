#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf -- "$T"' EXIT
D=app/src/main/java/com/halftonestudio
javac -J-Xmx500m -d "$T" "$D/BoundedBinaryMaskPreview.java" tests/BoundedBinaryMaskPreviewC2Test.java tests/ParseJavaSource.java
java -ea -Xmx300m -cp "$T" com.halftonestudio.BoundedBinaryMaskPreviewC2Test
java -cp "$T" ParseJavaSource "$D/StudioActivity.java" "$D/BoundedBinaryMaskPreview.java"
python3 tests/check-v3-milestone-c2-wiring.py
printf 'PASS v3 Milestone C2 preview allocation and cancellation gate\n'
