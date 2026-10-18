/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

public class CombatRollProbabilityTest
{
    @Test public void allSmallSignedRollPairsMatchEnumeratedGameRolls()
    {
        for(int attack=-12;attack<=12;++attack)
            for(int defence=-12;defence<=12;++defence)
            {
                String rolls="attack="+attack+", defence="+defence;
                double normal=enumeratedChance(attack,defence,false);
                double fang=enumeratedChance(attack,defence,true);
                assertEquals(rolls,normal,CombatRollProbability.normalAccuracy(attack,defence),1e-12);
                assertEquals(rolls,fang,CombatRollProbability.fangAccuracy(attack,defence),1e-12);
                assertEquals(rolls,normal,DpsCalculator.normalAccuracy(attack,defence),1e-12);
            }
    }

    @Test public void calculatorRetainsItsSignedRollCompatibilityPolicy()
    {
        assertEquals(.75,DpsCalculator.fangAccuracy(1,-3),1e-12);
        assertEquals(.875,CombatRollProbability.fangAccuracy(1,-3),1e-12);
        assertEquals(.375,DpsCalculator.fangAccuracy(1,1),1e-12);
        assertEquals(5.0/18,DpsCalculator.fangAccuracy(-4,-3),1e-12);
        assertEquals(.75,DpsCalculator.fangAccuracy(1,-2),1e-12);
    }

    @Test public void twoAttacksShareOneDefenceRollAndTiesMiss()
    {
        assertEquals(0,CombatRollProbability.normalAccuracy(0,0),0);
        assertEquals(0,CombatRollProbability.fangAccuracy(0,0),0);
        assertEquals(.25,CombatRollProbability.normalAccuracy(1,1),1e-12);
        assertEquals(.375,CombatRollProbability.fangAccuracy(1,1),1e-12);
        // Independent defence draws instead would incorrectly give 1-(1-.25)^2.
        assertEquals(1.0/6,CombatRollProbability.normalAccuracy(-4,-3),1e-12);
        assertEquals(5.0/18,CombatRollProbability.fangAccuracy(-4,-3),1e-12);
        assertEquals(.75,CombatRollProbability.normalAccuracy(1,-3),1e-12);
        assertEquals(.875,CombatRollProbability.fangAccuracy(1,-3),1e-12);
        for(int collapsed=-2;collapsed<=0;++collapsed)
            assertEquals(.5,CombatRollProbability.fangAccuracy(collapsed,-3),1e-12);
    }

    @Test public void veryLargeSupportsPreserveSmallPositiveChancesAndStayBounded()
    {
        double normal=CombatRollProbability.normalAccuracy(1,Long.MAX_VALUE);
        double fang=CombatRollProbability.fangAccuracy(1,Long.MAX_VALUE);
        assertEquals(Math.scalb(1.0,-64),normal,Math.ulp(normal)*4);
        assertEquals(3*Math.scalb(1.0,-65),fang,Math.ulp(fang)*4);
        assertTrue(normal>0);
        assertTrue(fang>normal);
        long[] bounds={Long.MIN_VALUE,-1,0,1,Long.MAX_VALUE};
        for(long attack:bounds)for(long defence:bounds)
        {
            double single=CombatRollProbability.normalAccuracy(attack,defence);
            double doubled=CombatRollProbability.fangAccuracy(attack,defence);
            assertTrue(Double.isFinite(single)&&single>=0&&single<=1);
            assertTrue(Double.isFinite(doubled)&&doubled>=single-1e-12&&doubled<=1);
        }
    }

    @Test public void conflictionNormalizesSuccessAgainstFailureOdds()
    {
        assertEquals(0,CombatRollProbability.conflictionAccuracy(0,0),0);
        assertEquals(0,CombatRollProbability.conflictionAccuracy(1,0),0);
        assertEquals(1,CombatRollProbability.conflictionAccuracy(1,.375),0);
        assertEquals(1.0/3,CombatRollProbability.conflictionAccuracy(.25,.375),1e-12);
        assertEquals(.5,CombatRollProbability.conflictionAccuracy(.5,.5),1e-12);
    }

    private static double enumeratedChance(int attack,int defence,boolean twoAttacks)
    {
        int[] attacks=possibleRolls(attack),defences=possibleRolls(defence);
        long hits=0,total=0;
        for(int first:attacks)for(int target:defences)
        {
            if(twoAttacks)
            {
                for(int second:attacks)
                {
                    ++total;
                    if(Math.max(first,second)>target)++hits;
                }
            }
            else
            {
                ++total;
                if(first>target)++hits;
            }
        }
        return (double)hits/total;
    }

    private static int[] possibleRolls(int bound)
    {
        int normalized=bound<0?Math.min(0,bound+2):bound;
        int low=Math.min(0,normalized),high=Math.max(0,normalized);
        int[] values=new int[high-low+1];
        for(int index=0;index<values.length;++index)values[index]=low+index;
        return values;
    }
}
