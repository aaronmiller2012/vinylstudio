#!/usr/bin/env bash
# profile.sh <seconds> <outdir>   simpleperf call-graph of the running app (perf build only).
. "$(dirname "$0")/devlib.sh"
sec=${1:-20}; out=${2:-.}; mkdir -p "$out"
sh_ simpleperf record --app "$VINYL_PKG" -e cpu-clock -f 1000 -g --duration "$sec" -o /data/local/tmp/vs-perf.data >/dev/null 2>&1
sh_ simpleperf report -i /data/local/tmp/vs-perf.data --children --sort symbol -n --percent-limit 0.5 > "$out/report-children.txt" 2>/dev/null
sh_ simpleperf report -i /data/local/tmp/vs-perf.data --sort comm,symbol --percent-limit 0.5 > "$out/report-self.txt" 2>/dev/null
a pull /data/local/tmp/vs-perf.data "$out/perf.data" >/dev/null; echo "$out"
