#!/usr/bin/env python3
"""Check the production JAR that would be delivered through the Plugin Hub."""
import argparse,json,pathlib,re,struct,zipfile

ROOT=pathlib.Path(__file__).resolve().parents[1]

def verify(path):
    version=re.search(r"version\s*=\s*'([^']+)'",(ROOT/'build.gradle').read_text()).group(1)
    properties=dict(line.split('=',1) for line in (ROOT/'runelite-plugin.properties').read_text().splitlines() if '=' in line)
    assert properties.get('build')=='standard','The Hub build must use standard Java/resources packaging'
    assert properties.get('version')==version,'Plugin metadata version differs'
    assert properties.get('plugins')=='com.runewarden.RunewardenPlugin','Wrong entry point'
    with zipfile.ZipFile(path) as archive:
        assert archive.testzip() is None,'Corrupt plugin JAR'
        names=archive.namelist();assert len(names)==len(set(names)),'Duplicate JAR entries'
        classes=[name for name in names if name.endswith('.class')]
        assert 'com/runewarden/RunewardenPlugin.class' in classes,'Missing plugin entry point'
        assert all(name.startswith('com/runewarden/') for name in classes),'Foreign or stale class namespace in plugin'
        assert not any(name.endswith('Test.class') or name.startswith(('org/junit/','org/mockito/','com/google/gson/','okhttp3/')) for name in names),'Test or duplicate shared dependency in plugin'
        assert not any(name.startswith(('com/runewarden/RunewardenLauncher','com/runewarden/QuestHelperLauncherBridge','com/runewarden/RoutingCompanionStartup','com/questhelper/','shortestpath/')) for name in names),'Development launcher or companion classes must not be shipped as a Hub plugin'
        for name in classes:
            data=archive.read(name)
            assert data[:4]==b'\xca\xfe\xba\xbe' and struct.unpack('>H',data[6:8])[0]==55,'Not Java 11: '+name
        resources=ROOT/'src/main/resources'
        for source in resources.rglob('*'):
            if source.is_file():
                name=source.relative_to(resources).as_posix()
                assert archive.read(name)==source.read_bytes(),'Packaged resource differs: '+name
        assert {name for name in names if not name.endswith('/')} == set(classes) | {source.relative_to(resources).as_posix() for source in resources.rglob('*') if source.is_file()} | {'META-INF/MANIFEST.MF'},'Unexpected or stale packaged resources'
        assert b'runewarden' in archive.read('com/runewarden/RunewardenConfig.class'),'Wrong configuration namespace'
        assert version.encode() in archive.read('com/runewarden/RunewardenVersion.class'),'Packaged version differs'
    return {'version':version,'classes':len(classes),'jar':path.name,'resourcesMatch':True,'javaRelease':11,'hubApproved':False}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('jar',nargs='?');args=parser.parse_args()
    if args.jar: path=pathlib.Path(args.jar)
    else:
        version=re.search(r"version\s*=\s*'([^']+)'",(ROOT/'build.gradle').read_text()).group(1)
        candidates=list((ROOT/'build/libs').glob('runewarden-'+version+'.jar'))
        assert len(candidates)==1,'Supply the exact production JAR'
        path=candidates[0]
    print(json.dumps(verify(path),indent=2))
if __name__=='__main__':main()
