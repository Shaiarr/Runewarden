package com.runewarden;

import com.google.gson.Gson;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CurrentMaxHitTest
{
    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final ItemManager manager = mock(ItemManager.class);
        final Item[] equipment = new Item[14];
        Fixture(String style, int index)
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            ItemContainer container = mock(ItemContainer.class);
            when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(container);
            when(container.getItems()).thenReturn(equipment);
            when(client.getRealSkillLevel(Skill.MAGIC)).thenReturn(99);
            when(client.getBoostedSkillLevel(Skill.MAGIC)).thenReturn(99);
            when(client.getRealSkillLevel(Skill.STRENGTH)).thenReturn(99);
            when(client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(99);
            when(client.getRealSkillLevel(Skill.RANGED)).thenReturn(99);
            when(client.getBoostedSkillLevel(Skill.RANGED)).thenReturn(99);
            when(client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(index);
            EnumComposition categories = mock(EnumComposition.class);
            EnumComposition styles = mock(EnumComposition.class);
            StructComposition entry = mock(StructComposition.class);
            when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(categories);
            when(categories.getIntValue(anyInt())).thenReturn(1000);
            when(client.getEnum(1000)).thenReturn(styles);
            when(styles.getIntVals()).thenReturn(new int[] {2000, 2001, 2002, 2003, 2004, 2005});
            when(client.getStructComposition(2000 + index)).thenReturn(entry);
            when(entry.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn(style);
        }
        void gear(int slot, int id, String name, int strength, int ranged)
        {
            equipment[slot] = new Item(id, 1);
            ItemComposition definition = mock(ItemComposition.class);
            when(definition.getName()).thenReturn(name);
            when(manager.getItemComposition(id)).thenReturn(definition);
            when(manager.getItemStats(id)).thenReturn(new ItemStats(true, 0, 0,
                ItemEquipmentStats.builder().str(strength).rstr(ranged).build()));
        }
        CurrentMaxHit.Reading read() { return CurrentMaxHit.read(client, manager); }
    }

    @Test public void currentBoostPrayerAndAggressiveStanceChangeOrdinaryMeleeHit()
    {
        Fixture f = new Fixture("Aggressive", 1);
        f.gear(EquipmentInventorySlot.WEAPON.getSlotIdx(), 1, "Dragon axe", 42, 0);
        assertEquals(Integer.valueOf(18), f.read().maxHit);
        when(f.client.getBoostedSkillLevel(Skill.STRENGTH)).thenReturn(118);
        when(f.client.isPrayerActive(Prayer.PIETY)).thenReturn(true);
        // floor(118 * 1.23) + 3 + 8 = 156; floor((156 * (42 + 64) + 320) / 640) = 26.
        assertEquals(Integer.valueOf(26), f.read().maxHit);
        assertEquals("BASE MELEE", f.read().scope);
    }

    @Test public void longrangeDoesNotGetAccurateStanceDamageBonus()
    {
        Fixture accurate = new Fixture("Ranging", 0);
        when(accurate.client.getBoostedSkillLevel(Skill.RANGED)).thenReturn(100);
        accurate.gear(3, 1, "Magic shortbow", 0, 0);
        accurate.gear(13, 2, "Rune arrow", 0, 49);
        assertEquals(Integer.valueOf(20), accurate.read().maxHit);
        Fixture rapid = new Fixture("Ranging", 1);
        when(rapid.client.getBoostedSkillLevel(Skill.RANGED)).thenReturn(100);
        rapid.gear(3, 1, "Magic shortbow", 0, 0);
        rapid.gear(13, 2, "Rune arrow", 0, 49);
        assertEquals(Integer.valueOf(19), rapid.read().maxHit);
        Fixture longrange = new Fixture("Longrange", 3);
        when(longrange.client.getBoostedSkillLevel(Skill.RANGED)).thenReturn(100);
        longrange.gear(3, 1, "Magic shortbow", 0, 0);
        longrange.gear(13, 2, "Rune arrow", 0, 49);
        assertEquals(Integer.valueOf(19), longrange.read().maxHit);
        when(rapid.client.isPrayerActive(Prayer.RIGOUR)).thenReturn(true);
        assertEquals(Integer.valueOf(23), rapid.read().maxHit);
        assertEquals("BASE RANGED", rapid.read().scope);
    }

    @Test public void throwingWeaponIgnoresUnrelatedEquippedAmmunitionStrength()
    {
        Fixture f = new Fixture("Ranging", 0);
        f.gear(3, 1, "Rune dart", 0, 26);
        f.gear(13, 2, "Dragon arrow", 0, 60);
        assertEquals(Integer.valueOf(15), f.read().maxHit);
    }

    @Test public void missingAmmoStatsOrStyleStaysUnknown()
    {
        Fixture f = new Fixture("Ranging", 0);
        f.gear(3, 1, "Magic shortbow", 0, 0);
        assertNull(f.read().maxHit);
        f.gear(13, 2, "Rune arrow", 0, 49);
        when(f.manager.getItemStats(2)).thenReturn(null);
        assertNull(f.read().maxHit);
        when(f.client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(null);
        assertNull(f.read().maxHit);
    }

    @Test public void unsupportedWeaponsAndMagicNeverMasqueradeAsExactNumbers()
    {
        for (String name : new String[] {"Twisted bow", "Toxic blowpipe", "Eclipse atlatl"})
        {
            Fixture f = new Fixture("Ranging", 0);
            f.gear(3, 1, name, 0, 100);
            assertNull(name, f.read().maxHit);
            assertEquals("—", f.read().text());
        }
        Fixture magic = new Fixture("Casting", 4);
        assertNull(magic.read().maxHit);
        Fixture powered = new Fixture("Accurate", 0);
        powered.gear(3, 1, "Trident of the seas", 0, 0);
        assertEquals(Integer.valueOf(28), powered.read().maxHit);
        assertEquals("BASE MAGIC", powered.read().scope);
    }

    @Test public void voidBonusIsAppliedOnlyWithFullMatchingSet()
    {
        Fixture f = new Fixture("Aggressive", 1);
        f.gear(3, 1, "Dragon scimitar", 66, 0);
        assertEquals(Integer.valueOf(22), f.read().maxHit);
        f.gear(0, 2, "Void melee helm", 0, 0);
        f.gear(4, 3, "Void knight top", 0, 0);
        f.gear(7, 4, "Void knight robe", 0, 0);
        assertEquals(Integer.valueOf(22), f.read().maxHit);
        f.gear(9, 5, "Void knight gloves", 0, 0);
        assertEquals(Integer.valueOf(25), f.read().maxHit);
    }

    @Test public void prayerTierOneHasGuaranteedLowLevelBoostAndFloorOrderIsPreserved()
    {
        assertEquals(10, CurrentMaxHit.effectiveLevel(1, 105, 0));
        assertEquals(28, CurrentMaxHit.effectiveLevel(19, 105, 0));
        assertEquals(156, CurrentMaxHit.effectiveLevel(118, 123, 3));
        assertEquals(26, CurrentMaxHit.baseHit(156, 42));
        assertEquals(0, CurrentMaxHit.baseHit(8, -100));
    }

    @Test public void loadedZeroBonusSkillingGearDoesNotBlockAxeButAbsentMetadataStillDoes()
    {
        Fixture f = new Fixture("Aggressive", 1);
        f.gear(3, 1, "Dragon axe", 42, 0);
        f.gear(4, 2, "Lumberjack top", 0, 0);
        when(f.manager.getItemStats(2)).thenReturn(new ItemStats(true, 0.907, 0, null));
        assertEquals(Integer.valueOf(18), f.read().maxHit);
        when(f.manager.getItemStats(2)).thenReturn(null);
        assertNull(f.read().maxHit);
    }
    @Test public void fangReducesTheNormalMaximumAfterFlooring()
    {
        Fixture f = new Fixture("Accurate", 0);
        f.gear(3, 1, "Osmumten's fang", 103, 0);
        int ordinary = CurrentMaxHit.baseHit(107, 103);
        assertEquals(Integer.valueOf(ordinary - ordinary * 3 / 20), f.read().maxHit);
    }

    @Test public void crystalBowUsesOwnAmmunitionAndCrystalArmourThenFloors()
    {
        Fixture f = new Fixture("Ranging", 0);
        f.gear(3, 1, "Bow of faerdhinen (c)", 0, 106);
        f.gear(13, 2, "Dragon arrow", 0, 60); // irrelevant to self-ammo bow
        int ordinary = CurrentMaxHit.baseHit(110, 106);
        assertEquals(Integer.valueOf(ordinary), f.read().maxHit);
        f.gear(0, 3, "Crystal helm", 0, 0);
        f.gear(4, 4, "Crystal body", 0, 0);
        f.gear(7, 5, "Crystal legs", 0, 0);
        assertEquals(Integer.valueOf(ordinary * 46 / 40), f.read().maxHit);
    }

    @Test public void blowpipeRequiresExplicitDartAndIgnoresAmmoSlot()
    {
        Fixture f = new Fixture("Ranging", 1);
        f.gear(3, 1, "Toxic blowpipe", 0, 20);
        f.gear(13, 2, "Dragon arrow", 0, 60);
        assertNull(f.read().maxHit);
        CurrentMaxHit.Reading supplied = CurrentMaxHit.read(f.client, f.manager,
            new CurrentMaxHit.Inputs(null, 35, false, false, null));
        assertEquals(Integer.valueOf(CurrentMaxHit.baseHit(107, 55)), supplied.maxHit);
        assertEquals("INPUT RANGED", supplied.scope);
    }

    @Test public void soulreaperStacksAreAdditiveWithPrayerBeforeVoid()
    {
        Fixture f = new Fixture("Aggressive", 1);
        f.gear(3, 1, "Soulreaper axe", 121, 0);
        when(f.client.isPrayerActive(Prayer.PIETY)).thenReturn(true);
        when(f.client.getVarpValue(VarPlayerID.SOULREAPER_STACKS)).thenReturn(5);
        // floor(99*1.23) + floor(99*0.30) + 11 = 161, not floor(99*1.23*1.30)+11.
        assertEquals(Integer.valueOf(CurrentMaxHit.baseHit(161, 121)), f.read().maxHit);
    }

    @Test public void bulwarkUsesObservedDefensiveBonusesAndBlockDoesNotAttack()
    {
        assertEquals(0, CurrentMaxHit.bulwarkStrength(1000));
        assertEquals(62, CurrentMaxHit.bulwarkStrength(2000));
        Fixture f = new Fixture("Defensive", 1);
        f.gear(3, 1, "Dinh's bulwark", 38, 0);
        assertNull(f.read().maxHit);
    }

    @Test public void poweredMagicFloorsBaseAndDamageBonusAndIncludesPrayer()
    {
        Fixture f = new Fixture("Accurate", 0);
        f.gear(3, 1, "Sanguinesti staff", 0, 0);
        when(f.manager.getItemStats(1)).thenReturn(new ItemStats(true, 0, 0,
            ItemEquipmentStats.builder().mdmg(17.5f).build()));
        when(f.client.isPrayerActive(Prayer.AUGURY)).thenReturn(true);
        assertEquals(Integer.valueOf(48), f.read().maxHit); // floor(33 * 1.215) + 8 normal proc maximum.
        assertEquals(Integer.valueOf(24), CurrentMaxHit.poweredBase("warped sceptre", 99));
        assertEquals(Integer.valueOf(1), CurrentMaxHit.poweredBase("trident of the seas", 1));
    }

    @Test public void shadowMultiplierCapsGearBeforeAddingPrayerAndIsExplicitAboutToa()
    {
        Fixture f = new Fixture("Accurate", 0);
        f.gear(3, 1, "Tumeken's shadow", 0, 0);
        when(f.manager.getItemStats(1)).thenReturn(new ItemStats(true, 0, 0,
            ItemEquipmentStats.builder().mdmg(30f).build()));
        when(f.client.isPrayerActive(Prayer.AUGURY)).thenReturn(true);
        assertEquals(Integer.valueOf(65), f.read().maxHit); // floor(34 * 1.94)
        CurrentMaxHit.Reading toa = CurrentMaxHit.read(f.client, f.manager,
            new CurrentMaxHit.Inputs(null, null, false, true, null));
        assertEquals(Integer.valueOf(69), toa.maxHit); // gear caps at100%; augury4% afterward
        assertTrue(toa.reason.contains("ToA multiplier selected"));
    }

    @Test public void explicitAncientSpellAddsVirtusAndManualScope()
    {
        Fixture f = new Fixture("Casting", 4);
        f.gear(3, 1, "Kodai wand", 0, 0);
        when(f.manager.getItemStats(1)).thenReturn(new ItemStats(true, 0, 0,
            ItemEquipmentStats.builder().mdmg(5f).build()));
        f.gear(4, 2, "Virtus robe top", 0, 0);
        CombatSpellCatalog catalog = new CombatSpellCatalog(new Gson());
        CombatSpellCatalog.Spell ice = catalog.named("Ice Barrage");
        assertNotNull(ice);
        CurrentMaxHit.Reading result = CurrentMaxHit.read(f.client, f.manager,
            new CurrentMaxHit.Inputs(ice, null, true, false, null));
        assertEquals(Integer.valueOf(32), result.maxHit);
        assertEquals("INPUT MAGIC", result.scope);
    }

    @Test public void elementalSpellDamageScalesAtPublishedMagicThresholds()
    {
        CombatSpellCatalog catalog = new CombatSpellCatalog(new Gson());
        assertEquals(2, catalog.named("Wind Strike").maxHit(1));
        assertEquals(4, catalog.named("Wind Strike").maxHit(5));
        assertEquals(8, catalog.named("Wind Strike").maxHit(13));
        assertEquals(21, catalog.named("Wind Surge").maxHit(84));
        assertEquals(24, catalog.named("Wind Surge").maxHit(95));
        assertEquals(19, catalog.named("Magic Dart").maxHit(99));
    }

    @Test public void twistedBowReferenceUsesMagicAttackBonusAndXericianCap()
    {
        Fixture f = new Fixture("Ranging", 0);
        f.gear(3, 1, "Twisted bow", 0, 20);
        f.gear(13, 2, "Dragon arrow", 0, 60);
        MobKnowledgeCatalog.Entry target = new MobKnowledgeCatalog.Entry();
        target.magicLevel = 1; target.magicAttackBonus = 350;
        target.attributes = java.util.Collections.singletonList("xerician");
        CurrentMaxHit.Reading result = CurrentMaxHit.read(f.client, f.manager,
            new CurrentMaxHit.Inputs(null, null, false, false, target));
        assertEquals(Integer.valueOf(CurrentMaxHit.twistedBowHit(CurrentMaxHit.baseHit(110, 80), 350, 350)), result.maxHit);
        assertEquals("REF TBOW", result.scope);
        assertTrue(result.reason.contains("live drains"));
        assertEquals(248, CurrentMaxHit.twistedBowHit(100, 1000, 350));
        assertEquals(CurrentMaxHit.twistedBowHit(100, 250, 250), CurrentMaxHit.twistedBowHit(100, 350, 250));
    }

    @Test public void unchargedWeaponsAndUnknownSpellDoNotProduceNumbers()
    {
        Fixture f = new Fixture("Accurate", 0);
        f.gear(3, 1, "Tumeken's shadow (uncharged)", 0, 0);
        assertNull(f.read().maxHit);
        Fixture spell = new Fixture("Casting", 4);
        spell.gear(3, 1, "Ancient staff", 0, 0);
        assertNull(spell.read().maxHit);
    }

    @Test public void missingWornWeaponNeverMasqueradesAsAnUnarmedHitWhileAppearanceStillHasAWeapon()
    {
        Fixture f = new Fixture("Aggressive", 1);
        net.runelite.api.Player player = mock(net.runelite.api.Player.class);
        net.runelite.api.PlayerComposition appearance = mock(net.runelite.api.PlayerComposition.class);
        when(f.client.getLocalPlayer()).thenReturn(player); when(player.getPlayerComposition()).thenReturn(appearance);
        when(appearance.getEquipmentId(net.runelite.api.kit.KitType.WEAPON)).thenReturn(1234);
        assertNull(f.read().maxHit);
        assertEquals("Worn equipment is updating", f.read().reason);
        when(appearance.getEquipmentId(net.runelite.api.kit.KitType.WEAPON)).thenReturn(-1);
        assertEquals(Integer.valueOf(11), f.read().maxHit);
    }

}
