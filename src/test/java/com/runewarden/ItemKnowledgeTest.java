/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemKnowledgeTest
{
    private ItemKnowledgeCatalog catalog;
    @Before public void load()
    {
        Gson gson = new Gson(); catalog = new ItemKnowledgeCatalog(gson, new GuideCatalog(gson)); catalog.load();
    }

    @Test public void fullBundledItemNamesAreSearchableWithoutLogin()
    {
        assertTrue(catalog.size() > 12_000);
        List<ItemKnowledgeCatalog.ItemRef> found = catalog.search("Twisted bow");
        assertFalse(found.isEmpty()); assertEquals(20997, found.get(0).id);
    }

    @Test public void exactIdReturnsOnlyThatItem()
    {
        List<ItemKnowledgeCatalog.ItemRef> found = catalog.search("995");
        assertEquals(1, found.size()); assertEquals("Coins", found.get(0).name);
    }

    @Test public void nativeNewItemsBecomeSearchableAndNamesStayExact()
    {
        catalog.addNative(999999, "New release test item");
        assertEquals(999999, catalog.search("New release test item").get(0).id);
        catalog.addNative(999999, null);
        assertEquals("New release test item", catalog.item(999999).name);
    }

    @Test public void nativeNullDefinitionsAreIgnored()
    {
        int before = catalog.size(); catalog.addNative(999998, "null"); catalog.addNative(999997, "");
        assertEquals(before, catalog.size());
    }

    @Test public void identicalNamesHaveOneBrowserRow()
    {
        catalog.addNative(999996, "Unique browser variant"); catalog.addNative(999995, "Unique browser variant");
        assertEquals(1, catalog.search("Unique browser variant").size());
        assertEquals(999995, catalog.search("Unique browser variant").get(0).id);
        assertEquals(999996, catalog.search("999996").get(0).id);
    }

    @Test public void exactSupplyItemsLinkToTrainingWithoutReintroducingQuestWalkthroughSupplies()
    {
        assertFalse(catalog.related(1944).stream().anyMatch(guide -> "quest".equals(guide.category)));
        assertTrue(catalog.related(10010).stream().anyMatch(guide -> guide.id.equals("training:ruby-harvests")));
    }

    @Test public void bossSourceNamesLinkToExistingGuides()
    { assertTrue(catalog.sourceGuides("Sarachnis").stream().anyMatch(guide -> guide.title.equals("Sarachnis"))); }

    @Test public void commonItemSourcesHavePinnedWikiRevisions()
    {
        ItemSourceService.Lookup yew = catalog.bundled(1515);
        assertNotNull(yew); assertTrue(yew.revision > 0); assertTrue(yew.wiki.contains("oldid="));
        assertTrue(yew.sources.stream().anyMatch(source -> source.name.equals("Yew tree")));
    }
}
