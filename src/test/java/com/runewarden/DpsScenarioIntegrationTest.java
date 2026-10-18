/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Full gear/reference/calculator/workbench integration in addition to arithmetic fixtures. */
public class DpsScenarioIntegrationTest
{
    @Test public void daggerSpecialHasTwoHitsAndFiniteEnergyWithoutARepeatedSpecTtk()
    {
        DpsCalculatorTest.Fixture f=new DpsCalculatorTest.Fixture();f.weapon(1215,"Dragon dagger",40,0,40,4);
        DpsCalculator.Result n=f.calculate("stab","Accurate"),s=special(f,"stab","Accurate");
        assertTrue(s.reason,s.supported);assertEquals(Integer.valueOf(25),s.specialEnergy);assertNull(s.ttkSeconds);assertNull(s.withTtk(100).ttkSeconds);
        assertEquals(Integer.valueOf(n.maxHit*23/20),s.maxHit);assertTrue(s.expectedHit>n.expectedHit);assertNull(n.specialEnergy);
        verify(f.client,never()).setVarbit(anyInt(),anyInt());assertEquals(Integer.valueOf(100),f.target.defenceLevel);
    }
    @Test public void specialDefenceStyleIsIndependentOfTheSelectedOrdinaryAttackType()
    {
        DpsCalculatorTest.Fixture f=new DpsCalculatorTest.Fixture();f.weapon(1215,"Dragon dagger",40,0,40,4);
        f.target.defenceBonuses.put("stab",300);f.target.defenceBonuses.put("slash",0);
        assertTrue(special(f,"stab","Accurate").accuracy>f.calculate("stab","Accurate").accuracy);
    }
    @Test public void voidwakerAlwaysHitsAndUsesMagicDamageRatherThanMeleeFlatArmour()
    {
        DpsCalculatorTest.Fixture f=new DpsCalculatorTest.Fixture();f.weapon(27690,"Voidwaker",80,0,80,4);f.target.defenceBonuses.put("flat_armour",1000);
        DpsCalculator.Result s=special(f,"slash","Aggressive");assertTrue(s.reason,s.supported);assertEquals(1,s.accuracy,0);
        assertTrue(s.minHit>0);assertTrue(s.expectedHit>0);assertNull(s.withTtk(100).ttkSeconds);
    }
    @Test public void specialsDoNotBecomeLiveDefenceDrainObservations()
    {
        DpsCalculatorTest.Fixture f=new DpsCalculatorTest.Fixture();f.weapon(13576,"Dragon warhammer",95,0,85,6);
        DpsCalculator.Result n=f.calculate("crush","Aggressive"),s=special(f,"crush","Aggressive");
        assertTrue(s.reason,s.supported);assertEquals(Integer.valueOf(n.maxHit*3/2),s.maxHit);assertEquals(n.accuracy,s.accuracy,1e-12);
        assertEquals(Integer.valueOf(100),f.target.defenceLevel);assertTrue(s.mechanics.stream().anyMatch(t->t.contains("separately")));
    }
    @Test public void manualPhaseSnapshotRemainsASnapshotAfterTheSelectedSetRequestsTtk()
    {
        DpsCalculatorTest.Fixture f=new DpsCalculatorTest.Fixture();f.weapon(4151,"Abyssal whip",82,0,82,4);
        DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.phase="Vardorvis HP snapshot";i.phaseHp=350;
        DpsReferenceCatalog.Target target=DpsTargetScenario.apply(f.references.variant(12223,"Post-quest"),i);
        DpsCalculator.Result r=DpsCalculator.evaluatePlanned(f.client,f.items,f.references,target,f.gear,new DpsReferenceCatalog.Mode("Flick","slash","Accurate"),null,false,false,null,true);
        assertTrue(r.reason,r.supported);assertTrue(r.snapshotOnly);assertNull(r.ttkSeconds);assertNull(r.withTtk(700).ttkSeconds);
    }
    @Test public void perSetSpecialAndExactVariantChoicesPersistWithoutChangingOtherSets()
    {
        DpsWorkbenchTest.Fixture f=new DpsWorkbenchTest.Fixture();f.tool.selectTargetNow(12191,"Post-quest, Awake");f.tool.selectSetupNow("scimitar");f.tool.specialNow("scimitar",true);
        assertTrue(f.tool.reading().target.contains("Post-quest, Awake"));assertEquals(Integer.valueOf(55),setup(f.tool,"scimitar").result.specialEnergy);assertNull(setup(f.tool,"lance").result.specialEnergy);
        assertFalse(f.tool.reading().current.mode.contains("Special"));f.tool.stop();f.tool.start();f.tool.refreshNow();
        assertEquals(Integer.valueOf(55),setup(f.tool,"scimitar").result.specialEnergy);assertTrue(f.tool.reading().target.contains("Post-quest, Awake"));
    }
    @Test public void drainsRecomputeAccuracyAndAreClearedWhenAPlayerChoosesAnotherTarget()
    {
        DpsWorkbenchTest.Fixture f=new DpsWorkbenchTest.Fixture();f.tool.selectTargetNow(8059);double before=f.tool.reading().current.accuracy;
        DpsTargetScenario.Inputs i=new DpsTargetScenario.Inputs();i.warhammer=2;f.tool.effectsScenarioNow(i);
        assertEquals(Integer.valueOf(105),f.tool.reading().targetFacts.defenceLevel);assertTrue(f.tool.reading().current.accuracy>before);
        f.tool.selectTargetNow(8059);assertEquals(Integer.valueOf(214),f.tool.reading().targetFacts.defenceLevel);assertNull(f.tool.reading().scenario);
    }
    @Test public void unsupportedSpecialNeverFallsBackToAnOrdinaryDpsRanking()
    {
        DpsWorkbenchTest.Fixture f=new DpsWorkbenchTest.Fixture();f.tool.selectTargetNow(8059);f.tool.specialNow("lance",true);
        DpsComparisonTool.Setup s=setup(f.tool,"lance");assertFalse(s.result.supported);assertTrue(s.result.reason.contains("special"));
    }
    private static DpsCalculator.Result special(DpsCalculatorTest.Fixture f,String type,String stance)
    {return DpsCalculator.evaluateAttack(f.client,f.items,f.references,f.target,f.gear,new DpsReferenceCatalog.Mode("Test",type,stance),f.inputs,false,false,null,true,true);}
    private static DpsComparisonTool.Setup setup(DpsComparisonTool tool,String id)
    {for(DpsComparisonTool.Setup s:tool.reading().setups)if(s.id.equals(id))return s;throw new AssertionError(id);}
}
