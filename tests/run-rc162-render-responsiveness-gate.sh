#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
python3 tests/check-rc162-render-responsiveness.py
TMP="$(mktemp -d)";trap 'rm -rf "$TMP"' EXIT
SRC=app/src/main/java/com/halftonestudio
javac -J-Xmx900m -sourcepath app/src/main/java -d "$TMP" \
  "$SRC"/{StyleKit,TopographicLayersEngine,PatternPortraitTrioEngine,PrismaticDiagonalPortraitEngine,RenderDraftPolicy,CancellationCheckpoint,StudioRenderController,RenderWorkBudget,VinylStyleSuite}.java \
  tests/{RenderResponsivenessRC162Test,RenderDraftPolicyHostTest,StudioRenderControllerTest}.java
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.RenderResponsivenessRC162Test
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.RenderDraftPolicyHostTest
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.StudioRenderControllerTest
javac -J-Xmx900m -sourcepath app/src/main/java -d "$TMP" tests/UnderlayRecolorRC162Test.java
java -ea -Xmx900m -cp "$TMP" com.halftonestudio.UnderlayRecolorRC162Test
# Check Android-source syntax of changed files, including Activity and the major engines.
javac -d "$TMP" tests/ParseJavaSource.java
java -cp "$TMP" ParseJavaSource "$SRC"/{StudioActivity,LowPolyDelaunayEngine,LinocutEngine,PatternPortraitTrioEngine,PrismaticDiagonalPortraitEngine,BanknoteEngravingEngine,FallenConfettiEngine,InterlockingChainmailEngine,TessellationEngine,VinylEngine}.java
bash tests/run-rc161-android-stability-gate.sh
printf '\nPASS RC162 render responsiveness and inherited release gates\n'
