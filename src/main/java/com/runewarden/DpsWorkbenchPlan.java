/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;

/** Data-only character-specific combat experiments. These are never equipment actions. */
final class DpsWorkbenchPlan
{
    static final String KEY = "dpsWorkbenchV1";
    static final int MAX_BUILDS = 12, MAX_ACTIVE = 8;
    static final class Choice
    {
        String id, style, spell, dart;
        boolean special;
        boolean enabled = true;
        Choice() { }
        Choice(String id) { this.id = id; }
    }
    static final class Saved
    {
        int version = 1, target = -1;
        Integer hp, defence, magic;
        long budget;
        String selected, targetVersion;
        DpsTargetScenario.Inputs scenario;
        List<LoadoutPlan> builds = new ArrayList<>();
        List<Choice> choices = new ArrayList<>();
    }
    static boolean valid(Saved saved)
    {
        if (saved == null || saved.version != 1 || saved.target < -1 || saved.target > 1_000_000
            || !text(saved.targetVersion, 120) || !DpsTargetScenario.valid(saved.scenario) || saved.budget < 0 || saved.budget > 1_000_000_000_000L || saved.builds == null || saved.builds.size() > MAX_BUILDS
            || saved.choices == null || saved.choices.size() > 50 || saved.selected != null && !id(saved.selected)
            || saved.hp != null && (saved.hp < 1 || saved.hp > 50000) || saved.defence != null && (saved.defence < 0 || saved.defence > 2000)
            || saved.magic != null && (saved.magic < 0 || saved.magic > 2000)) return false;
        Set<String> ids = new HashSet<>();
        try
        {
            for (LoadoutPlan build : saved.builds)
                if (build == null || !ids.add(build.checked().id) || exactEquipment(build) != null) return false;
            ids.clear();
            for (Choice choice : saved.choices)
                if (choice == null || !id(choice.id) || !ids.add(choice.id) || !text(choice.style, 100)
                    || !text(choice.spell, 80) || !text(choice.dart, 30)) return false;
        }
        catch (RuntimeException invalid) { return false; }
        return true;
    }
    private static boolean id(String value) { return value != null && value.matches("[A-Za-z0-9_-]{1,64}"); }
    private static boolean text(String value, int maximum)
    { return value == null || value.length() <= maximum && value.indexOf('\n') < 0 && value.indexOf('<') < 0 && value.indexOf('>') < 0; }
    static String exactEquipment(LoadoutPlan plan)
    {
        if (plan == null) return "Select an equipment set";
        boolean weapon = false;
        for (LoadoutPlan.Requirement requirement : plan.requirements)
        {
            if (requirement.location != LoadoutPlan.Location.EQUIPMENT) continue;
            if (requirement.slot < 0 || requirement.alternatives.size() != 1) return "Equipment has unresolved slots or item alternatives";
            if (requirement.slot == 3) weapon = true;
        }
        return weapon ? null : "Save an exact weapon slot before comparing this loadout";
    }
    static LoadoutPlan replace(LoadoutPlan source, int slot, int itemId, int quantity, String name)
    {
        if (source == null || slot < 0 || slot > 13 || itemId < -1 || itemId == 0 || quantity < 1)
            throw new IllegalArgumentException("Choose a valid equipment slot and item");
        List<LoadoutPlan.Requirement> next = new ArrayList<>();
        for (LoadoutPlan.Requirement requirement : source.requirements)
            if (requirement.location == LoadoutPlan.Location.EQUIPMENT && requirement.slot != slot) next.add(requirement);
        if (itemId > 0) next.add(new LoadoutPlan.Requirement(Collections.singleton(itemId), quantity, LoadoutPlan.Location.EQUIPMENT, slot));
        return new LoadoutPlan(source.id, name == null ? source.name : name, next, source.spellbook);
    }
    static final class Purchase
    {
        final Map<Integer, Long> unobserved;
        final long pricedTotal, unobservedCost;
        final int unknownPrices;
        final boolean ownershipComplete, observedOwned, overBudget;
        Purchase(Map<Integer, Long> missing, long total, long missingCost, int unknown, boolean complete, boolean owned, long budget)
        {
            unobserved = Collections.unmodifiableMap(missing); pricedTotal = total; unobservedCost = missingCost;
            unknownPrices = unknown; ownershipComplete = complete; observedOwned = owned;
            overBudget = budget > 0 && unknown == 0 && missingCost > budget;
        }
        String label()
        {
            if (observedOwned) return "Observed owned equipment";
            return ownershipComplete ? "Equipment needed" : "Ownership incomplete · unobserved items";
        }
    }
    static Purchase purchase(LoadoutPlan plan, OwnershipLedger.View observed, ItemManager items, long budget)
    {
        Map<Integer, Long> required = new LinkedHashMap<>(), missing = new LinkedHashMap<>();
        if (plan != null) for (LoadoutPlan.Requirement requirement : plan.requirements)
            if (requirement.location == LoadoutPlan.Location.EQUIPMENT && requirement.alternatives.size() == 1)
                required.merge(requirement.firstId(), requirement.quantity, Long::sum);
        boolean complete = observed != null && observed.active;
        if (complete) for (OwnershipLedger.Storage storage : new OwnershipLedger.Storage[] {
            OwnershipLedger.Storage.INVENTORY, OwnershipLedger.Storage.EQUIPMENT, OwnershipLedger.Storage.BANK })
        {
            OwnershipLedger.Observation entry = observed.storages.get(storage);
            complete &= entry != null && entry.currentSession;
        }
        long total = 0, missingCost = 0; int unknown = 0;
        for (Map.Entry<Integer, Long> entry : required.entrySet())
        {
            long owned = 0;
            if (observed != null && observed.active) for (Map.Entry<OwnershipLedger.Storage, OwnershipLedger.Observation> storage : observed.storages.entrySet())
                if (storage.getValue().currentSession && (storage.getKey() == OwnershipLedger.Storage.INVENTORY
                    || storage.getKey() == OwnershipLedger.Storage.EQUIPMENT || storage.getKey() == OwnershipLedger.Storage.BANK))
                    owned += storage.getValue().quantity(entry.getKey());
            long absent = Math.max(0, entry.getValue() - owned);
            if (absent > 0) missing.put(entry.getKey(), absent);
            ItemComposition definition = items.getItemComposition(entry.getKey());
            long price = definition != null && definition.isTradeable() ? items.getItemPrice(entry.getKey()) : 0;
            if (price > 0) { total += price * entry.getValue(); missingCost += price * absent; }
            else if (absent > 0) unknown++;
        }
        return new Purchase(missing, total, missingCost, unknown, complete, missing.isEmpty() && observed != null && observed.active, budget);
    }
    static LoadoutPlan capture(String id, String name, Item[] equipment)
    {
        List<LoadoutPlan.Requirement> lines = new ArrayList<>();
        if (equipment != null) for (int slot = 0; slot < Math.min(14, equipment.length); ++slot)
        {
            Item item = equipment[slot];
            if (item != null && item.getId() > 0 && item.getQuantity() > 0)
                lines.add(new LoadoutPlan.Requirement(Collections.singleton(item.getId()), item.getQuantity(), LoadoutPlan.Location.EQUIPMENT, slot));
        }
        return new LoadoutPlan(id, name, lines, null);
    }
}
