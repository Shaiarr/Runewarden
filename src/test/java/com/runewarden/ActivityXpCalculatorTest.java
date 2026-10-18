/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Skill;
import org.junit.Test;
import static org.junit.Assert.*;

public class ActivityXpCalculatorTest
{
    private final GuideCatalog guides = new GuideCatalog(new Gson());
    private final ActivityXpCatalog catalog = new ActivityXpCatalog(new Gson(), guides);

    @Test public void everyTrainingAndMinigameGuideHasAnOpenableMethod()
    {
        for (String category : new String[]{"training", "minigame"})
            for (GuideCatalog.Entry guide : guides.search("", category))
            {
                ActivityXpCatalog.Method method = catalog.find(guide.id);
                assertNotNull(guide.id, method); assertEquals(guide.id, method.guideId);
                assertNotNull(method.source);
                if (guide.skill == null) assertNull(method.skill); else assertNotNull(method.skill);
            }
        assertTrue(catalog.methods(Skill.RUNECRAFT).size() >= 17);
        assertTrue(catalog.methods(null).size() >= 336);
    }

    @Test public void nonSkillActivitiesRemainBrowsableWithDeprecatedNullOverallAlias() throws Exception
    {
        assertNull("Pinned RuneLite alias is not an enum member", Skill.OVERALL);
        assertEquals(23, catalog.activities().size());
        assertTrue(catalog.methods(null).containsAll(catalog.activities()));
        assertTrue(catalog.activities().stream().anyMatch(method -> method.id.equals("minigame:barbarian-assault")));
        ActivityXpTool tool = new ActivityXpTool(catalog);
        SwingUtilities.invokeAndWait(() -> { tool.createPanel(); tool.openMethod("minigame:barbarian-assault"); });
    }

    @Test public void ratesUseOnlyVerifiedLevelBandsAndWikiRevisions()
    {
        ActivityXpCatalog.Method mixology = catalog.find("training:mastering-mixology");
        assertNull(mixology.ratesAt(60)); assertEquals(45000, mixology.ratesAt(81).low(81), 0.01);
        ActivityXpCatalog.Method wintertodt = catalog.find("activity:wintertodt-no-fletch");
        assertEquals(350000 * 50.0 / 99, wintertodt.ratesAt(50).low(50), 0.01);
        assertTrue(wintertodt.source.contains("oldid=15358528"));
        assertNull(catalog.find("training:tempoross").ratesAt(70));
    }

    @Test public void sailingMethodsHaveCurrentUnlocksWithoutInventedUniversalRates()
    {
        assertTrue(catalog.methods(Skill.SAILING).size()>=9);
        assertEquals(15,catalog.find("training:sailing-salvaging").level);
        assertEquals(30,catalog.find("training:tempor-tantrum").level);
        assertEquals(55,catalog.find("training:advanced-barracuda").level);
        assertEquals(72,catalog.find("activity:sailing-gwenith-glide").level);
        assertEquals(56,catalog.find("training:deep-sea-trawling").level);
        assertTrue(catalog.find("activity:sailing-gwenith-glide").requirements.contains("Complete Regicide"));
        assertEquals("oneoff",catalog.find("training:sailing-charting").kind);
        for(ActivityXpCatalog.Method method:catalog.methods(Skill.SAILING))
        {assertTrue(method.source.contains("oldid="));assertTrue(method.rates.isEmpty());}
    }

    @Test public void foundryBattleaxesAreTwoBarsAndBankIsNeverMutated()
    {
        Map<Integer, Long> bank = Map.of(2353, 28L, 1369, 14L);
        ActivityXpCalculator.Estimate result = ActivityXpCalculator.foundry(catalog.find("activity:foundry-steel-mithril"), catalog.metals(), bank, 61, 123);
        assertEquals(2, result.units); assertEquals(23520, result.xp, 0.01);
        assertEquals(Long.valueOf(28), result.supplies.get("mithril")); assertEquals(Long.valueOf(14), bank.get(1369));
        assertEquals(0, ActivityXpCalculator.foundry(catalog.find("activity:foundry-steel-mithril"), catalog.metals(), bank, 49, 123).units);
    }

    @Test public void foundryCannotInventTheOtherAlloyOrDoubleAllocateEquipment()
    {
        assertEquals(0, ActivityXpCalculator.foundry(catalog.find("activity:foundry-steel-mithril"), catalog.metals(), Map.of(1369, 100L), 99, 123).units);
        Set<Integer> inputs = new HashSet<>();
        for (ActivityXpCatalog.Metal metal : catalog.metals()) for (ActivityXpCatalog.Material item : metal.items)
            assertTrue("A material belongs to one metal", inputs.add(item.itemId));
    }

    @Test public void foundryHandInFormulaFloorsEachTermAndValidatesQuality()
    {
        assertEquals(30, ActivityXpCalculator.foundryHandInXp(0)); assertEquals(25230, ActivityXpCalculator.foundryHandInXp(199));
        assertEquals(11760, ActivityXpCalculator.foundryHandInXp(123));
        try { ActivityXpCalculator.foundryHandInXp(200); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void homesRequiresSteelBarsAndUsesOnlyWholeAverageContracts()
    {
        ActivityXpCatalog.Method homes = catalog.find("training:homes-expert");
        assertEquals(0, ActivityXpCalculator.homes(homes, Map.of(8782, 100L), 99, false).units);
        ActivityXpCalculator.Estimate bare = ActivityXpCalculator.homes(homes, Map.of(8782, 129L, 2353, 6L), 99, false);
        assertEquals(10, bare.units); assertEquals(43788, bare.xp, .01);
        assertEquals(bare.xp * 1.025, ActivityXpCalculator.homes(homes, Map.of(8782, 129L, 2353, 6L), 99, true).xp, .01);
        assertEquals(0, ActivityXpCalculator.homes(homes, Map.of(8782, 129L, 2353, 6L), 69, false).units);
    }

    @Test public void mixologyCountsCleanAndUnfinishedInputsButNeverGrimyHerbs()
    {
        ActivityXpCatalog.Potion mmm = catalog.potions().stream().filter(p -> p.code.equals("MMM")).findFirst().get();
        ActivityXpCalculator.Estimate result = ActivityXpCalculator.mixology(mmm, catalog.herbs(), Map.of(249, 3L, 91, 3L, 199, 1000L), 60);
        assertEquals(60L, result.supplies.get("mox").longValue()); assertEquals(2, result.units); assertEquals(380, result.xp, .01);
        assertEquals(36L, result.supplies.get("refining XP (separate)").longValue());
    }

    @Test public void mixedPotionsAreLimitedByEveryPasteAndPlayerLevel()
    {
        ActivityXpCatalog.Potion mal = catalog.potions().stream().filter(p -> p.code.equals("MAL")).findFirst().get();
        Map<Integer, Long> bank = Map.of(249, 3L, 259, 1L, 257, 1L);
        ActivityXpCalculator.Estimate result = ActivityXpCalculator.mixology(mal, catalog.herbs(), bank, 81);
        assertEquals(2, result.units); assertEquals(730, result.xp, .01);
        assertEquals(0, ActivityXpCalculator.mixology(mal, catalog.herbs(), bank, 80).units);
        assertEquals(0, ActivityXpCalculator.mixology(mal, catalog.herbs(), Map.of(249, 3L, 259, 1L), 81).units);
    }

    @Test public void riftBonusCannotIncludeUnobservedCraftingOrFailedRounds()
    {
        assertEquals(45000, ActivityXpCalculator.rift(50, 20).xp, .01);
        assertEquals(0, ActivityXpCalculator.rift(26, 20).xp, .01);
        assertTrue(ActivityXpCalculator.rift(50, 20).basis.contains("crafting XP additional"));
        try { ActivityXpCalculator.rift(50, -1); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void customTimeUsesRemainingXpAndRejectsInvalidRates()
    {
        assertEquals(2, ActivityXpCalculator.hours(10000, 110000, 50000), .01);
        assertEquals(0, ActivityXpCalculator.hours(110000, 10000, 50000), .01);
        for (double rate : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            try { ActivityXpCalculator.hours(0, 10000, rate); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void activityPanelFitsSidebarAndOpensMethodBeforeOrAfterCreation() throws Exception
    {
        ActivityXpTool tool = new ActivityXpTool(catalog); String[] opened = {null};
        SwingUtilities.invokeAndWait(() -> {
            tool.openMethod("training:mastering-mixology"); tool.setOpenGuideAction(id -> opened[0] = id);
            tool.updateSnapshot(Map.of(), Map.of(Skill.HERBLORE, 1000000), Map.of(Skill.HERBLORE, 81), true);
            JPanel panel = tool.createPanel();
            assertTrue("Fits 225px sidebar: " + panel.getPreferredSize(), panel.getPreferredSize().width <= 225);
            tool.openMethod("activity:foundry-steel-mithril");
            for (java.awt.Component component : panel.getComponents()) if (component instanceof javax.swing.JButton) ((javax.swing.JButton) component).doClick();
            assertEquals("training:giants-foundry", opened[0]);
        });
    }

    @Test public void combinedBankAndTrainingViewsFitSidebar() throws Exception
    {
        net.runelite.client.game.ItemManager items = org.mockito.Mockito.mock(net.runelite.client.game.ItemManager.class);
        org.mockito.Mockito.when(items.getImage(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i ->
            new net.runelite.client.util.AsyncBufferedImage(org.mockito.Mockito.mock(net.runelite.client.callback.ClientThread.class), 36, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB));
        BankedExperience banked = new BankedExperience(org.mockito.Mockito.mock(net.runelite.api.Client.class), items, new ActivityXpTool(catalog));
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = banked.createPanel();
            assertTrue("Combined views fit 225px: " + panel.getPreferredSize(), panel.getPreferredSize().width <= 225);
        });
    }
}
