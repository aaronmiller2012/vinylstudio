#!/usr/bin/env python3
"""Summarize a real `adb dumpsys gfxinfo ... framestats` capture; no estimates."""
import csv
import math
import re
import sys
from pathlib import Path


def percentile(values, pct):
    if not values:
        return 0.0
    values = sorted(values)
    rank = (len(values) - 1) * (pct / 100.0)
    before = int(math.floor(rank))
    after = int(math.ceil(rank))
    return values[before] + (values[after] - values[before]) * (rank - before)


def frame_durations(text):
    lines = text.splitlines()
    results = []
    for number, line in enumerate(lines):
        if not line.startswith('Flags,IntendedVsync,'):
            continue
        header = next(csv.reader([line]))
        try:
            flags = header.index('Flags')
            vsync = header.index('IntendedVsync')
            completed = header.index('FrameCompleted')
        except ValueError:
            continue
        for frame in lines[number+1:]:
            if frame.startswith('---PROFILEDATA---') or frame.startswith('Flags,'):
                break
            try:
                values = next(csv.reader([frame]))
                if len(values) <= max(flags, vsync, completed) or int(values[flags]) != 0:
                    continue
                elapsed = int(values[completed]) - int(values[vsync])
                if 0 < elapsed < 60_000_000_000:
                    results.append(elapsed / 1_000_000)
            except (ValueError, IndexError):
                continue
    return results


def summarize(frames, memory):
    values = frame_durations(frames)
    report = []
    if values:
        report.append(f'Frame records: {len(values)}')
        report.append('Frame duration (ms): median={:.1f}, p90={:.1f}, p95={:.1f}, max={:.1f}'.format(
            percentile(values, 50), percentile(values, 90), percentile(values, 95), max(values)))
        for threshold in (16.67, 33.33, 50.0, 700.0):
            over = sum(v > threshold for v in values)
            report.append(f'Frames > {threshold:g} ms: {over}/{len(values)} ({100*over/len(values):.1f}%)')
    else:
        report.append('No FrameCompleted data found. Confirm framestats was captured while the app was active.')
    pss = re.search(r'^\s*TOTAL PSS:\s*([\d,]+)', memory, re.MULTILINE)
    if pss:
        report.append(f'Process total PSS: {int(pss.group(1).replace(",", ""))/1024:.1f} MiB')
    else:
        report.append('Process total PSS: unavailable in this capture')
    return '\n'.join(report)


def render_timings(text):
    """Completed worker passes only, never treat a draft as final coverage."""
    result = {}
    for line in text.splitlines():
        key = re.search(r'\bstyleKey=(\S+)', line)
        draft = re.search(r'\bdraft=(true|false)', line)
        if not key or not draft:
            continue
        values = {k: int(v) for k, v in re.findall(r'\b(\w+Ms)=(\d+)\b', line)}
        if 'totalMs' in values:
            total = values['totalMs']
        elif 'prepareMs' in values and 'engineMs' in values and ('canvasMs' in values or 'previewMs' in values):
            total = values['prepareMs'] + values['engineMs'] + values.get('canvasMs', values.get('previewMs', 0))
        else:
            continue
        result.setdefault((key.group(1), draft.group(1)), []).append(total)
    return result


def style_report(timings, registry, destination):
    fields = ['style_key', 'name', 'final_count', 'final_p50_ms', 'final_p95_ms', 'draft_count', 'draft_p50_ms', 'status']
    covered = 0
    with destination.open('w', newline='') as out:
        writer = csv.writer(out); writer.writerow(fields)
        for row in registry:
            key, name = row['style_key'], row['name']
            final, draft = timings.get((key, 'false'), []), timings.get((key, 'true'), [])
            covered += bool(final)
            writer.writerow([key, name, len(final), round(percentile(final,50),1) if final else '',
                round(percentile(final,95),1) if final else '', len(draft), round(percentile(draft,50),1) if draft else '',
                'MEASURED' if final else 'NOT RUN'])
    return f'Final worker timing coverage: {covered}/{len(registry)} registered cases (not device qualification).'

if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit('Usage: python3 tests/profile_summary.py /path/to/VinylPerf-capture')
    path = Path(sys.argv[1])
    print(summarize((path/'frames.txt').read_text(errors='replace'),
                    (path/'memory.txt').read_text(errors='replace')))
    log = path/'render-timings.txt'
    registry = Path(__file__).resolve().parent.parent/'qualification'/'render-profile-registry.csv'
    if log.exists() and registry.exists():
        rows = list(csv.DictReader(registry.open()))
        text = log.read_text(errors='replace')
        marker = path/'session-marker.txt'
        if marker.exists():
            value = marker.read_text().strip()
            position = text.rfind(value)
            if position < 0:
                print('Session marker not found: render history may be incomplete; no current-session timing claims.')
                text = ''
            else:
                text = text[position+len(value):]
        else:
            print('No session marker supplied: results may include earlier runs.')
        print(style_report(render_timings(text), rows, path/'style-timings.csv'))
