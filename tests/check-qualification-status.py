#!/usr/bin/env python3
"""Summarize physical qualification evidence. Never infers or fills measurements.

Exit 0 = every evidence file is complete and passing; 2 = evidence missing or failing.
Informational in development builds; BUILD_PLAY_BUNDLE_ON_NAS.sh fails on it only when
VINYL_REQUIRE_QUALIFICATION=1.
"""
import csv, subprocess, sys
from pathlib import Path
root=Path(__file__).resolve().parents[1]
q=root/'qualification'
problems=[]

def run(cmd):
    r=subprocess.run(cmd,cwd=root,capture_output=True,text=True)
    return r.returncode,(r.stdout+r.stderr).strip()

code,out=run([sys.executable,'tests/qualify-cutter-import.py',str(q/'cutter-measurements.csv')])
print('1. Import scale/registration measurements:', 'PASS' if code==0 else 'NOT QUALIFIED')
if code: problems.append('cutter-measurements.csv: '+out.splitlines()[-1] if out else 'measurement check failed')

code,out=run([sys.executable,'qualification/check-portrait-quality.py',str(q/'portrait-quality-results.csv')])
print('2. Portrait quality sign-off:', 'PASS' if code==0 else 'NOT QUALIFIED')
if code: problems.append('portrait-quality-results.csv incomplete')

REQUIRED=('cutter','cutter_software_version','material','blade','pressure_or_force','style','width_in','layers',
          'sheets_cut','cut_clean','weed_clean','registration_offset_mm','app_build','operator','date')
rows=list(csv.DictReader((q/'physical-cut-log.csv').open(encoding='utf-8',newline='')))
complete=[r for r in rows if all((r.get(k) or '').strip() for k in REQUIRED)]
failed=[]
for r in complete:
    try: offset=abs(float(r['registration_offset_mm']))
    except ValueError: offset=float('inf')
    if r['cut_clean'].upper()!='PASS' or r['weed_clean'].upper()!='PASS' or offset>0.5:
        failed.append(f"{r['cutter']} {r['style']} {r['width_in']}in")
cutters={r['cutter'] for r in complete if f"{r['cutter']} {r['style']} {r['width_in']}in" not in failed}
ok=len(complete)==len(rows) and not failed and {'Cricut','Silhouette'}<=cutters
print(f'3. Physical cut/weed log: {len(complete)}/{len(rows)} rows recorded, {len(failed)} failing', '-> PASS' if ok else '-> NOT QUALIFIED')
if not ok: problems.append(f'physical-cut-log.csv: {len(rows)-len(complete)} rows unrecorded; failing: {failed or "none"} (registration offset limit 0.5 mm)')
if problems:
    print('\nNOT READY for cutter-compatibility claims:'); [print(' -',p) for p in problems]; sys.exit(2)
print('\nREADY: physical evidence complete. Keep it with the release record.')
