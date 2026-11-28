#!/usr/bin/env bash
# Shared helpers for VinylStudio device engineering (source this file).
# Device selection: $VINYL_DEVICE, else the first mDNS _adb-tls-connect service
# (wireless debugging). A lost connection is reported as CONNECTION_LOST (exit 75),
# never as an application crash.
set -o pipefail
VINYL_PKG=${VINYL_PKG:-com.halftonestudio.perf}
VINYL_TEST_PKG=${VINYL_TEST_PKG:-$VINYL_PKG.test}
EX_CONN=75

dev_resolve(){
  if [[ -n ${VINYL_DEVICE:-} ]]; then echo "$VINYL_DEVICE"; return; fi
  local s; s=$(adb devices | awk '$2=="device"{print $1}' | grep -E ':[0-9]+$' | grep -v ':5555$' | head -1)
  if [[ -z $s ]]; then
    s=$(adb mdns services 2>/dev/null | awk '$2 ~ /_adb-tls-connect/{print $3}' | head -1)
    [[ -n $s ]] && adb connect "$s" >/dev/null 2>&1
  fi
  [[ -n $s ]] || { echo "CONNECTION_LOST: no wireless adb device (pair via Developer options > Wireless debugging)" >&2; return $EX_CONN; }
  echo "$s"
}
DEV=$(dev_resolve) || exit $EX_CONN
export ANDROID_SERIAL=$DEV

dev_alive(){ [[ $(adb -s "$DEV" get-state 2>/dev/null) == device ]]; }
# Run adb; distinguish transport failure from command failure.
a(){
  adb -s "$DEV" "$@"; local rc=$?
  if (( rc != 0 )) && ! dev_alive; then
    echo "CONNECTION_LOST during: adb $*" >&2
    adb connect "$DEV" >/dev/null 2>&1; sleep 2
    dev_alive || exit $EX_CONN
  fi
  return $rc
}
sh_(){ a shell "$@"; }
app_pid(){ sh_ pidof "$VINYL_PKG" 2>/dev/null | tr -d '\r'; }
# Returns 0 if the app process died since $1 (pid) - an actual crash/kill, not a link drop.
app_died(){ dev_alive || return 2; [[ -z $(app_pid) || $(app_pid) != "$1" ]]; }
thermal(){ sh_ dumpsys thermalservice | awk -F'[=,]' '/Temperature\{mValue=.*mName=AP/{print $2; exit}'; }
wait_cool(){ # wait until AP temperature <= $1 (default 38 C), max 10 min
  local lim=${1:-38} t i; for i in $(seq 120); do t=$(thermal); awk -v t="$t" -v l="$lim" 'BEGIN{exit !(t<=l)}' && return 0; sleep 5; done; return 1; }
