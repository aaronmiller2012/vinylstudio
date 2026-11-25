#!/usr/bin/env python3
"""Static release-boundary contract; Gradle/Android execution still requires SDK."""
from pathlib import Path
import re, subprocess
root=Path(__file__).resolve().parents[1]
build=(root/'BUILD_ON_NAS.sh').read_text()
gradle=(root/'app/build.gradle').read_text()
play=(root/'BUILD_PLAY_BUNDLE_ON_NAS.sh').read_text()
assert 'VINYL_QUALIFY_RELEASE' in build
assert 'VINYL_PRIVACY_URL' in build
assert ':app:testDebugUnitTest :app:lintDebug :app:assembleDebug' in build
assert 'case "${VINYL_QUALIFY_RELEASE:-0}" in' in build
parts=build.split('case "${VINYL_QUALIFY_RELEASE:-0}" in')
assert len(parts)>=3, 'Expected early validation and guarded release branch'
branch=parts[-1].split('esac',1)[0]
assert ':app:testReleaseUnitTest :app:lintRelease :app:assembleRelease' in branch
assert re.search(r'\n\s*1\)\s*\n', branch), 'Release checks must live under explicit opt-in'
assert 'validateReleasePrivacy' in gradle and 'task.dependsOn(requireReleasePrivacy)' in gradle
assert "'bundleRelease', 'assembleRelease', 'lintRelease'" in gradle
assert 'VINYL_PRIVACY_URL' in play and ':app:bundleRelease' in play
assert 'VINYL_QUALIFY_RELEASE' not in play, 'Play upload must never depend on development bypass'
for f in ('BUILD_ON_NAS.sh','BUILD_PLAY_BUNDLE_ON_NAS.sh'):
    subprocess.run(['bash','-n',str(root/f)],check=True)
print('PASS RC173-W3: DEBUG builds never run release gates by default; release still requires privacy policy')
