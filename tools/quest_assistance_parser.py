"""Conservative data extractor for the pinned BSD-licensed Quest Helper sources.

This deliberately understands a finite Java subset, not arbitrary Java. Unknown
predicates remain explicit unknown nodes; they must never become false defaults.
Only straight-line, uniquely assigned declarations are eligible.
"""
import copy
import json
import re
from collections import Counter
from quest_stage_parser import strip


def split_args(raw):
    result, begin, depth = [], 0, 0
    mask = strip(raw)
    for i, char in enumerate(mask):
        if char in '([{': depth += 1
        elif char in ')]}': depth -= 1
        elif char == ',' and depth == 0:
            result.append(raw[begin:i].strip()); begin = i + 1
    result.append(raw[begin:].strip())
    return result if result != [''] else []


def calls(raw, pattern):
    mask = strip(raw)
    for match in re.finditer(pattern, mask):
        begin, end, depth = match.end(), match.end(), 1
        while end < len(mask) and depth:
            if mask[end] == '(': depth += 1
            elif mask[end] == ')': depth -= 1
            end += 1
        if depth == 0:
            yield match, split_args(raw[begin:end - 1]), end


def method(raw, name):
    mask = strip(raw)
    match = re.search(r'\b' + name + r'\s*\([^)]*\)\s*\{', mask)
    if not match: return ''
    begin, end, depth = match.end(), match.end(), 1
    while end < len(mask) and depth:
        if mask[end] == '{': depth += 1
        elif mask[end] == '}': depth -= 1
        end += 1
    return raw[begin:end - 1]


def literal(raw):
    parts = re.findall(r'"(?:\\.|[^"\\])*"', raw)
    if not parts or re.sub(r'"(?:\\.|[^"\\])*"|\s|\+', '', raw): return None
    try: return ''.join(json.loads(part) for part in parts)
    except ValueError: return None


UNKNOWN = {'kind': 'unknown'}


def java_constants(raw):
    """Keep gameval nested namespaces (ItemID.Cert/Placeholder) distinct."""
    result, scopes, pending = {}, [], None
    for token in re.finditer(r'\bclass\s+(\w+)|[{}]|\bpublic\s+static\s+final\s+int\s+(\w+)\s*=\s*(-?\d+)', strip(raw)):
        if token.group(1): pending = token.group(1)
        elif token.group(2):
            prefix = '.'.join(scope for scope in scopes if scope)
            if prefix: result[prefix + '.' + token.group(2)] = int(token.group(3))
        elif token.group() == '{': scopes.append(pending); pending = None
        elif scopes: scopes.pop()
    return result


class Extractor:
    def __init__(self, raw, constants, leaves):
        self.raw, self.constants, self.leaves = raw, constants, leaves
        self.mask = strip(raw)
        self.assignments = {}
        self.duplicates = set()
        # Constructors and expressions must be uniquely assigned. Java local/field
        # collisions cannot safely be scoped by this finite recognizer.
        for match in re.finditer(r'(?<![\w.])(?:this\s*\.\s*)?(\w+)\s*=(?!=)', self.mask):
            name, begin = match.group(1), match.end()
            depth, end = 0, begin
            while end < len(self.mask):
                char = self.mask[end]
                if char in '([{': depth += 1
                elif char in ')]}': depth -= 1
                elif char == ';' and depth == 0: break
                end += 1
            if name in self.assignments: self.duplicates.add(name)
            self.assignments[name] = raw[begin:end].strip()
            if self.mask[:match.start()].count('{') - self.mask[:match.start()].count('}') not in {1, 2}:
                self.duplicates.add(name)
        self.mutations = {}
        for match, args, end in calls(raw, r'(?<![\w.])(\w+)\s*\.\s*(\w+)\s*\('):
            self.mutations.setdefault(match.group(1), []).append((match.group(2), args))
            if match.group(2) == 'addStep' and self.mask[:match.start()].count('{') - self.mask[:match.start()].count('}') != 2:
                self.duplicates.add(match.group(1))
        self.conditional = {}
        for match, args, end in calls(raw, r'(?<![\w.])(\w+)\s*=\s*new ConditionalStep\s*\('):
            name = match.group(1)
            if len(args) == 2:
                self.conditional[name] = {'default': args[1], 'branches': [], 'safe': True}
            else:
                self.conditional[name] = {'safe': False}
        for name, node in self.conditional.items():
            # Locks, IDs, blockers and manual-state semantics require the full
            # upstream runtime. Do not approximate them with a positional guess.
            if name in self.duplicates: node['safe'] = False
            predicates = set()
            for action, args in self.mutations.get(name, []):
                if action == 'addStep' and len(args) == 2:
                    predicate_key = re.sub(r'\s+', '', args[0])
                    # LinkedHashMap replaces a reused Requirement key. This
                    # finite parser cannot prove Java object identity for every
                    # expression, so repeated keys make the node ambiguous.
                    if predicate_key in predicates: node['safe'] = False
                    predicates.add(predicate_key)
                    node.setdefault('branches', []).append(args)
                elif action not in {'addText', 'setShouldConsiderSubStepsForSidebarHighlight'}:
                    node['safe'] = False
            if re.search(r'\bthis\s*\.\s*' + name + r'\s*\.\s*addStep\s*\(', self.mask): node['safe'] = False
            if re.search(r'\(\s*\(\s*[\w.<>]+\s*\)\s*(?:this\s*\.\s*)?' + name + r'\s*\)\s*\.', self.mask): node['safe'] = False
        self.cache = {}

    def number(self, raw):
        raw = raw.strip()
        if re.fullmatch(r'-?\d+', raw): return int(raw)
        return self.constants.get(raw)

    def ids(self, raw, visiting=frozenset()):
        number = self.number(raw)
        if number is not None: return [number] if number > 0 else None
        match = re.fullmatch(r'(?:new int\[\]\s*\{|List\.of\(|Arrays\.asList\()(.*)[})]', raw, re.S)
        if match:
            numbers = [self.number(value) for value in split_args(match.group(1))]
            return numbers if numbers and all(x is not None and x > 0 for x in numbers) else None
        if raw in visiting or raw in self.duplicates: return None
        if raw in self.assignments: return self.ids(self.assignments[raw], visiting | {raw})
        return None

    def point(self, raw):
        match = re.fullmatch(r'new WorldPoint\s*\((.*)\)', raw, re.S)
        if not match: return None
        values = [self.number(x) for x in split_args(match.group(1))]
        return values if len(values) == 3 and all(x is not None for x in values) else None

    def zone(self, raw, visiting=frozenset()):
        point = self.point(raw)
        if point: return point + point
        if raw in visiting or raw in self.duplicates: return None
        if raw in self.assignments: return self.zone(self.assignments[raw], visiting | {raw})
        match = re.fullmatch(r'new Zone\s*\((.*)\)', raw, re.S)
        if not match: return None
        args = split_args(match.group(1))
        points = [self.point(value) for value in args]
        if len(points) in {1, 2} and all(points):
            return points[0] + points[-1]
        return None

    def item(self, expression, visiting=frozenset()):
        # These clone/display-only modifiers do not change ownership semantics.
        expression = re.sub(r'\.(?:highlighted|isNotConsumed|canBeObtainedDuringQuest)\(\)', '', expression)
        quantity = None
        match = re.fullmatch(r'(.+)\.quantity\((\d+)\)', expression)
        if match: expression, quantity = match.group(1), int(match.group(2))
        if expression in visiting or expression in self.duplicates: return None
        if expression in self.assignments:
            result = self.item(self.assignments[expression], visiting | {expression})
            if not result: return None
            for action, args in self.mutations.get(expression, []):
                if action == 'addAlternates':
                    for argument in args:
                        ids = self.ids(argument)
                        if not ids: return None
                        result['ids'] = list(dict.fromkeys(result['ids'] + ids))
                elif action in {'setMustBeEquipped', 'setMustBeUnequipped'} and args in [['true'], ['false']]:
                    if args == ['false']: return None
                    expected = 'equipment' if action == 'setMustBeEquipped' else 'inventory'
                    if result['container'] not in {'carried', expected}: return None
                    result['container'] = expected
                elif action not in {'setTooltip', 'setConditionToHide', 'canBeObtainedDuringQuest',
                                    'isNotConsumed', 'setHighlightInInventory', 'setDisplayItemId', 'addToText'}:
                    return None
            if quantity is not None: result['quantity'] = quantity
            return result
        match = re.fullmatch(r'new ItemRequirement\s*\((.*)\)', expression, re.S)
        if not match: return None
        args = split_args(match.group(1))
        if len(args) not in {2, 3, 4}: return None
        name, ids = literal(args[0]), self.ids(args[1])
        count = self.number(args[2]) if len(args) >= 3 else 1
        if not name or not ids or count is None or count <= 0: return None
        if len(args) == 4 and args[3] not in {'true', 'false'}: return None
        return {'kind': 'item', 'name': name, 'ids': ids, 'quantity': quantity or count,
                'container': 'equipment' if len(args) == 4 and args[3] == 'true' else 'carried'}

    def predicate(self, expression, visiting=frozenset()):
        expression = expression.strip()
        if expression in visiting or expression in self.duplicates: return dict(UNKNOWN)
        item = self.item(expression)
        if item: return item
        if expression in self.assignments:
            # Mutating a requirement's value/bit/window changes its meaning.
            if any(action not in {'setDisplayText'} for action, args in self.mutations.get(expression, [])):
                return dict(UNKNOWN)
            return self.predicate(self.assignments[expression], visiting | {expression})
        match = re.fullmatch(r'(and|or|nor|not)\s*\((.*)\)', expression, re.S)
        if match:
            children = [self.predicate(value, visiting) for value in split_args(match.group(2))]
            if not children or (match.group(1) == 'not' and len(children) != 1): return dict(UNKNOWN)
            return {'kind': match.group(1), 'children': children}
        match = re.fullmatch(r'new Conditions\s*\((.*)\)', expression, re.S)
        if match:
            args = split_args(match.group(1)); kind = 'and'
            if args and args[0].startswith('LogicType.'):
                kind = args.pop(0).split('.')[-1].lower()
            if kind not in {'and', 'or', 'nor'} or not args: return dict(UNKNOWN)
            return {'kind': kind, 'children': [self.predicate(value, visiting) for value in args]}
        match = re.fullmatch(r'new (VarbitRequirement|VarplayerRequirement)\s*\((.*)\)', expression, re.S)
        if match:
            args = split_args(match.group(2))
            if len(args) not in {2, 3}: return dict(UNKNOWN)
            variable, value = self.number(args[0]), self.number(args[1])
            operation = args[2].replace('Operation.', '') if len(args) == 3 else 'EQUAL'
            if variable is None or value is None or operation not in {'EQUAL', 'NOT_EQUAL', 'LESS', 'LESS_EQUAL', 'GREATER', 'GREATER_EQUAL'}:
                return dict(UNKNOWN)
            return {'kind': 'varbit' if match.group(1) == 'VarbitRequirement' else 'varp',
                    'variable': variable, 'value': value, 'operation': operation}
        match = re.fullmatch(r'new ZoneRequirement\s*\((.*)\)', expression, re.S)
        if match:
            args = split_args(match.group(1)); negate = False
            if args and args[0] in {'true', 'false'}: negate = args.pop(0) == 'false'
            zones = [self.zone(value) for value in args]
            if zones and all(zones): return {'kind': 'zone', 'zones': zones, 'negate': negate}
        return dict(UNKNOWN)

    def node(self, symbol, visiting=frozenset()):
        symbol = symbol.strip()
        # Explicit this.field is the field, not a same-named local conditional.
        if re.fullmatch(r'this\s*\.\s*\w+', symbol):
            key = re.sub(r'^this\s*\.\s*', '', symbol)
            if key in self.duplicates: return dict(UNKNOWN)
            if any(action in {'setLockingCondition', 'setBlocker', 'setId', 'setLocked'} for action, args in self.mutations.get(key, [])):
                return dict(UNKNOWN)
            return {'step': self.leaves[key]} if key in self.leaves else dict(UNKNOWN)
        if symbol in visiting: return dict(UNKNOWN)
        if symbol in self.conditional:
            data = self.conditional[symbol]
            if not data['safe']: return dict(UNKNOWN)
            return {'fallback': self.node(data['default'], visiting | {symbol}),
                    'branches': [{'when': self.predicate(args[0]), 'then': self.node(args[1], visiting | {symbol})}
                                 for args in data['branches']]}
        if symbol in self.duplicates or any(action in {'setLockingCondition', 'setBlocker', 'setId', 'setLocked'} for action, args in self.mutations.get(symbol, [])):
            return dict(UNKNOWN)
        return {'step': self.leaves[symbol]} if symbol in self.leaves else dict(UNKNOWN)

    def stages(self):
        raw = method(self.raw, 'loadSteps'); mask = strip(raw)
        result = {}
        top_returns = [match for match in re.finditer(r'\breturn\b', mask)
                       if mask[:match.start()].count('{') - mask[:match.start()].count('}') == 0]
        steps_return = len(top_returns) == 1 and re.match(r'return\s+steps\s*;', mask[top_returns[0].start():]) is not None
        for match, args, end in calls(raw, r'\bsteps\s*\.\s*put\s*\('):
            # Reject puts nested under if/for/while; they are not unconditional.
            depth = mask[:match.start()].count('{') - mask[:match.start()].count('}')
            if not steps_return or len(args) != 2 or depth != 0 or not re.fullmatch(r'\d+', args[0]): continue
            result[args[0]] = self.node(args[1])
        # Literal immutable map builders have the same explicit stage semantics.
        # Parse only a returned complete builder chain, not arbitrary .put calls.
        for match in re.finditer(r'\breturn\s+new ImmutableMap\.Builder\s*<[^;]+?>\s*\(\)', mask):
            if len(top_returns) != 1 or match.start() != top_returns[0].start(): continue
            tail = raw[match.end():raw.find(';', match.end())]
            residue = strip(tail)
            puts = list(calls(tail, r'\.put\s*\('))
            for put, args, end in reversed(puts): residue = residue[:put.start()] + ' ' * (end - put.start()) + residue[end:]
            if not re.fullmatch(r'\s*\.build\s*\(\)\s*', residue): continue
            for put, args, end in puts:
                if len(args) == 2 and re.fullmatch(r'\d+', args[0]): result[args[0]] = self.node(args[1])
        # SimpleQuestHelper dispatches one conditional tree for every quest stage.
        # The runtime still requires a native matching quest identity/status.
        if not raw:
            single = method(self.raw, 'loadStep')
            returns = re.findall(r'\breturn\s+([\w.]+)\s*;', strip(single))
            if len(returns) == 1: result['*'] = self.node(returns[0])
        return result

    def hints(self, guide_steps):
        result = {}
        constructors = {}
        for match, args, end in calls(self.raw, r'(?<![\w.])(\w+)\s*=\s*new (NpcStep|ObjectStep|ItemStep|DetailedQuestStep)\s*\('):
            constructors.setdefault(match.group(1), []).append((match.group(2), args))
        for match, args, end in calls(self.raw, r'(?<![\w.])(\w+)\s*=\s*new PuzzleWrapperStep\s*\('):
            if len(args) != 2: continue
            inner = re.fullmatch(r'new (NpcStep|ObjectStep|ItemStep|DetailedQuestStep)\s*\((.*)\)', args[1], re.S)
            if inner: constructors.setdefault(match.group(1), []).append((inner.group(1), split_args(inner.group(2))))
        for symbol, step in guide_steps.items():
            rows = constructors.get(symbol, [])
            if len(rows) != 1: continue
            kind, args = rows[0]; hint = {}
            text_index = next((i for i, value in enumerate(args) if literal(value) is not None), None)
            if text_index is None: continue
            if kind in {'NpcStep', 'ObjectStep'} and len(args) > 2:
                ids = self.ids(args[1])
                if ids: hint['npcIds' if kind == 'NpcStep' else 'objectIds'] = ids
            items = [self.item(value) for value in args[text_index + 1:]]
            items = [item for item in items if item]
            if items: hint['items'] = items
            choices, extra = [], []
            for action, values in self.mutations.get(symbol, []):
                if action in {'addDialogStep', 'addDialogSteps', 'addText'}:
                    values = [literal(value) for value in values]
                    values = [value for value in values if value]
                    (extra if action == 'addText' else choices).extend(values)
            if choices: hint['dialogue'] = list(dict.fromkeys(choices))
            if extra: hint['notes'] = list(dict.fromkeys(extra))
            if hint: result[step['id']] = hint
        return result
