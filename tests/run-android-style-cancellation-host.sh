#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
javac -sourcepath app/src/main/java -d "$TEMP" tests/android-host-stubs/android/graphics/*.java \
    app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,PortraitStructureMap,MazePortraitEngine,SingleLineSwirlEngine,ProceduralPortraitLines}.java \
    tests/AndroidStyleCancellationHostTest.java
java -cp "$TEMP" com.halftonestudio.AndroidStyleCancellationHostTest
