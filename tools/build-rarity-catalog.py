"""Build offline ownership and source-rate facts; never equate the two metrics.

Inputs are the pinned public CollectionLogPopupEnhanced ownership snapshot,
the publisher's 2026-10-15 data-branch rates, and our pinned Wiki NPC catalogue.
No network access occurs in this generator or at runtime.
"""
import json, re, sys
from pathlib import Path

project = Path(__file__).resolve().parents[1]
ownership = json.loads(Path(sys.argv[1]).read_text())
drops = json.loads(Path(sys.argv[2]).read_text())
npcs = json.loads(Path(sys.argv[3]).read_text())
def normalize(text): return re.sub(r'[^a-z0-9]+', ' ', text.lower()).strip()
def display(probability):
    value = 1 / probability
    return 'Always' if probability == 1 else '1/' + (f'{round(value):,}' if abs(value-round(value)) < .00001 else f'{value:,.2f}'.rstrip('0').rstrip('.'))
npc_names = {normalize(entry['name']) for entry in npcs['entries'] if entry.get('name')}
by_name = {}
for item_id, row in ownership.items():
    if isinstance(row.get('name'), str) and row['name'].strip(): by_name.setdefault(normalize(row['name']), []).append(item_id)
items = {}
for item_id, row in ownership.items():
    if not isinstance(row.get('name'), str) or not row['name'].strip(): continue
    items[item_id] = {'id': int(item_id), 'name': row['name'], 'ownershipPercent': row.get('comp'), 'rates': []}
conditional_sources = {
    'ancient chest': 'Purple-table chance; full Chambers of Xeric chance depends on raid points and reward conditions',
    'monumental chest': 'Purple-table chance; full Theatre of Blood chance depends on contribution and reward conditions',
    'chest tombs of amascut': 'Conditional raid reward table; full chance depends on invocations, contribution and unique reward roll',
    'chest barrows': 'Reward chance depends on brothers defeated and reward potential',
    'lunar chest': 'Reward chance depends on moons defeated and reward conditions',
    'nex': 'Personal drop chance depends on contribution and group conditions',
    'vardorvis': 'Perfect kills and vestige progression can alter the effective chance',
    'duke sucellus': 'Perfect kills and vestige progression can alter the effective chance',
    'the leviathan': 'Perfect kills and vestige progression can alter the effective chance',
    'the whisperer': 'Perfect kills and vestige progression can alter the effective chance',
}
for source, rows in drops.items():
    if source.endswith(' (max)'): continue
    key = normalize(source)
    max_rows = drops.get(source + ' (max)', {})
    for name, probability in rows.items():
        if not isinstance(probability, (int, float)) or not 0 < probability <= 1: continue
        for item_id in by_name.get(normalize(name), []):
            row = ownership[item_id]
            maximum = max_rows.get(name)
            reason = conditional_sources.get(key)
            if key.startswith('revenant '): reason = 'Skull, task and revenant conditions change the effective chance'
            if key.startswith('doom of mokhaiotl'): reason = 'Delve level and reward conditions change the effective chance'
            if 'Skilling Pets' in row.get('tabs', []): reason = 'Skilling pet chance depends on skill level and activity'
            if key in ['basilisk knight', 'wyrm', 'drake'] and name.lower() in [
                'basilisk jaw', 'dragon harpoon', 'dragon sword', "drake's claw", "drake's tooth"]:
                reason = 'On-task and off-task rates differ; task state is not observed'
            if name.lower() in ['dragon spear', 'shield left half']:
                reason = 'Rare drop table luck and source conditions are not observed'
            if name.lower() in ['noxious blade', 'noxious point', 'noxious pommel', "hydra's eye", "hydra's fang", "hydra's heart"]:
                reason = 'Component progression determines which piece can drop'
            if maximum is not None: reason = reason or 'Source table supplies a range; scaling conditions are not observed'
            if key not in npc_names and key not in ['gauntlet', 'corrupted gauntlet']:
                reason = reason or 'Table reference only; reward rolls or activity conditions are not fully represented'
            text = display(probability)
            if maximum is not None and isinstance(maximum, (int, float)) and maximum > 0:
                text = 'Reference range ' + text + ' – ' + display(maximum)
            elif key in ['ancient chest', 'monumental chest']:
                text = 'Purple-table chance ' + text
            elif reason:
                text = 'Table reference ' + text
            items[item_id]['rates'].append({'source': source, 'probability': probability,
                'maximumProbability': maximum, 'conditional': reason is not None,
                'basis': reason or 'Wiki base source chance (including fixed multiple rolls); excludes unobserved modifiers',
                'oddsText': text})
result = {
    'source': 'OSRS Wiki factual data via CollectionLogPopupEnhanced and osrs-collection-log-data',
    'snapshotDate': '2026-09-26',
    'dropSnapshotDate': '2026-10-15',
    'populationLabel': 'WikiSync collection-log accounts',
    'ownershipSource': 'https://github.com/TimHeessels/collection-log-popup-enhanced/blob/99ce2f01b1a0c56af75f39e2afbc8f226cf8a94e/src/main/resources/data/collection-log.json',
    'ownershipLastDataCommit': '19584497696bce99dee576964ebf17b953caf0aa',
    'dropSource': 'https://github.com/TimHeessels/osrs-collection-log-data/blob/0452ad13e77b29618ab1b6d22e3a728c5e396510/drop-rates.json',
    'populationSource': 'https://oldschool.runescape.wiki/w/Collection_log/Table?oldid=15341154',
    'notes': 'Ownership is a historical WikiSync sample, not all players and never drop probability. Conditional rates have no per-kill rarity denominator. Four unnamed ownership entries omitted.',
    'items': list(items.values())
}
path = project / 'src/main/resources/com/runewarden/rarity-catalog.json'
path.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n')
print({'items': len(items), 'ownership': sum(row['ownershipPercent'] is not None for row in items.values()),
       'rateRows': sum(len(row['rates']) for row in items.values()),
       'unconditionalReferenceRows': sum(not rate['conditional'] for row in items.values() for rate in row['rates'])})
