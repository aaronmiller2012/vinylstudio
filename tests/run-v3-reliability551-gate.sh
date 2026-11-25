#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T=$(mktemp -d);trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java:tests -d "$T" tests/{V3Reliability551Test,ParseJavaSource,V3Beta4ProcessDeathTest}.java
java -ea -cp "$T" com.halftonestudio.V3Reliability551Test
java -ea -cp "$T" com.halftonestudio.V3Beta4ProcessDeathTest
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,StudioProjectLibrary,NativeOriginalPhoto,OwnedSourceStage,StudioLibraryJobs,ProjectTempJanitor}.java
python3 tests/check-v3-reliability551.py
