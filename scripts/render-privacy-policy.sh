#!/usr/bin/env bash
# Fill legal/privacy-policy-template.html for publication. Refuses to emit a page that
# still contains any [REPLACE ...] placeholder or the unpublished-draft warning.
# Usage: scripts/render-privacy-policy.sh "Developer Name" "privacy@yourdomain" "2026-11-20" > privacy.html
set -Eeuo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
(( $# == 3 )) || { echo 'usage: render-privacy-policy.sh NAME CONTACT YYYY-MM-DD' >&2; exit 2; }
NAME="$1" CONTACT="$2" DATE="$3" python3 - <<'PY'
import os,re,html,sys
from pathlib import Path
name,contact,date=(os.environ[k].strip() for k in ('NAME','CONTACT','DATE'))
if not name or not contact or not re.fullmatch(r'\d{4}-\d{2}-\d{2}',date):
    sys.exit('name, contact and an ISO date are required')
if any(bad in contact.lower() for bad in ('example.com','placeholder','your-email','todo')):
    sys.exit('contact looks like a placeholder')
s=Path('legal/privacy-policy-template.html').read_text()
s=s.replace('[REPLACE WITH PUBLICATION DATE]',html.escape(date))
s=s.replace('[REPLACE WITH ACTUAL DEVELOPER NAME AND CONTACT METHOD]',html.escape(f'{name}, {contact}'))
s=s.replace('[REPLACE WITH ACTUAL CONTACT]',html.escape(contact))
s=re.sub(r'<p><strong>IMPORTANT: THIS IS AN UNPUBLISHED DRAFT\.</strong>.*?</p>\n?','',s,flags=re.S)
if '[REPLACE' in s or 'UNPUBLISHED DRAFT' in s:
    sys.exit('template still contains placeholders')
sys.stdout.write(s)
PY
