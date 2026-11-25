#!/usr/bin/env python3
"""Validate current release metadata without freezing a previous version code."""
import json
import re
from pathlib import Path
root = Path(__file__).resolve().parents[1]
info = json.loads((root / 'tests/release-info.json').read_text())
gradle = (root / 'app/build.gradle').read_text()
build = (root / 'BUILD_ON_NAS.sh').read_text()
assert re.search(r'\bversionCode\s+' + str(info['versionCode']) + r'\b', gradle), 'Gradle versionCode / release-info.json mismatch'
assert re.search(r'\bversionName\s+[\"\']' + re.escape(info['versionName']) + r'[\"\']', gradle), 'Gradle versionName / release-info.json mismatch'
assert 'tests/run-v3-milestone-a2-gate.sh' in build, 'A2 geometry regression gate missing from NAS build'
assert 'tests/run-v3-milestone-a3-b1-gate.sh' in build, 'A3/B1 regression gate missing from NAS build'
assert info['gate'] in build, 'Configured host test gate missing from NAS build script'
assert info['apk'] in build, 'Current APK filename missing from NAS build script'
# Prevent another stale R8 mapping filename from halting the NAS release gate.
# Both the mapping copy and its checksum must use the current source versionCode.
expected_mapping = f"V3-code{info['versionCode']}-mapping.txt"
mapping_refs = re.findall(r'V3-code\d+-mapping\.txt', build)
assert len(mapping_refs) == 2 and all(name == expected_mapping for name in mapping_refs), (
    f'Stale R8 mapping filename: expected {expected_mapping}; found {mapping_refs}'
)
assert 'sha256sum release-artifacts/' + expected_mapping in build, 'R8 mapping checksum not tied to current build'
expected_digest = f"V3-code{info['versionCode']}-mapping.sha256"
digest_refs = re.findall(r'V3-code\d+-mapping\.sha256', build)
assert digest_refs == [expected_digest], (
    f'Stale R8 mapping checksum filename: expected {expected_digest}; found {digest_refs}'
)
print('PASS current release metadata: build code, version name, gate and APK match')
assert 'tests/run-v3-milestone-a2-gate.sh' in (root/'.github/workflows/verify.yml').read_text(), 'A2 missing in CI'
assert 'tests/run-v3-milestone-a2-gate.sh' in (root/'app/build.gradle').read_text(), 'A2 missing in opt-in Gradle host gate'

assert "tests/run-v3-milestone-a3-b1-gate.sh" in (root/".github/workflows/verify.yml").read_text()
assert "tests/run-v3-milestone-a3-b1-gate.sh" in (root/"app/build.gradle").read_text()

assert "tests/run-v3-milestone-b2-gate.sh" in build, "B2 host gate missing on NAS"
assert "tests/run-v3-milestone-b2-gate.sh" in (root/".github/workflows/verify.yml").read_text()
assert "tests/run-v3-milestone-b2-gate.sh" in (root/"app/build.gradle").read_text()

assert "tests/run-v3-milestone-b2-lines-gate.sh" in build, "B2 Lines not mandatory on NAS"
assert "tests/run-v3-milestone-b2-lines-gate.sh" in (root/".github/workflows/verify.yml").read_text()
assert "tests/run-v3-milestone-b2-lines-gate.sh" in (root/"app/build.gradle").read_text()

assert 'tests/run-v3-milestone-ab-completion-gate.sh' in build, 'A4/B2-B4 gate missing on NAS'
assert 'tests/run-v3-milestone-ab-completion-gate.sh' in (root/'.github/workflows/verify.yml').read_text(), 'A4/B2-B4 gate missing in CI'
assert 'tests/run-v3-milestone-ab-completion-gate.sh' in (root/'app/build.gradle').read_text(), 'A4/B2-B4 gate missing in Gradle host gate'
