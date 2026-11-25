#!/usr/bin/env python3
"""Manual photographic quality release gate; no synthetic score claims.

A trained operator assesses identical original photos, Snips, swatches, layers,
physical sizes and export versions. Score 0..5 for recognizable facial features
(eyes, nose, mouth, facial silhouette and hair). Absolute target 3.5/5.
This script cannot measure physical weedability or substitute a scrap cut.
"""
import csv, math, sys
from pathlib import Path
FAMILIES=('Shapes','Lines','Multilayer','Tessellations')
SIZES=(6,11,15)
REQUIRED={(f,str(n)) for f in FAMILIES for n in SIZES}

def assess(csv_file):
    with open(csv_file,encoding='utf8',newline='') as handle:
        reader=csv.DictReader(handle)
        columns={'family','width_in','layers','photo_id','palette_id','snip_id','baseline_version','candidate_version',
            'baseline_feature_score','candidate_feature_score','continuity_pass',
            'swatch_pass','min_cut_pass','operator','notes'}
        if not reader.fieldnames or not columns.issubset(reader.fieldnames):
            raise ValueError('Missing B2 quality assessment columns')
        rows=list(reader)
    seen=set(); failures=[]
    comparable=set()
    for row in rows:
        key=(row['family'],row['width_in'])
        if key in seen:failures.append(f'duplicate {key}')
        seen.add(key)
        if key not in REQUIRED:failures.append(f'unknown family/size {key}')
        if not row['photo_id'] or not row['palette_id'] or not row['snip_id'] or not row['operator'] or not row['layers']:
            failures.append(f'{key}: photo/palette/snip/layers/operator evidence missing');continue
        if not row['baseline_version'] or not row['candidate_version']:
            failures.append(f'{key}: missing compared builds');continue
        try:
            base=float(row['baseline_feature_score'])
            candidate=float(row['candidate_feature_score'])
            count=int(row['layers'])
        except (ValueError,TypeError):
            failures.append(f'{key}: missing numerical score/layers');continue
        if not all(map(math.isfinite,[base,candidate])) or not(0<=base<=5 and 0<=candidate<=5 and count==8):
            failures.append(f'{key}: score/layer outside allowed range');continue
        comparable.add((row['photo_id'].strip(),row['palette_id'].strip(),row['snip_id'].strip(),
            row['baseline_version'].strip(),row['candidate_version'].strip()))
        if candidate<3.5 or candidate+0.01<base:
            failures.append(f'{key}: recognition declined or below 3.5/5');continue
        for col in ('continuity_pass','swatch_pass','min_cut_pass'):
            if row[col].strip().upper()!='PASS':failures.append(f'{key}: {col} not signed off')
    for missing in sorted(REQUIRED-seen):failures.append(f'missing {missing}')
    if len(comparable)>1:failures.append('A/B cases do not share one photo, palette, Snip and build pair')
    if failures:
        for line in failures:print('NOT QUALIFIED:',line)
        return 2
    print('B2 MANUAL PHOTO ASSESSMENTS COMPLETE. Physical cutter qualification remains separate.')
    return 0
if __name__=='__main__':
    if len(sys.argv)!=2:
        print('Usage: python3 qualification/check-portrait-quality.py qualification/portrait-quality-results.csv')
        sys.exit(2)
    try:sys.exit(assess(Path(sys.argv[1])))
    except (OSError,ValueError) as error:
        print('NOT QUALIFIED:',error,file=sys.stderr);sys.exit(2)
