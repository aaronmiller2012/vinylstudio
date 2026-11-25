#!/usr/bin/env python3
"""Milestone A physical-cut sign-off: must use *observed* cutter measurements.

Usage: python3 tests/qualify-cutter-import.py qualification/cutter-measurements.csv
No values are ever supplied automatically or inferred from SVG source.
"""
import csv
import math
import sys
from pathlib import Path

TOLERANCE_MM=0.30
CANONICAL={
    'svg_reference_square':25.4,'svg_horizontal_registration':263.4,'svg_vertical_registration':187.2,
    'dxf_reference_square':25.4,'dxf_horizontal_registration':263.4,'dxf_vertical_registration':187.2
}
REQUIRED=tuple(CANONICAL)

def verify(path):
    with open(path,newline='',encoding='utf-8') as f:
        reader=csv.DictReader(f)
        expected_headers={'check','expected_mm','observed_mm','cutter_software','device','notes'}
        if reader.fieldnames is None or not expected_headers.issubset(reader.fieldnames):
            raise ValueError('Measurement CSV must include '+', '.join(sorted(expected_headers)))
        by_name={}
        for row in reader:
            label=(row['check'] or '').strip()
            if not label or label in by_name:raise ValueError('Missing or duplicate test name')
            by_name[label]=row
    if set(REQUIRED)-set(by_name):raise ValueError('Missing required checks: '+', '.join(set(REQUIRED)-set(by_name)))
    failures=[]
    for name in REQUIRED:
        row=by_name[name]
        if not row['observed_mm'] or not row['cutter_software'] or not row['device']:
            failures.append(name+': missing physical measurement/software/device')
            continue
        try:observed=float(row['observed_mm']);nominal=float(row['expected_mm'])
        except ValueError:raise ValueError(name+': non-numeric size')
        if not math.isfinite(observed) or observed<=0 or not math.isfinite(nominal) or nominal<=0:
            raise ValueError(name+': invalid measured dimension')
        # Do not trust a user-editable 'expected_mm' cell as the acceptance
        # standard: otherwise replacing BOTH values with an incorrect scale
        # would manufacture a false physical import PASS.
        if abs(nominal-CANONICAL[name])>0.0001:
            failures.append(f'{name}: expected_mm differs from immutable calibration target {CANONICAL[name]:.3f} mm')
            continue
        if abs(observed-nominal)>TOLERANCE_MM:
            failures.append(f'{name}: measured {observed:.3f} mm versus {nominal:.3f} mm')
    if failures:
        for problem in failures:print('NOT QUALIFIED:',problem)
        return 2
    print('PHYSICAL IMPORT MEASUREMENTS ACCEPTED (±0.30 mm). Separate registration alignment and scrap-cut sign-off are still required.')
    return 0

if __name__=='__main__':
    if len(sys.argv)!=2:
        print('Usage: python3 tests/qualify-cutter-import.py qualification/cutter-measurements.csv',file=sys.stderr)
        sys.exit(2)
    try:sys.exit(verify(Path(sys.argv[1])))
    except (OSError,ValueError) as error:print('NOT QUALIFIED:',error,file=sys.stderr);sys.exit(2)
