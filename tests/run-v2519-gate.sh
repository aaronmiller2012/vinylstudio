#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash tests/run-v2518-gate.sh
TMP=$(mktemp -d);trap 'rm -rf "$TMP"' EXIT
javac -sourcepath app/src/main/java -d "$TMP" app/src/main/java/com/halftonestudio/SavedVinylPaletteLibrary.java tests/SavedVinylPaletteLibraryTest.java
java -cp "$TMP" SavedVinylPaletteLibraryTest
python3 tests/check-v2519-library-ui.py
printf 'PASS v2.51.9 named palette library and inherited render/geometry checks\n'
