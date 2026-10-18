/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class DpsReferenceCatalogTest
{
    private final DpsReferenceCatalog catalog = new DpsReferenceCatalog(new Gson());
    @Test public void pinnedBaseStatsIncludeDefenceHpAndAllThreeRangedDefences()
    {
        assertTrue(catalog.targetCount() > 2500);
        assertEquals("a27fc3c898eb15accbfa0457c89b6b6a1b132f1f", catalog.monsterCommit());
        assertEquals("462054e6c0686ce318a051288ab91b9bfa1e91b5", catalog.calculationCommit());
        DpsReferenceCatalog.Target vorkath = catalog.target(8059);
        assertNotNull(vorkath); assertNull(vorkath.unsupportedReason);
        assertEquals(Integer.valueOf(750), vorkath.hitpoints); assertEquals(Integer.valueOf(214), vorkath.defenceLevel);
        assertEquals(Integer.valueOf(26), vorkath.defenceBonuses.get("heavy")); assertTrue(vorkath.has("dragon")); assertTrue(vorkath.has("undead"));
    }
    @Test public void sharedNpcIdsWithDifferentHpOrDefenceAreNotResolvedByName()
    {
        assertNull(catalog.target(12191)); // Quest/post-quest/awakened Duke cannot be separated by this ID alone.
        assertTrue(catalog.unavailable(12191).contains("conflicting"));
        assertNull(catalog.target(14176)); // Yama defence changes between reference variants.
        assertNull(catalog.target(999999));
    }
    @Test public void nativeStyleIndexUsesWeaponCategoryRatherThanTheLargestAttackBonus()
    {
        DpsReferenceCatalog.Weapon whip = catalog.weapon(4151);
        assertEquals("slash", catalog.observedMode(whip, 1, false).type);
        assertEquals("Controlled", catalog.observedMode(whip, 1, false).stance);
        DpsReferenceCatalog.Weapon fang = catalog.weapon(26219);
        assertEquals("stab", catalog.observedMode(fang, 0, false).type);
        assertEquals("slash", catalog.observedMode(fang, 2, false).type);
        DpsReferenceCatalog.Mode cast = catalog.observedMode(catalog.weapon(1381), 4, true);
        assertTrue(cast.casting()); assertEquals("Defensive Autocast", cast.stance);
        assertNull(catalog.observedMode(catalog.weapon(1381), 3, false));
        assertTrue(new DpsReferenceCatalog.Mode("Spell", "magic", "Manual Cast").casting());
        assertEquals(Integer.valueOf(81), catalog.spellLevel("Wind Surge"));
        assertEquals(Integer.valueOf(94), catalog.spellLevel("Ice Barrage"));
    }
    @Test public void aliasesKeepVerifiedWeaponCategoryAndAmmoCompatibility()
    {
        assertEquals(catalog.weapon(4151).category, catalog.weapon(26482).category);
        assertTrue(catalog.weapon(861).ammo.contains(892)); assertFalse(catalog.weapon(861).ammo.contains(11212));
        assertTrue(catalog.weapon(12926).ammo.isEmpty());
        assertNotNull(catalog.target(11789)); assertNotNull(catalog.target(11789).unsupportedReason);
        assertNotNull(catalog.target(7851).unsupportedReason); // Dusk immunity list uses a private upstream constant.
    }
    @Test public void outsideCombatSearchKeepsExactVariantsAndNeverResolvesConflictingIds()
    {
        assertTrue(catalog.search("vorkath").size() >= 1);
        assertEquals(8059, catalog.search("8059").get(0).id);
        assertTrue(catalog.search("12191").isEmpty());
        java.util.List<DpsReferenceCatalog.Target> first = catalog.search("dragon"), second = catalog.search("dragon");
        assertEquals(first.size(), second.size());
        for (int i=0;i<first.size();i++) assertEquals(first.get(i).id, second.get(i).id);
    }
    @Test public void scenarioCopiesStatsWithoutMutatingTheCatalogueOrErasingUnsupportedMechanics()
    {
        DpsReferenceCatalog.Target original = catalog.target(8059);
        DpsReferenceCatalog.Target scenario = DpsReferenceCatalog.scenario(original, 100, 0, 50);
        assertEquals(Integer.valueOf(100), scenario.hitpoints); assertEquals(Integer.valueOf(0), scenario.defenceLevel);
        assertEquals(Integer.valueOf(750), original.hitpoints); assertEquals(Integer.valueOf(214), original.defenceLevel);
        DpsReferenceCatalog.Target unsupported = catalog.target(11789);
        assertEquals(unsupported.unsupportedReason, DpsReferenceCatalog.scenario(unsupported, 100, 0, 0).unsupportedReason);
        try { DpsReferenceCatalog.scenario(original, 0, null, null); fail(); } catch (IllegalArgumentException expected) { }
    }
}
