#!/usr/bin/env bash
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
python3 tests/check-rc142-style-cleanup.py
python3 tests/check-rc133-thumbnails.py
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT
compile_java(){
  if command -v javac >/dev/null 2>&1; then javac -J-Xmx700m "$@"
  else java -Xmx700m -m jdk.compiler/com.sun.tools.javac.Main "$@"
  fi
}
compile_java -sourcepath app/src/main/java:tests -d "$TEMP" \
  app/src/main/java/com/halftonestudio/StyleKit.java \
  app/src/main/java/com/halftonestudio/TopographicLayersEngine.java \
  app/src/main/java/com/halftonestudio/ExpandedPortraitStylesEngine.java \
  tests/ExpandedPortraitStylesHostTest.java
java -Xmx768m -cp "$TEMP" com.halftonestudio.ExpandedPortraitStylesHostTest
compile_java -sourcepath app/src/main/java:tests -d "$TEMP" tests/RemovedTypographyStyleTest.java
java -Xmx768m -cp "$TEMP" com.halftonestudio.RemovedTypographyStyleTest
compile_java -sourcepath tests -d "$TEMP" tests/JavaSyntaxCheck.java
java -cp "$TEMP" com.halftonestudio.JavaSyntaxCheck \
  app/src/main/java/com/halftonestudio/ExpandedPortraitStylesEngine.java \
  app/src/main/java/com/halftonestudio/VinylStyleSuite.java \
  app/src/main/java/com/halftonestudio/StudioActivity.java \
  app/src/main/java/com/halftonestudio/StudioStylePreview.java \
  app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java \
  app/src/main/java/com/halftonestudio/NewStyleColorSlots.java \
  app/src/main/java/com/halftonestudio/NewStyleVariantPolicy.java \
  app/src/main/java/com/halftonestudio/PaletteStylePolicy.java
bash tests/run-rc133-thumbnail-jvm.sh
bash tests/run-rc140-assembly-guide-gate.sh
printf '\nPASS RC142 reviewed style-cleanup release gate\n'
