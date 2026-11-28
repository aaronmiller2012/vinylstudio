#!/usr/bin/env bash
# profile-style.sh <style-key> <outdir> [widthIn] [seconds]
# Runs one benchmark render of <style-key> (after a warm-up render of the same style) and
# records an on-device simpleperf call graph (cpu-clock, 1 kHz) of the app meanwhile.
. "$(dirname "$0")/devlib.sh"
key=${1:?style}; out=${2:?outdir}; w=${3:-6}; sec=${4:-30}; mkdir -p "$out"
here=$(cd "$(dirname "$0")" && pwd)
"$here/bench.sh" "$out/bench" -e vinylBench "$key" -e photos astronaut.png -e widths "$w" -e trials 2 -e shots 0 -e timeoutS 300 ${BENCH_EXTRA:-} > "$out/bench.log" 2>&1 &
bp=$!
# wait for trial 1 (caches cleared, same style) to begin, then record
for i in $(seq 600); do grep -q "begin $key w=$w photo=astronaut.png trial=1" "$out/bench/logcat.txt" 2>/dev/null && break; sleep 0.5; done
sh_ simpleperf record --app "$VINYL_PKG" -e cpu-clock -f 1000 -g --duration "$sec" -o /data/local/tmp/vs-perf.data >/dev/null 2>&1
wait $bp
sh_ "simpleperf report -i /data/local/tmp/vs-perf.data --children --sort symbol -n --percent-limit 0.7" > "$out/children.txt" 2>/dev/null
sh_ "simpleperf report -i /data/local/tmp/vs-perf.data --sort comm --percent-limit 0.5" > "$out/threads.txt" 2>/dev/null
sh_ "simpleperf report -i /data/local/tmp/vs-perf.data --sort symbol -n --percent-limit 0.5" > "$out/self.txt" 2>/dev/null
grep -E "wallMs" "$out/bench/logcat.txt" | grep -o "trial=[01] ok=[a-z]* wallMs=[0-9]*"
echo "$out"
