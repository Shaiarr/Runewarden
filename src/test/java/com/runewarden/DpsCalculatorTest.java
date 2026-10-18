/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.Prayer;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class DpsCalculatorTest
{
    @Test public void normalAccuracyHandlesTheBoundaryAndNegativeRolls()
    {
        assertEquals(10000.0 / 20002, DpsCalculator.normalAccuracy(10000, 10000), 1e-12);
        assertEquals(1 - 10002.0 / 20004, DpsCalculator.normalAccuracy(10001, 10000), 1e-12);
        assertEquals(0, DpsCalculator.normalAccuracy(-100, 100), 0);
        assertEquals(1 - 1.0 / 99 / 101, DpsCalculator.normalAccuracy(100, -100), 1e-12);
        assertEquals(DpsCalculator.normalAccuracy(1998, 998), DpsCalculator.normalAccuracy(-1000, -2000), 1e-12);
    }

    @Test public void fangUsesTheDoubleAttackRollRatherThanTheToaRerollFormula()
    {
        double expected = 10000.0 * 40005 / (6 * 10001.0 * 10001);
        double chance = DpsCalculator.fangAccuracy(10000, 10000);
        assertEquals(expected, chance, 1e-12);
        assertTrue(chance > DpsCalculator.normalAccuracy(10000, 10000));
        assertTrue(Math.abs(chance - (1 - Math.pow(1 - DpsCalculator.normalAccuracy(10000, 10000), 2))) > .05);
        assertEquals(0, DpsCalculator.fangAccuracy(-100, 100), 0);
    }

    @Test public void accurateZeroMinimumAndBothSignsOfFlatArmourChangeExpectedDamage()
    {
        double[] plain = DpsCalculator.hitDistribution(.8, 0, 3, 0);
        assertEquals(.2, plain[0], 1e-12); assertEquals(.4, plain[1], 1e-12);
        assertEquals(1.4, DpsCalculator.mean(plain), 1e-12); // Not 0.8 * max / 2.
        double[] protectedTarget = DpsCalculator.hitDistribution(.8, 0, 3, 2);
        assertEquals(.8, protectedTarget[0], 1e-12); assertEquals(.2, DpsCalculator.mean(protectedTarget), 1e-12);
        assertEquals(3, DpsCalculator.mean(DpsCalculator.hitDistribution(.8, 0, 3, -2)), 1e-12);
        assertEquals(.8, DpsCalculator.mean(DpsCalculator.hitDistribution(.8, 0, 0, 0)), 1e-12);
    }

    @Test public void expectedKillCyclesAccountForMissesAndOverkill()
    {
        assertEquals(6, DpsCalculator.expectedAttacks(new double[] {.5, .5}, 3), 1e-12);
        assertEquals(1.5, DpsCalculator.expectedAttacks(new double[] {0, .5, .5}, 2), 1e-12);
        assertTrue(Double.isInfinite(DpsCalculator.expectedAttacks(new double[] {1}, 100)));
        double[] twoHits = DpsCalculator.convolve(new double[] {.5, .5}, new double[] {.5, .5});
        assertArrayEquals(new double[] {.25, .5, .25}, twoHits, 1e-12);
    }

    @Test public void nativeWornBonusesAndPrayerUseDifferentAttackAndStrengthFactors()
    {
        Fixture f = new Fixture(); f.weapon(4151, "Abyssal whip", 82, 0, 82, 4);
        DpsCalculator.Result plain = f.calculate("slash", "Accurate");
        assertTrue(plain.reason, plain.supported); assertEquals(Integer.valueOf(24), plain.maxHit);
        assertEquals(DpsCalculator.normalAccuracy(110L * 146, 109L * 64), plain.accuracy, 1e-12);
        when(f.client.isPrayerActive(Prayer.PIETY)).thenReturn(true);
        DpsCalculator.Result prayed = f.calculate("slash", "Accurate");
        assertEquals(Integer.valueOf(29), prayed.maxHit);
        assertEquals(DpsCalculator.normalAccuracy(129L * 146, 109L * 64), prayed.accuracy, 1e-12);
        assertTrue(prayed.dps > plain.dps); assertEquals(plain.attackTicks, prayed.attackTicks);
    }

    @Test public void fangHitRangeAndScytheTargetSizeUseTheirOwnDistributions()
    {
        Fixture fang = new Fixture(); fang.weapon(26219, "Osmumten's fang", 105, 0, 103, 5);
        DpsCalculator.Result stab = fang.calculate("stab", "Accurate");
        assertEquals(Integer.valueOf(4), stab.minHit); assertEquals(Integer.valueOf(24), stab.maxHit);
        assertEquals(14 * stab.accuracy, stab.expectedHit, 1e-12);
        assertEquals(DpsCalculator.fangAccuracy(110L * 169, 109L * 64), stab.accuracy, 1e-12);
        Fixture scythe = new Fixture(); scythe.weapon(22325, "Scythe of vitur", 110, 0, 75, 5);
        DpsCalculator.Result small = scythe.calculate("slash", "Aggressive");
        scythe.target.size = 3; DpsCalculator.Result large = scythe.calculate("slash", "Aggressive");
        double expected = DpsCalculator.mean(DpsCalculator.hitDistribution(large.accuracy, 0, large.maxHit, 0))
            + DpsCalculator.mean(DpsCalculator.hitDistribution(large.accuracy, 0, large.maxHit / 2, 0))
            + DpsCalculator.mean(DpsCalculator.hitDistribution(large.accuracy, 0, large.maxHit / 4, 0));
        assertEquals(expected, large.expectedHit, 1e-12); assertTrue(large.dps > small.dps);
    }

    @Test public void dragonHunterAndSalveBonusesStackWithVerifiedDragonUndeadAttributes()
    {
        Fixture f = new Fixture(); f.weapon(22978, "Dragon hunter lance", 85, 0, 70, 4);
        f.item(2, 12018, "Salve amulet(ei)", ItemEquipmentStats.builder().slot(2).build());
        f.target.attributes = List.of("dragon", "undead");
        DpsCalculator.Result reading = f.calculate("stab", "Accurate");
        assertTrue(reading.reason, reading.supported);
        int base = CurrentMaxHit.baseHit(107, 70);
        assertEquals(Integer.valueOf(base * 6 / 5 * 6 / 5), reading.maxHit);
        long attack = 110L * (85 + 64) * 6 / 5 * 6 / 5;
        assertEquals(DpsCalculator.normalAccuracy(attack, 109L * 64), reading.accuracy, 1e-12);
    }

    @Test public void magicUsesMagicDefenceAndElementalWeaknessAddsTheUnboostedSpellBase()
    {
        Fixture f = new Fixture(); f.weapon(1381, "Staff of air", 0, 20, 0, 5);
        CombatSpellCatalog.Spell spell = new CombatSpellCatalog(new Gson()).named("Wind Surge");
        f.inputs = new CurrentMaxHit.Inputs(spell, null, false, false, null);
        f.target.magicLevel = 50; f.target.defenceBonuses.put("magic", 40);
        f.target.weakness = new DpsReferenceCatalog.Weakness(); f.target.weakness.element = "air"; f.target.weakness.severity = 50;
        DpsCalculator.Result reading = f.calculate("magic", "Autocast");
        assertEquals(Integer.valueOf(36), reading.maxHit); assertEquals(Integer.valueOf(5), reading.attackTicks);
        long baseAttack = 108L * (20 + 64);
        assertEquals(DpsCalculator.normalAccuracy(baseAttack + baseAttack / 2, 59L * 104), reading.accuracy, 1e-12);
        f.target.magicUsesDefence = true;
        assertTrue(f.calculate("magic", "Autocast").accuracy < reading.accuracy);
    }

    @Test public void poweredStaffDoesNotBorrowAStaleAutocastSpellAndShadowAccuracyIsTripled()
    {
        Fixture f = new Fixture(); f.weapon(27275, "Tumeken's shadow", 0, 35, 0, 5);
        f.inputs = new CurrentMaxHit.Inputs(new CombatSpellCatalog(new Gson()).named("Ice Barrage"), null, false, false, null);
        DpsCalculator.Result shadow = f.calculate("magic", "Accurate");
        assertTrue(shadow.reason, shadow.supported); assertEquals(Integer.valueOf(34), shadow.maxHit);
        assertEquals(DpsCalculator.normalAccuracy(110L * (35 * 3 + 64), 109L * 64), shadow.accuracy, 1e-12);
        assertEquals(Integer.valueOf(5), shadow.attackTicks);
    }

    @Test public void compatibleAmmoAndObservedBlowpipeLoadAreRequiredAndRapidChangesInterval()
    {
        Fixture bow = new Fixture(); bow.weapon(861, "Magic shortbow", 69, 0, 0, 4);
        assertFalse(bow.calculate("ranged", "Rapid").supported);
        bow.item(13, 11212, "Dragon arrow", ItemEquipmentStats.builder().slot(13).rstr(60).build());
        assertFalse(bow.calculate("ranged", "Rapid").supported); // Above the bow's supported arrow tier.
        bow.item(13, 892, "Rune arrow", ItemEquipmentStats.builder().slot(13).rstr(49).build());
        DpsCalculator.Result ranged = bow.calculate("ranged", "Rapid");
        assertTrue(ranged.reason, ranged.supported); assertEquals(Integer.valueOf(3), ranged.attackTicks);
        Fixture pipe = new Fixture(); pipe.weapon(12926, "Toxic blowpipe", 30, 0, 20, 3);
        assertFalse(pipe.calculate("ranged", "Rapid").supported);
        pipe.inputs = new CurrentMaxHit.Inputs(null, 35, false, false, null, true, true);
        assertTrue(pipe.calculate("ranged", "Rapid").supported);
        assertFalse(DpsCalculator.evaluate(pipe.client, pipe.items, pipe.references, pipe.target, pipe.gear,
            new DpsReferenceCatalog.Mode("Rapid", "ranged", "Rapid"), pipe.inputs, null, true).supported);
    }

    @Test public void unknownTaskProcsAndScalingNeverProduceRankableNumbers()
    {
        Fixture f = new Fixture(); f.weapon(4151, "Abyssal whip", 82, 0, 82, 4);
        f.item(0, 11864, "Slayer helmet", ItemEquipmentStats.builder().slot(0).build());
        assertFalse(f.calculate("slash", "Accurate").supported);
        assertTrue(DpsCalculator.evaluate(f.client, f.items, f.references, f.target, f.gear,
            new DpsReferenceCatalog.Mode("Flick", "slash", "Accurate"), null, false, false).supported);
        f.target.unsupportedReason = "Raid scaling not observed";
        assertNull(f.calculate("slash", "Accurate").dps);
        f.target.unsupportedReason = null;
        when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); assertFalse(f.calculate("slash", "Accurate").supported);
    }

    @Test public void sanguinestiProcIsWeightedBeforeTheAccurateZeroMinimum()
    {
        Fixture f = new Fixture(); f.weapon(22323, "Sanguinesti staff", 0, 25, 0, 4);
        DpsCalculator.Result reading = f.calculate("magic", "Accurate");
        assertTrue(reading.reason, reading.supported); assertEquals(Integer.valueOf(41), reading.maxHit);
        assertEquals(reading.accuracy * (33 / 2.0 + 1.6 + .8 / 34), reading.expectedHit, 1e-12);
        assertEquals(Integer.valueOf(4), reading.attackTicks);
        double[] proc = DpsCalculator.procDistribution(.5, 0, 0, 0, .2, 1, 8);
        assertEquals(.5, proc[0], 1e-12); assertEquals(.4, proc[1], 1e-12); assertEquals(.1, proc[8], 1e-12);
    }

    @Test public void kerisProcScalesTheDamageRollBeforeRaisingAccurateZeros()
    {
        Fixture f = new Fixture(); f.weapon(10581, "Keris", 40, 0, 30, 4); f.target.attributes = List.of("kalphite");
        DpsCalculator.Result reading = f.calculate("stab", "Accurate");
        assertTrue(reading.reason, reading.supported);
        int ordinary = CurrentMaxHit.baseHit(107, 30) * 133 / 100;
        assertEquals(Integer.valueOf(ordinary * 3), reading.maxHit);
        assertEquals(DpsCalculator.mean(DpsCalculator.procDistribution(reading.accuracy, 0, ordinary, 0, 1.0 / 51, 3, 0)), reading.expectedHit, 1e-12);
    }

    @Test public void uniquelyCompatibleNativeQuiverAmmoReplacesTheIncompatibleOrdinarySlot()
    {
        Fixture f = new Fixture(); f.weapon(861, "Magic shortbow", 69, 0, 0, 4);
        f.item(13, 11212, "Dragon arrow", ItemEquipmentStats.builder().slot(13).rstr(60).build()); Item dragon = f.gear[13];
        f.item(13, 892, "Rune arrow", ItemEquipmentStats.builder().slot(13).rstr(49).build()); f.gear[13] = dragon;
        f.item(1, 28951, "Dizana's quiver", ItemEquipmentStats.builder().slot(1).arange(18).rstr(3).build());
        when(f.items.getItemComposition(28951).getIntValue(ParamID.QUIVER_AMMO_AVAILABLE)).thenReturn(1);
        when(f.client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)).thenReturn(892);
        when(f.client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)).thenReturn(100);
        DpsCalculator.Result reading = f.calculate("ranged", "Rapid");
        assertTrue(reading.reason, reading.supported); assertEquals(Integer.valueOf(CurrentMaxHit.baseHit(107, 49 + 3 + 1)), reading.maxHit);
        assertEquals(DpsCalculator.normalAccuracy(107L * (69 + 18 + 10 + 64), 109L * 64), reading.accuracy, 1e-12);
        f.gear[13] = new Item(892, 1);
        when(f.client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO)).thenReturn(21326);
        assertFalse(f.calculate("ranged", "Rapid").supported); // Two different compatible arrows: no selection guess.
    }

    @Test public void selectedSpellsMustMatchTheNativeBookAndCurrentMagicRequirement()
    {
        Fixture f = new Fixture(); f.weapon(1381, "Staff of air", 0, 10, 0, 5);
        CombatSpellCatalog spells = new CombatSpellCatalog(new Gson());
        f.inputs = new CurrentMaxHit.Inputs(spells.named("Wind Surge"), null, true, false, null);
        when(f.client.getBoostedSkillLevel(Skill.MAGIC)).thenReturn(80);
        assertTrue(f.calculate("magic", "Manual Cast").reason.contains("too low"));
        when(f.client.getBoostedSkillLevel(Skill.MAGIC)).thenReturn(99);
        when(f.client.getVarbitValue(VarbitID.SPELLBOOK)).thenReturn(1);
        assertTrue(f.calculate("magic", "Manual Cast").reason.contains("spellbook"));
        f.inputs = new CurrentMaxHit.Inputs(spells.named("Ice Barrage"), null, true, false, null);
        assertTrue(f.calculate("magic", "Manual Cast").supported);
    }

    @Test public void harmonisedStandardAutocastUsesFourTicksWhileManualCastingUsesFive()
    {
        Fixture f = new Fixture(); f.weapon(24423, "Harmonised nightmare staff", 0, 16, 0, 5);
        f.inputs = new CurrentMaxHit.Inputs(new CombatSpellCatalog(new Gson()).named("Wind Surge"), null, false, false, null);
        DpsCalculator.Result automatic = f.calculate("magic", "Autocast"), manual = f.calculate("magic", "Manual Cast");
        assertTrue(automatic.reason, automatic.supported); assertEquals(Integer.valueOf(4), automatic.attackTicks);
        assertEquals(Integer.valueOf(5), manual.attackTicks); assertEquals(automatic.dps * .8, manual.dps, 1e-12);
    }

    @Test public void anImbuedRingCannotTurnAnUnimbuedSlayerHelmetIntoARangedTaskBonus()
    {
        Fixture f = new Fixture(); f.weapon(861, "Magic shortbow", 69, 0, 0, 4);
        f.item(13, 892, "Rune arrow", ItemEquipmentStats.builder().slot(13).rstr(49).build());
        f.item(0, 11864, "Slayer helmet", ItemEquipmentStats.builder().slot(0).build());
        f.item(12, 11773, "Berserker ring (i)", ItemEquipmentStats.builder().slot(12).build());
        assertTrue(f.calculate("ranged", "Rapid").supported);
    }

    @Test public void planningCanSelectADifferentSpellbookWithoutChangingTheClientOrLiveCalculator()
    {
        Fixture f = new Fixture(); f.weapon(1381, "Staff of air", 0, 20, 0, 5);
        CurrentMaxHit.Inputs inputs = new CurrentMaxHit.Inputs(new CombatSpellCatalog(new Gson()).named("Ice Barrage"), null, true, false, null);
        DpsReferenceCatalog.Mode mode = new DpsReferenceCatalog.Mode("Spell", "magic", "Manual Cast");
        assertFalse(DpsCalculator.evaluate(f.client, f.items, f.references, f.target, f.gear, mode, inputs, false, true).supported);
        DpsCalculator.Result planned = DpsCalculator.evaluatePlanned(f.client, f.items, f.references, f.target, f.gear, mode, inputs, false, true, 1, false);
        assertTrue(planned.reason, planned.supported); assertNull(planned.ttkSeconds);
        assertTrue(planned.mechanics.stream().anyMatch(text -> text.contains("Planned ancient")));
        assertNotNull(planned.withTtk(100).ttkSeconds);
        assertEquals(0, f.client.getVarbitValue(VarbitID.SPELLBOOK));
        verify(f.client, never()).setVarbit(anyInt(), anyInt());
    }

    static final class Fixture
    {
        final Client client = mock(Client.class); final ItemManager items = mock(ItemManager.class);
        final Item[] gear = new Item[14]; final DpsReferenceCatalog references = new DpsReferenceCatalog(new Gson());
        final DpsReferenceCatalog.Target target = target(); CurrentMaxHit.Inputs inputs;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            for (Skill skill : new Skill[] {Skill.ATTACK, Skill.STRENGTH, Skill.RANGED, Skill.MAGIC})
            { when(client.getRealSkillLevel(skill)).thenReturn(99); when(client.getBoostedSkillLevel(skill)).thenReturn(99); }
        }
        void weapon(int id, String name, int attack, int magicAttack, int strength, int speed)
        { item(3, id, name, ItemEquipmentStats.builder().slot(3).astab(attack).aslash(attack).acrush(attack).arange(attack).amagic(magicAttack).str(strength).rstr(strength).aspeed(speed).build()); }
        void item(int slot, int id, String name, ItemEquipmentStats stats)
        {
            gear[slot] = new Item(id, 1); ItemComposition item = mock(ItemComposition.class); when(item.getName()).thenReturn(name);
            when(items.getItemComposition(id)).thenReturn(item); when(items.getItemStats(id)).thenReturn(new ItemStats(true, 0, 0, stats));
        }
        DpsCalculator.Result calculate(String type, String stance)
        { return DpsCalculator.evaluate(client, items, references, target, gear, new DpsReferenceCatalog.Mode("Test", type, stance), inputs, null, false); }
        static DpsReferenceCatalog.Target target()
        {
            DpsReferenceCatalog.Target target = new DpsReferenceCatalog.Target(); target.id = 1; target.name = "Reference target";
            target.hitpoints = 100; target.defenceLevel = 100; target.magicLevel = 100; target.magicAttackBonus = 0; target.size = 1;
            target.attributes = List.of(); target.defenceBonuses = new LinkedHashMap<>();
            for (String type : new String[] {"stab", "slash", "crush", "magic", "light", "standard", "heavy", "flat_armour"}) target.defenceBonuses.put(type, 0);
            return target;
        }
    }
}
