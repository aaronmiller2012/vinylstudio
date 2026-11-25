#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
D=app/src/main/java/com/halftonestudio
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$T" tests/{V3Stability550Test,ParseJavaSource}.java
java -ea -cp "$T" com.halftonestudio.V3Stability550Test
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,SpecialtyVectorCache,TessellationEngine,PortraitMapCache,BoundedEditHistory,LifecycleEditorSnapshot}.java
python3 tests/check-v3-stability550-wiring.py

javac -sourcepath app/src/main/java:tests -d "$T" tests/{V3ProjectSaveRepairTest,StudioProjectLibraryTest,StudioProjectSessionStateTest}.java
java -ea -cp "$T" com.halftonestudio.V3ProjectSaveRepairTest
java -ea -cp "$T" com.halftonestudio.StudioProjectLibraryTest
java -ea -cp "$T" com.halftonestudio.StudioProjectSessionStateTest
python3 tests/check-v3-project-save-repair.py
