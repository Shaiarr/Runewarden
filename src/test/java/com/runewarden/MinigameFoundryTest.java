/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MinigameFoundryTest
{
    @Test public void defaultBroadHeavyCommissionUsesOnlyFreeMoulds()
    {
        List<MinigameFoundry.Mould> best=MinigameFoundry.bestOwned(4,5,99,bit->0);
        assertEquals(3,best.size());assertEquals("Medusa Ricasso",best.get(0).name);
        assertEquals(44,best.stream().mapToInt(m->m.score(4,5)).sum());
        assertTrue(best.stream().allMatch(m->m.unlock==0));
    }
    @Test public void allOwnedMouldsGiveKnownOptimalLightFlatScore()
    {
        List<MinigameFoundry.Mould> best=MinigameFoundry.bestOwned(2,3,99,bit->1);
        assertEquals("Chopper Forte +1",best.get(0).name);assertEquals("Choppa!",best.get(1).name);assertEquals("The Point!",best.get(2).name);
        assertEquals(69,best.stream().mapToInt(m->m.score(2,3)).sum());
    }
    @Test public void ownershipDoesNotBypassRequiredSmithingLevel()
    {
        List<MinigameFoundry.Mould> best=MinigameFoundry.bestOwned(2,3,50,bit->1);
        assertTrue(best.stream().allMatch(m->m.level<=50));assertFalse(best.stream().anyMatch(m->m.name.equals("Choppa!")));
    }
    @Test public void lockedOrInvalidUnlockStatesNeverCountAsOwned()
    {
        assertTrue(MinigameFoundry.bestOwned(1,6,99,bit->2).stream().allMatch(m->m.unlock==0));
        assertTrue(MinigameFoundry.bestOwned(0,6,99,bit->1).isEmpty());
        assertTrue(MinigameFoundry.bestOwned(2,2,99,bit->1).isEmpty());
        assertTrue(MinigameFoundry.bestOwned(1,6,14,bit->1).isEmpty());
    }
    @Test public void specificUnlockOnlyAffectsItsOwnPart()
    {
        List<MinigameFoundry.Mould> best=MinigameFoundry.bestOwned(4,6,99,bit->bit==13920?1:0);
        assertEquals("Spiker!",best.get(0).name);assertEquals(0,best.get(1).unlock);assertEquals(0,best.get(2).unlock);
    }
    @Test public void alloyAndPureMetalScoresDifferCorrectly()
    {
        assertEquals(65,MinigameFoundry.alloyScore(new int[]{0,0,14,14,0,0}),.00001);
        assertEquals(20,MinigameFoundry.alloyScore(new int[]{0,28,0,0,0,0}),.00001);
        assertEquals(-1,MinigameFoundry.alloyScore(new int[]{0,0,14,13,0,0}),0);
        assertEquals(-1,MinigameFoundry.alloyScore(new int[]{0,0,-1,29,0,0}),0);
    }
    @Test public void progressBoundariesDoNotRepeatPreviousSection()
    {
        assertEquals(0,MinigameFoundry.stageIndex(166,6));assertEquals(1,MinigameFoundry.stageIndex(167,6));
        assertEquals(-1,MinigameFoundry.stageIndex(1000,6));assertEquals(-1,MinigameFoundry.stageIndex(-1,6));
        assertArrayEquals(new int[]{1,1},MinigameFoundry.actionsLeft(166,6,20,0));
        assertArrayEquals(new int[]{9,9},MinigameFoundry.actionsLeft(167,6,20,0));
    }
    @Test public void partialUniformGivesRangeAndFullUniformIsDeterministic()
    {
        assertArrayEquals(new int[]{23,25},MinigameFoundry.actionsLeft(0,4,10,1));
        assertArrayEquals(new int[]{23,23},MinigameFoundry.actionsLeft(0,4,10,4));
        assertArrayEquals(new int[]{0,0},MinigameFoundry.actionsLeft(1000,4,10,4));
    }
    @Test public void exactHeatBoundaryPermitsOneActionBeforeCorrection()
    {
        assertEquals(1,MinigameFoundry.safeActions(700,700,900,-25));
        assertEquals(9,MinigameFoundry.safeActions(900,700,900,-25));
        assertEquals(1,MinigameFoundry.safeActions(600,400,600,15));
        assertEquals(0,MinigameFoundry.safeActions(699,700,900,-25));
    }
    @Test public void heatAdjustmentNeverRecommendsOvershootingNarrowBand()
    {
        assertEquals(0,MinigameFoundry.heatTicksToBand(500,400,600,false));
        assertEquals(2,MinigameFoundry.heatTicksToBand(685,700,900,false));
        assertEquals(1,MinigameFoundry.heatTicksToBand(685,700,900,true));
        assertEquals(-1,MinigameFoundry.heatTicksToBand(690,700,710,true));
        assertEquals(-1,MinigameFoundry.heatTicksToBand(-10,700,900,false));
    }
}
