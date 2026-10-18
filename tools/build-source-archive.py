#!/usr/bin/env python3
"""Archive committed plugin sources; exclude untracked builds and local account files."""
import argparse
import pathlib
import subprocess
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('destination')
args = parser.parse_args()
out = pathlib.Path(args.destination).resolve()
top = pathlib.Path(subprocess.check_output(['git', 'rev-parse', '--show-toplevel'], cwd=root, text=True).strip()).resolve()
if top != root:
    parser.error('Initialize the plugin folder as its own repository first')
if subprocess.check_output(['git', 'status', '--porcelain'], cwd=root, text=True).strip():
    parser.error('Commit the reviewed source before packaging')
tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=root).decode().split('\0')
excluded = {'build', '.gradle', '.git', '.idea', '__pycache__', 'node_modules', '.runelite', 'logs'}
out.parent.mkdir(parents=True, exist_ok=True)
count = 0
with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
    for name in sorted(filter(None, tracked)):
        path = root / name
        relative = pathlib.PurePosixPath(name)
        if any(part in excluded for part in relative.parts) or relative.parts[0] in {'vendor', 'launcher-files', 'runtime'} or relative.parts[:2] == ('src', 'launcher'):
            continue
        if path.is_symlink() or not path.is_file() or path == out:
            continue
        if path.name.startswith('.env') or path.suffix.lower() in {'.key', '.pem', '.pyc', '.log', '.hprof', '.jfr', '.heapdump'}:
            continue
        if path.suffix.lower() == '.jar' and name != 'gradle/wrapper/gradle-wrapper.jar':
            continue
        info = zipfile.ZipInfo('runewarden/' + relative.as_posix(), (2026, 10, 18, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = (0o100755 if path.name == 'gradlew' or path.suffix == '.sh' else 0o100644) << 16
        archive.writestr(info, path.read_bytes())
        count += 1
print(f'{out}: {count} tracked plugin files')
