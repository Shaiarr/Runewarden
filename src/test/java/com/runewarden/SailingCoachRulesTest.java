/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.Arrays;
import java.util.HashSet;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;
import static org.junit.Assert.*;

public class SailingCoachRulesTest
{
    @Test public void allThirtyPortsHaveUniqueNativeRowsAndCorrectSarimKhazardIdentity()
    {
        assertEquals(30,SailingCoachRules.PORTS.length);HashSet<Integer> rows=new HashSet<>(),ledgers=new HashSet<>();
        for(SailingCoachRules.Port port:SailingCoachRules.PORTS){assertTrue(rows.add(port.row));assertTrue(ledgers.add(port.ledger));assertEquals(0,port.point.getPlane());}
        assertEquals("Port Sarim",SailingCoachRules.port(8587).name);assertEquals("Port Khazard",SailingCoachRules.port(8597).name);assertNull(SailingCoachRules.port(0));
    }
    @Test public void headingUsesTheNativeSouthZeroClockwiseOrientation()
    {
        assertEquals("S",SailingCoachRules.heading(0));assertEquals("W",SailingCoachRules.heading(512));assertEquals("N",SailingCoachRules.heading(1024));assertEquals("E",SailingCoachRules.heading(1536));assertEquals("SSE",SailingCoachRules.heading(2047));
        assertEquals("Unknown",SailingCoachRules.heading(-1));assertEquals("Unknown",SailingCoachRules.heading(2048));
    }
    @Test public void speedRoundsToQuarterTilesAndPreservesWorldPositionAcrossSceneShifts()
    {
        assertEquals(1.25,SailingCoachRules.speed(400000,400000,400160,400000),0);assertEquals(.25,SailingCoachRules.speed(0,0,31,0),0);assertEquals(0,SailingCoachRules.speed(0,0,15,0),0);
        assertEquals(0,SailingCoachRules.speed(400000,400000,400000,400000),0);assertEquals(1.5,SailingCoachRules.speed(0,0,128,128),0);
    }
    @Test public void progressAcceptsBothNativeHudModesAndRejectsMalformedOrImpossibleCounts()
    {
        assertArrayEquals(new int[]{2,3},SailingCoachRules.progress("2 / 3"));assertArrayEquals(new int[]{12,24},SailingCoachRules.progress(" 12 / 24 Jubblies lured "));
        for(String input:Arrays.asList(null,"","1 / 0","4 / 3","99999999999999 / 3","-1 / 3","1 / three","1 / 3oops","1 / 3 / 4"))assertNull(input,SailingCoachRules.progress(input));
    }
    @Test public void ambiguousTrialStateIsUnknownRatherThanGuessingAContext()
    {
        assertEquals("Tempor Tantrum",SailingCoachRules.trial(2,0,0));assertEquals("Jubbly Jive",SailingCoachRules.trial(0,2,0));assertEquals("Gwenith Glide",SailingCoachRules.trial(0,0,2));
        assertNull(SailingCoachRules.trial(2,2,0));assertNull(SailingCoachRules.trial(0,0,0));assertEquals("Unranked",SailingCoachRules.difficulty(1));assertEquals("Marlin",SailingCoachRules.difficulty(4));assertNull(SailingCoachRules.difficulty(5));
    }
    @Test public void cargoCapacityUsesObservedHoldTierSizeAndAccountRestriction()
    {
        assertEquals(240,SailingCoachRules.capacity(ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE,false));assertEquals(10,SailingCoachRules.capacity(ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE,true));
        assertEquals(30,SailingCoachRules.capacity(ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT,false));assertEquals(-1,SailingCoachRules.capacity(0,false));
    }
}
