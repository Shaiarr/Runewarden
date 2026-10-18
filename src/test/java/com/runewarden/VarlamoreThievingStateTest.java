/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class VarlamoreThievingStateTest
{
    private final VarlamoreThievingState state=new VarlamoreThievingState();
    @Test public void lockedDoorOnEntryDoesNotInventDepartureTime()
    {state.observeDoor(VarlamoreThievingState.House.LAVINIA,VarlamoreThievingState.DoorState.LOCKED,1000);assertNull(state.ownerReturn(VarlamoreThievingState.House.LAVINIA,2000));}
    @Test public void witnessedOpenToLockedTransitionStartsTheVerifiedReturnRange()
    {
        state.observeDoor(VarlamoreThievingState.House.LAVINIA,VarlamoreThievingState.DoorState.OPEN,1000);
        state.observeDoor(VarlamoreThievingState.House.LAVINIA,VarlamoreThievingState.DoorState.LOCKED,2000);
        VarlamoreThievingState.Window window=state.ownerReturn(VarlamoreThievingState.House.LAVINIA,2000);
        assertEquals(182000,window.earliest);assertEquals(192000,window.latest);assertEquals("~180–190s",window.remaining(2000));
        assertEquals("≤ 5s",window.remaining(187000));assertEquals("Timing window passed",window.remaining(195000));
    }
    @Test public void reopeningOrLosingTheDoorInvalidatesItsReturnEstimate()
    {
        state.observeDoor(VarlamoreThievingState.House.CAIUS,VarlamoreThievingState.DoorState.OPEN,0);state.observeDoor(VarlamoreThievingState.House.CAIUS,VarlamoreThievingState.DoorState.LOCKED,1000);
        state.observeDoor(VarlamoreThievingState.House.CAIUS,VarlamoreThievingState.DoorState.UNKNOWN,2000);assertNull(state.ownerReturn(VarlamoreThievingState.House.CAIUS,3000));
    }
    @Test public void distractionAndNextCycleUseRangesAndExpireWithoutPretendingNewEvent()
    {
        assertNull(state.distraction(0));state.noticeDistraction(1000);assertEquals(25000,state.distraction(2000).latest);
        assertEquals(85000,state.nextDistraction(2000).earliest);assertEquals(100000,state.nextDistraction(2000).latest);
        assertNull(state.distraction(25000));assertNull(state.nextDistraction(100001));
    }
    @Test public void searchClickAloneCannotStartAContainerDeadline()
    {
        state.enter(VarlamoreThievingState.House.LAVINIA);state.requestSearch(new WorldPoint(1670,3088,0));assertNull(state.container(1000));
        state.observeValuables(10,10,1000);assertNull(state.container(1000));state.observeValuables(10,11,2000);
        assertEquals(52000,state.container(3000).earliest);assertEquals(62000,state.container(3000).latest);
    }
    @Test public void repeatedClickOnSameContainerDoesNotRestartTheObservedLootWindow()
    {
        state.enter(VarlamoreThievingState.House.LAVINIA);WorldPoint target=new WorldPoint(1670,3088,0);state.requestSearch(target);state.observeValuables(10,11,1000);
        state.requestSearch(target);state.observeValuables(11,12,2000);assertEquals(1000,state.container(3000).started);
        state.emptyContainer();assertTrue(state.depleted());assertNull(state.container(3000));
        state.requestSearch(new WorldPoint(1671,3088,0));assertFalse(state.depleted());assertNull(state.container(3000));
    }
    @Test public void houseSwitchAndClearInvalidateHouseSpecificBonusAndWarning()
    {
        state.enter(VarlamoreThievingState.House.LAVINIA);state.noticeBonus(1000);state.warnReturn();assertNotNull(state.bonus(2000));assertTrue(state.returnWarning());
        state.enter(VarlamoreThievingState.House.CAIUS);assertNull(state.bonus(2000));assertFalse(state.returnWarning());state.noticeDistraction(1000);state.clear();assertNull(state.nextDistraction(2000));
    }
    @Test public void outsideMessagesCannotCreateHouseTimerOrReturnWarning()
    {state.noticeBonus(1000);state.warnReturn();assertNull(state.bonus(1000));assertFalse(state.returnWarning());}
    @Test public void tradeInUsesGladiatorThresholdAndLongArithmetic()
    {assertEquals(5500,VarlamoreThievingState.tradeIn(100,7999));assertEquals(6500,VarlamoreThievingState.tradeIn(100,8000));assertEquals(139586437055L,VarlamoreThievingState.tradeIn(Integer.MAX_VALUE,8000));assertEquals(0,VarlamoreThievingState.tradeIn(-1,8000));}
    @Test public void houseAndCityGatesRequireTheCorrectPlane()
    {
        assertEquals(VarlamoreThievingState.House.LAVINIA,VarlamoreThievingState.House.at(new WorldPoint(1670,3088,0)));
        assertNull(VarlamoreThievingState.House.at(new WorldPoint(1670,3088,1)));assertFalse(VarlamoreThievingState.activityArea(new WorldPoint(1670,3088,1)));
        assertFalse(VarlamoreThievingState.activityArea(new WorldPoint(3200,3200,0)));
    }
}
