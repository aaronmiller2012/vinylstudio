#!/usr/bin/env python3
"""Fail if any release pipeline skips a mandatory gate, or a gate script is unaccounted for."""
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
GATE = re.compile(r'tests/run-v3-[a-z0-9-]+-gate\.sh')


def entries(name):
    out = []
    for line in (root / name).read_text().splitlines():
        line = line.split('\t', 1)[0].strip()
        if line and not line.startswith('#'):
            out.append(line)
    return out


gates = entries('tests/gates.manifest')
retired = entries('tests/gates.retired')
assert len(gates) == len(set(gates)), 'duplicate gate in manifest'
assert not set(gates) & set(retired), 'gate is both mandatory and retired'
for g in gates + retired:
    assert (root / g).is_file(), f'listed gate missing: {g}'
on_disk = {f'tests/{p.name}' for p in (root / 'tests').glob('run-v3-*-gate.sh')}
unaccounted = sorted(on_disk - set(gates) - set(retired))
assert not unaccounted, f'gate scripts neither mandatory nor retired: {unaccounted}'

info = __import__('json').loads((root / 'tests/release-info.json').read_text())
assert gates[0] == info['gate'], 'current release gate must run first'

pipelines = {
    'CI': '.github/workflows/verify.yml',
    'NAS dev build': 'BUILD_ON_NAS.sh',
    'Play bundle build': 'BUILD_PLAY_BUNDLE_ON_NAS.sh',
    'Gradle hostRegression': 'app/build.gradle',
}
for label, path in pipelines.items():
    text = (root / path).read_text()
    if path == 'app/build.gradle':
        text = text.split("tasks.register('hostRegression'", 1)[1].split('\n}', 1)[0]
    present = set(GATE.findall(text))
    skipped = [g for g in gates if g not in present]
    assert not skipped, f'{label} ({path}) skips mandatory gates: {skipped}. Run scripts/sync-gates.py'
    extra = sorted(present - set(gates))
    assert not extra, f'{label} runs gates missing from tests/gates.manifest: {extra}'
print(f'PASS gate manifest: {len(gates)} mandatory gates in CI, NAS, Play and Gradle; {len(retired)} retired with reasons')
