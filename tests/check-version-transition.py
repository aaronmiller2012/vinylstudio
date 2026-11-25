#!/usr/bin/env python3
"""Catch stale 2.54-only release assertions before expensive Android builds."""
from pathlib import Path
import json
from release_version import at_least

assert at_least('2.54.99', '2.54.0')
assert at_least('2.55.0', '2.54.0')
assert at_least('2.55.0-render-performance-rc134', '2.54.0')
assert at_least('3.0.0', '2.54.0')
for bad in ('2.53.99', '2.54', '2.55.z', 'banana', '', '2.54.0junk'):
    assert not at_least(bad, '2.54.0'), bad
root = Path(__file__).resolve().parents[1]
info = json.loads((root/'tests/release-info.json').read_text())
assert at_least(info['version'], '2.54.0'), 'Current release is older than historical baseline'
assert at_least(info['versionName'], info['version']), 'Version name no longer matches release floor'
assert info['versionCode'] >= 406, 'Android versionCode rollback'
assert (root/'tests/check-rc113-rollback.py').is_file()
print('PASS historical release tests accept 2.55+; reject rollback and malformed versions')
