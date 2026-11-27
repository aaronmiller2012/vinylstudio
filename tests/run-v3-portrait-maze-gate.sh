#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
javac --release 17 -d "$T" "$D/PerfectPortraitMaze.java" tests/PerfectPortraitMazeTest.java
java -ea -Xmx512m -cp "$T" com.halftonestudio.PerfectPortraitMazeTest
