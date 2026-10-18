/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

/** Probabilities obtained from the integer supports of independent combat rolls. */
final class CombatRollProbability
{
    private CombatRollProbability() { }

    static double normalAccuracy(long attack, long defence)
    {
        return chanceToBeatDefence(attack, defence, false);
    }

    static double fangAccuracy(long attack, long defence)
    {
        return chanceToBeatDefence(attack, defence, true);
    }

    /** Normalize success mass q against failure mass 1-p. */
    static double conflictionAccuracy(double singleRollChance, double twoRollChance)
    {
        if (twoRollChance == 0) return 0;
        double failureToSuccessOdds = (1 - singleRollChance) / twoRollChance;
        return 1 / (1 + failureToSuccessOdds);
    }

    private static double chanceToBeatDefence(long attack, long defence, boolean twoAttacks)
    {
        long attackBound = normalizedBound(attack);
        long defenceBound = normalizedBound(defence);
        long attackLow = Math.min(0, attackBound), attackHigh = Math.max(0, attackBound);
        long defenceLow = Math.min(0, defenceBound), defenceHigh = Math.max(0, defenceBound);
        double attackCount = count(attackLow, attackHigh);
        double defenceCount = count(defenceLow, defenceHigh);

        // Below the attack support, every attack wins. At or above its upper end, none wins.
        double probability = count(defenceLow, Math.min(defenceHigh, attackLow - 1)) / defenceCount;
        long overlapLow = Math.max(defenceLow, attackLow);
        long overlapHigh = Math.min(defenceHigh, attackHigh - 1);
        if (overlapLow <= overlapHigh)
        {
            double overlapCount = count(overlapLow, overlapHigh);
            // For a defence d in this interval, r = attackHigh - d attacks win out of N.
            // Average r/N and (r/N)^2 over consecutive r using their first two moments.
            double firstWinningCount = attackHigh - overlapHigh;
            double mean = (firstWinningCount + (overlapCount - 1) / 2) / attackCount;
            double chance = mean;
            if (twoAttacks)
            {
                double variance = ((overlapCount - 1) / attackCount)
                    * ((overlapCount + 1) / attackCount) / 12;
                // 1 - (1-r/N)^2, expressed without cancellation at small hit chances.
                chance = mean * (2 - mean) - variance;
            }
            probability += overlapCount / defenceCount * chance;
        }
        return Math.max(0, Math.min(1, probability));
    }

    private static long normalizedBound(long bound)
    {
        return bound < 0 ? Math.min(0, bound + 2) : bound;
    }

    private static double count(long low, long high)
    {
        if (high < low) return 0;
        // Preserve narrow same-sign intervals before converting to floating point.
        return low < 0 && high > 0 ? (double) high - low + 1 : (double) (high - low) + 1;
    }
}
