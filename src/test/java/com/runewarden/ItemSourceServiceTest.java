/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

public class ItemSourceServiceTest
{
    @Test public void dropQuantitiesAndConditionalRarityAreRetainedVerbatim()
    {
        String table = "<table class='item-drops'><tr><th>Source</th><th>Level</th><th>Quantity</th><th>Rarity</th><th>League region</th></tr>"
            + "<tr><td><a href='/w/King_Black_Dragon'>King Black Dragon</a></td><td>276</td><td>150&nbsp;(noted)</td><td>10/128</td><td>Wilderness</td></tr></table>";
        ItemSourceService.Lookup result = ItemSourceService.parse("Yew logs", 123, table);
        assertEquals(1, result.sources.size());
        ItemSourceService.Source source = result.sources.get(0);
        assertEquals("King Black Dragon", source.name); assertEquals("Drop / reward", source.kind);
        assertTrue(source.details.contains("150 (noted)")); assertTrue(source.details.contains("10/128"));
        assertFalse(source.details.contains("League")); assertTrue(source.wiki.endsWith("/King_Black_Dragon"));
        assertTrue(result.wiki.endsWith("oldid=123"));
    }

    @Test public void gatheringSourcesAreDistinguishedFromCombatDrops()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Yew logs", 123,
            "<table class='item-drops'><tr><th>Source</th><th>Level</th><th>Quantity</th><th>Rarity</th></tr>"
            + "<tr><td>Yew tree</td><td><span class='drops-woodcutting'>60</span></td><td>1</td><td>Always</td></tr></table>");
        assertEquals("Woodcutting", result.sources.get(0).kind);
        assertTrue(result.sources.get(0).details.contains("Level: 60"));
    }

    @Test public void shopsRetainStockLocationAndSalePrice()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Knife", 123,
            "<table class='store-locations-list'><tr><th>Seller</th><th>Location</th><th>Number<br>in stock</th><th>Price<br>sold at</th></tr>"
            + "<tr><td><a href='/w/Shop'>Shop</a></td><td>Lumbridge</td><td>10</td><td>6</td></tr></table>");
        assertEquals("Shop", result.sources.get(0).kind);
        assertTrue(result.sources.get(0).details.contains("Location: Lumbridge"));
        assertTrue(result.sources.get(0).details.contains("Price sold at: 6"));
    }

    @Test public void skillInfoPreservesNamedActionRequirementAndXp()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Yew logs", 123,
            "<table class='infobox skill-info'><tr><th>Yew tree</th></tr><tr><th>Level required</th><td>60</td></tr>"
            + "<tr><th>Woodcutting XP</th><td>175 xp</td></tr><tr><th>Tree</th><td>Yew tree</td></tr></table>");
        assertEquals("Training", result.sources.get(0).kind);
        assertTrue(result.sources.get(0).details.contains("Woodcutting XP: 175 xp"));
    }

    @Test public void productionTablesIdentifyNamedProductAndItsIngredients()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Yew logs", 123,
            "<h2>Products</h2><table class='products-list'><tr><th></th><th>Item</th><th>Skills</th><th>Materials</th></tr>"
            + "<tr><td></td><td>Yew longbow (u)</td><td>70 Fletching</td><td>1 Yew logs</td></tr></table>");
        assertEquals("Production", result.sources.get(0).kind);
        assertEquals("Yew longbow (u)", result.sources.get(0).name);
        assertTrue(result.sources.get(0).details.contains("Materials: 1 Yew logs"));
    }

    @Test public void unrelatedArticleTablesAreNotInventedSources()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Test", 123,
            "<h2>Changes</h2><table class='wikitable'><tr><th>Date</th><th>Change</th></tr><tr><td>2026</td><td>Updated</td></tr></table>");
        assertTrue(result.sources.isEmpty());
    }

    @Test public void repeatedTranscludedRowsAreDeduplicated()
    {
        String table = "<table class='item-drops'><tr><th>Source</th><th>Rarity</th></tr><tr><td>Boss</td><td>Varies</td></tr></table>";
        ItemSourceService.Lookup result = ItemSourceService.parse("Item", 123, table + table);
        assertEquals(1, result.sources.size()); assertEquals("Rarity: Varies", result.sources.get(0).details);
    }

    @Test public void markupAndEntitiesAreConvertedToPlainText()
    { assertEquals("1–4 (noted) & more", ItemSourceService.plain("<span>1&ndash;4&nbsp;(noted)</span> &amp; more")); }

    @Test public void equipmentRequirementsStaySeparateFromToolUseRequirements()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Dragon pickaxe", 123,
            "<p>It requires 61 <a>Mining</a> to mine with the pickaxe and 60 <a>Attack</a> to wield it.</p><h2>Changes</h2>"
            + "<p>It previously required 40 Mining to mine.</p>");
        assertEquals(2, result.sources.size());
        assertTrue(result.sources.stream().anyMatch(source -> source.name.equalsIgnoreCase("Mine") && source.details.equals("Mining 61")));
        assertTrue(result.sources.stream().anyMatch(source -> source.name.equalsIgnoreCase("Wield") && source.details.equals("Attack 60")));
        assertFalse(result.sources.stream().anyMatch(source -> source.details.contains("40")));
    }

    @Test public void sharedEquipmentLevelAppliesToBothExplicitSkills()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Masori body (f)", 123,
            "<p>The body requires level 80 <a>Ranged</a> and <a>Defence</a> to equip.</p>");
        assertEquals("Ranged 80 · Defence 80", result.sources.get(0).details);
        assertEquals("Equip", result.sources.get(0).name);
    }

    @Test public void namedSkillLevelRequirementIsReadWithoutHistoricalValues()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Twisted bow", 123,
            "<p>The bow requires a <a>Ranged</a> level of 85 to wield.</p>");
        assertEquals("Ranged 85", result.sources.get(0).details);
    }

    @Test public void realWikiColspanItemHeaderAlignsWithIconAndProductName()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Yew logs", 123,
            "<h2>Products</h2><table class='products-list'><tr><th colspan='2'>Item</th><th>Skills</th><th>Materials</th></tr>"
            + "<tr><td><img src='icon.png'></td><td>Yew longbow (u)</td><td>70 Fletching</td><td>1 Yew logs</td></tr></table>");
        assertEquals("Yew longbow (u)", result.sources.get(0).name);
        assertTrue(result.sources.get(0).details.contains("Skills: 70 Fletching"));
    }

    @Test public void creationRequirementsKeepLevelAndXpInTheirCorrectColumns()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Masori body (f)", 123,
            "<h2>Creation</h2><table class='wikitable'><caption>Requirements</caption>"
            + "<tr><th colspan='2'>Skill</th><th>Level</th><th>XP</th></tr>"
            + "<tr><td colspan='2'>Crafting</td><td>90 (b)</td><td>3,320</td></tr></table>");
        assertEquals("Creation requirements", result.sources.get(0).kind);
        assertEquals("Crafting", result.sources.get(0).name);
        assertEquals("Level: 90 (b) · XP: 3,320", result.sources.get(0).details);
    }

    @Test public void fullWidthQuestUnlockRowRemainsAVisibleCreationRequirement()
    {
        ItemSourceService.Lookup result = ItemSourceService.parse("Ranarr weed", 123,
            "<h2>Creation</h2><table class='wikitable'><caption>Requirements</caption>"
            + "<tr><th colspan='2'>Skill</th><th>Level</th><th>XP</th></tr>"
            + "<tr><td colspan='4'>Completion of A Kingdom Divided</td></tr></table>");
        assertEquals("Completion of A Kingdom Divided", result.sources.get(0).name);
    }
}
