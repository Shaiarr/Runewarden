/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Exact-ID base target/weapon reference; duplicate variants never silently choose combat stats. */
@Singleton
final class DpsReferenceCatalog
{
    static final class Weakness { String element; int severity; }
    static final class Target
    {
        int id;
        String name, version, unsupportedReason;
        Integer hitpoints, defenceLevel, magicLevel, magicAttackBonus, size;
        Map<String, Integer> defenceBonuses;
        List<String> attributes;
        Weakness weakness;
        boolean magicUsesDefence;
        boolean snapshotOnly;
        String scenarioDescription;
        boolean has(String attribute) { return attributes != null && attributes.contains(attribute); }
    }
    static final class Weapon
    {
        int id;
        String category;
        Integer speed;
        List<Integer> aliases, ammo;
    }
    static final class Mode
    {
        String name, type, stance;
        Mode() { }
        Mode(String name, String type, String stance) { this.name = name; this.type = type; this.stance = stance; }
        boolean ranged() { return "ranged".equals(type); }
        boolean magic() { return "magic".equals(type); }
        boolean melee() { return "stab".equals(type) || "slash".equals(type) || "crush".equals(type); }
        boolean casting() { return stance != null && stance.toLowerCase(java.util.Locale.ROOT).contains("cast"); }
        String label() { return name + " · " + type + " · " + stance; }
    }
    private static final class Data
    {
        String calculationCommit, monsterCommit;
        List<Target> targets;
        List<Weapon> weapons;
        Map<String, List<Mode>> styles;
        Map<String, Integer> spellLevels;
    }
    private final Data data;
    private final Map<Integer, Target> targets = new HashMap<>();
    private final Map<String, Target> variants = new HashMap<>();
    private final java.util.Set<String> ambiguousVariants = new java.util.HashSet<>();
    private final Map<Integer, Weapon> weapons = new HashMap<>();
    private final Map<Integer, String> unavailable = new HashMap<>();
    private final Weapon fists = new Weapon();

    @Inject DpsReferenceCatalog(Gson gson)
    {
        try (InputStream stream = DpsReferenceCatalog.class.getResourceAsStream("/com/runewarden/dps-reference.json"))
        {
            if (stream == null) throw new IllegalStateException("Missing normal-combat reference");
            data = gson.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Data.class);
            if (data == null || data.targets == null || data.weapons == null || data.styles == null
                || data.calculationCommit == null || data.monsterCommit == null)
                throw new IllegalStateException("Invalid normal-combat reference");
        }
        catch (java.io.IOException error) { throw new IllegalStateException("Cannot read normal-combat reference", error); }
        for (Target target : data.targets)
        {
            String variant = variantKey(target.id, target.version);
            Target previousVariant = variants.putIfAbsent(variant, target);
            if (previousVariant != null && !same(previousVariant, target)) ambiguousVariants.add(variant);
            Target previous = targets.putIfAbsent(target.id, target);
            if (previous != null && !same(previous, target)) unavailable.put(target.id, "NPC ID has conflicting combat variants");
        }
        for (Integer id : unavailable.keySet()) targets.remove(id);
        for (String key : ambiguousVariants) variants.remove(key);
        for (Weapon weapon : data.weapons) weapons.putIfAbsent(weapon.id, weapon);
        for (Weapon weapon : data.weapons)
            if (weapon.aliases != null) for (Integer id : weapon.aliases) weapons.putIfAbsent(id, weapon);
        fists.id = -1; fists.category = "Unarmed"; fists.speed = 4;
    }
    Target target(int id) { return targets.get(id); }
    Target variant(int id, String version) { return version == null ? target(id) : variants.get(variantKey(id, version)); }
    private static String variantKey(int id, String version) { return id + "|" + (version == null ? "" : version); }
    List<Target> searchVariants(String query)
    { return searchIn(variants.values(), query); }
    /** Stable exact-variant search, independent of an interacting NPC. */
    List<Target> search(String query)
    { return searchIn(targets.values(), query); }
    private List<Target> searchIn(java.util.Collection<Target> candidates, String query)
    {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int numeric; try { numeric = Integer.parseInt(needle); } catch (NumberFormatException invalid) { numeric = -1; }
        List<Target> found = new ArrayList<>();
        for (Target target : candidates)
            if (target.id == numeric || target.name != null && (target.name + " " + (target.version == null ? "" : target.version)).toLowerCase(Locale.ROOT).contains(needle)) found.add(target);
        found.sort(Comparator.comparing((Target target) -> !target.name.equalsIgnoreCase(needle))
            .thenComparing(target -> target.name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(target -> target.version == null ? "" : target.version).thenComparingInt(target -> target.id));
        return Collections.unmodifiableList(found);
    }
    static Target scenario(Target source, Integer hp, Integer defence, Integer magic)
    {
        if (source == null) return null;
        if (hp != null && (hp < 1 || hp > 50000) || defence != null && (defence < 0 || defence > 2000)
            || magic != null && (magic < 0 || magic > 2000)) throw new IllegalArgumentException("Invalid target scenario");
        Target copy = new Target(); copy.id = source.id; copy.name = source.name; copy.version = source.version;
        copy.unsupportedReason = source.unsupportedReason; copy.hitpoints = hp == null ? source.hitpoints : hp;
        copy.defenceLevel = defence == null ? source.defenceLevel : defence; copy.magicLevel = magic == null ? source.magicLevel : magic;
        copy.magicAttackBonus = source.magicAttackBonus; copy.size = source.size; copy.defenceBonuses = source.defenceBonuses;
        copy.snapshotOnly = source.snapshotOnly; copy.scenarioDescription = source.scenarioDescription;
        copy.attributes = source.attributes; copy.weakness = source.weakness; copy.magicUsesDefence = source.magicUsesDefence;
        return copy;
    }
    String unavailable(int id) { return unavailable.getOrDefault(id, "Target combat stats are not catalogued"); }
    Weapon weapon(int id) { return id <= 0 ? fists : weapons.get(id); }
    List<Mode> modes(Weapon weapon)
    { return weapon == null ? Collections.emptyList() : data.styles.getOrDefault(weapon.category, Collections.emptyList()); }
    Mode observedMode(Weapon weapon, int index, boolean defensiveCasting)
    {
        if (weapon == null || index < 0) return null;
        boolean staff = weapon.category.toLowerCase(java.util.Locale.ROOT).contains("staff") || weapon.category.contains("Wand");
        if (index == 3 && staff) return null; // Native index 3 is not the UI calculator's pseudo casting choice.
        if (index == 4 && staff)
            return new Mode("Spell", "magic", defensiveCasting ? "Defensive Autocast" : "Autocast");
        List<Mode> modes = modes(weapon);
        return index >= modes.size() ? null : modes.get(index);
    }
    List<Mode> comparisonModes(Weapon weapon, boolean spellKnown)
    {
        List<Mode> result = new ArrayList<>();
        for (Mode mode : modes(weapon)) if (!mode.casting()) result.add(mode);
        if (spellKnown) result.add(new Mode("Spell", "magic", "Manual Cast"));
        return Collections.unmodifiableList(result);
    }
    String calculationCommit() { return data.calculationCommit; }
    String monsterCommit() { return data.monsterCommit; }
    int targetCount() { return targets.size(); }
    Integer spellLevel(String name) { return data.spellLevels == null ? null : data.spellLevels.get(name); }

    private static boolean same(Target first, Target second)
    {
        return Objects.equals(first.hitpoints, second.hitpoints) && Objects.equals(first.defenceLevel, second.defenceLevel)
            && Objects.equals(first.magicLevel, second.magicLevel) && Objects.equals(first.magicAttackBonus, second.magicAttackBonus)
            && Objects.equals(first.size, second.size) && Objects.equals(first.defenceBonuses, second.defenceBonuses)
            && Objects.equals(first.attributes, second.attributes) && Objects.equals(first.unsupportedReason, second.unsupportedReason)
            && first.magicUsesDefence == second.magicUsesDefence && sameWeakness(first.weakness, second.weakness);
    }
    private static boolean sameWeakness(Weakness first, Weakness second)
    { return first == null ? second == null : second != null && first.severity == second.severity && Objects.equals(first.element, second.element); }
}
