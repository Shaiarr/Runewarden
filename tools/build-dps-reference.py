#!/usr/bin/env python3
"""Build the small normal-combat reference from explicitly pinned primary files.

Inputs are supplied locally: monster-reference.json, dps-equipment.json,
dps-Equipment.ts, dps-EquipmentAliases.ts, dps-EquipmentCategory.ts,
dps-utils.ts and dps-constants.ts. No executable upstream code is evaluated.
"""
import argparse
import hashlib
import json
import re
from pathlib import Path

CALC_COMMIT = "462054e6c0686ce318a051288ab91b9bfa1e91b5"
MONSTER_COMMIT = "a27fc3c898eb15accbfa0457c89b6b6a1b132f1f"


def without_comments(text):
    return re.sub(r"//[^\n]*|/\*.*?\*/", "", text, flags=re.S)


def build(source):
    monsters = json.loads((source / "monster-reference.json").read_text())
    equipment = json.loads((source / "dps-equipment.json").read_text())
    constants = without_comments((source / "dps-constants.ts").read_text())
    arrays = dict(re.findall(r"(?:export\s+)?const (\w+)(?:\s*:[^=]+)?\s*=\s*\[([^]]*)\]", constants))

    def array(name, seen=()):
        if name in seen:
            raise ValueError("Cyclic constant " + name)
        if name not in arrays:
            raise ValueError("Unresolved target constant " + name)
        raw = arrays[name]
        result = {int(n) for n in re.findall(r"(?<![\w.])\d+(?![\w.])", raw)}
        for ref in re.findall(r"\.\.\.(\w+)", raw):
            result.update(array(ref, seen + (name,)))
        return result

    magic_defence = array("USES_DEFENCE_LEVEL_FOR_MAGIC_DEFENCE_NPC_IDS")
    exclusions = set()
    for name in arrays:
        if name.endswith("_IDS") or name in {"BA_ATTACKER_MONSTERS", "ONE_HIT_MONSTERS", "GUARANTEED_ACCURACY_MONSTERS", "INFINITE_HEALTH_MONSTERS", "UNDERWATER_MONSTERS", "PARTY_SIZE_REQUIRED_MONSTER_IDS"}:
            if name not in {"BLOWPIPE_IDS", "USES_DEFENCE_LEVEL_FOR_MAGIC_DEFENCE_NPC_IDS", "IMMUNE_TO_BURN_DAMAGE_NPC_IDS"}:
                exclusions.update(array(name))
    always_max = re.search(r"ALWAYS_MAX_HIT_MONSTERS\s*=\s*\{(.*?)\};", constants, re.S)
    if always_max:
        exclusions.update(int(n) for n in re.findall(r"(?<![\w.])\d+(?![\w.])", always_max.group(1)))
        for ref in re.findall(r"\.\.\.(\w+)", always_max.group(1)):
            exclusions.update(array(ref))
    # Named branches in the primary calculator not represented by its NPC-ID constants.
    special_names = {"Corporeal Beast", "Zulrah", "Fragment of Seren", "Kraken", "Cave kraken", "Slagilith",
        "Slash Bash", "Zogre", "Skogre", "Tormented Demon", "Fire Warrior of Lesarkus", "Fareed", "Demonic gorilla",
        "Kalphite Queen", "Nex", "The Nightmare", "Phosani's Nightmare", "Vardorvis", "Yama",
        "Respiratory system", "Acidic Araxyte", "Mirrorback Araxyte"}
    targets = []
    for monster in monsters:
        skills = monster.get("skills", {})
        target = {"id": monster["id"], "name": monster["name"], "version": monster.get("version", ""),
            "hitpoints": skills.get("hp"), "defenceLevel": skills.get("def"), "magicLevel": skills.get("magic"),
            "size": monster.get("size"), "magicAttackBonus": monster.get("offensive", {}).get("magic"),
            "defenceBonuses": monster.get("defensive"), "attributes": monster.get("attributes", []),
            "weakness": monster.get("weakness"), "magicUsesDefence": monster["id"] in magic_defence}
        if monster["id"] in exclusions or monster["name"] in special_names or "xerician" in target["attributes"]:
            target["unsupportedReason"] = "Target has scaling, phase, immunity or damage rules outside the normal-combat model"
        targets.append(target)

    enum = dict(re.findall(r"(\w+)\s*=\s*'([^']*)'", (source / "dps-EquipmentCategory.ts").read_text()))
    style_source = (source / "dps-utils.ts").read_text().split("export const getCombatStylesForCategory", 1)[1].split("// Add a psuedo", 1)[0]
    styles = {}
    for match in re.finditer(r"case EquipmentCategory\.(\w+):", style_source):
        tail = style_source[match.end():]
        block = re.search(r"ret = \[([\s\S]*?)\];|return \[\];", tail)
        if block is None or block.group(1) is None:
            continue
        rows = []
        for row in re.findall(r"\{([^{}]+)\}", block.group(1)):
            values = dict(re.findall(r"(name|type|stance):\s*'([^']*)'", row))
            if len(values) == 3:
                rows.append(values)
        styles[enum[match.group(1)]] = rows

    eq_text = without_comments((source / "dps-Equipment.ts").read_text())
    ammo_categories = {}

    def ammo_values(raw):
        result = []
        for token in raw.strip().strip("[]").split(","):
            token = token.strip()
            if not token:
                continue
            if token.isdigit():
                result.append(int(token))
            else:
                key = re.fullmatch(r"\.\.\.(?:ret\.|commonAmmoCategories\(\)\.)(\w+)", token)
                if not key or key.group(1) not in ammo_categories:
                    raise ValueError("Unresolved ammo category " + token)
                result.extend(ammo_categories[key.group(1)])
        return sorted(set(result))

    initial = eq_text.split("const ret: { [k: string]: number[] } = {", 1)[1].split("};", 1)[0]
    for key, raw in re.findall(r"(\w+):\s*(\[[^]]*\])", initial):
        ammo_categories[key] = ammo_values(raw)
    for key, raw in re.findall(r"ret\.(\w+)\s*=\s*(\[[^]]*\])", eq_text):
        ammo_categories[key] = ammo_values(raw)
    ammo_map = {}
    raw_ammo = eq_text.split("const ammoForRangedWeapons:", 1)[1].split("= {", 1)[1].split("};", 1)[0]
    for item_id, raw in re.findall(r"(\d+):\s*(\[[^]]*\]|commonAmmoCategories\(\)\.\w+)", raw_ammo):
        if raw.startswith("commonAmmoCategories"):
            ammo_map[int(item_id)] = ammo_categories[raw.rsplit(".", 1)[1]]
        else:
            ammo_map[int(item_id)] = ammo_values(raw)
    aliases = {int(key): [int(n) for n in re.findall(r"\d+", raw)] for key, raw in
        re.findall(r"(\d+):\s*\[([^]]*)\]", without_comments((source / "dps-EquipmentAliases.ts").read_text()))}
    weapons = [{"id": e["id"], "category": e["category"], "speed": e.get("speed"),
        "aliases": aliases.get(e["id"], []), "ammo": ammo_map.get(e["id"])}
        for e in equipment if e.get("slot") == "weapon"]
    level_source = json.loads((source / "dps-spell-levels-source.json").read_text())["parse"]
    spell_levels = dict((name, int(level)) for name, level in re.findall(r"\|\[\[([^]|]+)\]\]\s*\n\|(\d+)\s*\n", level_source["wikitext"]["*"]))
    return {"source": "https://github.com/weirdgloop/osrs-dps-calc", "calculationCommit": CALC_COMMIT,
        "monsterCommit": MONSTER_COMMIT, "retrieved": "2026-10-16", "targets": targets, "weapons": weapons, "styles": styles,
        "spellLevelSource": "https://oldschool.runescape.wiki/w/List_of_spells?oldid=" + str(level_source["revid"]),
        "spellLevels": spell_levels,
        "inputSha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in source.glob("dps-*.ts")}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("source_dir", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    result = build(args.source_dir)
    args.output.write_text(json.dumps(result, separators=(",", ":")) + "\n")
    print(len(result["targets"]), "targets;", len(result["weapons"]), "weapons;", len(result["styles"]), "style categories")
