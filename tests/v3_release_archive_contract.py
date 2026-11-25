"""Production release/transfer archive contract, independent of a beta label.

This contract intentionally validates the *active* shell assignments. It must not
accept an archive name which appears only in comments, READMEs, or old tests.
The outer TransferKit's VERIFY_PACKAGE validates the actual bytes and SHA-256.
"""
import re

_ASSIGNMENT = r"(?m)^[ \t]*{name}[ \t]*=[ \t]*([\"'])([^\"'\r\n]+)\1[ \t]*(?:\#.*)?$"
_ARCHIVE = re.compile(r"VinylStudio-[A-Za-z0-9][A-Za-z0-9._-]*-source\.zip\Z")


def shell_value(content, key):
    """Read one literal shell variable assignment (no eval, no substitutions)."""
    matches = re.findall(_ASSIGNMENT.format(name=re.escape(key)), content)
    if len(matches) != 1:
        raise AssertionError(f"Expected exactly one literal {key}= assignment; found {len(matches)}")
    return matches[0][1]


def verify_archive_contract(phone, verifier, receive, expected_apk):
    """Fail closed on inconsistent transfer/archive metadata."""
    name = shell_value(phone, 'ARCHIVE')
    verified_name = shell_value(verifier, 'ARCHIVE')
    if not _ARCHIVE.fullmatch(name):
        raise AssertionError(f"Invalid or unsafe source ZIP basename: {name!r}")
    if verified_name != name:
        raise AssertionError(f"Phone source ZIP {name!r} != verifier source ZIP {verified_name!r}")
    if shell_value(phone, 'SOURCE_DIR') != 'VinylStudio-V3-source':
        raise AssertionError('Wrong extracted source directory')
    if shell_value(phone, 'APK') != expected_apk:
        raise AssertionError('Build-and-receive APK inconsistent with release-info.json')
    if shell_value(receive, 'APK') != expected_apk:
        raise AssertionError('Receive-only APK inconsistent with release-info.json')
    # These are actual transfer/verification steps, not only metadata mentions.
    required_phone = (
        'bash "$BASE/VERIFY_PACKAGE.sh"',
        'EXPECTED="$(awk -v name="$ARCHIVE"',
        'vinyl_send_source "$BASE" "$ARCHIVE" "$EXPECTED"',
        'vinyl_ssh bash -s -- "$ARCHIVE" "$EXPECTED" "$SOURCE_DIR" "$APK"',
    )
    required_verify = (
        'awk -v name="$ARCHIVE"',
        'sha256sum -c -- SOURCE_SHA256.txt',
        'unzip -tq "$BASE/$ARCHIVE"',
        'source_part(){ unzip -p "$BASE/$ARCHIVE"',
    )
    for marker in required_phone:
        if marker not in phone:
            raise AssertionError(f"Required phone source verification/transfer step absent: {marker}")
    for marker in required_verify:
        if marker not in verifier:
            raise AssertionError(f"Required source package integrity step absent: {marker}")
    return name
