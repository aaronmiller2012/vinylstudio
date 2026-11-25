#!/usr/bin/env bash
# M5 Linocut relief polarity: carved solid shadows, halo-free balanced spacing, feature emphasis and
# final cut geometry on real photos (tests/m4/photos) at 3-11 in, plus the M4 Linocut tone contract
# and the RC132 ribbon-endpoint minimum (engine defaults keep the M4 behaviour).
set -Eeuo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT
printf '\n===== M5 LINOCUT RELIEF =====\n'
javac -J-Xmx700m -nowarn -sourcepath app/src/main/java:tests/android-host-stubs -d "$T" \
    tests/{M4ArtBench,M5LinocutReliefTest,M4LinocutToneTest,LinocutMinimumRC132Test}.java \
    2>&1 | { grep -v '^Note:' || true; }
java -ea -Xmx512m  -cp "$T" com.halftonestudio.LinocutMinimumRC132Test
java -ea -Xmx1200m -Djava.awt.headless=true -cp "$T" com.halftonestudio.M4LinocutToneTest
java -ea -Xmx1200m -Djava.awt.headless=true -cp "$T" com.halftonestudio.M5LinocutReliefTest
printf '\nPASS M5 Linocut relief gate\n'
