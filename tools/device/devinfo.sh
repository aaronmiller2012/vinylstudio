#!/usr/bin/env bash
# Device environment snapshot (markdown) for benchmark provenance.
. "$(dirname "$0")/devlib.sh"
p(){ sh_ getprop "$1" | tr -d '\r'; }
echo "| Item | Value |"; echo "|---|---|"
echo "| Serial | $DEV |"
echo "| Model | $(p ro.product.model) / $(p ro.product.device) |"
echo "| Android | $(p ro.build.version.release) (SDK $(p ro.build.version.sdk)), patch $(p ro.build.version.security_patch) |"
echo "| ABI | $(p ro.product.cpu.abilist) |"
echo "| Cores | $(sh_ nproc | tr -d '\r') |"
echo "| CPU max kHz | $(sh_ 'cat /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq' | tr -d '\r' | sort | uniq -c | xargs) |"
echo "| RAM | $(sh_ 'grep -E "MemTotal|MemAvailable" /proc/meminfo' | tr -s ' ' | tr -d '\r' | paste -sd';') |"
echo "| Heap growth limit / max | $(p dalvik.vm.heapgrowthlimit) / $(p dalvik.vm.heapsize) |"
echo "| /data free | $(sh_ df -h /data | tail -1 | awk '{print $4" of "$2}') |"
echo "| Display | $(sh_ wm size | tail -1 | tr -d '\r' | sed 's/.*: //') @ $(sh_ wm density | tail -1 | tr -d '\r' | sed 's/.*: //') dpi |"
echo "| AP temperature | $(thermal) C, thermal status $(sh_ dumpsys thermalservice | grep -m1 'Thermal Status' | tr -d '\r' | sed 's/.*: //') |"
for pk in com.halftonestudio com.halftonestudio.perf; do
  v=$(sh_ dumpsys package $pk | grep -m1 versionName | tr -d '\r ' ); [[ -z $v ]] && continue
  c=$(sh_ dumpsys package dexopt | grep -A2 "\[$pk\]" | grep -m1 status | tr -d '\r' | xargs)
  f=$(sh_ dumpsys package $pk | grep -m1 ' flags=' | tr -d '\r' | xargs)
  echo "| $pk | $v; $c; $f |"
done
pid=$(app_pid); [[ -n $pid ]] && echo "| $VINYL_PKG PSS | $(sh_ dumpsys meminfo $VINYL_PKG | grep -m1 'TOTAL PSS' | tr -s ' ' | tr -d '\r') |"
echo "| Top background CPU | $(sh_ 'top -b -n1 -m 4' | sed -n '8,11p' | awk '{print $9"% "$12}' | tr -d '\r' | paste -sd';') |"
echo "| WebView sandbox RSS | $(sh_ 'ps -A -o RSS,NAME' | awk '/webview:sandboxed/{print $1" kB"}' | tr -d '\r' | paste -sd' ') |"
