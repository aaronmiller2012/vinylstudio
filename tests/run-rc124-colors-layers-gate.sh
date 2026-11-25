#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
TMP="$(mktemp -d)"; trap 'rm -rf -- "$TMP"' EXIT
printf '\n===== RC124 COLORS / LAYERS / SPACING =====\n'
javac -J-Xmx768m -sourcepath app/src/main/java:tests -d "$TMP" tests/RC124PaletteLayersHostTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.RC124PaletteLayersHostTest
python3 tests/check-rc124-ui.py
javac -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
java -cp "$TMP" com.halftonestudio.JavaSyntaxCheck \
 app/src/main/java/com/halftonestudio/StudioActivity.java \
 app/src/main/java/com/halftonestudio/VinylPaletteMapping.java \
 app/src/main/java/com/halftonestudio/PaletteOrientation.java \
 app/src/main/java/com/halftonestudio/VinylLayerDelete.java
bash tests/run-rc123-fish-scale-shape-gate.sh
printf '\nPASS RC124 colors, layer removal, spacing and inherited geometry gate\n'
