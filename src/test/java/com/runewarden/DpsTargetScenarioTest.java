/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class DpsTargetScenarioTest
{
    private final DpsReferenceCatalog refs=new DpsReferenceCatalog(new Gson());
    @Test public void manuallySelectedVariantsDoNotMakeAmbiguousNativeIdsAutomatic()
    {
        assertNull(refs.target(12191));assertEquals(2,refs.searchVariants("12191").size());
        assertEquals(Integer.valueOf(275),refs.variant(12191,"Post-quest, Awake").defenceLevel);
        assertEquals(Integer.valueOf(316),refs.variant(12191,"Awakened, Awake").defenceLevel);
        assertNull(refs.variant(12191,"made-up variant"));assertNull(refs.target(12223));
    }
    @Test public void warhammerAndMaulSubtractAFlooredReductionInsteadOfFlooringTheRemainder()
    {
        DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.warhammer=1;
        DpsReferenceCatalog.Target base=refs.target(8059),t=DpsTargetScenario.apply(base,i);
        assertEquals(Integer.valueOf(150),t.defenceLevel);assertEquals(Integer.valueOf(214),base.defenceLevel);
        i.maul=1;assertEquals(Integer.valueOf(98),DpsTargetScenario.apply(base,i).defenceLevel);
    }
    @Test public void curseOrderAndDemonbaneUseCurrentAndBaseDefenceAtTheirCorrectStages()
    {
        DpsReferenceCatalog.Target base=refs.variant(12191,"Post-quest, Awake");
        DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.accursed=true;i.maul=1;i.warhammer=1;i.arclight=1;i.emberlight=1;
        // 275→233→152→107→79→37. Demon drains use the pre-curse base (275).
        DpsReferenceCatalog.Target t=DpsTargetScenario.apply(base,i);assertEquals(Integer.valueOf(37),t.defenceLevel);assertEquals(Integer.valueOf(263),t.magicLevel);
    }
    @Test public void knownNpcFloorsAreRespectedAndUnrelatedNamesDoNotAcquireThem()
    {
        DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.maul=20;i.warhammer=20;i.bgs=2000;
        assertEquals(Integer.valueOf(90),DpsTargetScenario.apply(refs.target(13668),i).defenceLevel);
        DpsReferenceCatalog.Target unrelated=DpsReferenceCatalog.scenario(refs.target(8059),null,null,null);unrelated.name="Nex";
        assertEquals(0,DpsTargetScenario.defenceFloor(unrelated));
    }
    @Test public void magicLevelAndMagicDefenceBonusDrainsStaySeparateAndDoNotMutateCatalogue()
    {
        DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.seercull=10;i.ayak=100;
        DpsReferenceCatalog.Target base=refs.target(8059),t=DpsTargetScenario.apply(base,i);
        assertEquals(Integer.valueOf(base.magicLevel-10),t.magicLevel);assertEquals(Integer.valueOf(140),t.defenceBonuses.get("magic"));
        assertEquals(Integer.valueOf(240),base.defenceBonuses.get("magic"));
    }
    @Test public void bgsOverflowDoesNotInventMissingAttackAndStrengthStats()
    {DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.bgs=215;assertTrue(DpsTargetScenario.apply(refs.target(8059),i).unsupportedReason.contains("BGS"));}
    @Test public void araxxorPhasesAreExplicitSnapshotsWithNoFakeFullFightTtk()
    {
        DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.phase="Araxxor enraged";
        DpsReferenceCatalog.Target t=DpsTargetScenario.apply(refs.target(13668),i);assertNull(t.unsupportedReason);assertTrue(t.snapshotOnly);
        assertEquals(Integer.valueOf(170),t.defenceLevel);assertEquals(Integer.valueOf(218),t.magicLevel);
        assertNotNull(refs.target(13668).unsupportedReason);
    }
    @Test public void vardorvisExactVariantHpScalingMatchesWikiInterpolation()
    {
        DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.phase="Vardorvis HP snapshot";i.phaseHp=350;i.warhammer=5;
        DpsReferenceCatalog.Target t=DpsTargetScenario.apply(refs.variant(12223,"Post-quest"),i);
        assertNull(t.unsupportedReason);assertEquals(Integer.valueOf(180),t.defenceLevel);assertTrue(t.snapshotOnly);
        i.phaseHp=0;assertEquals(Integer.valueOf(145),DpsTargetScenario.apply(refs.variant(12223,"Post-quest"),i).defenceLevel);
        i.phaseHp=700;assertEquals(Integer.valueOf(224),DpsTargetScenario.apply(refs.variant(12223,"Awakened"),i).defenceLevel);
    }
    @Test public void unsupportedEncountersRemainUnsupportedAndInvalidPhaseChoicesFail()
    {
        DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.warhammer=2;
        assertNotNull(DpsTargetScenario.apply(refs.target(11789),i).unsupportedReason);
        i.phase="Araxxor normal";try{DpsTargetScenario.apply(refs.target(8059),i);fail();}catch(IllegalArgumentException expected){}
        i=new DpsTargetScenario.Inputs();i.accursed=true;i.vulnerability=true;assertFalse(DpsTargetScenario.valid(i));
    }
}
