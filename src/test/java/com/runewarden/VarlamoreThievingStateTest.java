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
    @Test public void distractionAndNextCycleUseRangesWithoutInventingANewEvent()
    {
        assertNull(state.distraction(0));state.noticeDistraction(1000);assertEquals(25000,state.distraction(2000).latest);
        assertEquals(85000,state.nextDistraction(2000).earliest);assertEquals(100000,state.nextDistraction(2000).latest);
        assertNull(state.distraction(25000));assertEquals(100000,state.nextDistraction(100001).latest);
        assertEquals("Due · waiting",state.distractionReading(100001).nextText(100001));
        assertFalse(state.distractionReading(100001).active);
    }
    @Test public void nativeEndImmediatelyRemovesTheActiveTimerButPreservesNextCycle()
    {
        state.noticeDistraction(1000);state.observeDistraction(true,true,1000);
        VarlamoreThievingState.DistractionReading active=state.distractionReading(8000);
        assertTrue(active.active);assertTrue(active.synced);assertEquals("~7s",active.elapsed(8000));assertEquals("≤ 17s",active.left(8000));
        state.observeDistraction(false,false,9000);
        VarlamoreThievingState.DistractionReading ended=state.distractionReading(9000);
        assertFalse(ended.active);assertNull(ended.remaining);assertEquals("Not distracted",ended.left(9000));
        assertEquals("~76–91s",ended.nextText(9000));assertEquals(1000,ended.started);
    }
    @Test public void joiningDuringADistractionUsesBoundsFromFirstSeenNotAnInventedStart()
    {
        state.observeDistraction(true,false,100000);
        VarlamoreThievingState.DistractionReading first=state.distractionReading(100000);
        assertTrue(first.active);assertFalse(first.synced);assertEquals(100000,first.observedAt);assertEquals(100000,first.started);
        assertEquals("≥ 0s",first.elapsed(100000));assertEquals("≤ 24s",first.left(100000));
        assertEquals(160000,first.next.earliest);assertEquals(199000,first.next.latest);
        state.observeDistraction(true,false,107000);
        VarlamoreThievingState.DistractionReading later=state.distractionReading(107000);
        assertEquals("≥ 7s",later.elapsed(107000));assertEquals("≤ 17s",later.left(107000));
        assertEquals("~53–92s",later.nextText(107000));assertEquals(100000,later.started);
    }
    @Test public void aContinuouslyWitnessedNativeStartSynchronizesWithoutChat()
    {
        state.observeDistraction(false,false,50000);state.observeDistraction(true,true,50600);
        VarlamoreThievingState.DistractionReading reading=state.distractionReading(52600);
        assertTrue(reading.active);assertTrue(reading.synced);assertEquals("~2s",reading.elapsed(52600));
        assertEquals(134600,reading.next.earliest);assertEquals(149600,reading.next.latest);
    }
    @Test public void simultaneousMessagesAndNativeStartsDoNotRenewTheSameCycle()
    {
        state.noticeDistraction(1000);state.noticeDistraction(1800);state.observeDistraction(true,true,2200);state.noticeDistraction(2900);
        assertEquals(1000,state.distractionReading(3000).started);assertEquals(25000,state.distraction(3000).latest);
        state.observeDistraction(false,false,3100);state.noticeDistraction(3900);
        assertFalse(state.distractionReading(4000).active);
    }
    @Test public void nativeStartFollowedByTheSameChatKeepsItsOriginalAnchor()
    {
        state.observeDistraction(true,true,1000);state.noticeDistraction(1600);state.noticeDistraction(2500);
        assertEquals(1000,state.distractionReading(3000).started);
    }
    @Test public void aTransientNativeGapCannotRestartASynchronizedCycle()
    {
        state.observeDistraction(true,true,1000);state.observeDistraction(false,false,8000);
        assertFalse(state.distractionReading(8000).active);
        state.observeDistraction(true,true,9000);
        VarlamoreThievingState.DistractionReading resumed=state.distractionReading(9000);
        assertTrue(resumed.active);assertTrue(resumed.synced);assertEquals(1000,resumed.started);
        assertEquals("~8s",resumed.elapsed(9000));assertEquals("≤ 16s",resumed.left(9000));
        assertEquals(85000,resumed.next.earliest);assertEquals(100000,resumed.next.latest);
    }
    @Test public void aTransientNativeGapCannotTurnAnUncertainCycleIntoAFreshObservedStart()
    {
        state.observeDistraction(true,false,1000);state.observeDistraction(false,false,8000);state.observeDistraction(true,true,9000);
        VarlamoreThievingState.DistractionReading resumed=state.distractionReading(9000);
        assertTrue(resumed.active);assertFalse(resumed.synced);assertEquals(1000,resumed.started);
        assertEquals("≥ 8s",resumed.elapsed(9000));assertEquals("≤ 16s",resumed.left(9000));
        assertEquals(61000,resumed.next.earliest);assertEquals(100000,resumed.next.latest);
    }
    @Test public void reappearingAfterTheActiveEstimateExpiresKeepsTheOldCycleAndUnknownRemainder()
    {
        state.observeDistraction(true,true,1000);state.observeDistraction(false,false,27000);state.observeDistraction(true,true,28000);
        VarlamoreThievingState.DistractionReading resumed=state.distractionReading(28000);
        assertTrue(resumed.active);assertEquals(1000,resumed.started);assertNull(resumed.remaining);
        assertEquals("Active · timing unknown",resumed.left(28000));assertEquals(85000,resumed.next.earliest);
    }
    @Test public void aNativeActiveFlagOutlivingTheEstimateStaysActiveWithoutRollingItsTimer()
    {
        state.observeDistraction(true,true,1000);state.observeDistraction(true,false,26000);
        VarlamoreThievingState.DistractionReading late=state.distractionReading(26000);
        assertTrue(late.active);assertNull(late.remaining);assertEquals("Active · timing unknown",late.left(26000));
        assertEquals(1000,late.started);assertEquals(100000,late.next.latest);
        state.observeDistraction(true,false,101000);
        assertEquals("Due · waiting",state.distractionReading(101000).nextText(101000));
    }
    @Test public void aLaterWitnessedCycleReplacesThePreviousUncertainObservation()
    {
        state.observeDistraction(true,false,1000);state.observeDistraction(false,false,10000);
        state.observeDistraction(true,true,90000);
        VarlamoreThievingState.DistractionReading reading=state.distractionReading(90000);
        assertTrue(reading.synced);assertEquals(90000,reading.started);assertEquals(174000,reading.next.earliest);
    }
    @Test public void aLateRealChatCanSynchronizeAnInitialMidCycleObservation()
    {
        state.observeDistraction(true,false,1000);state.noticeDistraction(1300);
        assertTrue(state.distractionReading(2000).synced);assertEquals(1300,state.distractionReading(2000).started);
    }
    @Test public void sceneHouseInvalidationPreservesTheGlobalCycleAndClearsOnlyHouseData()
    {
        VarlamoreThievingState.House house=VarlamoreThievingState.House.LAVINIA;
        state.observeDoor(house,VarlamoreThievingState.DoorState.OPEN,1000);state.observeDoor(house,VarlamoreThievingState.DoorState.LOCKED,2000);
        state.enter(house);state.noticeBonus(3000);state.warnReturn();state.requestSearch(new WorldPoint(1670,3088,0));state.observeValuables(1,2,4000);
        state.noticeDistraction(5000);state.observeDistraction(true,true,5000);state.clearHouseObservations();
        assertEquals(VarlamoreThievingState.DoorState.UNKNOWN,state.door(house));assertNull(state.ownerReturn(house,6000));
        assertNull(state.bonus(6000));assertNull(state.container(6000));assertNull(state.looting());assertFalse(state.returnWarning());
        assertEquals(5000,state.distractionReading(6000).started);assertTrue(state.distractionReading(6000).active);
        state.clear();assertSame(VarlamoreThievingState.DistractionReading.EMPTY,state.distractionReading(6000));
    }
    @Test public void emptyAndFutureReadingsCannotDisplayNegativeCountdowns()
    {
        VarlamoreThievingState.DistractionReading empty=state.distractionReading(0);
        assertSame(VarlamoreThievingState.DistractionReading.EMPTY,empty);assertEquals("Unknown",empty.elapsed(0));
        assertEquals("Waiting for first distraction",empty.nextText(0));
        state.noticeDistraction(1000);assertSame(VarlamoreThievingState.DistractionReading.EMPTY,state.distractionReading(500));
    }
    @Test public void houseRouteTargetsSitJustOutsideEachEntrance()
    {
        assertEquals(new WorldPoint(1668,3095,0),VarlamoreThievingState.House.LAVINIA.exterior);
        assertEquals(new WorldPoint(1636,3104,0),VarlamoreThievingState.House.VICTOR.exterior);
        assertEquals(new WorldPoint(1630,3121,0),VarlamoreThievingState.House.CAIUS.exterior);
        for(VarlamoreThievingState.House house:VarlamoreThievingState.House.values())
        {assertFalse(house.contains(house.exterior));assertEquals(1,house.door.distanceTo(house.exterior));}
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
