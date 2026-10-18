#!/usr/bin/env python3
"""Report evidence-backed release gates; a public 1.0 cannot silently skip live checks."""
import argparse,json,pathlib,re,sys,xml.etree.ElementTree as ET

ROOT=pathlib.Path(__file__).resolve().parents[1]
REQUIRED_GATES={'windows-client','quest-journey','training-journey','slayer-journey','farm-journey','boss-journey','profile-lifecycle','long-session','plugin-compatibility','hub-review'}

def assess(results,live,require_public=False):
    count=failures=skipped=0
    paths=list(pathlib.Path(results).glob('TEST-*.xml'))
    for path in paths:
        suite=ET.parse(path).getroot();count+=int(suite.get('tests',0))
        failures+=int(suite.get('failures',0))+int(suite.get('errors',0));skipped+=int(suite.get('skipped',0))
    assert paths and count>0,'No actual JUnit result files found'
    assert count>skipped,'No non-skipped automated tests ran'
    assert failures==0,f'{failures} tests failed'
    manifest=json.loads(pathlib.Path(live).read_text())
    assert manifest.get('format')=='runewarden-live-acceptance' and manifest.get('version')==1,'Invalid acceptance manifest'
    checks=manifest['checks']
    assert len({check['id'] for check in checks})==len(checks),'Duplicate acceptance gate'
    assert REQUIRED_GATES <= {check['id'] for check in checks},'Required acceptance gates are missing'
    for check in checks:
        assert check['status'] in {'unverified','passed','failed'},'Invalid acceptance state'
        if check['status']=='passed':assert check.get('evidence','').strip(),'Passed gate needs its actual evidence'
    pending=[check['id'] for check in checks if check['status']!='passed']
    if require_public and pending:raise ValueError('Public 1.0 gates remain open: '+', '.join(pending))
    if require_public and skipped:raise ValueError('Public 1.0 cannot skip automated tests')
    return {'automatedTests':count,'automatedFailures':failures,'automatedSkipped':skipped,'liveGatesPending':pending,'publicReleaseReady':not pending and skipped==0}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--results',type=pathlib.Path,default=ROOT/'build/test-results/test')
    parser.add_argument('--live',type=pathlib.Path,default=ROOT/'docs/live-acceptance.json')
    parser.add_argument('--require-public-release',action='store_true');parser.add_argument('--output',type=pathlib.Path)
    args=parser.parse_args()
    version=re.search(r"version\s*=\s*'([^']+)'",(ROOT/'build.gradle').read_text()).group(1)
    try:report=assess(args.results,args.live,args.require_public_release or version.startswith('1.'))
    except (AssertionError,ValueError) as error:sys.exit(str(error))
    report['pluginVersion']=version;text=json.dumps(report,indent=2)+'\n'
    if args.output:args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(text)
    print(text,end='')
if __name__=='__main__':main()
