"""Protect release evidence: automated success cannot imply live approval."""
import json,pathlib,tempfile,unittest
from release_gate import assess,REQUIRED_GATES

def manifest(status='unverified',evidence=''):
    return {'format':'runewarden-live-acceptance','version':1,'checks':[
        {'id':key,'status':status,'evidence':evidence} for key in sorted(REQUIRED_GATES)]}

class ReleaseGateTests(unittest.TestCase):
    def test_development_evidence_preserves_open_gates_and_blocks_public_release(self):
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder)
            (root/'TEST-flow.xml').write_text('<testsuite tests="12" failures="0" errors="0" skipped="0"/>')
            live=root/'live.json';live.write_text(json.dumps(manifest()))
            report=assess(root,live)
            self.assertEqual(report['automatedTests'],12)
            self.assertFalse(report['publicReleaseReady'])
            with self.assertRaises(ValueError):assess(root,live,True)

    def test_a_claimed_pass_requires_evidence_and_failed_tests_block_it(self):
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder);results=root/'TEST-flow.xml'
            results.write_text('<testsuite tests="12" failures="0" errors="0" skipped="0"/>')
            live=root/'live.json';live.write_text(json.dumps(manifest('passed')))
            with self.assertRaises(AssertionError):assess(root,live)
            live.write_text(json.dumps(manifest('passed','Recorded specific version and scenario')))
            self.assertTrue(assess(root,live,True)['publicReleaseReady'])
            results.write_text('<testsuite tests="12" failures="1" errors="0" skipped="0"/>')
            with self.assertRaises(AssertionError):assess(root,live,True)

    def test_absent_test_results_cannot_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(AssertionError):assess(folder,pathlib.Path(folder)/'missing.json')

    def test_empty_gates_and_all_skipped_tests_cannot_manufacture_a_public_pass(self):
        with tempfile.TemporaryDirectory() as folder:
            root=pathlib.Path(folder);results=root/'TEST-flow.xml';live=root/'live.json'
            results.write_text('<testsuite tests="1" failures="0" errors="0" skipped="0"/>')
            value=manifest();value['checks']=[];live.write_text(json.dumps(value))
            with self.assertRaises(AssertionError):assess(root,live,True)
            live.write_text(json.dumps(manifest('passed','Actual scenario evidence')))
            results.write_text('<testsuite tests="1" failures="0" errors="0" skipped="1"/>')
            with self.assertRaises(AssertionError):assess(root,live,True)

if __name__=='__main__':unittest.main()
