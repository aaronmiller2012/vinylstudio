#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
D="app/src/main/java/com/halftonestudio"
SOURCES=(SnipMask RegistrationMarks CmykWhitePaperBalance CmykPortraitDefaults LinePresenceTracker PortraitBackgroundGuard LineArtEngine StencilSeparationEngine VinylEngine AsciiScreenMotif StyleKit LineCutDiagnostics)
FILES=()
for name in "${SOURCES[@]}"; do FILES+=("$D/$name.java"); done
javac -sourcepath app/src/main/java app/src/main/java/com/halftonestudio/{RegionalPortraitDetail,ScaleAwarePhotoSampler,CancellationCheckpoint,CmykLineVisibility,ParallelGeometry}.java -d "$TMP" "${FILES[@]}" tests/ClassicLineV2523Test.java tests/ClassicV224GeometryFixture.java tests/LineV2521HighlightRetentionTest.java
java -Xmx768m -cp "$TMP" com.halftonestudio.ClassicLineV2523Test
java -Xmx768m -cp "$TMP" com.halftonestudio.ClassicV224GeometryFixture > "$TMP/v224-actual.csv"
diff -u tests/fixtures/v224-golden-sha256.csv "$TMP/v224-actual.csv"
echo 'PASS exact v2.24 geometry: 12 line families x 4 CMYK channels = 48 matches'
java -Dmodern=true -Xmx768m -cp "$TMP" com.halftonestudio.ClassicV224GeometryFixture > "$TMP/v2522-modern-actual.csv"
diff -u tests/fixtures/v2531-modern-golden-sha256.csv "$TMP/v2522-modern-actual.csv"
echo 'PASS modern fallback geometry: 48 matches to v2.52.2'
java -Xmx768m -cp "$TMP" com.halftonestudio.LineV2521HighlightRetentionTest
python3 tests/check-v2523-release.py
echo 'PASS v2.52.3 complete mandatory release gate'
