#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/halftonestudio
SOURCES=(SnipMask RegistrationMarks CmykWhitePaperBalance CmykPortraitDefaults LinePresenceTracker PortraitBackgroundGuard LineArtEngine StencilSeparationEngine VinylEngine AsciiScreenMotif StyleKit LineCutDiagnostics CancellationCheckpoint)
FILES=();for name in "${SOURCES[@]}";do FILES+=("$D/$name.java");done
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" "${FILES[@]}" tests/ClassicLineV2523Test.java tests/ClassicV224GeometryFixture.java tests/LineV2521HighlightRetentionTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.ClassicLineV2523Test
java -Xmx768m -cp "$TMP" com.halftonestudio.ClassicV224GeometryFixture > "$TMP/old.csv"
diff -u tests/fixtures/v224-golden-sha256.csv "$TMP/old.csv"
java -Dmodern=true -Xmx768m -cp "$TMP" com.halftonestudio.ClassicV224GeometryFixture > "$TMP/new.csv"
diff -u tests/fixtures/v2531-modern-golden-sha256.csv "$TMP/new.csv"
java -Xmx768m -cp "$TMP" com.halftonestudio.LineV2521HighlightRetentionTest
bash tests/run-v2524-vector-gate.sh
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" "$D/SvgNumberWriter.java" tests/SvgNumberWriterV2525Test.java
java -cp "$TMP" com.halftonestudio.SvgNumberWriterV2525Test
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" \
  tests/perf-stubs/android/graphics/{Bitmap,Shader,BitmapShader,Paint,Matrix,Canvas}.java \
  tests/perf-stubs/android/util/Log.java \
  "$D"/{CheckerboardPainter,ProjectPhotoStore,PhotoRevisionStore,CancellationCheckpoint}.java \
  tests/PerformanceInfrastructureV2525Test.java
java -cp "$TMP" com.halftonestudio.PerformanceInfrastructureV2525Test
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" "$D/StudioRenderController.java" tests/RenderCoordinatorV2525Test.java
java -cp "$TMP" com.halftonestudio.RenderCoordinatorV2525Test
python3 tests/check-v2525-release.py
printf 'PASS v2.52.5 classic 48 + modern 48, specialty vectors, performance changes and metadata\n'
