/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmGrowthTimingTest
{
    @Test public void observedClockMatchesNativeDeadlinesForEveryGrowingCropAndStage() throws Exception
    {
        ConfigManager config=mock(ConfigManager.class);String profile="rsprofile.current";
        when(config.getConfiguration("timetracking",profile,"farmTickOffsetPrecision",int.class)).thenReturn(160);
        when(config.getConfiguration("timetracking",profile,"farmTickOffset",int.class)).thenReturn(37);
        FarmingTracker nativeTracker=mock(FarmingTracker.class,CALLS_REAL_METHODS);
        // Test-only access lets us compare the real pinned native calculation without changing its services.
        java.lang.reflect.Field field=FarmingTracker.class.getDeclaredField("configManager");field.setAccessible(true);field.set(nativeTracker,config);
        FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson());long observed=1_792_300_837L;int checked=0;
        java.util.Set<String> types=new java.util.HashSet<>();for(FarmPatchCatalog.Patch patch:catalog.patches())types.add(patch.type);
        for(String type:types)for(int value=0;value<256;value++)
        {
            FarmPatchCatalog.State state=catalog.state(type,value);if(state==null || !"GROWING".equals(state.state) || state.tickMinutes<=0)continue;
            FarmGrowthTiming.Window actual=FarmGrowthTiming.predict(state,observed*1000,(observed-300)*1000,160,37,false);
            assertTrue(actual.clockKnown);long expected=nativeTracker.getTickTime(state.tickMinutes,state.stages-1-state.stage,observed,profile);
            assertEquals(type+" "+value,expected,actual.latest);assertEquals(expected,actual.earliest);checked++;
        }
        assertTrue("Compare a broad native crop/stage set",checked>250);
    }
    @Test public void unknownClockKeepsTheRemainingStageRangeAndCountsDownWithoutRestartingAtNow()
    {
        FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson());FarmPatchCatalog.State palm=catalog.state("FRUIT_TREE",204);
        FarmGrowthTiming.Window window=FarmGrowthTiming.predict(palm,1_000_000,1_000_000,null,null,false);
        assertFalse(window.clockKnown);assertEquals(1000+160*60,window.earliest);assertEquals(1000+320*60,window.latest);
        FarmPatchPlanner.PatchReading reading=new FarmPatchPlanner.PatchReading(catalog.forGuide("training:fruit-tree-runs").get(0),palm,1_000_000,true);
        assertEquals("~2h 40m–5h 20m",FarmPatchOverlay.timerLabel(reading,1000));
        assertEquals("~1h 40m–4h 20m",FarmPatchOverlay.timerLabel(reading,4600));
        assertEquals("GROWING",reading.state.state);
    }
    @Test public void insufficientClockPrecisionDoesNotInventAnExactDeadlineAndLeaguesUseNativeFasterCycles()
    {
        FarmPatchCatalog.State state=new FarmPatchCatalog(new Gson()).state("FRUIT_TREE",200);
        assertFalse(FarmGrowthTiming.predict(state,1_000_000,1_000_000,20,7,false).clockKnown);
        assertTrue(FarmGrowthTiming.predict(state,1_000_000,1_000_000,40,7,false).clockKnown);
        FarmGrowthTiming.Window league=FarmGrowthTiming.predict(state,1_000_000,1_000_000,null,null,true);
        assertEquals(1000+6*32*60,league.latest);assertEquals(1000+5*32*60,league.earliest);
    }
    @Test public void matureHealthCheckStateIsDueAndNeverShownAsSixMoreGrowthCycles()
    {
        FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson());FarmPatchCatalog.State mature=catalog.state("FRUIT_TREE",226);
        FarmPatchPlanner.PatchReading reading=new FarmPatchPlanner.PatchReading(catalog.forGuide("training:fruit-tree-runs").get(0),mature,1_000_000,1_000_000,true,null,0,160,37,false);
        assertEquals(6,mature.stage);assertEquals("Due to check · estimated",FarmPatchOverlay.timerLabel(reading,1000));
        assertEquals(net.runelite.api.gameval.ItemID.PLANTPOT_PALM_SAPLING,catalog.iconId(mature));
    }
}
