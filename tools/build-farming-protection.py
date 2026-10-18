#!/usr/bin/env python3
"""Build exact payments from the retained revision-pinned primary Wiki plant API response."""
import argparse
import json
import re
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('plant_pages', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
pages = json.loads(args.plant_pages.read_text())['query']['pages']
names = json.loads((root / 'src/main/resources/com/runewarden/guide-item-names.json').read_text())
resources = json.loads((root / 'src/main/resources/com/runewarden/skilling-resource-details.json').read_text())
match = {
 'Oak tree (Farming)': ('Oak sapling', [(1, 'Tomatoes(5)')]),
 'Willow tree (Farming)': ('Willow sapling', [(1, 'Apples(5)')]),
 'Maple tree (Farming)': ('Maple sapling', [(1, 'Oranges(5)')]),
 'Yew tree (Farming)': ('Yew sapling', [(10, 'Cactus spine')]),
 'Magic tree (Farming)': ('Magic sapling', [(25, 'Coconut')]),
 'Apple tree': ('Apple sapling', [(9, 'Sweetcorn')]),
 'Banana tree (Farming)': ('Banana sapling', [(4, 'Apples(5)')]),
 'Orange tree': ('Orange sapling', [(3, 'Strawberries(5)')]),
 'Curry tree': ('Curry sapling', [(5, 'Bananas(5)')]),
 'Pineapple plant': ('Pineapple sapling', [(10, 'Watermelon')]),
 'Papaya tree': ('Papaya sapling', [(10, 'Pineapple')]),
 'Palm tree': ('Palm sapling', [(15, 'Papaya fruit')]),
 'Dragonfruit tree': ('Dragonfruit sapling', [(15, 'Coconut')]),
 'Calquat tree': ('Calquat sapling', [(8, 'Poison ivy berries')]),
 'Teak tree (Farming)': ('Teak sapling', [(15, 'Limpwurt root')]),
 'Mahogany tree (Farming)': ('Mahogany sapling', [(25, 'Yanillian hops')]),
 'Celastrus tree': ('Celastrus sapling', [(8, 'Potato cactus')]),
 'Redwood tree (Farming)': ('Redwood sapling', [(6, 'Dragonfruit')]),
 'Spirit Tree (Farming)': ('Spirit sapling', [(5, 'Monkey nuts'), (1, 'Monkey bar'), (1, 'Ground tooth')]),
}
def exact_id(name):
    ids = names.get(name.strip().lower().replace(' (', '('), [])
    # Native RuneLite 1.13.2 top-level ItemID.SWEETCORN, rather than BOWL_SWEETCORN.
    if name == 'Sweetcorn':
        assert 5986 in ids
        return 5986
    if name == 'Bittercap mushroom':
        assert names.get('mushroom') == [6004]  # Native top-level ItemID.BITTERCAP_MUSHROOM.
        return 6004
    assert len(ids) == 1, (name, ids)
    return ids[0]
rows = []
for page in pages:
    if not page.get('revisions'):
        continue
    title, revision = page['title'], page['revisions'][0]
    text = revision['slots']['main']['content']
    payment = re.search(r'^\|payment\s*=\s*(.*)$', text, re.M)
    if not payment:
        continue
    source = 'https://oldschool.runescape.wiki/w/' + title.replace(' ', '_') + '?oldid=' + str(revision['revid'])
    if title in match:
        seed, requirements = match[title]
        rows.append({'seedId': exact_id(seed), 'available': True, 'source': source,
                     'evidence': payment.group(1), 'items': [
                         {'itemId': exact_id(name), 'quantity': quantity, 'name': name, 'notesAllowed': True}
                         for quantity, name in requirements]})
    elif payment.group(1).strip().lower() == 'no':
        resource = next((row for row in resources if row.get('farmingSource', '').endswith('oldid=' + str(revision['revid']))), None)
        if resource:
            rows.append({'seedId': resource['itemId'], 'available': False, 'source': source, 'items': []})
assert len(rows) == 34
# Primary Wiki Allotment patch table, retrieved 2026-10-17; levels are independently
# cross-checked at runtime against RuneLite 1.13.2's public FarmingAction enum.
allotments = [
    (5318, 2, 'Compost'), (5319, 1, 'Potatoes(10)'),
    (5324, 1, 'Onions(10)'), (5322, 2, 'Cabbages(10)'),
    (5320, 10, 'Jute fibre'), (5323, 1, 'Apples(5)'),
    (5321, 10, 'Curry leaf'), (22879, 5, 'Jangerberries'),
]
for seed_id, quantity, name in allotments:
    rows.append({'seedId': seed_id, 'available': True,
                 'source': 'https://oldschool.runescape.wiki/w/Allotment_patch',
                 'evidence': 'Allotment seed protection table',
                 'items': [{'itemId': exact_id(name), 'quantity': quantity, 'name': name, 'notesAllowed': True}]})
seed_facts = []
for seed, level, payment in [
    ('Redberry seed', 10, [(4, 'Cabbages(10)')]),
    ('Cadavaberry seed', 22, [(3, 'Tomatoes(5)')]),
    ('Dwellberry seed', 36, [(3, 'Strawberries(5)')]),
    ('Jangerberry seed', 48, [(6, 'Watermelon')]),
    ('Whiteberry seed', 59, [(8, 'Bittercap mushroom')]),
    ('Poison ivy seed', 70, []),
]:
    seed_id = exact_id(seed)
    seed_facts.append({'seedId': seed_id, 'level': level, 'quantity': 1, 'source': 'https://oldschool.runescape.wiki/w/Bush_patch?oldid=14786922'})
    rows.append({'seedId': seed_id, 'available': bool(payment), 'reason': '' if payment else 'Immune to disease; no payment needed',
                 'source': 'https://oldschool.runescape.wiki/w/Bush_patch?oldid=14786922', 'items': [
                     {'itemId': exact_id(name), 'quantity': quantity, 'name': name, 'notesAllowed': True}
                     for quantity, name in payment]})
for seed, level, source in [('Cactus seed', 55, 'Farming/Patch_locations'), ('Potato cactus seed', 64, 'Farming/Patch_locations'), ('Mushroom spore', 53, 'Farming/Patch_locations')]:
    seed_facts.append({'seedId': exact_id(seed), 'level': level, 'quantity': 1, 'source': 'https://oldschool.runescape.wiki/w/' + source})
rows.append({'seedId': exact_id('Potato cactus seed'), 'available': True, 'source': 'https://oldschool.runescape.wiki/w/Farming_contracts',
             'evidence': 'Potato cactus protection: Snape grass ×8; independently corroborated by the Allotment patch Snape grass Uses column',
             'items': [{'itemId': exact_id('Snape grass'), 'quantity': 8, 'name': 'Snape grass', 'notesAllowed': True}]})
seed_facts.append({'seedId': exact_id('Yanillian seed'), 'level': 16, 'quantity': 4, 'source': 'https://oldschool.runescape.wiki/w/Yanillian_seed'})
output = {'version': 1, 'verifiedAt': '2026-10-17', 'payments': sorted(rows, key=lambda row: row['seedId']), 'seedFacts': seed_facts}
(root / 'src/main/resources/com/runewarden/farming-protection-payments.json').write_text(json.dumps(output, indent=2) + '\n')
print(f'{len(rows)} crops: {sum(row["available"] for row in rows)} exact gardener payment models; {len(seed_facts)} extra planting facts')
