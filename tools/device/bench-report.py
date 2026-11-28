"""Summarize a bench.sh run: per style median/worst wall time, heap, plus VinylPerf
phase lines (engine/preview/total) attributed between VinylBench begin/end markers."""
import json, os, re, statistics, sys
out = sys.argv[1]
cases = []
p = os.path.join(out, 'results.jsonl')
if os.path.exists(p):
    for line in open(p):
        line = line.strip()
        if line:
            try: cases.append(json.loads(line))
            except ValueError: pass
phases = {}
cur = None
for line in open(os.path.join(out, 'logcat.txt'), errors='replace'):
    m = re.search(r'VinylBench: begin (.+)$', line)
    if m: cur = m.group(1).strip(); phases[cur] = []; continue
    if re.search(r'VinylBench: end ', line): cur = None; continue
    if cur and 'VinylPerf' in line:
        phases[cur].append(line.split('VinylPerf:', 1)[-1].strip())
def num(lines, key):
    vals = []
    for l in lines:
        for m in re.finditer(r'\b%s=(\d+)' % key, l): vals.append(int(m.group(1)))
    return max(vals) if vals else None
groups = {}
for c in cases:
    if c.get('event') != 'case': continue
    k = (c['style'], c['widthIn'], c['photo'])
    tag = '%s w=%s photo=%s trial=%d' % (c['style'], c['widthIn'], c['photo'], c['trial'])
    c['_engine'] = num(phases.get(tag, []), 'engineMs')
    c['_preview'] = num(phases.get(tag, []), 'previewMs')
    groups.setdefault(k, []).append(c)
print('| Style | Width in | Photo | n | ok | wall median ms | wall worst ms | first (switch) ms | engine ms | preview ms | peak heap MB | GC n | layers | cut contours | crc stable |')
print('|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|')
for k, cs in groups.items():
    walls = [c['wallMs'] for c in cs]
    crcs = {c['artworkCrc'] for c in cs if c['trial'] > 0}
    eng = [c['_engine'] for c in cs if c['_engine'] is not None]
    pre = [c['_preview'] for c in cs if c['_preview'] is not None]
    print('| %s | %s | %s | %d | %d | %d | %d | %d | %s | %s | %.0f | %d | %d | %d | %s |' % (
        k[0], k[1], k[2], len(cs), sum(1 for c in cs if c['ok']), statistics.median(walls), max(walls),
        cs[0]['wallMs'], statistics.median(eng) if eng else '-', statistics.median(pre) if pre else '-',
        max(c['peakHeapKb'] for c in cs) / 1024, sum(c['gcCount'] for c in cs), cs[-1]['layers'],
        cs[-1]['cutContours'], 'yes' if len(crcs) <= 1 else 'NO(%d)' % len(crcs)))
imports = [c for c in cases if c.get('event') == 'import']
for c in imports: print('\nimport %s %s first render %d ms' % (c['photo'], c['source'], c['firstRenderMs']))
