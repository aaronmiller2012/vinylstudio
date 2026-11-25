#!/usr/bin/env bash
# Signed release gate. A local policy draft or a placeholder URL is not acceptable.
set -Eeuo pipefail
: "${VINYL_PRIVACY_URL:?Set VINYL_PRIVACY_URL to a live public HTTPS privacy page}"
: "${VINYL_PRIVACY_CONTACT:?Set VINYL_PRIVACY_CONTACT to the contact address visible on that page}"
command -v curl >/dev/null || { echo 'Play release requires curl to check public policy availability' >&2; exit 12; }
python3 - <<'PY'
import os,ipaddress,urllib.parse
url=os.environ['VINYL_PRIVACY_URL'].strip()
p=urllib.parse.urlparse(url)
if p.scheme!='https' or not p.hostname or p.username or p.password or p.fragment or p.port not in (None,443):
    raise SystemExit('Play release requires a public HTTPS privacy page (no credentials, nonstandard ports or fragments)')
host=p.hostname.lower()
if host=='localhost' or host.endswith(('.local','.internal','.invalid','.test','.example')) or host in ('example.com','www.example.com'):
    raise SystemExit('Play policy URL is a local/placeholder host')
try:
    ipaddress.ip_address(host)
    raise SystemExit('Play policy URL must use a publicly accessible DNS hostname')
except ValueError: pass
contact=os.environ['VINYL_PRIVACY_CONTACT'].strip()
if not contact or any(s in contact.lower() for s in ('your-email','placeholder','example.com','todo','contact-here')):
    raise SystemExit('Provide the actual developer privacy contact printed on the policy page')
PY
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' \
  --connect-timeout 12 --max-time 35 --output "$TMP" "$VINYL_PRIVACY_URL" || {
    echo 'Public privacy URL unreachable. Release AAB blocked.' >&2; exit 31;
}
# This is a sanity check, not a substitute for a human review of the published policy.
grep -Eiq '<html|<!doctype html' "$TMP" || { echo 'Privacy URL did not return an HTML page' >&2; exit 31; }
grep -iq 'VinylStudio' "$TMP" || { echo 'Privacy page must identify VinylStudio' >&2; exit 31; }
grep -iq 'privacy' "$TMP" || { echo 'Privacy policy text not found' >&2; exit 31; }
grep -Fqi -- "$VINYL_PRIVACY_CONTACT" "$TMP" || { echo 'Developer contact was not found in the live policy page' >&2; exit 31; }
echo 'Public HTTPS privacy page reachable; application and contact labels found. Review actual statements and Data safety manually.'
