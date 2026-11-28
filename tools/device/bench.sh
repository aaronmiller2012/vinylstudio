#!/usr/bin/env bash
# bench.sh <outdir> [instrument -e args...]
# Example: bench.sh out/base -e vinylBench all -e photos astronaut.png -e widths 6 -e trials 2
# Requires the perf APK + perf androidTest APK installed (install.sh). Records device
# provenance, logcat (VinylPerf/VinylBench/VinylNewStyles/AndroidRuntime), instrument
# output, results.jsonl and screenshots into <outdir>. Never clears app data.
. "$(dirname "$0")/devlib.sh"
out=${1:?outdir}; shift; mkdir -p "$out"
here=$(cd "$(dirname "$0")" && pwd); repo=$(cd "$here/../.." && pwd)
bdir=/sdcard/Android/data/$VINYL_PKG/files/bench
sh_ mkdir -p $bdir
for f in "$repo"/tests/m4/photos/*.{png,jpg}; do
  sh_ "[ -f $bdir/$(basename $f) ]" || a push "$f" $bdir/ >/dev/null
done
sh_ rm -f $bdir/results.jsonl
"$here/devinfo.sh" > "$out/device.md"
sh_ am force-stop $VINYL_PKG; sleep 1
a logcat -c
adb -s "$DEV" logcat -v threadtime VinylPerf:D VinylBench:I VinylNewStyles:I VinylMem:I AndroidRuntime:E ActivityManager:W '*:S' > "$out/logcat.txt" 2>/dev/null </dev/null &
lp=$!
start=$(date +%s)
a shell am instrument -w -r "$@" $VINYL_TEST_PKG/com.halftonestudio.StageOneInstrumentation > "$out/instrument.txt"; rc=$?
kill $lp 2>/dev/null
echo "elapsed $(( $(date +%s)-start )) s, instrument rc=$rc" | tee -a "$out/device.md"
dev_alive || { echo "CONNECTION_LOST: results incomplete (not an app crash)" | tee -a "$out/device.md"; exit $EX_CONN; }
a pull $bdir/results.jsonl "$out/" >/dev/null 2>&1
a pull $bdir/inventory.tsv "$out/" >/dev/null 2>&1
mkdir -p "$out/shots"; for f in $(sh_ "ls $bdir/ | grep '^shot_'" | tr -d '\r'); do adb -s "$DEV" pull "$bdir/$f" "$out/shots/" >/dev/null 2>&1 && sh_ rm -f "$bdir/$f"; done
grep -E "FATAL|crash" "$out/logcat.txt" | head -5
python3 -I "$here/bench-report.py" "$out" > "$out/summary.md" && cat "$out/summary.md" | head -60
