#!/usr/bin/env python3
"""RC173-W1 wrapper bootstrapping regression checks (offline, reproducible)."""
from pathlib import Path
import os, subprocess, tempfile, shutil
src=Path(__file__).resolve().parents[1]
ensure=src/'scripts'/'ensure-gradle-wrapper.sh'
gradlew=src/'gradlew'
a=ensure.read_text(); g=gradlew.read_text()
assert "https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar" in a
assert "https://services.gradle.org/distributions/gradle-8.11.1-wrapper.jar" not in a
assert "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046" in a
assert "--proto '=https'" in a and "--proto-redir '=https'" in a
assert 'scripts/ensure-gradle-wrapper.sh' in g and 'curl ' not in g
assert 'distributionSha256Sum=f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6' in (src/'gradle'/'wrapper'/'gradle-wrapper.properties').read_text()
with tempfile.TemporaryDirectory(prefix='vinyl-wrapper-') as d:
    root=Path(d)
    (root/'scripts').mkdir()
    (root/'gradle/wrapper').mkdir(parents=True)
    shutil.copy2(ensure,root/'scripts'/'ensure-gradle-wrapper.sh')
    (root/'gradle/wrapper/gradle-wrapper.jar').write_bytes(b'not an official wrapper')
    cmd=['bash',str(root/'scripts'/'ensure-gradle-wrapper.sh')]
    result=subprocess.run(cmd, capture_output=True, text=True, timeout=8)
    assert result.returncode==29, (result.returncode,result.stderr)
    assert 'SHA-256' in result.stderr
print('PASS RC173-W1 wrapper URL, official checksums, fail-closed bad-JAR handling')
