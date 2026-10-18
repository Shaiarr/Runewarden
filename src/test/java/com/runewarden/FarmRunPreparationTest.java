/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.time.Instant;
import java.util.List;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.WorldView;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmRunPreparationTest
{
    @Test public void repeatBirdhouseRunAcceptsLogsAndRecycledClockworksWithoutFourPrebuiltHouses()
    {
        BirdFixture f=new BirdFixture();f.base.planner.chooseSeed(22201);
        f.carry(new Item(1513,4),new Item(2347,1),new Item(1755,1),new Item(5311,20));
        FarmPatchPlanner.RunReading run=f.run();assertEquals(4,run.totalStops);assertEquals(1,run.quantity);
        assertFalse(run.supplies.stream().anyMatch(s->s.itemId==22201||s.itemId==8792));
        assertTrue(run.supplies.stream().allMatch(s->s.missing==0));
        assertEquals(4,f.supply(1513).required);assertEquals(40,f.seedUnits().required);assertEquals(40,f.seedUnits().carried);
        assertTrue(run.preparationNotes.stream().anyMatch(note->note.contains("returns the clockwork")));
        f.base.planner.setQuantity(10);assertEquals("One house is rebuilt per space",1,f.run().quantity);
    }
    @Test public void prebuiltAndMixedBirdhouseSuppliesOnlyRequireToolsForTheLogPortion()
    {
        BirdFixture f=new BirdFixture();f.base.planner.chooseSeed(21515);
        f.carry(new Item(21515,4),new Item(5305,40));
        assertTrue(f.run().supplies.stream().allMatch(s->s.missing==0));
        assertFalse(f.run().supplies.stream().anyMatch(s->s.itemId==2347||s.itemId==1755||s.itemId==1521));
        f.carry(new Item(21515,2),new Item(1521,2),new Item(25644,1),new Item(1755,1),new Item(5305,40));
        assertEquals(2,f.supply(21515).required);assertEquals(2,f.supply(1521).required);
        assertTrue(f.run().supplies.stream().allMatch(s->s.missing==0));
        assertFalse(f.run().supplies.stream().anyMatch(s->s.itemId==2347));assertEquals(1,f.supply(25644).required);
    }
    @Test public void mixedAcceptedSeedsCountCorrectFillUnitsAndUnknownOrNotedItemsDoNotSubstitute()
    {
        BirdFixture f=new BirdFixture();f.carry(new Item(5305,10),new Item(5295,15),new Item(30088,100));
        assertEquals(40,f.seedUnits().carried);assertEquals(0,f.seedUnits().missing);assertFalse(f.seedUnits().note.isEmpty());
        f.carry(new Item(30088,100),new Item(1512,4));assertEquals(40,f.seedUnits().missing);assertEquals(4,f.supply(1511).missing);
    }
    @Test public void treeAndFruitRunsRequireReadySaplingsAndOnlyDescribeOptionalProtection()
    {
        BirdFixture f=new BirdFixture();
        for(int sapling:new int[]{5374,5502})
        {
            f.base.planner.selectRun(sapling==5374?"tree":"fruit");f.base.planner.chooseSeed(sapling);f.base.planner.setCompost(0);f.base.tick(1);
            FarmPatchPlanner.RunReading run=f.run();assertEquals(run.totalStops,f.supply(sapling).required);
            assertTrue(run.preparationNotes.stream().anyMatch(note->note.contains("No watering can")));
            assertTrue(run.preparationNotes.stream().anyMatch(note->note.contains(sapling==5374?"25 coconuts":"15 papayas")));
            assertTrue(run.preparationNotes.stream().anyMatch(note->note.contains("Noted payments accepted")));
            assertFalse(run.supplies.stream().anyMatch(s->List.of(5974,5972,5331).contains(s.itemId)));
        }
    }
    @Test public void preExistingSeededHousesNeverCompleteJustBecauseAPlayerStartsOrArrives()
    {
        BirdFixture f=new BirdFixture();f.record(3,Instant.now().getEpochSecond()-100);f.base.planner.startSelectedRun();assertTrue(f.run().active);
        f.base.tick(6);assertEquals(0,f.run().completedStops);
        f.record(3,f.run().startedAt/1000);f.base.tick(1);assertEquals("Loading a newer seeded record is not a refill",0,f.run().completedStops);
    }
    @Test public void nativeUnseededToSeededTransitionAdvancesAndRoutesToNextSelectedSpace()
    {
        BirdFixture f=new BirdFixture();f.record(3,Instant.now().getEpochSecond()-100);f.base.planner.startSelectedRun();f.base.tick(3);
        f.record(2,f.run().startedAt/1000);f.base.tick(1);assertEquals(0,f.run().completedStops);
        f.record(3,f.run().startedAt/1000);f.base.tick(1);
        assertEquals(1,f.run().completedStops);assertEquals("birdhouse:1",f.run().nextStopId);
        assertEquals("Refill observed",f.run().stops.get(0).status);
        assertEquals(new net.runelite.api.coords.WorldPoint(3763,3755,0),f.port.points.get(f.port.points.size()-1));
    }
    @Test public void sameSecondNativeBuildAndSeedingCanCompleteAfterAnActualUnseededWitness()
    {
        BirdFixture f=new BirdFixture();f.base.planner.startSelectedRun();long second=f.run().startedAt/1000;
        f.record(2,second);f.base.tick(3);assertEquals(0,f.run().completedStops);
        f.record(3,second);f.base.tick(1);assertEquals(1,f.run().completedStops);
    }
    @Test public void staleNativeRecordWrongTierDistantHouseOrChangedProfileCannotAutoComplete()
    {
        BirdFixture f=new BirdFixture();long old=Instant.now().getEpochSecond()-100;f.record(2,old);f.base.planner.startSelectedRun();f.base.tick(3);
        f.record(3,old);f.base.tick(1);assertEquals(0,f.run().completedStops);
        f.record(6,f.run().startedAt/1000);f.base.tick(1);assertEquals(0,f.run().completedStops);
        f.base.location.set(new net.runelite.api.coords.WorldPoint(3768,3780,0));f.record(3,f.run().startedAt/1000);f.base.tick(3);assertEquals(0,f.run().completedStops);
        f.base.nativeState.profile.set("rsprofile.other");assertNull(f.run());f.base.tick(1);assertFalse(f.run().active);
    }
    @Test public void losingLoggedInStateHidesTheRunImmediatelyWithoutDestroyingItsChecklist()
    {
        BirdFixture f=new BirdFixture();f.base.planner.startSelectedRun();f.base.planner.checkNext("birdhouse:0",false);
        when(f.base.nativeState.client.getGameState()).thenReturn(GameState.LOADING);assertNull(f.run());f.base.tick(1);assertNull(f.run());
        when(f.base.nativeState.client.getGameState()).thenReturn(GameState.LOGGED_IN);assertEquals(1,f.run().completedStops);assertTrue(f.run().active);
    }
    private static final class BirdFixture
    {
        final FarmPatchPlannerTest.Fixture base=new FarmPatchPlannerTest.Fixture();final FarmRunNavigationTest.Port port=new FarmRunNavigationTest.Port();
        final HarvestClockTool.BirdHouseSite site=HarvestClockTool.BIRD_SITES.get(2);
        BirdFixture()
        {
            when(base.nativeState.client.getIntStack()).thenReturn(new int[]{2});
            WorldView view=mock(WorldView.class);when(base.nativeState.client.getTopLevelWorldView()).thenReturn(view);
            base.location.set(site.point);base.planner.setNavigationPort(port);base.planner.start();base.tick(6);base.planner.selectRun("birdhouse");
        }
        void carry(Item...items)
        {ItemContainer inventory=mock(ItemContainer.class);when(inventory.getItems()).thenReturn(items);when(base.nativeState.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);base.tick(1);}
        void record(int state,long time)
        {when(base.nativeState.client.getVarpValue(site.varp)).thenReturn(state);base.nativeState.settings.put("timetracking|rsprofile.characterA|birdhouse."+site.varp,state+":"+time);}
        FarmPatchPlanner.RunReading run(){return base.planner.currentRun();}
        FarmPatchPlanner.SupplyReading supply(int id){return run().supplies.stream().filter(s->s.itemId==id).findFirst().orElseThrow();}
        FarmPatchPlanner.SupplyReading seedUnits(){return run().supplies.stream().filter(s->s.name.equals("Bird-house seed units")).findFirst().orElseThrow();}
    }
}
