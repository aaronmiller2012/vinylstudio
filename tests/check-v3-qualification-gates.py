#!/usr/bin/env python3
"""Adversarial QA gate tests using synthetic CSV rows only.

Never writes fabricated measurements to a real qualification worksheet.
"""
import csv
import subprocess
import sys
import tempfile
from pathlib import Path

root=Path(__file__).resolve().parents[1]
template=root/'qualification/portrait-quality-results.csv'
rows=list(csv.DictReader(template.open(newline='',encoding='utf8')))
assert len(rows)==12, 'Expected one case per family at 6/11/15 inches'
script=root/'qualification/check-portrait-quality.py'

def check(items,success):
    with tempfile.TemporaryDirectory() as t:
        output=Path(t)/'synthetic-test-only.csv'
        with output.open('w',newline='',encoding='utf8') as stream:
            writer=csv.DictWriter(stream,fieldnames=items[0].keys())
            writer.writeheader();writer.writerows(items)
        result=subprocess.run([sys.executable,str(script),str(output)],capture_output=True,text=True)
        assert (result.returncode==0)==success,(result.returncode,result.stdout,result.stderr)

check(rows,False)
valid=[dict(item) for item in rows]
for row in valid:
    row.update(photo_id='synthetic-fixture',palette_id='synthetic-eight-swatch-fixture',
               snip_id='fixture-alpha',baseline_version='TESTBASE',candidate_version='TESTNEXT',
               baseline_feature_score='3.5',candidate_feature_score='4.0',
               continuity_pass='PASS',swatch_pass='PASS',min_cut_pass='PASS',operator='fixture-only')
check(valid,True)
for field,bad in [('photo_id','different-photo'),('palette_id','different-palette'),
                  ('snip_id','different-snip'),('candidate_version','different-build'),
                  ('layers','7'),('min_cut_pass','FAIL')]:
    broken=[dict(item) for item in valid]
    broken[3][field]=bad
    check(broken,False)
print('PASS B portrait A/B qualification: blank fails; consistent synthetic fixture passes; identity, layer and cut mismatches fail')

# A: the import checker must reject a fabricated nominal target even when the
# 'observed' measurement has been altered to match it.
source=root/'qualification/cutter-measurements.csv'
checks=list(csv.DictReader(source.open(newline='',encoding='utf8')))
import_script=root/'tests/qualify-cutter-import.py'

def cut_check(items,success):
    with tempfile.TemporaryDirectory() as t:
        output=Path(t)/'synthetic-test-only.csv'
        with output.open('w',newline='',encoding='utf8') as stream:
            writer=csv.DictWriter(stream,fieldnames=items[0].keys())
            writer.writeheader();writer.writerows(items)
        result=subprocess.run([sys.executable,str(import_script),str(output)],capture_output=True,text=True)
        assert (result.returncode==0)==success,(result.returncode,result.stdout,result.stderr)

cut_check(checks,False)
known=[dict(item) for item in checks]
for row in known:
    row.update(observed_mm=row['expected_mm'],cutter_software='SYNTHETIC_TEST_ONLY',device='SYNTHETIC_TEST_ONLY')
cut_check(known,True)
for field,bad in [('expected_mm','26.5'),('observed_mm','27.0'),('device','')]:
    changed=[dict(item) for item in known]
    changed[0][field]=bad
    if field=='expected_mm': changed[0]['observed_mm']=bad
    cut_check(changed,False)
print('PASS A cutter calibration: blank fails; canonical synthetic fixture passes; tampered nominal target and out-of-tolerance measurements fail')

