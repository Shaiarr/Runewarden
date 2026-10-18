#!/usr/bin/env python3
"""Build the bundled CA reference from saved Wiki Bucket and revision API responses.

Usage: python3 tools/build-combat-achievements.py --cache /path/to/reference-cache
The cache contains ca-bucket-all.json, ca-wiki-basics.json,
ca-reward-thresholds.json and ca-wiki-pages/*.json. No generated Java is executed.
"""
import argparse
import collections
import html
import json
import pathlib
import re
import urllib.parse


def plain(value):
    value = re.sub(r"<ref\b[^>]*>.*?</ref>|<ref\b[^>]*/>", "", value, flags=re.S | re.I)
    value = re.sub(r"<!--.*?-->", "", value, flags=re.S)
    value = re.sub(r"\[\[(?:File|Image):[^\n]*?\]\]", "", value, flags=re.I)
    value = re.sub(r"\[\[([^]|]+)\|([^]]+)\]\]", lambda m: m[2], value)
    value = re.sub(r"\[\[([^]]+)\]\]", lambda m: m[1].split("#", 1)[0], value)
    for _ in range(8):
        def template(match):
            args = match[1].split("|")
            name = args[0].strip().lower()
            if name == "scp" and len(args) >= 3:
                return args[2].strip() + " " + args[1].strip()
            if name in {"plink", "plinkt", "plinkp", "plural", "nowrap"} and len(args) > 1:
                return args[1].strip()
            if name == "sic" and len(args) > 1:
                return args[1].strip()
            return ""
        next_value = re.sub(r"\{\{([^{}]*)\}\}", template, value)
        if next_value == value:
            break
        value = next_value
    value = re.sub(r"\[(https?://[^ ]+) ([^]]+)\]", lambda m: m[2], value)
    value = re.sub(r"<[^>]*>", "", value).replace("'''", "").replace("''", "")
    value = re.sub(r"^[:*#;]+\s*", "", value, flags=re.M)
    value = re.sub(r"^={2,}[^\n]*={2,}\s*$", "", value, flags=re.M)
    value = re.sub(r"\{\|.*?\|\}", "", value, flags=re.S)
    value = html.unescape(value)
    return re.sub(r"\s+", " ", value).strip()


def normalize(value):
    return re.sub(r"[^a-z0-9]", "", value.lower().removeprefix("the "))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", type=pathlib.Path, required=True)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[1]
    rows = json.loads((args.cache / "ca-bucket-all.json").read_text())["bucket"]
    pages = {}
    for file in (args.cache / "ca-wiki-pages").glob("*.json"):
        for page in json.loads(file.read_text())["query"]["pages"].values():
            pages[normalize(page["title"])] = page
    guides = json.loads((root / "src/main/resources/com/runewarden/guides.json").read_text())["entries"]
    bosses = {normalize(g["title"]): g["id"] for g in guides if g["category"] in {"boss", "waves"}}
    quest_names = {g["title"] for g in guides if g["category"] == "quest"}
    item_names = json.loads((root / "src/main/resources/com/runewarden/guide-item-names.json").read_text())
    item_index = {name.lower(): ids for name, ids in item_names.items()}
    aliases = {"Crystalline Hunllef": "The Gauntlet", "Corrupted Hunllef": "The Corrupted Gauntlet",
               "TzKal-Zuk": "Inferno", "Theatre of Blood: Entry Mode": "Theatre of Blood",
               "Chambers of Xeric: Challenge Mode": "Chambers of Xeric: Challenge Mode"}
    thresholds = [int(value.strip()) for value in json.loads((args.cache / "ca-reward-thresholds.json").read_text())["expandtemplates"]["wikitext"].split("|")]
    tiers = ["Easy", "Medium", "Hard", "Elite", "Master", "Grandmaster"]
    tasks = []
    for row in sorted(rows, key=lambda item: item["id"]):
        page = pages.get(normalize(row["name"]), {})
        revision = page.get("revisions", [{}])[0]
        source = revision.get("slots", {}).get("main", {}).get("*", "")
        # Strip the balanced infobox, retaining task-specific introductory restrictions.
        depth = 0
        end = 0
        for token in re.finditer(r"\{\{|\}\}", source):
            depth += 1 if token.group() == "{{" else -1
            if depth == 0:
                end = token.end()
                break
        body = source[end:]
        body = re.split(r"==\s*(?:Other tasks|References|Trivia|Gallery|Changes|See also)\s*==", body, flags=re.I)[0]
        strategy = re.split(r"==\s*(?:Strategy|Strategies|Method|Methods)\s*==", body, maxsplit=1, flags=re.I)
        intro = strategy[0]
        method = strategy[1] if len(strategy) > 1 else ""
        paragraphs = [plain(p) for p in re.split(r"\n\s*\n", method)]
        paragraphs = [p for p in paragraphs if p and len(p) >= 15 and "{{" not in p and "}}" not in p]
        context = [plain(p) for p in re.split(r"\n\s*\n", intro)]
        context = [p for p in context if p and not p.startswith("#REDIRECT") and len(p) > 20]
        requirements = []
        clean_intro = plain(intro)
        for skill in ["Attack", "Strength", "Defence", "Ranged", "Prayer", "Magic", "Slayer", "Sailing"]:
            match = re.search(r"(?:requires?|requiring)\s+(?:a\s+)?(?:level\s+)?(\d+)\s+" + skill + r"\b", clean_intro, re.I)
            if match:
                requirements.append({"kind": "skill", "key": skill, "value": int(match[1]), "boostable": "boosts can be used" in clean_intro.lower()})
        for quest in quest_names:
            for sentence in re.split(r"(?<=[.!?])\s+", clean_intro):
                if quest in sentence and re.search(r"completion of.*required|required.*completion of", sentence, re.I):
                    requirements.append({"kind": "quest", "key": quest})
                    break
        wiki_title = page.get("title", row["name"])
        url = "https://oldschool.runescape.wiki/w/" + urllib.parse.quote(wiki_title.replace(" ", "_"), safe="_()/:'")
        if revision.get("revid"):
            url += "?oldid=" + str(revision["revid"])
        boss = row.get("monster") or "General"
        guide = bosses.get(normalize(aliases.get(boss, boss)))
        if guide is None:
            candidates = [(key, value) for key, value in bosses.items() if key and (key in normalize(boss) or normalize(boss) in key)]
            if candidates:
                guide = min(candidates, key=lambda pair: abs(len(pair[0]) - len(normalize(boss))))[1]
        tasks.append({"id": row["id"], "name": row["name"], "difficulty": row["tier"], "points": tiers.index(row["tier"]) + 1,
                      "boss": boss, "type": row.get("type") or "Other", "description": plain(row["task"]),
                      "source": url, "revision": revision.get("revid", 0), "guideId": guide,
                      "context": context, "method": paragraphs, "requirements": requirements,
                      "items": [{"name": name, "itemId": ids[0]} for name, ids in dict((link.strip(), item_index[link.strip().lower()])
                          for link in re.findall(r"\[\[([^]|#]+)(?:\|[^]]*)?\]\]", body)
                          if link.strip().lower() in item_index).items()]})
    source = {"retrieved": "2026-10-16", "bucketQuery": "bucket('combat_achievement').select('id','name','task','tier','monster','type').limit(1000).run()",
              "overviewSource": "https://oldschool.runescape.wiki/w/Combat_Achievements?oldid=15372533",
              "thresholdSource": "OSRS Wiki Globals expansion, 16 October 2026; regular-account cumulative reward points",
              "rewardThresholds": dict(zip(tiers, thresholds)), "tasks": tasks}
    output = root / "src/main/resources/com/runewarden/combat-achievements.json"
    output.write_text(json.dumps(source, ensure_ascii=False, separators=(",", ":")) + "\n")
    print("Tasks", len(tasks), "strategies", sum(bool(t["method"]) for t in tasks), "requirements", sum(bool(t["requirements"]) for t in tasks))
    print("Tiers", dict(collections.Counter(t["difficulty"] for t in tasks)))
    print("Points", sum(t["points"] for t in tasks), "bytes", output.stat().st_size)


if __name__ == "__main__":
    main()
