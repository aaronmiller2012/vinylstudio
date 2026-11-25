#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf -- "$TMP"' EXIT
javac -J-Xmx700m -sourcepath app/src/main/java:tests -d "$TMP" tests/V3MilestoneABCompletionHostTest.java \
  app/src/main/java/com/halftonestudio/ArtworkCutMetrics.java
java -Xmx900m -ea -cp "$TMP" com.halftonestudio.V3MilestoneABCompletionHostTest
python3 tests/check-v3-ab-completion-wiring.py
# Compile the real Android PDF class against narrowly scoped Canvas/PDF stubs.
# This checks Java syntax and page/worksheet flow, NOT PDF rendering quality.
STUBS=tests/b16-stubs
javac -J-Xmx700m -cp "$TMP" -d "$TMP" \
  "$STUBS"/android/graphics/{Canvas,Color,RectF,Paint,Typeface}.java \
  "$STUBS"/android/graphics/pdf/PdfDocument.java \
  "$STUBS"/com/halftonestudio/ArtworkDocumentExport.java \
  app/src/main/java/com/halftonestudio/{AssemblyGuideDocument,AssemblyGuidePlan,AssemblyMaterialPlan,ExportColorLabels,PhysicalSheetParity,ManufacturerVinylPalettes}.java \
  tests/V3MilestoneABAssemblyPdfHostTest.java
java -Xmx192m -ea -cp "$TMP" com.halftonestudio.V3MilestoneABAssemblyPdfHostTest
