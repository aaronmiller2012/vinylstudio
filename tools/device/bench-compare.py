"""bench-compare.py <before_dir> <after_dir> [--md]
Per style: median wall before/after, ratio, peak heap, and whether the rendered artwork
(CRC of the artwork view) and per-layer cut-contour counts are IDENTICAL."""
import json, os, statistics, sys
def load(d):
    g = {}
    for l in open(os.path.join(d, 'results.jsonl')):
        if '"case"' not in l: continue
        r = json.loads(l); g.setdefault((r['style'], str(r['widthIn']), r['photo']), []).append(r)
    return g
a, b = load(sys.argv[1]), load(sys.argv[2])
rows = []
for k in a:
    if k not in b: continue
    A, B = a[k], b[k]
    ma, mb = statistics.median(x['wallMs'] for x in A), statistics.median(x['wallMs'] for x in B)
    same_img = {x['artworkCrc'] for x in A} == {x['artworkCrc'] for x in B}
    same_cut = A[-1]['cutPerLayer'] == B[-1]['cutPerLayer']
    rows.append((k[0], k[1], ma, mb, mb / ma if ma else 1, max(x['peakHeapKb'] for x in A) // 1024,
                 max(x['peakHeapKb'] for x in B) // 1024, same_img, same_cut,
                 sum(x['gcMs'] for x in A) // len(A), sum(x['gcMs'] for x in B) // len(B)))
rows.sort(key=lambda r: -r[2])
print('| Style | in | before ms | after ms | ratio | heap MB before→after | GC ms before→after | artwork identical | cut counts identical |')
print('|---|---|---|---|---|---|---|---|---|')
for r in rows:
    print('| %s | %s | %d | %d | %.2f | %d→%d | %d→%d | %s | %s |' % (r[0], r[1], r[2], r[3], r[4], r[5], r[6], r[9], r[10],
          'yes' if r[7] else '**NO**', 'yes' if r[8] else '**NO**'))
tot_a = sum(r[2] for r in rows); tot_b = sum(r[3] for r in rows)
print('\nTotal median wall: %.1f s → %.1f s (%.0f%%); artwork identical %d/%d; cut counts identical %d/%d' % (
    tot_a / 1000, tot_b / 1000, 100 * (tot_b / tot_a - 1), sum(r[7] for r in rows), len(rows), sum(r[8] for r in rows), len(rows)))
