"""Offline regression checks for retained signed inputs and complete popularity coverage."""
import gzip
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

TOOLS=Path(__file__).resolve().parent

def module(name,filename):
    spec=importlib.util.spec_from_file_location(name,TOOLS/filename)
    result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result

FETCH=module('hub_fetch','fetch-hub-audit-sources.py')
BUILD=module('hub_build','build-hub-coverage-audit.py')

class HubAuditTest(unittest.TestCase):
    def test_retained_manifest_verifies_against_pinned_native_certificate(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);self.assertEqual(176,FETCH.restore(path,False))
            manifest,_,_=BUILD.verify(path)
            self.assertEqual(2681,len(manifest['display']));self.assertEqual(2655,len(manifest['jars']))
    def test_changed_signed_bytes_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);FETCH.restore(path,False)
            envelope=bytearray((path/'manifest-signed.bin').read_bytes());envelope[-2]^=1
            (path/'manifest-signed.bin').write_bytes(envelope)
            with self.assertRaisesRegex(ValueError,'Unsigned manifest bytes differ'):BUILD.verify(path)
    def test_replaced_verification_certificate_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);FETCH.restore(path,False);(path/'externalplugins.crt').write_bytes(b'fake')
            with self.assertRaisesRegex(ValueError,'Certificate differs'):BUILD.verify(path)
    def report_fixture(self,path):
        (path/'docs/hub-source-snapshot').mkdir(parents=True);(path/'build.gradle').write_text("version = '0.9.0'\n")
        (path/'Demo.java').write_text('class Demo {}')
        sources={'manifestEnvelopeSha256':hashlib.sha256(b'manifest').hexdigest(),'countsSha256':hashlib.sha256(b'counts').hexdigest()}
        retained=path/'docs/hub-source-snapshot';(retained/'manifest-signed.bin.gz').write_bytes(gzip.compress(b'manifest'));(retained/'api-latest-counts.json.gz').write_bytes(gzip.compress(b'counts'))
        summary={'comparisonMinimumActiveInstalls':50000};audit={'auditedRunewardenVersion':'0.9.0-working-tree','sources':sources,'summary':summary,'comparisons':[{'pluginId':'demo','unresolvedEvidenceNames':[],'sourceInspection':[{'file':'Demo.java','sha256':hashlib.sha256((path/'Demo.java').read_bytes()).hexdigest()}],'features':[{'status':'implemented'}]}]}
        inventory={'summary':summary,'plugins':[{'id':'demo','availableJar':True,'activeInstalls':50000}]}
        (path/'docs/hub-utility-audit.json').write_text(json.dumps(audit));(path/'docs/hub-inventory.json').write_text(json.dumps(inventory));return audit
    def test_offline_gate_rejects_stale_production_hash(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);self.report_fixture(path);before=BUILD.ROOT;BUILD.ROOT=path
            try:
                BUILD.check_evidence();(path/'Demo.java').write_text('class Demo { int changed; }')
                with self.assertRaisesRegex(ValueError,'source evidence is stale'):BUILD.check_evidence()
            finally:BUILD.ROOT=before
    def test_offline_gate_rejects_missing_popularity_baseline_member(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory);audit=self.report_fixture(path);audit['comparisons']=[];(path/'docs/hub-utility-audit.json').write_text(json.dumps(audit));before=BUILD.ROOT;BUILD.ROOT=path
            try:
                with self.assertRaisesRegex(ValueError,'baseline coverage differs'):BUILD.check_evidence()
            finally:BUILD.ROOT=before
    def test_every_available_popular_plugin_has_reviewed_features_and_pinned_text(self):
        source=FETCH.SNAPSHOT
        raw=gzip.decompress((source/'manifest-signed.bin.gz').read_bytes());manifest=json.loads(raw[516:])
        counts=json.loads(gzip.decompress((source/'api-latest-counts.json.gz').read_bytes()));available={p['internalName'] for p in manifest['jars']}
        baseline={p['internalName'] for p in manifest['display'] if p['internalName'] in available and counts.get(p['internalName'],0)>=50000}
        readmes=json.loads((source/'readme-index.json').read_text())
        self.assertEqual(151,len(baseline));self.assertEqual(baseline,set(BUILD.ASSESSMENTS));self.assertEqual(baseline,set(readmes))
        for slug in baseline:
            self.assertTrue(BUILD.ASSESSMENTS[slug]['features']);self.assertEqual(64,len(readmes[slug]['sha256']));self.assertIn('/'+readmes[slug]['commit']+'/',readmes[slug]['source'])

if __name__=='__main__':unittest.main()
