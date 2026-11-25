#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/.."
T="$(mktemp -d)"; trap 'rm -rf -- "$T"' EXIT
mkdir -p "$T/bin" "$T/phone/storage/downloads" "$T/local" "$T/remote/Downloads"
cat > "$T/bin/ssh" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
while (( $# )); do
  case "$1" in
   -o|-S) shift 2;;
   -O) exit 0;;
   -*) shift;;
   *) shift;break;;
  esac
done
export HOME="$TEST_REMOTE_HOME"
if (( $# == 0 )); then exit 0; fi
if [[ "$1" == bash ]]; then exec "$@"; fi
if [[ "$1" == 'command -v rsync >/dev/null' ]]; then exit 1; fi
exec bash -c "$1"
MOCK
cat > "$T/bin/scp" <<'MOCK'
#!/usr/bin/env bash
set -euo pipefail
while (( $# > 2 )); do
  case "$1" in
   -o|-S) shift 2;;
   -p|-q|-r) shift;;
   *) break;;
  esac
done
src="$1";dst="$2"
case "$src" in *:Downloads/*) src="$TEST_REMOTE_HOME/Downloads/${src#*:Downloads/}";; esac
case "$dst" in *:Downloads/*) dst="$TEST_REMOTE_HOME/Downloads/${dst#*:Downloads/}";; esac
cp -- "$src" "$dst"
printf '%s\n' 'scp' >> "$TEST_SCP_CALLS"
if [[ -n "${TEST_TAMPER_FILE:-}" && -f "$TEST_TAMPER_FILE" ]]; then
    printf X >> "$dst"
    rm -f -- "$TEST_TAMPER_FILE"
fi
if [[ "${TEST_TAMPER_ALWAYS:-0}" == 1 ]]; then printf X >> "$dst"; fi
MOCK
chmod +x "$T/bin/ssh" "$T/bin/scp"
export TEST_REMOTE_HOME="$T/remote" TEST_SCP_CALLS="$T/scp.count"
export HOME="$T/phone" NAS_HOST='test@nas' PHONE_DOWNLOADS="$T/phone/storage/downloads"
export VINYL_LOG_DIR="$T/phone/logs"
export PATH="$T/bin:$PATH"
# Ensure rsync fallback exercises scp in this hermetic sandbox.
# A fake remote has no rsync by design.
printf 'source code bytes\n' > "$T/local/source.zip"
(cd "$T/local" && sha256sum source.zip > SOURCE_SHA256.txt)
SHA="$(sha256sum "$T/local/source.zip" | awk '{print $1}')"
printf 'old verified APK\n' > "$T/phone/storage/downloads/test.apk"
printf 'fresh APK contents\n' > "$T/remote/Downloads/test.apk"
# Corrupt first upload, then verify automatic retry and cached second attempt.
touch "$T/tamper"
export TEST_TAMPER_FILE="$T/tamper"
LIB="$PWD/transfer/PHONE_COMMON.sh"
bash -c 'source "$1"; vinyl_init mock-upload; vinyl_send_source "$2" source.zip "$3"; vinyl_send_source "$2" source.zip "$3"' \
    -- "$LIB" "$T/local" "$SHA" > "$T/upload.log" 2>&1
cmp "$T/remote/Downloads/source.zip" "$T/local/source.zip" || { echo 'bad upload digest' >&2;exit 1; }
[[ "$(wc -l < "$TEST_SCP_CALLS")" -eq 2 ]] || { echo 'source retry/cache failed' >&2;cat "$T/upload.log";exit 1; }
# First APK receipt corrupted; second succeeds. Publication happens after verification.
touch "$T/tamper"
bash -c 'source "$1"; vinyl_init mock-receive; vinyl_receive_apk test.apk' \
    -- "$LIB" > "$T/receive.log" 2>&1
cmp "$T/phone/storage/downloads/test.apk" "$T/remote/Downloads/test.apk"
# All receipts corrupted: refuse to replace last valid APK.
printf 'old reliable APK\n' > "$T/phone/storage/downloads/test.apk"
export TEST_TAMPER_ALWAYS=1
if bash -c 'source "$1"; vinyl_init mock-failure; vinyl_receive_apk test.apk' \
    -- "$LIB" > "$T/failure.log" 2>&1; then
    echo 'expected failed transfer to stop' >&2;exit 1
fi
[[ "$(cat "$T/phone/storage/downloads/test.apk")" == 'old reliable APK' ]] || { echo 'rollback lost prior APK' >&2;exit 1; }
[[ -f "$T/phone/logs/$(ls -1 "$T/phone/logs" | tail -1)" ]] || { echo 'missing diagnostic log' >&2;exit 1; }
[[ "$(find "$T/phone/storage/downloads" -name '*.part' | wc -l)" -eq 0 ]] || { echo 'partial APK leaked' >&2;exit 1; }
echo 'PASS RC114 simulated source corruption/retry, cache skip, APK retry, failure rollback and logs'
