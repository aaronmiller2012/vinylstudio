#!/usr/bin/env bash
# regress.sh <outdir> [only=case1,case2]
# Runs StageOneInstrumentation (14 stage-1 cases + M7 B1/B2/B3/B8 in the perf sandbox), then
# the B6/B7/B9 two-phase foreground-process-death scenario. Exit 0 only if all pass.
. "$(dirname "$0")/devlib.sh"
out=${1:?outdir}; mkdir -p "$out"; only=${2:-}
inst(){ a shell am instrument -w -r "$@" $VINYL_TEST_PKG/com.halftonestudio.StageOneInstrumentation; }
rc=0
if [[ $only != scenario ]]; then
  inst ${only:+-e only ${only#only=}} > "$out/suite.txt"; grep -E "case\.|stage1=" "$out/suite.txt" | sed 's/INSTRUMENTATION_RESULT: //'
  grep -q "stage1=PASS" "$out/suite.txt" || rc=1
fi
if [[ -z $only || $only == scenario ]]; then
  sh_ am force-stop $VINYL_PKG
  inst -e vinylScenario b9set > "$out/b9set.txt"; grep -E "stage1=|workspace=|tool=|style=" "$out/b9set.txt" | sed 's/INSTRUMENTATION_RESULT: //'
  tool=$(sed -n 's/.*: tool=//p' "$out/b9set.txt" | tr -d '\r'); style=$(sed -n 's/.*: style=//p' "$out/b9set.txt" | tr -d '\r')
  sleep 2; [[ -z $(app_pid) ]] && echo "process killed after phase 1 (no onPause/onStop) - OK" || echo "WARNING: process still alive"
  inst -e vinylScenario b9check -e expectTool "$tool" -e expectStyle "$style" > "$out/b9check.txt"
  grep -E "stage1=" "$out/b9check.txt" | sed 's/INSTRUMENTATION_RESULT: //'
  grep -q "stage1=PASS" "$out/b9check.txt" || rc=1
fi
exit $rc
