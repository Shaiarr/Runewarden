#!/usr/bin/env python3
"""Reproducible local code budget; not the Plugin Hub reviewer's token count."""
import argparse
import json
from pathlib import Path
import re
import tiktoken

ROOT = Path(__file__).resolve().parents[1]
JAVA_PARTS = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')


def without_comments(source):
    return JAVA_PARTS.sub(lambda match: match.group() if match.group()[0] in '\"\'' else ' ', source)


def measure(root=ROOT):
    files = sorted((Path(root) / 'src/main/java').rglob('*.java'))
    rows = []
    encodings = {name: tiktoken.get_encoding(name) for name in ('cl100k_base', 'o200k_base')}
    for path in files:
        source = without_comments(path.read_text(encoding='utf-8'))
        rows.append({'file': path.relative_to(root).as_posix(), 'bytes': path.stat().st_size,
                     **{name: len(encoding.encode(source, disallowed_special=()))
                        for name, encoding in encodings.items()}})
    return {'format': 'runewarden-local-token-budget', 'officialHubCount': False,
            'scope': 'Production Java only, comments excluded; strings retained. Resources, tests and development launcher excluded.',
            'tokenizerVersion': tiktoken.__version__, 'files': len(rows),
            'sourceBytes': sum(row['bytes'] for row in rows),
            'tokens': {name: sum(row[name] for row in rows) for name in encodings},
            'largestFiles': sorted(rows, key=lambda row: row['o200k_base'], reverse=True)[:10]}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path)
    parser.add_argument('--max-tokens', type=int, default=160000)
    args = parser.parse_args()
    result = measure()
    result['localBudget'] = args.max_tokens
    result['withinLocalBudget'] = max(result['tokens'].values(), default=0) <= args.max_tokens
    text = json.dumps(result, indent=2) + '\n'
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding='utf-8')
    print(text, end='')
    if not result['withinLocalBudget']:
        raise SystemExit('Production code exceeds the local review budget')


if __name__ == '__main__':
    main()
