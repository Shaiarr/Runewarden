/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

/** Hand-checked cases from PlayerVsNPCCalc.ts at the bundled reference commit. */
public class DpsSpecialAttackTest
{
    @Test public void godswordsApplyTheirGenericMultiplierBeforeTheirOwnMultiplier()
    {
        // floor(floor(39 * 1.1) * 1.1) = 46, not floor(39 * 1.21) = 47.
        assertArrayEquals(new int[]{0,46},spec("Bandos godsword").damageRange(0,39));
        assertArrayEquals(new int[]{0,52},spec("Armadyl godsword").damageRange(0,39));
        assertArrayEquals(new int[]{0,42},spec("Saradomin godsword").damageRange(0,39));
        assertArrayEquals(new int[]{0,42},spec("Zamorak godsword").damageRange(0,39));
        assertEquals(20000,spec("Bandos godsword").accuracyRoll(10000));
    }
    @Test public void dragonAndAbyssalDaggersHaveDifferentDamageAndAccuracyFactors()
    {
        assertArrayEquals(new int[]{0,44},spec("Dragon dagger(p++)").damageRange(0,39));
        assertArrayEquals(new int[]{0,33},spec("Abyssal dagger (p++)").damageRange(0,39));
        assertEquals(11500,spec("Dragon dagger").accuracyRoll(10000));
        assertEquals(12500,spec("Abyssal dagger").accuracyRoll(10000));
    }
    @Test public void daggerSecondHitAccuracyIsConditionalForAbyssalAndIndependentForDragon()
    {
        DpsSpecialAttack dragon=spec("Dragon dagger"),abyssal=spec("Abyssal dagger");
        double[] independent=dragon.distribution(.5,0,2,0,1,100,100);
        double[] conditional=abyssal.distribution(.5,0,2,0,1,100,100);
        assertEquals(.25,independent[0],1e-12);assertEquals(.5,conditional[0],1e-12);
        assertEquals(4.0/3,DpsCalculator.mean(independent),1e-12);assertEquals(4.0/3,DpsCalculator.mean(conditional),1e-12);
        assertEquals(1,sum(independent),1e-12);assertEquals(1,sum(conditional),1e-12);
    }
    @Test public void voidwakerIsGuaranteedMagicDamageWithHalfToOneAndAHalfNormalMaximum()
    {
        DpsSpecialAttack p=spec("Voidwaker");assertTrue(p.guaranteed);assertEquals("magic",p.defenceStyle);
        assertArrayEquals(new int[]{19,58},p.damageRange(0,39));
        assertEquals(38.5,DpsCalculator.mean(p.distribution(.01,19,58,0,1,100,100)),1e-12);
    }
    @Test public void fangSpecialRetainsItsMinimumAndRestoresTheUnshrunkMaximum()
    {assertArrayEquals(new int[]{7,50},spec("Osmumten's fang (or)").damageRange(7,43));assertEquals(150,spec("Osmumten's fang").accuracyRoll(100));}
    @Test public void halberdLargeTargetUsesASecondAttackRollAtThreeQuarters()
    {
        DpsSpecialAttack p=spec("Crystal halberd");
        double chance=DpsCalculator.normalAccuracy(200,100),second=DpsCalculator.normalAccuracy(150,100);
        double single=DpsCalculator.mean(DpsCalculator.hitDistribution(1,0,10,0));
        assertEquals(single*chance,DpsCalculator.mean(p.distribution(chance,0,10,0,1,200,100)),1e-12);
        assertEquals(single*(chance+second),DpsCalculator.mean(p.distribution(chance,0,10,0,3,200,100)),1e-12);
    }
    @Test public void blowpipeBallistaAndMaulSpecialsUseDistinctEnergyAndAccuracy()
    {
        assertEquals(50,spec("Toxic blowpipe").energy);assertEquals(25,spec("Rosewood blowpipe").energy);
        assertArrayEquals(new int[]{0,58},spec("Toxic blowpipe").damageRange(0,39));
        assertEquals(200,spec("Toxic blowpipe").accuracyRoll(100));
        assertEquals(2,spec("Rosewood blowpipe").hits);
        assertArrayEquals(new int[]{0,48},spec("Heavy ballista").damageRange(0,39));
        assertEquals(65,spec("Heavy ballista").energy);assertEquals(125,spec("Elder maul").accuracyRoll(100));
        assertArrayEquals(new int[]{0,39},spec("Elder maul").damageRange(0,39));
        assertArrayEquals(new int[]{0,58},spec("Dragon warhammer").damageRange(0,39));
    }
    @Test public void unsupportedOrChargeAmbiguousSpecialsStayUnrated()
    {assertNull(DpsSpecialAttack.forWeapon("Dragon claws"));assertNull(DpsSpecialAttack.forWeapon("Ancient godsword"));assertNull(DpsSpecialAttack.forWeapon("Zaryte crossbow"));assertNull(DpsSpecialAttack.forWeapon(null));}
    @Test public void distributionProbabilityAndFlatArmourRemainCorrectForEverySupportedProfile()
    {
        for(String n:new String[]{"Dragon dagger","Abyssal dagger","Voidwaker","Crystal halberd","Dragon halberd","Elder maul","Dragon warhammer","Dragon mace","Dragon sword","Dragon longsword","Dragon scimitar","Arclight","Emberlight","Abyssal whip","Bandos godsword","Armadyl godsword","Saradomin godsword","Zamorak godsword","Toxic blowpipe","Blazing blowpipe","Rosewood blowpipe","Dragon knife","Heavy ballista","Light ballista","Osmumten's fang"})
        {DpsSpecialAttack p=spec(n);int[] range=p.damageRange(0,30);double[] d=p.distribution(.72,range[0],range[1],2,3,10000,10000);assertEquals(n,1,sum(d),1e-12);for(double probability:d)assertTrue(n,probability>=0&&probability<=1);}
    }
    private static DpsSpecialAttack spec(String name){DpsSpecialAttack p=DpsSpecialAttack.forWeapon(name);assertNotNull(name,p);return p;}
    private static double sum(double[] a){double v=0;for(double p:a)v+=p;return v;}
}
