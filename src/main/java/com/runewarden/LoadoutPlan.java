/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Exact, user-editable readiness requirements. Alternatives are explicit, never guessed. */
public final class LoadoutPlan
{
    public enum Location { EQUIPMENT, INVENTORY, RUNE_POUCH, QUIVER }
    public enum Unit { ITEMS, DOSES }
    public static final class Requirement
    {
        public final Set<Integer> alternatives;
        public final long quantity;
        public final Location location;
        public final int slot;
        public final Unit unit;
        public Requirement(Set<Integer> alternatives, long quantity, Location location, int slot)
        { this(alternatives, quantity, location, slot, Unit.ITEMS); }
        public Requirement(Set<Integer> alternatives, long quantity, Location location, int slot, Unit unit)
        {
            if (alternatives == null || alternatives.isEmpty() || alternatives.size() > 32 || quantity < 1 || quantity > Integer.MAX_VALUE || location == null
                || slot < -1 || slot > (location == Location.EQUIPMENT ? 13 : 27)) throw new IllegalArgumentException("Invalid item requirement");
            Set<Integer> ids = new LinkedHashSet<>();
            for (Integer id : alternatives)
            { if (id == null || id <= 0 || id > 1_000_000) throw new IllegalArgumentException("Invalid item ID"); ids.add(id); }
            if (unit == Unit.DOSES && (location != Location.INVENTORY || slot != -1 || !LoadoutItemVariants.validDoses(ids)))
                throw new IllegalArgumentException("Dose requirements need known carried potion variants and any slot");
            this.alternatives = Collections.unmodifiableSet(ids); this.quantity = quantity; this.location = location; this.slot = slot; this.unit = unit == null ? Unit.ITEMS : unit;
        }
        Requirement checked() { return new Requirement(alternatives, quantity, location, slot, unit); }
        public Unit unit() { return unit == null ? Unit.ITEMS : unit; }
        public int firstId() { return alternatives.iterator().next(); }
    }
    public enum CheckKind
    {
        CHARGES("Minimum charges"), EFFECT("Active effect"), MANUAL("Manual check");
        private final String label; CheckKind(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }
    public static final class DepartureCheck
    {
        public final String id, label, effectKey;
        public final CheckKind kind;
        public final Set<Integer> itemIds;
        public final long minimum;
        public DepartureCheck(String id, String label, CheckKind kind, Set<Integer> itemIds, long minimum, String effectKey)
        {
            if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || label == null || label.trim().isEmpty() || label.length() > 100
                || kind == null || minimum < 0 || minimum > 1_000_000_000 || effectKey != null && !effectKey.matches("[a-z0-9_-]{1,80}"))
                throw new IllegalArgumentException("Invalid departure check");
            Set<Integer> ids = itemIds == null ? Collections.emptySet() : new LinkedHashSet<>(itemIds);
            if (ids.size() > 32) throw new IllegalArgumentException("Too many charge item variants");
            for (Integer item : ids) if (item == null || item <= 0 || item > 1_000_000) throw new IllegalArgumentException("Invalid charge item ID");
            if (kind == CheckKind.CHARGES && (ids.isEmpty() || minimum < 1 || effectKey != null)
                || kind == CheckKind.EFFECT && (effectKey == null || !ids.isEmpty() || minimum != 0)
                || kind == CheckKind.MANUAL && (!ids.isEmpty() || effectKey != null || minimum != 0))
                throw new IllegalArgumentException("Departure check inputs do not match its type");
            this.id = id; this.label = label.replaceAll("[\\r\\n\\t<>]", " ").trim(); this.kind = kind;
            if (this.label.isEmpty()) throw new IllegalArgumentException("Enter a departure check name");
            this.itemIds = Collections.unmodifiableSet(ids); this.minimum = minimum; this.effectKey = effectKey;
        }
        DepartureCheck checked() { return new DepartureCheck(id, label, kind, itemIds, minimum, effectKey); }
    }
    public final String id, name;
    public final List<Requirement> requirements;
    public final Integer spellbook;
    public final List<DepartureCheck> departureChecks;
    public LoadoutPlan(String id, String name, List<Requirement> requirements, Integer spellbook)
    { this(id, name, requirements, spellbook, Collections.emptyList()); }
    public LoadoutPlan(String id, String name, List<Requirement> requirements, Integer spellbook, List<DepartureCheck> departureChecks)
    {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}") || name == null || name.trim().isEmpty() || name.length() > 80
            || requirements == null || requirements.size() > 100 || spellbook != null && (spellbook < 0 || spellbook > 3)) throw new IllegalArgumentException("Invalid loadout");
        this.id = id; this.name = name.replaceAll("[\\r\\n\\t<>]", " ").trim(); this.spellbook = spellbook;
        List<Requirement> copy = new ArrayList<>(); Set<String> slots = new LinkedHashSet<>();
        for (Requirement requirement : requirements)
        {
            if (requirement == null) throw new IllegalArgumentException("Invalid requirement");
            Requirement safe = requirement.checked();
            if (safe.slot >= 0 && !slots.add(safe.location + ":" + safe.slot)) throw new IllegalArgumentException("Duplicate slot");
            copy.add(safe);
        }
        this.requirements = Collections.unmodifiableList(copy);
        if (departureChecks != null && departureChecks.size() > 24) throw new IllegalArgumentException("Too many departure checks");
        List<DepartureCheck> checks = new ArrayList<>(); Set<String> checkIds = new LinkedHashSet<>();
        if (departureChecks != null) for (DepartureCheck check : departureChecks)
        {
            if (check == null || !checkIds.add(check.id)) throw new IllegalArgumentException("Duplicate departure check");
            checks.add(check.checked());
        }
        this.departureChecks = Collections.unmodifiableList(checks);
    }
    public LoadoutPlan checked() { return new LoadoutPlan(id, name, requirements, spellbook, departureChecks); }
    public LoadoutPlan withRequirements(List<Requirement> lines, Integer book)
    { return new LoadoutPlan(id, name, lines, book, departureChecks); }
    public List<DepartureCheck> checks() { return departureChecks == null ? Collections.emptyList() : departureChecks; }
    public Map<Integer, Integer> equipment()
    {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        for (Requirement requirement : requirements)
            if (requirement.location == Location.EQUIPMENT && requirement.slot >= 0 && requirement.alternatives.size() == 1)
                result.put(requirement.slot, requirement.firstId());
        return Collections.unmodifiableMap(result);
    }
    @Override public String toString() { return name; }
}
