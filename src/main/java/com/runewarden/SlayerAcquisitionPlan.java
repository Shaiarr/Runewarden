/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Local decisions only. None of these calculations request, cancel, store or swap a task. */
final class SlayerAcquisitionPlan
{
    static final String SOURCE = "https://oldschool.runescape.wiki/w/Slayer_Rewards";
    enum Mode { POINT_SKIP, TURAEL_RESET, NINE_PLUS_ONE }
    static final class Result
    {
        final boolean available; final String reason; final int resultingStreak, pointsSpent;
        final long completionRewards, preservedRewards; final List<String> steps;
        Result(boolean available, String reason, int streak, int spend, long earned, long preserved, List<String> steps)
        { this.available = available; this.reason = reason; resultingStreak = streak; pointsSpent = spend; completionRewards = earned;
            preservedRewards = preserved; this.steps = Collections.unmodifiableList(new ArrayList<>(steps)); }
    }
    static int blockCost(int master)
    { switch (master) { case 1: return 40; case 2: return 50; case 3: return 60; case 4: return 70; case 8: return 80; case 6: return 90; case 5: case 7: return 100; default: return -1; } }
    static Result calculate(Mode mode, int currentMaster, int streak, int completions, int[] schedule, boolean resettable, boolean hasAssignment)
    {
        if (mode == null || streak < 0 || completions < 1 || completions > 1000 || schedule == null || schedule.length != 6)
            return new Result(false, "Set the selected master's point schedule", streak, 0, 0, 0, List.of());
        long preserved = rewards(streak, completions, schedule);
        if (currentMaster == 10) return new Result(false, "Mortimer acquisition rules need confirmation", streak, 0, 0, preserved, List.of());
        if (mode == Mode.POINT_SKIP)
            return new Result(true, "30-point cancellation preserves the task streak", streak, hasAssignment ? 30 : 0, 0, preserved,
                hasAssignment ? List.of("Cancel the current task in Slayer Rewards.", "Request another task from your chosen master.") : List.of("Request a task from your chosen master."));
        if (mode == Mode.TURAEL_RESET)
        {
            if (!hasAssignment) return new Result(false, "No active assignment to replace", streak, 0, 0, preserved, List.of());
            if (!resettable || currentMaster == 1) return new Result(false, "Turael / Aya cannot replace this task family", streak, 0, 0, preserved, List.of());
            if (currentMaster == 10) return new Result(false, "Mortimer replacement rules need confirmation", streak, 0, 0, preserved, List.of());
            String counter = currentMaster == 7 ? "Wilderness streak resets; the ordinary streak is separate" : "Ordinary Slayer streak resets to zero";
            return new Result(true, counter, 0, 0, rewards(0, completions, schedule), preserved,
                List.of("Ask Turael / Aya to replace the active task and accept the streak reset.", "Complete the replacement assignment.", "Return to your target master for another roll."));
        }
        if (currentMaster == 1 || currentMaster == 7 || currentMaster == 10)
            return new Result(false, "Choose an ordinary reward master for milestone boosting", streak, 0, 0, preserved, List.of());
        long boosted = 0;
        for (int n = 1; n <= completions; n++) if ((streak + n) % 10 == 0) boosted += SlayerPlanEngine.reward(streak + n, schedule);
        return new Result(true, "Easy assignments between milestone tasks; no replacement reset", streak + completions, 0, boosted, preserved,
            List.of("Finish any current assignment without resetting it.", "Take Turael / Aya assignments between milestones.", "Take each tenth assignment from the selected reward master.", "Keep the ordinary task streak; do not ask for a replacement."));
    }
    private static long rewards(int streak, int count, int[] schedule)
    { long total = 0; for (int n = 1; n <= count; n++) total += SlayerPlanEngine.reward(streak + n, schedule); return total; }
    static boolean sameStreakCounter(int first, int second)
    { return first == 7 ? second == 7 : first == 10 ? second == 10 : second != 7 && second != 10; }
    /** Cape perk is a separate opportunity before a normal roll, for the same previous master. */
    static double capeChance(double normal, boolean capePresent, boolean sameMaster, boolean previousTaskStillEligible, boolean targetIsPrevious)
    {
        if (!Double.isFinite(normal) || normal < 0 || normal > 1) throw new IllegalArgumentException("Invalid normal probability");
        if (!capePresent || !sameMaster || !previousTaskStillEligible) return normal;
        return .9 * normal + (targetIsPrevious ? .1 : 0);
    }
    static double bossChance(double familyChance, Double conditionalBossChance)
    {
        if (!Double.isFinite(familyChance) || familyChance < 0 || familyChance > 1 || conditionalBossChance == null) return Double.NaN;
        if (!Double.isFinite(conditionalBossChance) || conditionalBossChance < 0 || conditionalBossChance > 1) throw new IllegalArgumentException("Invalid boss probability");
        return familyChance * conditionalBossChance;
    }
    static double creditsPerHour(int credits, double minutes)
    { if (credits <= 0 || !Double.isFinite(minutes) || minutes <= 0) throw new IllegalArgumentException("Enter credits and elapsed minutes"); return credits * 60.0 / minutes; }
}
