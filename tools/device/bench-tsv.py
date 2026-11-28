"""bench-tsv.py <label> <run_dir> [...]  -> append rows to BENCHMARK_RESULTS.tsv format on stdout.
One row per case: label, build note from device.md, style, photo, width, trial, ok, wall, heap, pss, gc."""
import json, os, sys
args = sys.argv[1:]
for i in range(0, len(args), 2):
    label, d = args[i], args[i + 1]
    p = os.path.join(d, 'results.jsonl')
    if not os.path.exists(p): continue
    for l in open(p):
        if '"case"' not in l: continue
        r = json.loads(l)
        print('\t'.join(str(x) for x in (label, r['style'], r['photo'], r['source'], r['widthIn'], r['trial'], r['ok'],
              r['wallMs'], r['peakHeapKb'] // 1024, r['pssKb'] // 1024, r['gcCount'], r['gcMs'], r['layers'],
              r['cutContours'], r['artworkCrc'])))
