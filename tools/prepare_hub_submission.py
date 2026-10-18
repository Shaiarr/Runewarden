#!/usr/bin/env python3
"""Prepare the exact local commit's Hub marker and review text; never publishes."""
import argparse,pathlib,re,subprocess

ROOT=pathlib.Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser();parser.add_argument('repository',help='Public HTTPS GitHub repository URL')
    parser.add_argument('--output',type=pathlib.Path,default=ROOT/'build/hub-submission');args=parser.parse_args()
    url=args.repository.rstrip('/')
    if not re.fullmatch(r'https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(?:\.git)?',url):
        parser.error('Use the actual public GitHub repository URL')
    repository_root=subprocess.check_output(['git','rev-parse','--show-toplevel'],cwd=ROOT,text=True).strip()
    if pathlib.Path(repository_root).resolve()!=ROOT.resolve():
        parser.error('Initialize this plugin folder as its own Git repository first')
    if subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip():
        parser.error('Commit the reviewed source before preparing a submission')
    commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    assert re.fullmatch(r'[0-9a-f]{40}',commit)
    if not url.endswith('.git'):url+='.git'
    destination=args.output.resolve();(destination/'plugins').mkdir(parents=True,exist_ok=True)
    (destination/'plugins/runewarden').write_text(f'repository={url}\ncommit={commit}\n')
    body='''Runewarden connects account goals, preparation, guides, bank focus, travel, activity journals and a configurable HUD.

The submitted JAR contains Java 11 production code and bundled source-attributed reference data. It has no game-input automation or website/account synchronization. Wiki item-source queries are default-off and require visible third-party consent; requests contain item article titles rather than character observations.

This repository publishes one RuneLite plugin, using build=standard. It contains no client launcher, Java runtime or bundled companion plugin. Shortest Path is optional and installed/enabled separately; production code sends supported public messages and reads fixed public result getters. Quest planning and account progress remain in Runewarden; walkthroughs use separately installed Quest Helper, without a launcher handoff adapter in this export.

World hopping is manually requested, with target/account checks and native confirmation. TCP latency probes use one bounded background worker. No combat, activity or low-resource event chooses a world or triggers a hop.

Fight Caves entrance rotations and wave-one NPC spawn-origin references follow the existing Hub Spawn Predictor feature. Boss standing/phase packs remain manually selected. Activity helpers use native inventory/interface/object observations. See docs/release-review-0.7.3.md and docs/release-readiness-0.9.6.md for precise scope.

Plugin-only verification is recorded in docs/plugin-only-evidence.json. Historical full-harness results are not the test count for this export. Two optional real Shortest Path contract suites need an externally supplied pinned upstream JAR. Outstanding live checks remain visible in docs/live-acceptance.json. This submission does not claim prior RuneLite/Jagex approval.
'''
    (destination/'pull-request.md').write_text(body)
    print('Prepared Hub marker and review text for '+commit+' in '+str(destination))
if __name__=='__main__':main()
