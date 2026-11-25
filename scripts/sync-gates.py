#!/usr/bin/env python3
"""Make every release pipeline run every gate in tests/gates.manifest.

Pipelines keep their hand-ordered gate lines (many inherited checks assert specific
gate names and relative order inside these files). This script only:
  * regenerates the Gradle hostRegression chain in manifest order, and
  * (re)writes one marked block of gates that a pipeline is otherwise missing.
Run it after editing tests/gates.manifest. tests/check-gate-manifest.py enforces the result.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BEGIN = '# >>> synced from tests/gates.manifest by scripts/sync-gates.py (do not edit by hand)'
END = '# <<< end synced gates'
GATE = re.compile(r'tests/run-v3-[a-z0-9-]+-gate\.sh')


def manifest():
    gates = []
    for line in (ROOT / 'tests/gates.manifest').read_text().splitlines():
        line = line.strip()
        if line and not line.startswith('#'):
            if line in gates:
                sys.exit(f'duplicate manifest entry: {line}')
            gates.append(line)
    return gates


def strip_block(text, begin, end):
    pattern = re.compile(r'\n?[ \t]*' + re.escape(begin) + r'.*?' + re.escape(end) + r'[^\n]*', re.S)
    return pattern.sub('', text)


def missing(text, gates):
    present = set(GATE.findall(text))
    return [g for g in gates if g not in present]


def sync_shell(path, gates, anchor):
    """Insert missing gates right after the last hand-written gate line."""
    text = strip_block(path.read_text(), BEGIN, END)
    need = missing(text, gates)
    if need:
        lines = text.split('\n')
        last = max(i for i, l in enumerate(lines) if re.match(r'\s*bash ' + GATE.pattern, l) or
                   (anchor and anchor in l))
        block = [BEGIN] + [f'bash {g}' for g in need] + [END]
        lines[last + 1:last + 1] = block
        text = '\n'.join(lines)
    path.write_text(text)
    return need


def sync_ci(path, gates):
    text = path.read_text()
    begin, end = '      ' + BEGIN.replace('# ', '# ', 1), '      ' + END
    text = strip_block(text, BEGIN, END)
    need = missing(text, gates)
    if need:
        anchor = '      - name: Android platform 36'
        if anchor not in text:
            sys.exit('CI anchor step "Android platform 36" not found')
        steps = [begin]
        for g in need:
            steps.append(f'      - name: Gate {Path(g).stem}')
            steps.append(f'        run: bash {g}')
        steps.append(end)
        text = text.replace(anchor, '\n'.join(steps) + '\n' + anchor, 1)
    path.write_text(text)
    return need


def sync_gradle(path, gates):
    text = path.read_text()
    chain = ' && '.join(f'bash {g}' for g in gates)
    new, n = re.subn(r"(tasks\.register\('hostRegression', Exec\) \{.*?commandLine 'bash', '-c', ')[^']*(')",
                     lambda m: m.group(1) + chain + m.group(2), text, count=1, flags=re.S)
    if n != 1:
        sys.exit('Gradle hostRegression commandLine not found')
    path.write_text(new)


def main():
    gates = manifest()
    for g in gates:
        if not (ROOT / g).is_file():
            sys.exit(f'manifest gate does not exist: {g}')
    report = {
        'BUILD_ON_NAS.sh': sync_shell(ROOT / 'BUILD_ON_NAS.sh', gates, None),
        'BUILD_PLAY_BUNDLE_ON_NAS.sh': sync_shell(ROOT / 'BUILD_PLAY_BUNDLE_ON_NAS.sh', gates, None),
        '.github/workflows/verify.yml': sync_ci(ROOT / '.github/workflows/verify.yml', gates),
    }
    sync_gradle(ROOT / 'app/build.gradle', gates)
    for name, added in report.items():
        print(f'{name}: {len(added)} synced gate(s)')
    print(f'app/build.gradle: hostRegression runs all {len(gates)} gates')


if __name__ == '__main__':
    main()
