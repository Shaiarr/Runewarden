/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.timetracking.SummaryState;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.farming.FarmingContractManager;
import net.runelite.client.plugins.timetracking.farming.FarmingTracker;
import net.runelite.client.plugins.timetracking.hunter.BirdHouseTracker;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FarmRunWorkflowTest
{
    @Test public void cropFactsKeepActionXpGrowthAndHarvestedItemDistinct()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson()); FarmPatchCatalog.Seed ranarr = catalog.seed(5295), magic = catalog.seed(5374);
        assertEquals(207, ranarr.produceId); assertEquals(80, ranarr.growthMinutes); assertEquals(26.5, ranarr.plantingXp, .001); assertEquals(30.5, ranarr.harvestXp, .001);
        assertEquals(480, magic.growthMinutes); assertEquals(13768.3, magic.healthXp, .001); assertTrue(magic.xpSource.contains("oldid="));
        assertEquals(199, catalog.seed(5291).produceId); assertEquals(209, catalog.seed(5297).produceId);
        assertTrue(catalog.seeds("SEAWEED").stream().anyMatch(seed -> seed.produceId == 21504 && seed.growthMinutes > 0));
    }
    @Test public void perPatchCropsCompostProtectionAndSkippedStopsDriveActualQuantities()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = new FarmPatchPlanner.Route();
        java.util.List<FarmPatchCatalog.Patch> herbs = fixture.catalog.forGuide("training:herb-runs"); FarmPatchCatalog.Patch one = herbs.get(0), two = herbs.get(1), three = herbs.get(2);
        route.stops.addAll(Arrays.asList(one.id, two.id, three.id)); route.seeds.put("HERB", 5295); route.patchSeeds.put(two.id, 5296); route.patchSeeds.put(three.id, 0);
        route.patchCompost.put(one.id, 6034); route.patchCompost.put(two.id, 0);
        Map<Integer, Long> supplies = fixture.planner.supplies(route);
        assertEquals(1L, supplies.get(5295).longValue()); assertEquals(1L, supplies.get(5296).longValue()); assertEquals(1L, supplies.get(6034).longValue()); assertFalse(supplies.containsKey(21483));
        route.completed.add(one.id); supplies = fixture.planner.supplies(route); assertFalse(supplies.containsKey(5295)); assertFalse(supplies.containsKey(6034)); assertTrue(supplies.containsKey(5296));
    }
    @Test public void readingSeparatesCarriedObservedStockAndUnknownStorage()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = herbRoute(fixture, false);
        fixture.nativeState.container(InventoryID.INV, new Item[]{new Item(5295, 1)}); fixture.planner.start(); fixture.tick(6);
        FarmPatchPlanner.RunReading reading = fixture.planner.reading("training:herb-runs", route);
        FarmPatchPlanner.SupplyReading seeds = reading.supplies.stream().filter(item -> item.itemId == 5295).findFirst().get();
        assertEquals(1, seeds.required); assertEquals(1, seeds.carried); assertFalse(seeds.bankObserved); assertFalse(seeds.seedVaultObserved); assertEquals(0, seeds.missing);
        try { reading.requiredItems.clear(); fail(); } catch (UnsupportedOperationException expected) { }
        try { reading.supplies.clear(); fail(); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void explicitYieldScenarioUsesVerifiedGrimyProductAndRealItemManagerPrices()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = herbRoute(fixture, false); route.yield.put(route.stops.get(0), 8);
        save(fixture, route); ItemManager items = mock(ItemManager.class); ItemComposition tradeable = mock(ItemComposition.class); when(tradeable.isTradeable()).thenReturn(true);
        when(items.getItemComposition(anyInt())).thenReturn(tradeable); when(items.getItemPrice(5295)).thenReturn(30_000L); when(items.getItemPrice(207)).thenReturn(6_000L);
        fixture.planner.setItemManager(items); fixture.planner.start(); fixture.planner.selectGuide("training:herb-runs"); fixture.tick(6);
        FarmPatchPlanner.RunReading reading = fixture.planner.currentRun(); assertEquals(Long.valueOf(30_000), reading.inputCost); assertEquals(Long.valueOf(48_000), reading.harvestRevenue);
        assertEquals(26.5, reading.knownBaseXp, .001); assertEquals(244, reading.scenarioHarvestXp, .001); verify(items, atLeastOnce()).getItemPrice(207);
    }
    @Test public void absentYieldAndUntradeableOrUnpricedInputsStayUnrated()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = herbRoute(fixture, false); save(fixture, route);
        fixture.planner.start(); fixture.planner.selectGuide("training:herb-runs"); fixture.tick(6);
        FarmPatchPlanner.RunReading reading = fixture.planner.currentRun(); assertNull(reading.inputCost); assertNull(reading.harvestRevenue); assertEquals(1, reading.unpricedInputs); assertEquals(1, reading.unratedHarvests);
    }
    @Test public void zeroHerbHarvestAwardsNeitherHarvestXpNorDeferredPlantingCredit()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = herbRoute(fixture, false); route.yield.put(route.stops.get(0), 0);
        fixture.planner.start(); fixture.tick(6); FarmPatchPlanner.RunReading reading = fixture.planner.reading("training:herb-runs", route);
        assertTrue(fixture.catalog.seed(5295).plantingOnFirstHarvest); assertEquals(0, reading.knownBaseXp, 0); assertEquals(0, reading.scenarioHarvestXp, 0); assertEquals(Long.valueOf(0), reading.harvestRevenue);
    }
    @Test public void exactLocalReplantAdvancesOnlyStartedRunAndCarriesNativeEvidence()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = herbRoute(fixture, false); save(fixture, route);
        fixture.planner.start(); fixture.planner.selectGuide("training:herb-runs"); fixture.tick(6); fixture.planner.startSelectedRun();
        FarmPatchCatalog.Patch patch = fixture.catalog.patch(route.stops.get(0)); int growing = stateValue(fixture.catalog, patch.type, "RANARR", "GROWING", 0);
        when(fixture.nativeState.client.getVarbitValue(patch.varbit)).thenReturn(growing); fixture.tick(5);
        FarmPatchPlanner.RunReading reading = fixture.planner.currentRun(); assertEquals(1, reading.completedStops); assertEquals(1, reading.autoObservedStops); assertEquals(0, reading.remainingStops); assertTrue(reading.requiredItems.isEmpty());
    }
    @Test public void plantedSeedDoesNotPretendCompostOrGardenerPaymentWasPerformed()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = herbRoute(fixture, true); save(fixture, route);
        fixture.planner.start(); fixture.planner.selectGuide("training:herb-runs"); fixture.tick(6); fixture.planner.startSelectedRun();
        FarmPatchCatalog.Patch patch = fixture.catalog.patch(route.stops.get(0)); when(fixture.nativeState.client.getVarbitValue(patch.varbit)).thenReturn(stateValue(fixture.catalog, patch.type, "RANARR", "GROWING", 0)); fixture.tick(5);
        FarmPatchPlanner.RunReading reading = fixture.planner.currentRun(); assertEquals(0, reading.completedStops); assertEquals(0, reading.autoObservedStops); assertTrue(reading.requiredItems.containsKey(21483));
        assertTrue(fixture.nativeState.settings.get("runewarden|rsprofile.characterA|" + FarmPatchPlanner.KEY).contains("confirm compost / payment"));
    }
    @Test public void growingOnFirstObservationOrBrowsingAnotherRouteNeverStartsOrChecksRun()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = herbRoute(fixture, false); save(fixture, route);
        FarmPatchCatalog.Patch patch = fixture.catalog.patch(route.stops.get(0)); when(fixture.nativeState.client.getVarbitValue(patch.varbit)).thenReturn(stateValue(fixture.catalog, patch.type, "RANARR", "GROWING", 0));
        fixture.planner.start(); fixture.planner.selectGuide("training:herb-runs"); fixture.tick(6); assertFalse(fixture.planner.currentRun().active); assertEquals(0, fixture.planner.currentRun().completedStops);
        fixture.planner.startSelectedRun(); fixture.planner.selectGuide("training:tree-runs"); assertTrue(fixture.planner.currentRun().active); assertEquals("training:herb-runs", fixture.planner.currentRun().guideId); assertEquals(0, fixture.planner.currentRun().completedStops);
    }
    @Test public void restartingPreservesRouteButRequiresExplicitResumeAndSwitchImmediatelyHidesData()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = herbRoute(fixture, false); save(fixture, route);
        fixture.planner.start(); fixture.planner.selectGuide("training:herb-runs"); fixture.tick(6); fixture.planner.startSelectedRun(); assertTrue(fixture.planner.currentRun().active);
        fixture.planner.stop(); fixture.planner.start(); fixture.tick(1); assertFalse(fixture.planner.currentRun().active); assertEquals(1, fixture.planner.currentRun().totalStops);
        fixture.nativeState.profile.set("rsprofile.other"); assertNull(fixture.planner.currentRun());
    }
    @Test public void actualNativeGardenerLocationIsRetainedAsRouteAnchor()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = herbRoute(fixture, false);
        FarmPatchCatalog.Patch herb = fixture.catalog.patch(route.stops.get(0)); FarmPatchCatalog.Patch allotment = fixture.catalog.patches().stream().filter(p -> p.region.equals(herb.region) && p.type.equals("ALLOTMENT")).findFirst().get();
        route.stops.add(allotment.id); save(fixture, route); NPC gardener = mock(NPC.class); when(gardener.getId()).thenReturn(allotment.farmer); when(gardener.getWorldLocation()).thenReturn(fixture.location.get()); when(fixture.nativeState.client.getNpcs()).thenReturn(Collections.singletonList(gardener));
        fixture.planner.start(); fixture.tick(6); fixture.planner.stop(); String json = fixture.nativeState.settings.get("runewarden|rsprofile.characterA|" + FarmPatchPlanner.KEY);
        FarmPatchPlanner.Saved saved = new Gson().fromJson(json, FarmPatchPlanner.Saved.class); int[] point = saved.routes.get("training:herb-runs").destinations.get(allotment.id);
        assertArrayEquals(new int[]{fixture.location.get().getX(), fixture.location.get().getY(), 0}, point);
    }
    @Test public void growthWindowUsesFirstStageObservationRatherThanSlidingWithLastSeenRefresh()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson()); FarmPatchCatalog.Patch patch = catalog.forGuide("training:herb-runs").get(0);
        FarmPatchCatalog.State state = catalog.state(patch.type, stateValue(catalog, patch.type, "RANARR", "GROWING", 0));
        FarmPatchPlanner.PatchReading reading = new FarmPatchPlanner.PatchReading(patch, state, 400_000, 100_000, true);
        assertEquals(4900, reading.latestGrowthSecond()); assertEquals(3700, reading.earliestGrowthSecond()); assertEquals("Due to check · estimated", reading.status(5000));
    }
    @Test public void publicNativeContractServiceAddsActualContractWithoutInventingCropReadiness()
    {
        FarmingTracker farming = mock(FarmingTracker.class); BirdHouseTracker birds = mock(BirdHouseTracker.class); FarmingContractManager contracts = mock(FarmingContractManager.class);
        for (Tab tab : Tab.FARMING_TABS) when(farming.getSummary(tab)).thenReturn(SummaryState.UNKNOWN);
        when(contracts.hasContract()).thenReturn(true); when(contracts.getContractName()).thenReturn("White lily"); when(contracts.getSummary()).thenReturn(SummaryState.IN_PROGRESS); when(contracts.getCompletionTime()).thenReturn(2000L);
        HarvestClockTool.Reading reading = HarvestClockTool.readTrackers(farming, birds, contracts); assertNotNull(reading.contract); assertEquals("Contract · White lily", reading.contract.name); assertTrue(reading.contract.estimated);
        assertFalse(reading.contract.due(1000)); assertTrue(reading.contract.due(2000)); assertEquals("training:farming-contracts", FarmRunsTool.primaryRun("contract").guideId);
    }
    @Test public void oldNewRunButtonCannotStartTheNextCharactersRoute() throws Exception
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); save(fixture, herbRoute(fixture, false)); fixture.planner.start(); fixture.planner.selectGuide("training:herb-runs"); fixture.tick(6);
        JPanel[] panel = new JPanel[1]; SwingUtilities.invokeAndWait(() -> panel[0] = fixture.planner.createPanel()); flush();
        javax.swing.JButton old = findButton(panel[0], "New run"); java.util.List<Runnable> queue = new java.util.ArrayList<>();
        doAnswer(call -> { queue.add(call.getArgument(0)); return null; }).when(fixture.thread).invokeLater(any(Runnable.class));
        SwingUtilities.invokeAndWait(old::doClick); fixture.nativeState.profile.set("rsprofile.other"); fixture.tick(1); queue.forEach(Runnable::run); flush();
        assertFalse(fixture.nativeState.settings.containsKey("runewarden|rsprofile.other|" + FarmPatchPlanner.KEY)); assertFalse(fixture.planner.currentRun().active);
    }
    @Test public void activeFarmRunRendersInHarvestHudWithoutNativeGroupTimers() throws Exception
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); save(fixture, herbRoute(fixture, false)); fixture.planner.start(); fixture.planner.selectGuide("training:herb-runs"); fixture.tick(6); fixture.planner.startSelectedRun();
        RunewardenConfig config = mock(RunewardenConfig.class); when(config.harvestHud()).thenReturn(true);
        HarvestClockTool harvest = new HarvestClockTool(fixture.nativeState.client, fixture.nativeState.config, mock(net.runelite.client.plugins.PluginManager.class), mock(net.runelite.client.ui.overlay.OverlayManager.class), config);
        harvest.setFarmRunSupplier(fixture.planner::currentRun); harvest.start();
        try
        {
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(264, 120, java.awt.image.BufferedImage.TYPE_INT_ARGB); java.awt.Graphics2D graphics = image.createGraphics();
            try { assertNotNull(((HarvestClockOverlay) harvest.overlay()).render(graphics)); } finally { graphics.dispose(); }
            assertEquals("Ardougne", harvest.farmRun().nextStopLabel); fixture.nativeState.profile.set("rsprofile.other"); assertNull(harvest.farmRun());
        }
        finally { harvest.stop(); flush(); }
    }
    private static FarmPatchPlanner.Route herbRoute(FarmPatchPlannerTest.Fixture fixture, boolean compost)
    {
        FarmPatchPlanner.Route route = new FarmPatchPlanner.Route(); route.stops.add(fixture.catalog.forGuide("training:herb-runs").stream().filter(p -> p.region.equals("Ardougne")).findFirst().get().id); route.seeds.put("HERB", 5295); route.compost = compost; return route;
    }
    private static void save(FarmPatchPlannerTest.Fixture fixture, FarmPatchPlanner.Route route)
    { FarmPatchPlanner.Saved saved = new FarmPatchPlanner.Saved(); saved.routes.put("training:herb-runs", route); fixture.nativeState.settings.put("runewarden|rsprofile.characterA|" + FarmPatchPlanner.KEY, new Gson().toJson(saved)); }
    private static int stateValue(FarmPatchCatalog catalog, String type, String crop, String state, int stage)
    { for (int i = 0; i < 256; i++) { FarmPatchCatalog.State item = catalog.state(type, i); if (item != null && crop.equals(item.crop) && state.equals(item.state) && stage == item.stage) return i; } throw new AssertionError(crop); }
    private static javax.swing.JButton findButton(java.awt.Container root, String text)
    { for (java.awt.Component child : root.getComponents()) { if (child instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) child).getText())) return (javax.swing.JButton) child; if (child instanceof java.awt.Container) { javax.swing.JButton found = findButton((java.awt.Container) child, text); if (found != null) return found; } } return null; }
    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> {}); SwingUtilities.invokeAndWait(() -> {}); }
}
