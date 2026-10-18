/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import org.junit.Test;
import static org.junit.Assert.*;

public class ItemSourceHtmlParserTest
{
    @Test public void iconLinksCannotHijackTheSourceArticle()
    {
        ItemSourceService.Lookup lookup=ItemSourceService.parse("Rune axe",1,"<table class='item-drops'><tr><th>Icon</th><th>Source</th><th>Rarity</th></tr><tr><td><a href='/w/File:Dragon.png'><img src='x'></a></td><td><a href='/w/King_Black_Dragon'>King Black Dragon</a></td><td>1/128</td></tr></table>");
        assertEquals("King Black Dragon",lookup.sources.get(0).name);assertTrue(lookup.sources.get(0).wiki.endsWith("/King_Black_Dragon"));
    }
    @Test public void sourceRowspansAndTwoColumnIconsRetainEveryLocation()
    {
        String html="<h2>Item sources</h2><table class='wikitable'><tr><th colspan='2'>Source</th><th>Location</th><th>Quantity</th></tr><tr><td><img></td><td rowspan='2'><a href='/w/Tool_store'>Tool store</a></td><td>Lumbridge</td><td>1</td></tr><tr><td><img></td><td>Varrock</td><td>2</td></tr></table>";
        ItemSourceService.Lookup lookup=ItemSourceService.parse("Hammer",1,html);
        assertEquals(2,lookup.sources.size());assertEquals("Tool store",lookup.sources.get(0).name);assertEquals("Tool store",lookup.sources.get(1).name);assertTrue(lookup.sources.get(1).details.contains("Location: Varrock"));assertTrue(lookup.sources.get(1).details.contains("Quantity: 2"));
    }
    @Test public void acquisitionProseIsAvailableWhileHistoricalStatementsStayExcluded()
    {
        String html="<p>The bag can be purchased from <a href='/w/Prospector_Percy'>Prospector Percy</a> for 100 golden nuggets.</p><h2>Changes</h2><p>It was previously obtained from <a href='/w/Old_source'>Old source</a>.</p><h2>Trivia</h2><p>It can be obtained from <a href='/w/Bad_source'>Bad source</a>.</p>";
        ItemSourceService.Lookup lookup=ItemSourceService.parse("Coal bag",1,html);
        assertEquals(1,lookup.sources.size());assertEquals("Acquisition",lookup.sources.get(0).kind);assertTrue(lookup.sources.get(0).wiki.endsWith("Prospector_Percy"));assertTrue(lookup.sources.get(0).details.contains("100 golden nuggets"));
    }
    @Test public void rewardsAndSpawnsRemainSeparateAndWikiLinksAreAnchoredToTheirSource()
    {
        String html="<h2>Rewards</h2><p>It is a reward from <a href='/w/Quest'>A Quest</a>.</p><h2>Spawns</h2><p>It spawns at <a href='/w/Lumbridge'>Lumbridge</a>.</p>";
        ItemSourceService.Lookup lookup=ItemSourceService.parse("Item",1,html);
        assertEquals(2,lookup.sources.size());assertEquals("Reward",lookup.sources.get(0).kind);assertEquals("Spawn",lookup.sources.get(1).kind);
    }
    @Test public void nestedDecorativeTablesDoNotDisplaceTheParentSourceName()
    {
        String html="<table class='item-drops'><tr><th>Source</th><th>Quantity</th></tr><tr><td><a href='/w/Boss'>Boss</a></td><td>1<table><tr><td>Note</td></tr></table></td></tr></table>";
        ItemSourceService.Lookup lookup=ItemSourceService.parse("Item",1,html);
        assertEquals(1,lookup.sources.size());assertEquals("Boss",lookup.sources.get(0).name);assertTrue(lookup.sources.get(0).details.startsWith("Quantity: 1"));
    }
    @Test public void aNegativeOrFormerSourceNeverBecomesAnAcquisitionCard()
    {
        ItemSourceService.Lookup lookup=ItemSourceService.parse("Item",1,"<p>It can be obtained from <a href='/w/Source'>Source</a> no longer.</p>");assertTrue(lookup.sources.isEmpty());
    }
}
