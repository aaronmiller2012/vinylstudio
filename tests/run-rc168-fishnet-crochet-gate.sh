#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
for cmd in java javac python3 bash; do command -v "$cmd" >/dev/null || { echo "Missing $cmd" >&2; exit 12; }; done
printf '\n===== RC168 KNITTED TEXTILE / KNOTTED NET GEOMETRY =====\n'
python3 tests/check-current-release.py
python3 tests/check-version-transition.py
python3 tests/check-rc113-rollback.py
T="$(mktemp -d)";trap 'rm -rf -- "$T"' EXIT
javac -J-Xmx950m -sourcepath app/src/main/java -d "$T" tests/FishnetCrochetRC168Test.java
java -ea -Xmx900m -cp "$T" com.halftonestudio.FishnetCrochetRC168Test
javac -d "$T" tests/ParseJavaSource.java
java -cp "$T" ParseJavaSource app/src/main/java/com/halftonestudio/{NineLayerCraftPatternEngine,PhotoStyleDefaults,StudioStylePreview,StudioActivity,VinylStyleSuite}.java
printf '\n===== RC167 INHERITED CAMOUFLAGE / STYLE RETIREMENT / PALETTE / REGISTRATION =====\n'
bash tests/run-rc167-camo-detail-gate.sh
printf '\nPASS RC168 fishnet and crochet topology, all layer cut geometry, colors, cancel and RC167 inherited host tests\n'
