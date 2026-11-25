#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
D=app/src/main/java/com/halftonestudio
javac -sourcepath app/src/main/java -d "$TMP" "$D/RegionalPortraitDetail.java" "$D/ScaleAwarePhotoSampler.java" tests/ScaleAwarePhotoSamplerV2531Test.java
java -Xmx256m -cp "$TMP" com.halftonestudio.ScaleAwarePhotoSamplerV2531Test
# Existing CMYK signatures and the new modern geometry signatures are strict.
bash tests/run-v2525-gate.sh
# Stencil mask/topological invariant and v2.53 regression tests remain mandatory.
bash tests/run-v2530-stencil-gate.sh
# Layered-cell host behavior runs in the optional extended gate below;
# this gate already runs exact vector parity through the inherited suite.
printf 'PASS v2.53.1 shared sampler, classic compatibility, geometry and legacy gates\n'
