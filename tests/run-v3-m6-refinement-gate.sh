#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
javac -J-Xmx750m -sourcepath app/src/main/java:tests:tests/android-host-stubs -d "$T" tests/M6PrismaticCostParity.java tests/PrismaticWeaveV3Test.java
java -ea -Xmx850m -cp "$T" com.halftonestudio.M6PrismaticCostParity
bash -n transfer/NAS_STAGE_BUILD.sh
bash -n BUILD_ON_NAS.sh
printf 'PASS M6 refinement: pinned draft geometry and NAS shell syntax\n'
