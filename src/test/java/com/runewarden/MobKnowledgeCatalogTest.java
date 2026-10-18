/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class MobKnowledgeCatalogTest
{
    private static MobKnowledgeCatalog catalog;

    @BeforeClass public static void load() { catalog = new MobKnowledgeCatalog(new Gson()); }

    private static String text(MobKnowledgeCatalog.Card card) { return String.join("\n", card.lines); }

    @Test public void shipsTheCompletePinnedMonsterSnapshot()
    {
        assertEquals(2817, catalog.npcCount());
        assertEquals(2860, catalog.variantCount());
        assertEquals("a27fc3c898eb15accbfa0457c89b6b6a1b132f1f", catalog.sourceCommit());
        assertEquals("2026-10-16", catalog.retrieved());
    }

    @Test public void separatesElementalVulnerabilityFromLowestMeleeDefenceBonus()
    {
        MobKnowledgeCatalog.Card sarachnis = catalog.describe(8713, "Sarachnis");
        assertTrue(sarachnis.catalogued);
        assertTrue(sarachnis.exactId);
        assertTrue(text(sarachnis).contains("Attacks: Melee / Ranged"));
        assertTrue(text(sarachnis).contains("Elemental weakness: Fire 40%"));
        assertTrue(text(sarachnis).contains("Lowest melee defence bonus: Crush +10"));
        assertFalse(text(sarachnis).contains("Best weapon"));
    }

    @Test public void usesExactNpcVariantsRatherThanFlatteningBossForms()
    {
        MobKnowledgeCatalog.Card magma = catalog.describe(2043, "Zulrah");
        MobKnowledgeCatalog.Card tanzanite = catalog.describe(2044, "Zulrah");
        assertEquals("Magma", magma.variant);
        assertTrue(text(magma).contains("Attacks: Typeless"));
        assertEquals("Tanzanite", tanzanite.variant);
        assertTrue(text(tanzanite).contains("Attacks: Magic / Ranged"));
    }

    @Test public void ambiguousSharedIdsDoNotInventCalculatorStatsOrDefence()
    {
        assertNull(catalog.exact(12191));
        MobKnowledgeCatalog.Card duke = catalog.describe(12191, "Duke Sucellus");
        assertTrue(duke.variant.contains("Multiple variants"));
        assertTrue(text(duke).contains("Defence bonuses: Vary by variant"));
    }

    @Test public void conservativeNameFallbackLabelsItsScopeAndVariableFacts()
    {
        MobKnowledgeCatalog.Card goblin = catalog.describe(999999, "<col=ff00ff>Goblin</col>");
        assertTrue(goblin.catalogued);
        assertFalse(goblin.exactId);
        assertEquals("Name match · variants may differ", goblin.variant);
        assertTrue(text(goblin).contains("Crush"));
        assertTrue(text(goblin).contains("Stab"));
        assertNull(catalog.exact(999999));
    }

    @Test public void unknownTargetsHaveExplicitUnavailableFacts()
    {
        MobKnowledgeCatalog.Card missing = catalog.describe(999999, "Not an OSRS monster");
        assertFalse(missing.catalogued);
        assertTrue(text(missing).contains("Attacks: Not catalogued"));
        assertTrue(text(missing).contains("Weakness: Not catalogued"));
        assertFalse(text(missing).contains("+0"));
    }

    @Test public void retainsBypassExceptionsAndMixedStylesDoNotGetOneProtectionPrayer()
    {
        assertTrue(text(catalog.describe(494, "Kraken")).contains("100% prayer penetration"));
        assertTrue(text(catalog.describe(7706, "TzKal-Zuk")).contains("Typeless"));
        assertTrue(text(catalog.describe(484, "Bloodveld")).contains("Magical melee"));
        assertFalse(text(catalog.describe(3127, "TzTok-Jad")).contains("Protect from"));
        assertFalse(text(catalog.describe(9035, "Corrupted Hunllef")).contains("Pray"));
        assertTrue(text(catalog.describe(494, "Kraken")).contains("Usual protection: None — attacks ignore protection"));
        assertTrue(text(catalog.describe(3127, "TzTok-Jad")).contains("Varies — Melee + Magic + Ranged"));
        assertTrue(text(catalog.describe(9035, "Corrupted Hunllef")).contains("Varies — Magic + Ranged"));
    }

    @Test public void staticUsualProtectionMapsPureAttackTypesAndLabelsPartialMitigation()
    {
        assertTrue(text(catalog.describe(484, "Bloodveld")).contains("Usual protection: Protect from Melee"));
        assertTrue(text(catalog.describe(1675, "Karil the Tainted")).contains("Usual protection: Protect from Missiles"));
        assertTrue(text(catalog.describe(2, "Aberrant spectre")).contains("Usual protection: Protect from Magic"));
        assertTrue(text(catalog.describe(12223, "Vardorvis")).contains("Protect from Melee (partial protection)"));
        assertTrue(text(catalog.describe(2042, "Zulrah")).contains("Varies — Ranged + Magic + Typeless"));
    }

    @Test public void compactSummaryShowsAttacksWeaknessAndProtectionBeforeExpandedStats()
    {
        MobKnowledgeCatalog.Card card = catalog.describe(8713, "Sarachnis");
        assertEquals(3, card.summary.size());
        assertTrue(card.summary.get(0).startsWith("Attacks:"));
        assertTrue(card.summary.get(1).startsWith("Elemental weakness:"));
        assertTrue(card.summary.get(2).startsWith("Usual protection:"));
        assertTrue(card.lines.size() > card.summary.size());
    }

    @Test public void exposesExactBaseFactsToCombatCalculatorsAndWikiLookupIsIdSpecific()
    {
        MobKnowledgeCatalog.Entry kbd = catalog.exact(239);
        assertNotNull(kbd);
        assertEquals(240, kbd.magicLevel);
        assertEquals(0, kbd.magicAttackBonus);
        assertEquals(5, kbd.size);
        assertTrue(kbd.attributes.contains("dragon"));
        assertEquals("https://oldschool.runescape.wiki/w/Special:Lookup?type=npc&id=239", MobKnowledgeCatalog.wikiLookup(239));
    }

    @Test public void globalSearchAcceptsPartialNamesAndExactNpcIds()
    {
        assertTrue(catalog.search("sarach").stream().anyMatch(entry -> entry.id == 8713));
        assertTrue(catalog.search("8713").stream().allMatch(entry -> entry.id == 8713));
        assertEquals(1, catalog.search("8713").size());
        assertTrue(catalog.search("  ").isEmpty());
    }
}
