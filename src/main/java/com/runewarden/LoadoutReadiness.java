/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure readiness calculation. Last-seen bank items never count as carried supplies. */
public final class LoadoutReadiness
{
    public static final class Line
    {
        public final LoadoutPlan.Requirement requirement;
        public final long carried, missing, banked;
        public final boolean observed, bankKnown, bankCurrentSession;
        public final long bankObservedAt, storedDoses;
        Line(LoadoutPlan.Requirement requirement, long carried, long banked, boolean observed, OwnershipLedger.Observation bank, long storedDoses)
        { this.requirement = requirement; this.carried = carried; missing = Math.max(0, requirement.quantity - carried); this.banked = banked; this.observed = observed; this.bankKnown = bank != null; bankCurrentSession = bank != null && bank.currentSession; bankObservedAt = bank == null ? 0 : bank.observedAt; this.storedDoses = storedDoses; }
        public boolean ready() { return observed && missing == 0; }
    }
    public enum CheckState { READY, MISSING, UNKNOWN }
    public static final class CheckLine
    {
        public final LoadoutPlan.DepartureCheck check;
        public final CheckState state;
        public final long observed;
        public final String detail;
        CheckLine(LoadoutPlan.DepartureCheck check, CheckState state, long observed, String detail)
        { this.check = check; this.state = state; this.observed = observed; this.detail = detail; }
    }
    public static final class CheckFacts
    {
        public final Map<Integer, Long> charges;
        public final Map<Integer, Integer> supported;
        public final Set<String> activeEffects, knownEffects, confirmations;
        public CheckFacts(Map<Integer, Long> charges, Map<Integer, Integer> supported, Set<String> activeEffects,
            Set<String> knownEffects, Set<String> confirmations)
        {
            this.charges = charges == null ? Collections.emptyMap() : charges; this.supported = supported == null ? Collections.emptyMap() : supported;
            this.activeEffects = activeEffects == null ? Collections.emptySet() : activeEffects;
            this.knownEffects = knownEffects == null ? Collections.emptySet() : knownEffects;
            this.confirmations = confirmations == null ? Collections.emptySet() : confirmations;
        }
        static CheckFacts empty() { return new CheckFacts(null,null,null,null,null); }
    }
    public final List<Line> lines;
    public final List<CheckLine> checks;
    public final boolean ready, itemsReady, spellbookMatches, spellbookKnown;
    private LoadoutReadiness(List<Line> lines, boolean bookMatches, boolean bookKnown, List<CheckLine> checks)
    {
        this.lines = Collections.unmodifiableList(lines); this.checks = Collections.unmodifiableList(checks);
        spellbookMatches = bookMatches; spellbookKnown = bookKnown;
        itemsReady = bookMatches && bookKnown && lines.stream().allMatch(Line::ready);
        ready = itemsReady && checks.stream().allMatch(line -> line.state == CheckState.READY);
    }
    public static LoadoutReadiness check(LoadoutPlan plan, OwnershipLedger.View state)
    { return check(plan,state,CheckFacts.empty()); }
    public static LoadoutReadiness check(LoadoutPlan plan, OwnershipLedger.View state, CheckFacts facts)
    {
        List<Line> lines = new ArrayList<>();
        LoadoutItemAllocation allocation=LoadoutItemAllocation.allocate(plan,state);
        Map<LoadoutPlan.Location, Map<Integer, Long>> available=allocation.remaining;
        OwnershipLedger.Observation bank = state.storages.get(OwnershipLedger.Storage.BANK);
        Map<Integer,Long> spareDoses = new LinkedHashMap<>();
        int lineIndex=0;
        for (LoadoutPlan.Requirement requirement : plan.requirements)
        {
            OwnershipLedger.Observation observation = state.storages.get(storage(requirement.location));
            boolean known = requirement.location == LoadoutPlan.Location.RUNE_POUCH ? state.runePouchObserved : observation != null && observation.currentSession;
            if (requirement.location == LoadoutPlan.Location.QUIVER) known &= state.quiverAvailable;
            Map<Integer, Long> counts = available.get(requirement.location);
            long carried = requirement.unit()==LoadoutPlan.Unit.ITEMS ? allocation.carried[lineIndex] : 0, banked = 0;
            lineIndex++;
            for (int id : requirement.alternatives)
            {
                long count = counts.getOrDefault(id, 0L);
                if (requirement.unit() == LoadoutPlan.Unit.DOSES)
                {
                    int doses = LoadoutItemVariants.doses(id);
                    long spare = spareDoses.getOrDefault(id,0L), fromSpare = Math.min(spare, requirement.quantity-carried);
                    carried += fromSpare; spare -= fromSpare;
                    long used = Math.min(count, (requirement.quantity-carried+doses-1)/doses);
                    long taken = Math.min(used*doses,requirement.quantity-carried);
                    carried += taken; spareDoses.put(id,spare+used*doses-taken);
                    counts.put(id,Math.max(0,counts.getOrDefault(id,0L)-used));
                    if(bank!=null)banked+=bank.quantity(id)*doses;
                }
                else
                {
                    if (bank != null) banked += bank.quantity(id);
                }
            }
            long storedDoses = state.potionDoses(requirement.alternatives);
            lines.add(new Line(requirement, carried, banked, known, bank, storedDoses));
        }
        List<CheckLine> checks = new ArrayList<>();
        for (LoadoutPlan.DepartureCheck check : plan.checks()) checks.add(departure(check,state,facts));
        return new LoadoutReadiness(lines, plan.spellbook == null || plan.spellbook.equals(state.spellbook), state.active && (plan.spellbook == null || state.spellbook != null), checks);
    }
    private static CheckLine departure(LoadoutPlan.DepartureCheck check, OwnershipLedger.View state, CheckFacts facts)
    {
        if (!state.active) return new CheckLine(check,CheckState.UNKNOWN,0,"Current character observation unavailable");
        if (check.kind == LoadoutPlan.CheckKind.MANUAL)
            return new CheckLine(check,facts.confirmations.contains(check.id)?CheckState.READY:CheckState.UNKNOWN,0,
                facts.confirmations.contains(check.id)?"Confirmed this login session":"Awaiting manual confirmation");
        if (check.kind == LoadoutPlan.CheckKind.EFFECT)
        {
            boolean known = facts.knownEffects.contains(check.effectKey), active = facts.activeEffects.contains(check.effectKey);
            return new CheckLine(check,!known?CheckState.UNKNOWN:active?CheckState.READY:CheckState.MISSING,0,
                !known?"Effect observation unavailable":active?"Native effect active":"Effect is not active");
        }
        OwnershipLedger.Observation inventory = state.storages.get(OwnershipLedger.Storage.INVENTORY), worn = state.storages.get(OwnershipLedger.Storage.EQUIPMENT);
        if (inventory == null || !inventory.currentSession || worn == null || !worn.currentSession)
            return new CheckLine(check,CheckState.UNKNOWN,0,"Current inventory/equipment not observed");
        for (Integer id : check.itemIds) if (!facts.supported.containsKey(id))
            return new CheckLine(check,CheckState.UNKNOWN,0,"A required item has no verified native charge state");
        long total = 0;
        for (Integer id : check.itemIds)
        {
            Integer encoded = facts.supported.get(id);
            if (encoded == null || encoded < 0) return new CheckLine(check,CheckState.UNKNOWN,0,"Charge state unavailable");
            // Encoded item states remain verifiable from carried quantities even if the optional supply HUD is off.
            long observed = encoded.longValue() * (inventory.quantity(id) + worn.quantity(id));
            Long supplied = facts.charges.get(id); if (supplied != null && supplied >= 0) observed = supplied;
            total += observed;
        }
        return new CheckLine(check,total >= check.minimum?CheckState.READY:CheckState.MISSING,total,
            total + " / " + check.minimum + " carried charges / doses");
    }
    private static OwnershipLedger.Storage storage(LoadoutPlan.Location location)
    {
        switch (location)
        {
            case EQUIPMENT: return OwnershipLedger.Storage.EQUIPMENT;
            case QUIVER: return OwnershipLedger.Storage.QUIVER;
            default: return OwnershipLedger.Storage.INVENTORY;
        }
    }
}
