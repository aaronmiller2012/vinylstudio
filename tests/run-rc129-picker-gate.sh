#!/usr/bin/env bash
# RC129 bounded, nonrecursive host qualification. Does not skip the key
# RC121 tiling, palette, layer, migration, style, or golden-image checks.
# FULL_TESTS=1 in BUILD_ON_NAS.sh runs additional historical gates separately.
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
CURRENT_STAGE='prerequisites'
TMP=''
cleanup(){ [[ -z "$TMP" ]] || rm -rf -- "$TMP"; }
failed(){
  local rc=$?
  printf '\nFAIL RC129 host gate: stage=%s exit=%s command=%s\n' "$CURRENT_STAGE" "$rc" "$BASH_COMMAND" >&2
  if command -v free >/dev/null 2>&1; then free -h >&2 || true; fi
  exit "$rc"
}
trap failed ERR
trap cleanup EXIT
run(){ CURRENT_STAGE="$1"; shift; printf '\n[RC129] %s\n' "$CURRENT_STAGE"; "$@"; }
TMP="$(mktemp -d)"
for cmd in java javac python3; do command -v "$cmd" >/dev/null || { printf 'Missing: %s\n' "$cmd" >&2; exit 12; }; done

run 'Release metadata' python3 tests/check-current-release.py
run 'RC130 fish scale shape' python3 tests/check-rc130-scale.py
run 'RC132 draft preview publication guard' python3 tests/check-rc132-draft-publication.py
run 'Compact style picker wiring' python3 tests/check-rc129-picker.py
run 'Tessellation dedicated tab direct selection' python3 tests/check-rc135-tessellation-tab.py
run 'RC133 complete thumbnail registry' python3 tests/check-rc133-thumbnails.py
run 'RC133/RC142 75 unique vector artworks / Java compile' bash tests/run-rc133-thumbnail-jvm.sh
run 'Style picker Java local scope guard' python3 tests/check-rc129-picker-scopes.py
run 'RC127 percentage control wiring' python3 tests/check-rc127-percent.py
run 'RC126 UI compatibility wiring' python3 tests/check-rc126-ui.py
run 'RC125 layer/palette wiring' python3 tests/check-rc125-integration.py
run 'RC120 tessellation integration wiring' python3 tests/check-rc120-tessellation-integration.py
run 'Project migration contract' python3 tests/check-v25472-render-schema.py
run 'Play packaging preflight' python3 tests/check-rc115-play.py
run 'Static security baseline' python3 tests/check-rc109-security.py

# Compile host-only tests once. Earlier recursive gates repeatedly compiled
# the same engines and reran all goldens on each legacy stage.
run 'Compile deterministic host tests (bounded JVM)' \
  javac -J-Xmx640m -sourcepath app/src/main/java:tests -d "$TMP" \
  tests/RC127PercentLimitsHostTest.java \
  tests/ProjectSettingsSchemaHostTest.java \
  tests/AutoPhotoTunerTest.java \
  tests/RC125OffsetHostTest.java \
  tests/RC124PaletteLayersHostTest.java \
  tests/TessellationTilesRC121Test.java \
  tests/TessellationMotifRC120Test.java \
  tests/ExtendedTessellationHostTest.java \
  tests/FishScaleRC123GeometryTest.java \
  tests/FishScaleRC131LatticeTest.java \
  tests/FishScaleClearanceRC132Test.java \
  tests/RC132MaskStressTest.java \
  tests/LinocutMinimumRC132Test.java \
  tests/GoldenGeometryHostTest.java

run_java(){
  local stage="$1" main="$2"
  run "$stage" java -Xmx512m -cp "$TMP" "$main"
}
# Golden geometry requires a larger heap; it runs as one isolated JVM.
run '228 deterministic golden geometry cases' java -Xmx900m -cp "$TMP" com.halftonestudio.GoldenGeometryHostTest
run_java 'RC127 percent controls' com.halftonestudio.RC127PercentLimitsHostTest
run_java 'Saved project schema and migration' com.halftonestudio.ProjectSettingsSchemaHostTest
run_java 'Auto-tuned defaults' AutoPhotoTunerTest
run_java 'RC125 offset cell isolation' com.halftonestudio.RC125OffsetHostTest
run_java 'RC124 palette and layer operations' com.halftonestudio.RC124PaletteLayersHostTest
run_java 'RC121 tessellation without motifs' com.halftonestudio.TessellationTilesRC121Test
run_java 'RC120 base tessellations' com.halftonestudio.TessellationMotifRC120Test
run_java 'RC122 expanded tessellations' com.halftonestudio.ExtendedTessellationHostTest
run_java 'RC123 fish scale profiles' com.halftonestudio.FishScaleRC123GeometryTest
run_java 'RC131 interlocking scallop lattice' com.halftonestudio.FishScaleRC131LatticeTest
run_java 'RC132 physical scallop clearance and masked export' com.halftonestudio.FishScaleClearanceRC132Test
run_java 'RC132 Snip mask gap stress fixtures' com.halftonestudio.RC132MaskStressTest
run_java 'RC132 Linocut ribbon minimum widths' com.halftonestudio.LinocutMinimumRC132Test

run 'Compile Java syntax parser' javac -J-Xmx256m -sourcepath tests -d "$TMP" tests/JavaSyntaxCheck.java
run 'Syntax of picker / tessellation / core UI' java -Xmx256m -cp "$TMP" \
  com.halftonestudio.JavaSyntaxCheck \
  app/src/main/java/com/halftonestudio/StudioActivity.java \
  app/src/main/java/com/halftonestudio/StudioStylePreview.java \
  app/src/main/java/com/halftonestudio/StudioThumbnailRegistry.java \
  app/src/main/java/com/halftonestudio/TessellationMotifEngine.java \
  app/src/main/java/com/halftonestudio/VinylStyleSuite.java
printf '\nPASS RC129 bounded host gate: key geometry, project, palette, security and picker tests\n'
