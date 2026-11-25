#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
printf '\n===== M3 EDITOR STATE (build 558) =====\n'
python3 tests/check-m3-editor-state.py
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/{V3M3StyleSelectionTest,V3M3LiveColorPreviewTest,V3M3LibraryIdHandoffTest}.java
java -ea -Xmx512m -cp "$T" com.halftonestudio.V3M3StyleSelectionTest
java -ea -Xmx512m -cp "$T" com.halftonestudio.V3M3LiveColorPreviewTest
java -ea -Xmx512m -cp "$T" com.halftonestudio.V3M3LibraryIdHandoffTest
printf '\nPASS M3 editor state gate\n'
