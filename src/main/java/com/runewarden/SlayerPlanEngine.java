/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;

/** Deterministic, inspectable assignment scenarios. Personal preferences never alter native weights. */
final class SlayerPlanEngine
{
    enum Access { ELIGIBLE, INELIGIBLE, UNCHECKED }
    static final class Check
    {
        final Access access; final List<String> reasons;
        Check(Access access, List<String> reasons) { this.access = access; this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons)); }
    }
    static final class Odds
    {
        final int taskId, weight, knownWeight, uncheckedWeight;
        final double minimum, maximum;
        final boolean exact;
        Odds(int taskId, int weight, int known, int unchecked, double minimum, double maximum, boolean exact)
        { this.taskId = taskId; this.weight = weight; knownWeight = known; uncheckedWeight = unchecked; this.minimum = minimum; this.maximum = maximum; this.exact = exact; }
    }
    static final class Projection
    {
        final boolean available, conditional;
        final String reason;
        final int weight, acceptedWeight, skippedWeight, plannedBlocks, plannedBlockCost;
        final double skipsPerCompleted, skipPointsPerCompleted, estimatedMinutes, estimatedXp, estimatedNetGp;
        final long rewards;
        final double netPoints;
        final int completed, ratedTasks;
        Projection(boolean available, boolean conditional, String reason, int weight, int acceptedWeight, int skippedWeight,
            int plannedBlocks, int blockCost, double skips, double minutes, double xp, double gp, long rewards, int completed, int rated)
        {
            this.available = available; this.conditional = conditional; this.reason = reason; this.weight = weight;
            this.acceptedWeight = acceptedWeight; this.skippedWeight = skippedWeight; this.plannedBlocks = plannedBlocks;
            plannedBlockCost = plannedBlocks * blockCost; skipsPerCompleted = skips; skipPointsPerCompleted = skips * 30;
            estimatedMinutes = minutes; estimatedXp = xp; estimatedNetGp = gp; this.rewards = rewards; this.completed = completed;
            ratedTasks = rated; netPoints = rewards - skipPointsPerCompleted * completed - plannedBlockCost;
        }
    }
    static Check check(SlayerPlanningTool.NativeTask task, SlayerPlanningTool.Snapshot account,
        SlayerPlanningTool.Rule rule, String combatChecking)
    {
        List<String> no = new ArrayList<>(), uncertain = new ArrayList<>();
        if (account.blocked.contains(task.taskId)) no.add("Blocked in game");
        if (effectiveWeight(task, account.bits) <= 0 && !hasUnmodelledWeight(task, account.bits)) no.add("No assignment weight");
        for (SlayerPlanningTool.Requirement requirement : task.all)
        {
            Integer level = account.levels.get(requirement.skill);
            if (level == null || level <= 0) uncertain.add(requirement.skill.getName() + " level unavailable");
            else if (level < requirement.level) no.add(requirement.level + " " + requirement.skill.getName() + " required");
        }
        if (!task.any.isEmpty())
        {
            boolean passed = false, missing = false;
            for (SlayerPlanningTool.Requirement requirement : task.any)
            { Integer level = account.levels.get(requirement.skill); passed |= level != null && level >= requirement.level; missing |= level == null || level <= 0; }
            if (!passed) { if (missing) uncertain.add("Alternative stat requirement unchecked"); else no.add("No alternative stat requirement met"); }
        }
        if (task.combat > account.combat)
        {
            if ("ENABLED".equals(combatChecking)) no.add(task.combat + " combat required with checking enabled");
            else if (!"DISABLED".equals(combatChecking)) uncertain.add("Confirm master's combat checking setting");
        }
        if (task.unlockBit > 0)
        {
            Integer bit = account.bits.get(task.unlockBit);
            if (bit == null) uncertain.add("Task unlock not observed"); else if (bit <= 0) no.add("Task unlock disabled");
        }
        else if (task.unlock != null) uncertain.add("Task unlock not observed");
        if (task.blockBit > 0 && account.bits.getOrDefault(task.blockBit, 0) > 0)
        {
            boolean disabledBlock = task.blockBit == net.runelite.api.gameval.VarbitID.SLAYER_UNLOCK_FOSSILWYVERNBLOCK
                && account.bits.getOrDefault(net.runelite.api.gameval.VarbitID.SLAYER_TOGGLEOFF_FOSSILWYVERNBLOCK, 0) > 0;
            if (!disabledBlock) no.add("Task disabled by reward unlock");
        }
        if (!account.blocksObserved) uncertain.add("Game block list unavailable");
        if ("NO".equals(rule.access)) no.add("Marked unavailable in this plan");
        else if (!"YES".equals(rule.access)) uncertain.add("Quest and area access unchecked");
        if (!no.isEmpty()) return new Check(Access.INELIGIBLE, no);
        if (!uncertain.isEmpty()) return new Check(Access.UNCHECKED, uncertain);
        return new Check(Access.ELIGIBLE, List.of("Native checks passed · access confirmed"));
    }
    static int effectiveWeight(SlayerPlanningTool.NativeTask task, Map<Integer, Integer> bits)
    {
        return Math.max(0, task.weight);
    }
    static Map<Integer, Odds> odds(SlayerPlanningTool.Snapshot account, Map<String, SlayerPlanningTool.Rule> rules, String combatChecking)
    {
        Map<Integer, Check> checks = new LinkedHashMap<>(); int known = 0, unchecked = 0;
        for (SlayerPlanningTool.NativeTask task : account.tasks)
        {
            Check check = check(task, account, rule(rules, task.name), combatChecking); checks.put(task.row, check);
            int weight = effectiveWeight(task, account.bits);
            if (check.access == Access.ELIGIBLE) known += weight; else if (check.access == Access.UNCHECKED) unchecked += weight;
        }
        boolean complete = account.total > 0 && account.indexed == account.total;
        boolean adjusted = account.tasks.stream().anyMatch(task -> hasUnmodelledWeight(task, account.bits));
        Map<Integer, Odds> result = new LinkedHashMap<>();
        for (SlayerPlanningTool.NativeTask task : account.tasks)
        {
            int weight = effectiveWeight(task, account.bits); Access access = checks.get(task.row).access;
            double low = 0, high = 0;
            if (access != Access.INELIGIBLE)
            {
                // Missing cache rows carry unknown weight: no finite lower probability can be asserted.
                if (complete && access == Access.ELIGIBLE && known + unchecked > 0) low = (double) weight / (known + unchecked);
                int denominator = known + (access == Access.UNCHECKED ? weight : 0);
                if (denominator > 0) high = (double) weight / denominator;
            }
            if (adjusted && access != Access.INELIGIBLE) { low = 0; high = 1; }
            result.put(task.row, new Odds(task.taskId, weight, known, unchecked, low, high, complete && unchecked == 0 && !adjusted));
        }
        return Collections.unmodifiableMap(result);
    }
    static Projection project(SlayerPlanningTool.Snapshot account, Map<String, SlayerPlanningTool.Rule> rules,
        String combatChecking, boolean includeUnchecked, int completed, int streak, int[] rewardSchedule)
    {
        if (completed < 1 || completed > 1000 || streak < 0 || account.indexed != account.total || account.total <= 0)
            return unavailable("Task table is still loading");
        int total = 0, accepted = 0, skipped = 0, blocks = 0, rated = 0; boolean conditional = false, weightConditional = false;
        double minutes = 0, xp = 0, gp = 0; boolean timeKnown = true, xpKnown = true, gpKnown = true;
        for (SlayerPlanningTool.NativeTask task : account.tasks)
        {
            SlayerPlanningTool.Rule rule = rule(rules, task.name); Check check = check(task, account, rule, combatChecking);
            if (check.access == Access.INELIGIBLE) continue;
            if (check.access == Access.UNCHECKED) { if (!includeUnchecked) return unavailable("Some task access is unchecked"); conditional = true; }
            if (hasUnmodelledWeight(task, account.bits))
            { if (!includeUnchecked) return unavailable("A reward changes task weight; base-weight scenario only"); conditional = true; weightConditional = true; }
            int weight = effectiveWeight(task, account.bits);
            if ("BLOCK".equals(rule.decision)) { blocks++; continue; }
            total += weight;
            if ("SKIP".equals(rule.decision)) { skipped += weight; continue; }
            accepted += weight;
            double count = rule.plannedCount > 0 ? rule.plannedCount : task.minimum >= 0 && task.maximum >= task.minimum ? (task.minimum + (double) task.maximum) / 2 : Double.NaN;
            boolean personal = rule.killsPerHour > 0 && Double.isFinite(count); if (personal) rated++;
            if (!personal) timeKnown = false; else minutes += weight * count * 60 / rule.killsPerHour;
            if (!rule.xpKnown || !Double.isFinite(count)) xpKnown = false; else xp += weight * count * rule.xpPerKill;
            if (!rule.gpKnown || !Double.isFinite(count)) gpKnown = false; else gp += weight * count * rule.netGpPerKill;
        }
        if (accepted <= 0) return unavailable("No task remains to complete");
        if (blocks > 0 && SlayerAcquisitionPlan.blockCost(account.master) < 0) return unavailable("Set this master's block cost before modelling new blocks");
        if (rewardSchedule == null || rewardSchedule.length != 6) return unavailable("Set the master's point rewards");
        long rewards = 0; for (int index = 1; index <= completed; index++) rewards += reward(streak + index, rewardSchedule);
        return new Projection(true, conditional, weightConditional ? "Base weight scenario · reward weighting unchecked" : conditional ? "Scenario includes unchecked task access" : "Confirmed task table",
            total, accepted, skipped, blocks, Math.max(0, SlayerAcquisitionPlan.blockCost(account.master)), (double) skipped / accepted,
            timeKnown ? minutes / accepted * completed : Double.NaN,
            xpKnown ? xp / accepted * completed : Double.NaN, gpKnown ? gp / accepted * completed : Double.NaN,
            rewards, completed, rated);
    }
    static boolean hasUnmodelledWeight(SlayerPlanningTool.NativeTask task, Map<Integer, Integer> bits)
    { return task.weightChanges.stream().anyMatch(change -> bits.getOrDefault(change.bit, 0) > 0); }
    static int reward(int streak, int[] schedule)
    {
        if (streak < 5) return 0;
        if (streak % 1000 == 0) return schedule[5];
        if (streak % 250 == 0) return schedule[4];
        if (streak % 100 == 0) return schedule[3];
        if (streak % 50 == 0) return schedule[2];
        if (streak % 10 == 0) return schedule[1];
        return schedule[0];
    }
    private static Projection unavailable(String reason)
    { return new Projection(false, false, reason, 0, 0, 0, 0, 0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0, 0, 0); }
    static SlayerPlanningTool.Rule rule(Map<String, SlayerPlanningTool.Rule> rules, String task)
    { return rules.getOrDefault(SlayerTaskCatalog.normalize(task), new SlayerPlanningTool.Rule()); }
}
