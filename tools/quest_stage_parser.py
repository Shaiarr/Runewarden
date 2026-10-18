"""Small deliberately conservative recognizer for Quest Helper stage frontiers.

This is not a Java parser. Any recognized conditional with a mutation syntax that
we do not understand is excluded, including every dependent conditional.
"""
import re


def strip(raw):
    # Keep offsets stable while removing comments and string contents.
    return re.sub(r'//[^\n]*|/\*.*?\*/|"(?:\\.|[^"\\])*"',
                  lambda match: ' ' * len(match.group()), raw, flags=re.S)


def arguments(raw, start):
    depth, result, begin = 1, [], start
    for index in range(start, len(raw)):
        if raw[index] == '(':
            depth += 1
        elif raw[index] == ')':
            depth -= 1
            if depth == 0:
                result.append(raw[begin:index].strip())
                return result
        elif raw[index] == ',' and depth == 1:
            result.append(raw[begin:index].strip())
            begin = index + 1
    return []


def frontiers(raw, leaves):
    """Return only exact literal stages whose complete branch frontier is known."""
    graph, ambiguous = {}, set()
    for match in re.finditer(r'(?<![\w.])(\w+)\s*=\s*new ConditionalStep\s*\(', raw):
        symbol = match.group(1)
        args = arguments(raw, match.end())
        if symbol in graph:
            ambiguous.add(symbol)  # Field/local collisions cannot be scoped by this recognizer.
        if len(args) >= 2:
            graph[symbol] = [args[1]]
    for match in re.finditer(r'(?<![\w.])(\w+)\s*\.\s*addStep\s*\(', raw):
        symbol = match.group(1)
        if symbol not in graph:
            continue
        args = arguments(raw, match.end())
        if len(args) >= 2:
            graph[symbol].append(args[1])
        else:
            ambiguous.add(symbol)  # addStep(otherConditional) is not the two-argument form.

    # A cast receiver bypasses the simple NAME.addStep recognizer above. Do not
    # accidentally treat its default child as the only possible branch.
    for match in re.finditer(r'\(\s*\(\s*[\w.<>]+\s*\)\s*(?:this\s*\.\s*)?(\w+)\s*\)\s*\.\s*addStep\s*\(', raw):
        ambiguous.add(match.group(1))
    # Qualified receiver mutations are also outside the unqualified recognizer.
    for match in re.finditer(r'\bthis\s*\.\s*(\w+)\s*\.\s*addStep\s*\(', raw):
        ambiguous.add(match.group(1))

    def resolve(symbol, visiting):
        symbol = re.sub(r'^this\s*\.\s*', '', symbol)
        if symbol in visiting or symbol in ambiguous:
            return None
        if symbol in graph:
            values = [resolve(target, visiting | {symbol}) for target in graph[symbol]]
            return min(values) if values and all(value is not None for value in values) else None
        return leaves.get(symbol)

    method = re.search(r'\bloadSteps\s*\([^)]*\)\s*\{', raw)
    if not method:
        return {}
    start, depth, end = method.end(), 1, method.end()
    while end < len(raw) and depth:
        if raw[end] == '{':
            depth += 1
        elif raw[end] == '}':
            depth -= 1
        end += 1
    stages = {}
    for stage, symbol in re.findall(r'\bsteps\.put\(\s*(\d+)\s*,\s*([\w.]+)\s*\)', raw[start:end]):
        value = resolve(symbol, set())
        # Map.put uses the final assignment, including an unresolved final value.
        # Keeping an earlier resolved value after a later unknown put is unsafe.
        stage = int(stage)
        if value is None:
            stages.pop(stage, None)
        else:
            stages[stage] = value
    return stages
