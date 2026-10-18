/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.TimeTrackingConfig;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class FarmHarvestTimersTest
{
    @Test public void readyFruitTreesAreIndependentOfGrowingCelastrusCalquatAndCrystalTrees()
    {
        Fixture f=new Fixture();f.record("FRUIT_TREE",0,"HARVESTABLE",f.now-60);
        for(String type:List.of("CELASTRUS","CALQUAT","CRYSTAL_TREE"))f.record(type,0,"GROWING",f.now-60);
        List<HarvestClockTool.Harvest> rows=f.read();assertEquals(17,rows.size());
        assertEquals(SummaryState.COMPLETED,f.row(rows,"fruit-tree").state);
        for(String id:List.of("celastrus","calquat","crystal-tree"))assertEquals(id,SummaryState.IN_PROGRESS,f.row(rows,id).state);
    }
    @Test public void readyRegularTreesAreIndependentOfHardwoodSpiritAndRedwood()
    {
        Fixture f=new Fixture();f.record("TREE",0,"HARVESTABLE",f.now-60);
        for(String type:List.of("HARDWOOD_TREE","SPIRIT_TREE","REDWOOD"))f.record(type,0,"GROWING",f.now-60);
        List<HarvestClockTool.Harvest> rows=f.read();assertEquals(SummaryState.COMPLETED,f.row(rows,"tree").state);
        for(String id:List.of("hardwood","spirit-tree","redwood"))assertEquals(id,SummaryState.IN_PROGRESS,f.row(rows,id).state);
    }
    @Test public void hesporiAndOtherSpecialImplementationsHaveIndependentObservations()
    {
        Fixture f=new Fixture();f.record("MUSHROOM",0,"HARVESTABLE",f.now-60);f.record("HESPORI",0,"GROWING",f.now-60);
        List<HarvestClockTool.Harvest> rows=f.read();assertEquals(SummaryState.COMPLETED,f.row(rows,"mushroom").state);
        assertEquals(SummaryState.IN_PROGRESS,f.row(rows,"hespori").state);assertEquals(SummaryState.UNKNOWN,f.row(rows,"seaweed").state);
        assertFalse(rows.stream().anyMatch(row->row.id.equals("special")));
    }
    @Test public void nativePreferenceSelectsSoonestOrLatestWithinOneImplementationOnly()
    {
        Fixture f=new Fixture();f.record("FRUIT_TREE",0,"GROWING",f.now-60);f.record("FRUIT_TREE",1,"GROWING",f.now-60);
        when(f.tracker.getTickTime(anyInt(),anyInt(),anyLong(),eq(f.owner))).thenReturn(f.now+60,f.now+3600);
        assertEquals(f.now+3600,f.row(f.read(),"fruit-tree").completion);
        when(f.config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,TimeTrackingConfig.PREFER_SOONEST,boolean.class)).thenReturn(true);
        when(f.tracker.getTickTime(anyInt(),anyInt(),anyLong(),eq(f.owner))).thenReturn(f.now+60,f.now+3600);
        assertEquals(f.now+60,f.row(f.read(),"fruit-tree").completion);
    }
    @Test public void validEmptyDiseasedAndDeadRecordsRetainNativeSummarySemantics()
    {
        Fixture f=new Fixture();FarmPatchCatalog.Patch patch=f.patch("TREE",0);
        f.put(patch,0,f.now-60);assertEquals(SummaryState.EMPTY,f.row(f.read(),"tree").state);
        for(String state:List.of("DISEASED","DEAD"))
        {
            f.record("TREE",0,state,f.now-60);HarvestClockTool.Harvest row=f.row(f.read(),"tree");
            assertEquals(state,SummaryState.COMPLETED,row.state);assertEquals(0,row.completion);
            assertEquals("Due to check · estimated",row.status(f.now));
        }
    }
    @Test public void nativeAccountGrowthClockAndLeaguesRateAreUsedWithoutCopyingTheClockAlgorithm() throws Exception
    {
        Fixture f=new Fixture();FarmPatchCatalog.Patch patch=f.patch("CELASTRUS",0);int value=f.record("CELASTRUS",0,"GROWING",f.now-1);
        FarmPatchCatalog.State state=f.catalog.state(patch.type,value);FarmingTracker nativeClock=mock(FarmingTracker.class,CALLS_REAL_METHODS);
        Field manager=FarmingTracker.class.getDeclaredField("configManager");manager.setAccessible(true);manager.set(nativeClock,f.config);
        when(f.config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,f.owner,TimeTrackingConfig.FARM_TICK_OFFSET_PRECISION,int.class)).thenReturn(160);
        when(f.config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,f.owner,TimeTrackingConfig.FARM_TICK_OFFSET,int.class)).thenReturn(37);
        long expected=nativeClock.getTickTime(state.tickMinutes,state.stages-1-state.stage,f.now-1,f.owner);clearInvocations(nativeClock);
        assertEquals(expected,f.row(f.timers.read(nativeClock,f.owner),"celastrus").completion);
        verify(nativeClock).getTickTime(state.tickMinutes,state.stages-1-state.stage,f.now-1,f.owner);
        when(f.client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL));clearInvocations(nativeClock);
        f.timers.read(nativeClock,f.owner);verify(nativeClock).getTickTime(state.tickMinutes/5,state.stages-1-state.stage,f.now-1,f.owner);
        when(f.client.getWorldType()).thenReturn(EnumSet.of(WorldType.SEASONAL,WorldType.DEADMAN));clearInvocations(nativeClock);
        f.timers.read(nativeClock,f.owner);verify(nativeClock).getTickTime(state.tickMinutes,state.stages-1-state.stage,f.now-1,f.owner);
    }
    @Test public void remotePatchRecordsRemainValidWhenTheSharedLiveVarbitDescribesAnotherRegion()
    {
        Fixture f=new Fixture();FarmPatchCatalog.Patch patch=f.patch("FRUIT_TREE",0);f.record("FRUIT_TREE",0,"GROWING",f.now-60);
        when(f.client.getVarbitValue(patch.varbit)).thenReturn(0);
        assertEquals(SummaryState.IN_PROGRESS,f.row(f.read(),"fruit-tree").state);
        verify(f.config).getConfiguration(TimeTrackingConfig.CONFIG_GROUP,f.owner,patch.regionId+"."+patch.varbit);
        verify(f.client,never()).getVarbitValue(anyInt());
    }
    @Test public void missingMalformedImpossibleOrFutureRecordsCannotBecomeReady()
    {
        Fixture f=new Fixture();FarmPatchCatalog.Patch patch=f.patch("CELASTRUS",0);
        for(String value:new String[]{"oops","4:0","4:-1","999:"+(f.now-60),"-1:"+(f.now-60),"4:"+(f.now+3600),"4:"+(f.now-60)+":extra"})
        {
            f.values.put(f.key(patch),value);HarvestClockTool.Harvest row=f.row(f.read(),"celastrus");
            assertEquals(value,SummaryState.UNKNOWN,row.state);assertEquals(-1,row.completion);
        }
        assertEquals(SummaryState.UNKNOWN,f.row(f.read(),"hespori").state);
    }
    @Test public void logoutAndProfileChangesHideRecordsIncludingChangesDuringTheRead()
    {
        Fixture f=new Fixture();FarmPatchCatalog.Patch patch=f.patch("CELASTRUS",0);int value=f.record("CELASTRUS",0,"GROWING",f.now-60);
        when(f.client.getGameState()).thenReturn(GameState.LOADING);assertTrue(f.read().isEmpty());
        when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);f.profile.set("other");assertTrue(f.read().isEmpty());
        f.profile.set(f.owner);
        when(f.config.getConfiguration(TimeTrackingConfig.CONFIG_GROUP,f.owner,f.key(patch))).thenAnswer(call->{f.profile.set("other");return value+":"+(f.now-60);});
        assertTrue(f.read().isEmpty());
    }
    private static final class Fixture
    {
        final Client client=mock(Client.class);final ConfigManager config=mock(ConfigManager.class);final FarmPatchCatalog catalog=new FarmPatchCatalog(new Gson());
        final FarmingTracker tracker=mock(FarmingTracker.class);final String owner="rsprofile.timer-owner";final AtomicReference<String> profile=new AtomicReference<>(owner);
        final Map<String,String> values=new HashMap<>();final long now=Instant.now().getEpochSecond();final FarmHarvestTimers timers=new FarmHarvestTimers(client,config,catalog);
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(mock(Player.class));when(client.getWorldType()).thenReturn(EnumSet.noneOf(WorldType.class));
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());
            when(config.getConfiguration(eq(TimeTrackingConfig.CONFIG_GROUP),eq(owner),anyString())).thenAnswer(call->values.get(call.getArgument(2)));
            when(tracker.getTickTime(anyInt(),anyInt(),anyLong(),eq(owner))).thenAnswer(call->(long)call.getArgument(2)+(int)call.getArgument(0)*(int)call.getArgument(1)*60L);
        }
        FarmPatchCatalog.Patch patch(String type,int index){return catalog.patches().stream().filter(p->type.equals(p.type)).skip(index).findFirst().orElseThrow();}
        String key(FarmPatchCatalog.Patch patch){return patch.regionId+"."+patch.varbit;}
        void put(FarmPatchCatalog.Patch patch,int value,long time){values.put(key(patch),value+":"+time);}
        int record(String type,int index,String state,long time)
        {
            FarmPatchCatalog.Patch patch=patch(type,index);
            for(int value=0;value<256;value++)
            {
                FarmPatchCatalog.State candidate=catalog.state(type,value);
                if(candidate!=null&&state.equals(candidate.state)&&!"WEEDS".equals(candidate.crop)&&!"SCARECROW".equals(candidate.crop)
                    &&(!"HARVESTABLE".equals(state)||candidate.tickMinutes==0||candidate.stage==candidate.stages-1))
                {put(patch,value,time);return value;}
            }
            throw new AssertionError(type+" has no native "+state+" state");
        }
        List<HarvestClockTool.Harvest> read(){return timers.read(tracker,owner);}
        HarvestClockTool.Harvest row(List<HarvestClockTool.Harvest> rows,String id){return rows.stream().filter(r->r.id.equals(id)).findFirst().orElseThrow();}
    }
}
