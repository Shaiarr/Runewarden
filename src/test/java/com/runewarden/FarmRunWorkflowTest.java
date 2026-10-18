/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
public class FarmRunWorkflowTest
{
    @Test public void requirementsAreForRemainingStopsAndUseOnlyCarriedNativeItems()
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture();ItemContainer inventory=mock(ItemContainer.class);
        when(inventory.getItems()).thenReturn(new Item[]{new Item(5295,1),new Item(952,1)});
        when(f.base.nativeState.client.getItemContainer(InventoryID.INVENTORY)).thenReturn(inventory);f.base.tick(1);
        FarmPatchPlanner.SupplyReading seeds=f.base.planner.currentRun().supplies.stream().filter(s->s.itemId==5295).findFirst().get();
        assertEquals(2,seeds.required);assertEquals(1,seeds.carried);assertEquals(1,seeds.missing);
        f.base.planner.startSelectedRun();f.base.planner.checkNext(f.first.id,false);
        seeds=f.base.planner.currentRun().supplies.stream().filter(s->s.itemId==5295).findFirst().get();assertEquals(1,seeds.required);assertEquals(0,seeds.missing);
        f.base.planner.checkNext(f.second.id,true);assertTrue(f.base.planner.currentRun().supplies.isEmpty());
    }
    @Test public void compostRequiredRunNeverTreatsAPlantingClickAsCompost()
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture();f.base.planner.setCompost(21483);f.base.planner.startSelectedRun();
        when(f.base.nativeState.client.getVarbitValue(f.first.varbit)).thenReturn(32);f.base.tick(5);
        assertEquals(0,f.base.planner.currentRun().completedStops);assertTrue(f.base.planner.currentRun().stops.stream().anyMatch(s->s.id.equals(f.first.id)&&s.status.contains("compost unchecked")));
        f.base.planner.checkNext(f.first.id,false);assertEquals(1,f.base.planner.currentRun().completedStops);
    }
    @Test public void cropLevelsAreCheckedBeforeStartingAndUnknownCropQuantitiesRequireAChoice()
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture();when(f.base.nativeState.client.getBoostedSkillLevel(Skill.FARMING)).thenReturn(1);
        f.base.planner.startSelectedRun();assertFalse(f.base.planner.currentRun().active);assertTrue(f.base.planner.currentRun().status.contains("required"));
    }
    @Test public void completionCallbackCannotAdvanceAChangedNextStopOrAnEndedRun()
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture();f.base.planner.startSelectedRun();FarmPatchPlanner.RunReading old=f.base.planner.currentRun();
        f.base.planner.checkNext(f.second.id,false);assertEquals(0,f.base.planner.currentRun().completedStops);
        f.base.planner.stopActiveRun();f.base.planner.withView(old,()->f.base.planner.startSelectedRun());assertFalse(f.base.planner.currentRun().active);
    }
    @Test public void savedActiveRunLoadsIdleAndRetainsOnlyItsOwnChecklist()
    {
        FarmRunNavigationTest.Fixture f=new FarmRunNavigationTest.Fixture();f.base.planner.startSelectedRun();f.base.planner.checkNext(f.first.id,false);
        f.base.planner.stop();f.base.planner.start();f.base.tick(1);FarmPatchPlanner.RunReading run=f.base.planner.currentRun();
        assertFalse(run.active);assertEquals(1,run.completedStops);assertEquals(f.second.id,run.nextStopId);
    }
    @Test public void birdhouseChoicesUseFourExplicitSpacesAndNeverClaimPerHouseNativeCompletion()
    {
        FarmPatchPlannerTest.Fixture f=new FarmPatchPlannerTest.Fixture();f.planner.start();f.tick(6);f.planner.selectRun("birdhouse");
        FarmPatchPlanner.RunReading run=f.planner.currentRun();assertEquals(4,run.stops.size());assertEquals(9,f.planner.cropChoices().size());
        assertEquals(new net.runelite.api.coords.WorldPoint(3768,3761,0),run.stops.get(0).destination);
        assertTrue(run.stops.stream().allMatch(s->!s.done&&s.status.contains("Check / skip")));
        assertTrue(run.stops.stream().allMatch(s->!s.unlock.isEmpty()));assertFalse(run.active);
    }
}
