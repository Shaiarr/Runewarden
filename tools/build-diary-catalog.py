"""Extract diary task observations and authored references from pinned Quest Helper.

This is a conservative source recognizer, not a Java evaluator. Unsupported
requirements and observations are retained as explicit references, never passed.
Usage: python3 tools/build-diary-catalog.py QUEST_HELPER_ROOT API_SOURCES_JAR
"""
from pathlib import Path
import json, re, subprocess, sys, zipfile

root = Path(sys.argv[1]); project = Path(__file__).resolve().parents[1]
java = root / 'src/main/java/com/questhelper'
commit = subprocess.check_output(['git', '-C', str(root), 'rev-parse', 'HEAD'], text=True).strip()
def mask(s):
    return re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"', lambda m: ' ' * len(m[0]), s, flags=re.S)

constants = {}
native_quests = {}
with zipfile.ZipFile(sys.argv[2]) as archive:
    for kind in ['VarPlayerID', 'VarbitID', 'ItemID']:
        text = archive.read('net/runelite/api/gameval/' + kind + '.java').decode()
        depth = 0
        for line in mask(text).splitlines():
            if depth == 1:
                for key, value in re.findall(r'public static final int (\w+) = (\d+)', line): constants[kind + '.' + key] = int(value)
            depth += line.count('{') - line.count('}')
    native_quests = {k: v for k, v in re.findall(r'\b(\w+)\(\d+,\s*"([^"]+)"', archive.read('net/runelite/api/Quest.java').decode())}

def args(s, start):
    m = mask(s); depth = 1; begin = start; out = []
    for i in range(start, len(s)):
        if m[i] in '([{': depth += 1
        elif m[i] in ')]}':
            depth -= 1
            if depth == 0: return out + [s[begin:i].strip()]
        elif m[i] == ',' and depth == 1: out.append(s[begin:i].strip()); begin = i + 1
    return []

def strings(s):
    out = []
    for v in re.findall(r'"(?:\\.|[^"\\])*"', s):
        try: out.append(json.loads(v))
        except ValueError: pass
    return ' '.join(out).strip()

def number(v):
    return int(v) if re.fullmatch(r'-?\d+', v.strip()) else constants.get(v.strip())

qraw = (java / 'questinfo/QuestHelperQuest.java').read_text()
quest_names = {m[1]: m[3] for m in re.finditer(r'\b(\w+)\(new (\w+)\([^\n]*?,\s*"([^"]+)"', qraw)}
for key, native in re.findall(r'\b(\w+)\(new \w+\([^\n]*?,\s*Quest\.(\w+)', qraw):
    if native in native_quests: quest_names[key] = native_quests[native]
diary_vars = {k: constants.get(v, int(v) if v.isdigit() else None) for k, v in
    re.findall(r'(ACHIEVEMENT_DIARY_\w+)\((VarbitID\.\w+|\d+)\)', (java / 'questinfo/QuestVarbits.java').read_text())}
records = []
notices = set()
for source in sorted((java / 'helpers/achievementdiaries').glob('*/*.java')):
    raw = source.read_text(); clean = mask(raw)
    meta = re.search(r'\b(\w+)\(new ' + source.stem + r'\(\),\s*"([^"]+)",\s*QuestVarbits\.(\w+)', qraw)
    if not meta or not meta[3].startswith('ACHIEVEMENT_DIARY_'): continue
    notice = re.match(r'/\*.*?\*/', raw, re.S)
    if notice: notices.add(notice[0])
    title = meta[2]; tier = next(t for t in ['Easy', 'Medium', 'Hard', 'Elite'] if t in title)
    region = title.replace(tier, '').replace('Diary', '').strip()
    if region == 'Western': region = 'Western Provinces'
    if region == 'Lumbridge & Draynor': region = 'Lumbridge & Draynor'
    if region == 'Kourend & Kebos': region = 'Kourend & Kebos'
    relative = source.relative_to(root).as_posix()
    url = 'https://github.com/Zoinkwiz/quest-helper/blob/' + commit + '/' + relative
    assignments = {}
    for match in re.finditer(r'\b(\w+)\s*=\s*new\s+(\w+)\s*\(', clean):
        assignments[match[1]] = (match[2], args(raw, match.end()))

    def requirement(expression, seen=None):
        seen = set() if seen is None else seen
        expression = expression.strip()
        quantity = re.search(r'\.quantity\(\s*(\d+)\s*\)', expression)
        base = expression.split('.')[0]
        if base in assignments and base not in seen:
            typ, a = assignments[base]
            out = parse_requirement(typ, a, seen | {base})
            if quantity and out.get('kind') == 'item': out['value'] = int(quantity[1])
            return out
        inline = re.match(r'new\s+(\w+)\s*\(', expression)
        if inline: return parse_requirement(inline[1], args(expression, inline.end()), seen)
        return {'kind': 'reference', 'label': strings(expression) or re.sub(r'([a-z])([A-Z])', r'\1 \2', base)}

    def parse_requirement(typ, a, seen):
        if typ == 'SkillRequirement' and len(a) >= 2:
            level = number(a[1]); skill = a[0].replace('Skill.', '')
            if level is not None: return {'kind': 'skill', 'key': skill, 'value': level, 'boostable': len(a) > 2 and a[2] == 'true', 'label': str(level) + ' ' + skill.title()}
        if typ == 'QuestRequirement' and len(a) >= 1:
            key = quest_names.get(a[0].replace('QuestHelperQuest.', ''))
            if key: return {'kind': 'quest', 'key': key, 'started': len(a) > 1 and 'IN_PROGRESS' in a[1], 'label': key}
        if typ == 'ItemRequirement' and len(a) >= 2:
            item = number(a[1]); count = number(a[2]) if len(a) > 2 else 1
            return {'kind': 'item', 'key': strings(a[0]), 'itemId': item if item and item > 0 else -1,
                    'value': count if count and count > 0 else 1, 'quantityKnown': count is not None and count > 0, 'label': strings(a[0])}
        if typ in ['Conditions', 'ItemRequirements']:
            children = [requirement(x, seen) for x in a if not x.startswith('LogicType.') and not x.startswith('"')]
            return {'kind': 'choice' if any('LogicType.OR' in x for x in a) else 'all', 'children': children, 'label': strings(', '.join(a)) or 'Requirement alternatives'}
        if typ == 'SpellbookRequirement' and a: return {'kind': 'spellbook', 'key': a[0].split('.')[-1], 'label': a[0].split('.')[-1].title() + ' spellbook'}
        return {'kind': 'reference', 'label': strings(','.join(a)) or typ.replace('Requirement', '')}

    def observation(symbol):
        if symbol not in assignments: return None
        typ, a = assignments[symbol]
        if typ == 'VarplayerRequirement' and len(a) >= 3 and a[1] in ['false', 'true']:
            var = number(a[0]); bit = number(a[2])
            if var is not None and bit is not None and 0 <= bit < 32:
                return {'kind': 'varpBit', 'variable': var, 'bit': bit, 'incompleteValue': a[1] == 'true'}
        if typ == 'VarbitRequirement' and len(a) >= 2:
            var = number(a[0]); value = number(a[1]); operation = a[2].split('.')[-1] if len(a) > 2 else 'EQUAL'
            if var is not None and value is not None and operation in ['EQUAL', 'LESS_EQUAL', 'LESS', 'GREATER_EQUAL', 'GREATER', 'NOT_EQUAL']:
                return {'kind': 'varbit', 'variable': var, 'value': value, 'incompleteOperation': operation}
        return None

    def step(symbol):
        if symbol not in assignments: return None
        typ, a = assignments[symbol]
        if not typ.endswith('Step') or typ == 'ConditionalStep': return None
        text_index = next((i for i, x in enumerate(a) if x.startswith('"')), None)
        if text_index is None: return None
        result = {'text': strings(a[text_index]), 'requirements': [requirement(x) for x in a[text_index + 1:] if x not in ['true', 'false']]}
        point = re.search(r'new WorldPoint\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)', ','.join(a))
        if point: result['location'] = [int(x) for x in point.groups()]
        return result

    tasks = []
    for display in re.finditer(r'\b(\w+)\.setDisplayCondition\(', clean):
        symbol = display[1]
        declarations = list(re.finditer(r'\bPanelDetails\s+' + symbol + r'\s*=', clean[:display.start()]))
        if not declarations: continue
        constructor = re.search(r'new PanelDetails\(', clean[declarations[-1].end():display.start()])
        if not constructor: continue
        begin = declarations[-1].end() + constructor.end()
        a = args(raw, begin)
        if len(a) < 2: continue
        condition_text = args(raw, display.end())[0]
        candidates = [(s, observation(s)) for s in re.findall(r'\b\w+\b', condition_text)]
        candidates = [(s, o) for s, o in candidates if o]
        bit_candidates = [(s, o) for s, o in candidates if o['kind'] == 'varpBit']
        if len(bit_candidates) == 1: candidates = bit_candidates
        chosen = candidates[0] if len(candidates) == 1 else (symbol, None)
        symbols = re.findall(r'\b\w+\b', a[1]); steps = [step(x) for x in symbols]; steps = [x for x in steps if x]
        reqs = [requirement(x) for x in a[2:]]
        if '?' in raw[declarations[-1].end():begin]:
            reqs = [dict(r, kind='reference', label='Account-specific: ' + r['label']) if r['kind'] not in ['item', 'reference'] else r for r in reqs]
            reqs.append({'kind': 'reference', 'label': 'Account-type-specific preparation: check the linked source'})
        task = {'id': meta[1].lower() + ':' + chosen[0], 'title': strings(a[0]), 'steps': steps,
                'requirements': reqs, 'observation': chosen[1]}
        existing = next((t for t in tasks if t['id'] == task['id']), None)
        if existing:
            if 'variants' not in existing: existing['variants'] = [{'title': existing['title'], 'steps': list(existing['steps']), 'requirements': list(existing['requirements'])}]
            existing['variants'].append({'title': task['title'], 'steps': list(steps), 'requirements': list(reqs)})
            existing['requirements'] = [r for r in existing['requirements'] if r in reqs]
            existing['requirements'].append({'kind': 'reference', 'label': 'Route-dependent supplies: choose the source route before preparing'})
            existing['steps'] += [s for s in steps if s not in existing['steps']]
        else: tasks.append(task)
    # The source's easy Ardougne helper groups ten native tasks in one panel.
    if not tasks:
        for condition, conditional in re.findall(r'\bdiary\.addStep\(\s*(\w+)\s*,\s*(\w+)\s*\)', raw):
            if conditional not in assignments: continue
            _, a = assignments[conditional]; s = step(a[1]) if len(a) > 1 else None
            if s: tasks.append({'id': meta[1].lower() + ':' + condition, 'title': s['text'], 'steps': [s], 'requirements': s['requirements'], 'observation': observation(condition)})
    rewards = [strings(args(raw, m.end())[0]) for m in re.finditer(r'new UnlockReward\(', clean)]
    item_rewards = [strings(args(raw, m.end())[0]) for m in re.finditer(r'new ItemReward\(', clean)]
    general = []
    method = re.search(r'getGeneralRequirements\s*\(\s*\)\s*\{', clean)
    if method:
        body = raw[method.end():]; end = body.find('\n\t}')
        body = body[:end] if end >= 0 else body[:2000]
        returned = re.search(r'return\s+(?:List\.of|Arrays\.asList)\s*\(', mask(body))
        if returned: general = [requirement(x) for x in args(body, returned.end())]
    records.append({'id': meta[1].lower(), 'name': title, 'region': region, 'tier': tier,
        'claimedVariable': diary_vars.get(meta[3]), 'tasks': tasks, 'requirements': general,
        'rewards': rewards + item_rewards, 'source': url,
        'wiki': 'https://oldschool.runescape.wiki/w/' + region.replace(' & ', '_%26_').replace(' ', '_') + '_Diary'})

if len(records) != 48: raise SystemExit('Expected 48 diaries, got ' + str(len(records)))
payload = {'version': 1, 'sourceCommit': commit, 'sourceLicense': 'BSD-2-Clause',
    'observationRule': 'Task complete is the negation of the exact source incomplete condition. Unresolved observations stay unknown.', 'diaries': records}
(project / 'src/main/resources/com/runewarden/diary-catalog.json').write_text(json.dumps(payload, indent=2) + '\n')
(project / 'src/main/resources/com/runewarden/licenses/diary-source-BSD-2-Clause.txt').write_text(
    'Diary task references adapted from Quest Helper at ' + commit + '\nhttps://github.com/Zoinkwiz/quest-helper\n\n' + '\n\n'.join(sorted(notices)) + '\n')
print({'diaries': len(records), 'tasks': sum(len(x['tasks']) for x in records), 'observedTasks': sum(t['observation'] is not None for x in records for t in x['tasks']), 'unknown': [(x['name'], t['title']) for x in records for t in x['tasks'] if t['observation'] is None]})
