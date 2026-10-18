/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class SlayerAcquisitionPlanTest
{
    @Test public void actualMasterBlockPricesReplaceTheOldUniversalPrice()
    { assertEquals(40, SlayerAcquisitionPlan.blockCost(1)); assertEquals(80, SlayerAcquisitionPlan.blockCost(8)); assertEquals(90, SlayerAcquisitionPlan.blockCost(6)); assertEquals(100, SlayerAcquisitionPlan.blockCost(5)); assertEquals(-1, SlayerAcquisitionPlan.blockCost(10)); }
    @Test public void paidCancellationPreservesStreakWithoutPretendingToCompleteATask()
    {
        SlayerAcquisitionPlan.Result result = plan(SlayerAcquisitionPlan.Mode.POINT_SKIP, 5, 49, 10, true, true);
        assertTrue(result.available); assertEquals(49, result.resultingStreak); assertEquals(30, result.pointsSpent); assertEquals(0, result.completionRewards); assertEquals(360, result.preservedRewards);
    }
    @Test public void replacementResetShowsLostMilestoneRewards()
    {
        SlayerAcquisitionPlan.Result result = plan(SlayerAcquisitionPlan.Mode.TURAEL_RESET, 5, 49, 10, true, true);
        assertTrue(result.available); assertEquals(0, result.resultingStreak); assertEquals(0, result.pointsSpent); assertEquals(150, result.completionRewards); assertEquals(360, result.preservedRewards);
    }
    @Test public void resetIsUnavailableWithoutAnAssignmentOrWhenTuraelCanAssignTheFamily()
    { assertFalse(plan(SlayerAcquisitionPlan.Mode.TURAEL_RESET,5,49,10,false,true).available); assertFalse(plan(SlayerAcquisitionPlan.Mode.TURAEL_RESET,5,49,10,true,false).available); assertFalse(plan(SlayerAcquisitionPlan.Mode.TURAEL_RESET,1,49,10,true,true).available); }
    @Test public void wildernessReplacementDoesNotClaimTheOrdinaryStreakWillReset()
    { assertTrue(plan(SlayerAcquisitionPlan.Mode.TURAEL_RESET,7,49,10,true,true).reason.contains("ordinary streak is separate")); }
    @Test public void comparisonsCannotMixOrdinaryWildernessAndMortimerCounters()
    { assertTrue(SlayerAcquisitionPlan.sameStreakCounter(5,8)); assertFalse(SlayerAcquisitionPlan.sameStreakCounter(5,7)); assertFalse(SlayerAcquisitionPlan.sameStreakCounter(7,5)); assertFalse(SlayerAcquisitionPlan.sameStreakCounter(10,5)); assertFalse(plan(SlayerAcquisitionPlan.Mode.POINT_SKIP,10,49,10,true,true).available); }
    @Test public void boostingUsesAbsoluteMilestonesInsteadOfTenTasksFromNow()
    {
        SlayerAcquisitionPlan.Result result = plan(SlayerAcquisitionPlan.Mode.NINE_PLUS_ONE,5,49,11,true,false);
        assertTrue(result.available); assertEquals(300,result.completionRewards); assertEquals(60,result.resultingStreak);
        assertFalse(plan(SlayerAcquisitionPlan.Mode.NINE_PLUS_ONE,7,49,10,true,false).available);
    }
    @Test public void capeOpportunityIsSeparateAndNeverDoublesNormalWeight()
    { assertEquals(.28,SlayerAcquisitionPlan.capeChance(.2,true,true,true,true),1e-12); assertEquals(.18,SlayerAcquisitionPlan.capeChance(.2,true,true,true,false),1e-12); assertEquals(.2,SlayerAcquisitionPlan.capeChance(.2,true,false,true,true),0); assertEquals(.2,SlayerAcquisitionPlan.capeChance(.2,true,true,false,true),0); }
    @Test public void unknownBossSublistProbabilityIsNotAssumedUniform()
    { assertTrue(Double.isNaN(SlayerAcquisitionPlan.bossChance(.2,null))); assertEquals(.02,SlayerAcquisitionPlan.bossChance(.2,.1),1e-12); }
    @Test public void personalSamplesMeasureAssignmentCreditsAndIncludeElapsedTime()
    { assertEquals(180,SlayerAcquisitionPlan.creditsPerHour(30,10),0); try { SlayerAcquisitionPlan.creditsPerHour(30,0); fail(); } catch(IllegalArgumentException expected) { } }
    @Test public void oldSavedPreferencesAcquireStorageAndMeasurementDefaults()
    { SlayerPlanningTool.Plan old = new Gson().fromJson("{\"version\":1,\"masters\":{\"5\":{\"gargoyles\":{\"decision\":\"DO\"}}}}",SlayerPlanningTool.Plan.class); assertTrue(SlayerPlanningTool.valid(old)); assertEquals(0,old.masters.get(5).get("gargoyles").measuredCredits); }
    @Test public void methodCatalogueCoversBossAndOrdinaryChoicesWithUnlocks()
    { SlayerMethodCatalog methods = new SlayerMethodCatalog(new Gson()); assertEquals(32,methods.size()); assertTrue(methods.forTask("Black demons").stream().anyMatch(method -> "MONKEY_MADNESS_II".equals(method.quest))); assertTrue(methods.forTask("Hellhounds").stream().anyMatch(method -> method.slayer == 91)); assertFalse(methods.forTask("Bloodvelds").isEmpty()); }
    private static SlayerAcquisitionPlan.Result plan(SlayerAcquisitionPlan.Mode mode,int master,int streak,int count,boolean resettable,boolean active)
    { return SlayerAcquisitionPlan.calculate(mode,master,streak,count,SlayerPlanningTool.defaultRewards(5),resettable,active); }
}
