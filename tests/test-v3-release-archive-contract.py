#!/usr/bin/env python3
"""Behavioral tests for source-archive naming drift and security regression."""
import unittest
from pathlib import Path
from v3_release_archive_contract import verify_archive_contract, shell_value

root = Path(__file__).resolve().parents[1]
phone = (root/'transfer/PHONE_BUILD_AND_RECEIVE.sh').read_text()
verifier = (root/'transfer/VERIFY_PACKAGE.sh').read_text()
receive = (root/'transfer/PHONE_RECEIVE_ONLY.sh').read_text()
apk = __import__('json').loads((root/'tests/release-info.json').read_text())['apk']

class ArchiveContractTest(unittest.TestCase):
    def test_current_release(self):
        source = verify_archive_contract(phone,verifier,receive,apk)
        self.assertEqual(source, shell_value(verifier,'ARCHIVE'))
        self.assertTrue(source.endswith('-source.zip'))

    def test_reject_old_archive_in_verifier(self):
        wrong = verifier.replace("ARCHIVE='"+shell_value(verifier,'ARCHIVE')+"'", "ARCHIVE='VinylStudio-obsolete-source.zip'")
        with self.assertRaisesRegex(AssertionError,'!= verifier'):
            verify_archive_contract(phone,wrong,receive,apk)

    def test_reject_missing_archive_assignment(self):
        wrong = phone.replace("ARCHIVE='"+shell_value(phone,'ARCHIVE')+"'", '# Old source archive')
        with self.assertRaisesRegex(AssertionError,'one literal ARCHIVE'):
            verify_archive_contract(wrong,verifier,receive,apk)

    def test_reject_double_archive_assignment(self):
        with self.assertRaisesRegex(AssertionError,'one literal ARCHIVE'):
            verify_archive_contract(phone+"\nARCHIVE='VinylStudio-other-source.zip'\n",verifier,receive,apk)

    def test_reject_path_traversal(self):
        wrong = phone.replace(shell_value(phone,'ARCHIVE'),'../VinylStudio-unsafe-source.zip')
        wrongverify = verifier.replace(shell_value(verifier,'ARCHIVE'),'../VinylStudio-unsafe-source.zip')
        with self.assertRaisesRegex(AssertionError,'unsafe'):
            verify_archive_contract(wrong,wrongverify,receive,apk)

    def test_reject_receive_only_apk_drift(self):
        wrong = receive.replace(apk,'Old-Beta.apk')
        with self.assertRaisesRegex(AssertionError,'Receive-only APK'):
            verify_archive_contract(phone,verifier,wrong,apk)

    def test_reject_missing_sha_verification(self):
        wrong = verifier.replace('sha256sum -c -- SOURCE_SHA256.txt','echo integrity-not-checked')
        with self.assertRaisesRegex(AssertionError,'integrity step absent'):
            verify_archive_contract(phone,wrong,receive,apk)

    def test_reject_missing_source_transfer(self):
        wrong = phone.replace('vinyl_send_source "$BASE" "$ARCHIVE" "$EXPECTED"',': # no upload')
        with self.assertRaisesRegex(AssertionError,'transfer step absent'):
            verify_archive_contract(wrong,verifier,receive,apk)

if __name__=='__main__':
    unittest.main(verbosity=2)
