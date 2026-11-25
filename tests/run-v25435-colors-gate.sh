#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
D=app/src/main/java/com/halftonestudio
T="$(mktemp -d)";trap 'rm -rf "$T"' EXIT
printf '\n===== RC69 UNIFIED COLORS AND REGISTRATION RESET =====\n'
python3 tests/check-java-switch-labels.py app/src/main/java
javac -sourcepath app/src/main/java -d "$T" tests/CheckDuplicateJavaMethods.java
java -cp "$T" CheckDuplicateJavaMethods app/src/main/java
python3 tests/check-v25435-colors.py
javac -J-Xmx768m -sourcepath app/src/main/java -d "$T" \
  "$D"/{ColorWorkspacePalette,PaletteOrientation,PhotoPaletteExtractor,SourceColorStencilQuantizer,VinylPalettes}.java \
  tests/{PaletteOrientationRC69Test,PhotoPaletteRC69Test}.java
java -ea -cp "$T" com.halftonestudio.PaletteOrientationRC69Test
java -ea -cp "$T" com.halftonestudio.PhotoPaletteRC69Test
javac -sourcepath app/src/main/java -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource "$D"/{StudioActivity,PhotoPaletteExtractor,PaletteOrientation}.java
printf '\n===== INHERITED RC68 REGISTRATION AND EXPORT =====\n'
bash tests/run-v25434-registration-gate.sh
printf '\nPASS RC69 mandatory source gate\n'
