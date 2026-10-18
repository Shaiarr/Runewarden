/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemAcquisitionCoverageTest
{
    @Test public void progressionSuppliesEquipmentAndUntradeablesHaveOfflineAcquisitionFacts()
    {
        Gson gson=new Gson();ItemKnowledgeCatalog catalog=new ItemKnowledgeCatalog(gson,new GuideCatalog(gson));catalog.load();
        assertEquals(0,catalog.loadedSourceShards());assertTrue(catalog.sourceItemCount()>4500);assertTrue(catalog.sourceCardCount()>22000);
        assertTrue(catalog.loadBundled(2347).sources.stream().anyMatch(s->s.kind.equals("Shop")));
        assertTrue(catalog.loadBundled(385).sources.stream().anyMatch(s->s.kind.equals("Drop / reward")));
        assertTrue(catalog.loadBundled(20997).sources.stream().anyMatch(s->s.kind.equals("Requirements")&&s.details.contains("Ranged 85")));
        for(int id=1;id<600;id++)catalog.loadBundled(id);assertTrue(catalog.loadedSourceShards()<=4);
        assertTrue(catalog.bundled(1515).revision>0); // Existing detailed revision-pinned records win the merge.
    }
    @Test public void everyOfflineActionTargetsAnArticleAndNoDerivedRarityClassLeaksIntoCards()
    {
        Gson gson=new Gson();ItemKnowledgeCatalog catalog=new ItemKnowledgeCatalog(gson,new GuideCatalog(gson));catalog.load();
        for(String id:new String[]{"2347","385","4151","20997","5295","2434"})
        {
            ItemSourceService.Lookup item=catalog.loadBundled(Integer.parseInt(id));assertNotNull(id,item);
            for(ItemSourceService.Source source:item.sources){assertTrue(source.wiki,source.wiki.startsWith(ItemSourceService.HOST+"/w/"));assertFalse(source.details.contains("rules_based_rarity"));}
        }
    }
}
