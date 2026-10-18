#!/usr/bin/env python3
"""Build finite Sailing reward scenarios from retained OSRS Wiki revision API JSON.

Usage: python3 tools/build-sailing-scenarios.py SNAPSHOT_DIRECTORY
The directory must contain Courier tasks.json and the three Barracuda trial page JSON.
No hourly rates are inferred. An unknown reward remains excluded.
"""
import json
import pathlib
import re
import sys


def revision(directory, title):
    page = json.loads((directory / (title + '.json')).read_text())['query']['pages'][0]
    rev = page['revisions'][0]
    return rev, rev['slots']['main']['content']


def build(directory):
    trials = []
    for title, method, level, extra in [
        ('The Tempor Tantrum', 'training:tempor-tantrum', 30, False),
        ('The Jubbly Jive', 'training:advanced-barracuda', 55, True),
        ('The Gwenith Glide', 'activity:sailing-gwenith-glide', 72, False),
    ]:
        rev, text = revision(directory, title)
        # Reward tables use exactly one rank-name line, followed by numeric fields.
        for rank in ['Unranked', 'Swordfish', 'Shark', 'Marlin']:
            match = re.search(r'^\|' + rank + r'\n(?P<row>.*?)(?=^\|-|^\|\})', text, re.M | re.S)
            if not match:
                raise ValueError('Missing reward row ' + title + ' ' + rank)
            fields = [line[1:].strip() for line in match.group('row').splitlines() if line.startswith('|')]
            numbers = []
            for value in fields:
                if re.fullmatch(r'[\d,]+', value):
                    numbers.append(int(value.replace(',', '')))
                elif value.startswith(' -') or value == '-':
                    numbers.append(0)
                elif ':' in value:
                    numbers.append(0)  # Time is deliberately not a rate.
            # columns: completion, [trimming], supplies, cumulative, time, hourly, bonus
            offset = 1 if extra else 0
            bonus = numbers[5 + offset]
            trials.append(dict(methodId=method, name=title, rank=rank, level=level,
                               completionXp=numbers[0], supplyXp=numbers[1 + offset], firstRankBonus=bonus,
                               source='https://oldschool.runescape.wiki/w/' + title.replace(' ', '_') + '?oldid=' + str(rev['revid']),
                               revision=rev['revid'], timestamp=rev['timestamp']))
    rev, text = revision(directory, 'Courier tasks')
    tasks = []
    excluded = []
    for match in re.finditer(r'\{\{CourierTaskLine\|([^}]+)\}\}', text):
        parts = dict(part.split('=', 1) for part in match.group(1).split('|') if '=' in part)
        if not parts.get('xp', '').strip():
            excluded.append(int(parts['taskId']))
            continue
        tasks.append(dict(id=int(parts['taskId']), level=int(parts['level']), xp=int(parts['xp'].replace(',', '')),
                          noticeBoard=parts['noticeBoard'], cargoLocation=parts['cargoLocation'], destination=parts['destination'],
                          cargo=parts['item'], quantity=int(parts['qty'])))
    return dict(source='OSRS Wiki contributors, CC BY-NC-SA 3.0', retrieved='2026-10-16', built='2026-10-17', trials=trials,
                courierSource='https://oldschool.runescape.wiki/w/Courier_tasks?oldid=' + str(rev['revid']),
                courierRevision=rev['revid'], courierTimestamp=rev['timestamp'], excludedUnknownTaskRewards=excluded,
                courierTasks=tasks)


if __name__ == '__main__':
    output = pathlib.Path(__file__).resolve().parents[1] / 'src/main/resources/com/runewarden/sailing-xp-scenarios.json'
    data = build(pathlib.Path(sys.argv[1]))
    output.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    print(f"Built {len(data['trials'])} rank scenarios, {len(data['courierTasks'])} courier tasks")
