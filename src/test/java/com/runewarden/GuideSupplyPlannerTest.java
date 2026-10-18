/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuideSupplyPlannerTest
{
    private final GuideCatalog catalog = new GuideCatalog(new Gson());
    private GuideSupplyPlanner.Plan plan(String id)
    { return GuideSupplyPlanner.resolve(catalog.entry(id), Collections.emptySet(), 0, GuideSupplyPlanner.GearView.GUIDE); }

    @Test public void everyBundledGuideHasAnExplicitAuditedSupplyContract()
    {
        assertEquals(783, catalog.search("", null).size());
        assertEquals(783, GuideSupplyPlanner.auditedIds().size());
        for (GuideCatalog.Entry guide : catalog.search("", null))
        {
            GuideSupplyPlanner.Plan plan = plan(guide.id);
            assertTrue(guide.id, plan.audited);
            assertTrue(guide.id + ": " + plan.kind, Set.of("none", "items", "loadout").contains(plan.kind));
            if (plan.isItemFree()) { assertTrue(guide.id, plan.itemIds.isEmpty()); assertTrue(guide.id, plan.families.isEmpty()); }
            else assertTrue(guide.id, !plan.itemIds.isEmpty() || !plan.families.isEmpty() || !plan.unresolved.isEmpty());
            assertTrue(guide.id + " " + plan.families, GuideBankFamilies.DYNAMIC.containsAll(plan.families));
        }
    }

    @Test public void currentTrainingRoutesHaveExactInputContractsAndExcludeOutputs()
    {
        assertTrue(plan("training:feldip-weasels").itemIds.containsAll(Set.of(10150,21126)));
        assertTrue(plan("training:ruby-harvests").itemIds.containsAll(Set.of(10010,10012,10006)));
        assertTrue(plan("training:red-crabs").itemIds.contains(11334));assertTrue(plan("training:red-crabs").families.contains("nails"));
        assertTrue(plan("training:embertailed-jerboas").itemIds.contains(10008));
        assertTrue(plan("training:razor-backed-kebbits").itemIds.containsAll(Set.of(10150,21126)));
        assertTrue(plan("training:rainbow-crabs").itemIds.containsAll(Set.of(32307,233)));
        assertTrue(plan("training:moonlight-moths").itemIds.containsAll(Set.of(10010,10012)));
        assertTrue(plan("training:rubium-rocks").itemIds.contains(954));assertTrue(plan("training:rubium-rocks").families.contains("dragonfire-protection"));
        assertTrue(plan("training:lead-rocks").itemIds.containsAll(Set.of(21345,21392)));
        assertTrue(plan("training:infernal-shale").itemIds.containsAll(Set.of(2347,1755,30808)));
        for(String id:new String[]{"training:red-crabs","training:rainbow-crabs","training:rubium-rocks","training:lead-rocks","training:infernal-shale"})
        {assertEquals("items",plan(id).kind);assertFalse(plan(id).hasGearView());assertFalse(plan(id).families.contains("ores"));assertFalse(plan(id).families.contains("all-equipment"));}
    }

    @Test public void fixedProcessingSuppliesExcludeOutputsAndUnrelatedMaterialFamilies()
    {
        GuideSupplyPlanner.Plan diamond = plan("training:diamond-bracelets");
        assertTrue(diamond.itemIds.containsAll(Set.of(2357, 1601, 11065)));
        assertFalse(diamond.itemIds.contains(11092));
        assertFalse(diamond.families.contains("bars"));
        GuideSupplyPlanner.Plan salmon = plan("training:cook-salmon");
        assertEquals("items", salmon.kind); assertTrue(salmon.itemIds.contains(331));
        assertFalse(salmon.families.contains("ranged-equipment"));
        GuideSupplyPlanner.Plan rune = plan("training:smith-rune");
        assertTrue(rune.itemIds.contains(2363)); assertFalse(rune.families.contains("runes")); assertFalse(rune.families.contains("bars"));
        assertFalse(plan("training:burn-yew").families.contains("logs"));
        assertFalse(plan("training:mithril-darts").families.contains("ammunition"));
    }

    @Test public void auditedMetadataIsAuthoritativeOverOldFallbacks()
    {
        GuideSupplyPlanner.Plan plan = GuideSupplyPlanner.resolve(catalog.entry("training:diamond-bracelets"), Set.of(999999), 0, GuideSupplyPlanner.GearView.GUIDE);
        assertFalse(plan.itemIds.contains(999999));
    }

    @Test public void mandatoryQuestRunesArePreservedByAMeleeEquipmentView()
    {
        GuideSupplyPlanner.Plan plan = GuideSupplyPlanner.resolve(catalog.entry("quest:A Kingdom Divided"), Collections.emptySet(), 0, GuideSupplyPlanner.GearView.MELEE);
        assertTrue(plan.families.contains("runes"));
    }

    @Test public void restrictedActivitiesDoNotOfferProhibitedOutsideSupplies()
    {
        GuideSupplyPlanner.Plan ba = plan("minigame:barbarian-assault");
        assertFalse(ba.families.contains("ammunition")); assertFalse(ba.families.contains("runes")); assertFalse(ba.families.contains("food"));
        GuideSupplyPlanner.Plan hope = plan("quest:Hopespear's Will");
        assertFalse(hope.families.stream().anyMatch(f -> f.endsWith("-equipment")));
        assertFalse(hope.families.contains("bones"));
        assertTrue(plan("training:clean-herbs").families.contains("grimy-herbs"));
    }

    @Test public void playerChoicesAugmentTheirGuideButCannotLeakIntoItemFreeActivities()
    {
        GuideSupplyPlanner.Plan custom = GuideSupplyPlanner.resolve(catalog.entry("minigame:gnome-restaurant"), Collections.emptySet(), 0,
            GuideSupplyPlanner.GearView.GUIDE, Set.of(221));
        assertTrue(custom.itemIds.contains(221));
        GuideSupplyPlanner.Plan none = GuideSupplyPlanner.resolve(catalog.entry("minigame:rogues-den"), Collections.emptySet(), 0,
            GuideSupplyPlanner.GearView.GUIDE, Set.of(221));
        assertTrue(none.isItemFree()); assertFalse(none.itemIds.contains(221));
    }
}
