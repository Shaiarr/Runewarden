/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmCropPreparationTest
{
    @Test public void ordinaryHerbTreeFruitAndAllEightAllotmentSelectorsHaveVerifiedPlantingLevels()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson());
        for (String type : Arrays.asList("HERB", "TREE", "FRUIT_TREE", "ALLOTMENT"))
            for (FarmPatchCatalog.Seed seed : catalog.seeds(type)) { assertNotNull(seed.name, seed.level); assertTrue(seed.name, seed.level > 0 && seed.level <= 99); assertFalse(seed.levelSource.isEmpty()); }
        assertEquals(Integer.valueOf(32), catalog.seed(5295).level); assertEquals(Integer.valueOf(75), catalog.seed(5374).level);
        assertEquals(Integer.valueOf(61), catalog.seed(22879).level);
        java.util.Set<Integer> allotmentIds = new HashSet<>(); catalog.seeds("ALLOTMENT").forEach(seed -> allotmentIds.add(seed.id));
        assertEquals(new HashSet<>(Arrays.asList(5318, 5319, 5324, 5322, 5320, 5323, 5321, 22879)), allotmentIds);
        assertFalse(allotmentIds.contains(7548)); assertFalse(allotmentIds.contains(7550)); assertFalse(allotmentIds.contains(7562));
    }
    @Test public void exactPaymentsPreserveFullBasketsSacksAndNoGardenerHerbs()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson()); assertEquals(49, catalog.payments().size());
        FarmPatchCatalog.Payment banana = catalog.payment(5497); assertTrue(banana.available); assertEquals(5386, banana.items.get(0).itemId); assertEquals(4, banana.items.get(0).quantity);
        assertFalse(banana.items.stream().anyMatch(item -> item.itemId == 1955)); // Loose apples cannot substitute for a basket.
        assertEquals(5438, catalog.payment(5319).items.get(0).itemId); assertEquals(1, catalog.payment(5319).items.get(0).quantity);
        assertEquals(247, catalog.payment(22879).items.get(0).itemId); assertEquals(5, catalog.payment(22879).items.get(0).quantity);
        assertFalse(catalog.payment(5295).available); assertTrue(catalog.payment(5295).items.isEmpty());
        assertNull(catalog.payment(999999));
    }
    @Test public void protectionAndSeedsAggregatePerRemainingPatchWithoutReusingFinishedStops()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = new FarmPatchPlanner.Route();
        fixture.catalog.forGuide("training:allotment-runs").stream().filter(patch -> patch.type.equals("ALLOTMENT") && patch.region.equals("Ardougne")).forEach(patch -> route.stops.add(patch.id));
        assertEquals(2, route.stops.size()); route.seeds.put("ALLOTMENT", 22879); route.protection.add("ALLOTMENT"); route.compost = false;
        Map<Integer, Long> full = fixture.planner.supplies(route); assertEquals(6L, full.get(22879).longValue()); assertEquals(10L, full.get(247).longValue());
        route.completed.add(route.stops.get(0)); Map<Integer, Long> remaining = fixture.planner.supplies(route);
        assertEquals(3L, remaining.get(22879).longValue()); assertEquals(5L, remaining.get(247).longValue());
    }
    @Test public void noGardenerAndObservedDiaryWaiversNeverAddUnnecessaryPayment()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = new FarmPatchPlanner.Route();
        FarmPatchCatalog.Patch harmony = fixture.catalog.forGuide("training:allotment-runs").stream().filter(patch -> patch.type.equals("ALLOTMENT") && patch.region.equals("Harmony")).findFirst().get();
        assertTrue(harmony.farmer <= 0); route.stops.add(harmony.id); route.seeds.put("ALLOTMENT", 22879); route.protection.add("ALLOTMENT"); route.compost = false;
        assertFalse(fixture.planner.supplies(route).containsKey(247));
        FarmPatchCatalog.Patch falador = fixture.catalog.forGuide("training:tree-runs").stream().filter(patch -> patch.region.equals("Falador")).findFirst().get();
        route.stops.clear(); route.stops.add(falador.id); route.seeds.put("TREE", 5370); route.protection.add("TREE");
        assertEquals(1L, fixture.planner.supplies(route).get(5968).longValue());
        when(fixture.nativeState.client.getVarbitValue(VarbitID.FALADOR_DIARY_ELITE_COMPLETE)).thenReturn(1); fixture.planner.start(); fixture.tick(1);
        assertFalse(fixture.planner.supplies(route).containsKey(5968));
    }
    @Test public void nativeValidatedNotedPaymentItemsCountButUnrelatedFormsNeverSubstitute()
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture();
        ItemComposition real = mock(ItemComposition.class), note = mock(ItemComposition.class);
        when(real.getLinkedNoteId()).thenReturn(5987); when(note.getLinkedNoteId()).thenReturn(5986); when(note.getNote()).thenReturn(799);
        when(fixture.nativeState.client.getItemDefinition(5986)).thenReturn(real); when(fixture.nativeState.client.getItemDefinition(5987)).thenReturn(note);
        fixture.nativeState.container(InventoryID.INV, new Item[]{new Item(5987, 9), new Item(7088, 50)});
        fixture.planner.start(); fixture.tick(1);
        assertEquals(9, fixture.planner.carried(fixture.nativeState.ledger.snapshot(), 5986, true));
        assertEquals(0, fixture.planner.carried(fixture.nativeState.ledger.snapshot(), 5986, false));
    }
    @Test public void selectedCropShowsObservedLevelShortfallAndHonoursNativeBoosts() throws Exception
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture();
        when(fixture.nativeState.client.getRealSkillLevel(Skill.FARMING)).thenReturn(60); when(fixture.nativeState.client.getBoostedSkillLevel(Skill.FARMING)).thenReturn(60);
        fixture.planner.start(); fixture.tick(6); fixture.planner.selectGuide("training:tree-runs"); JPanel[] panel = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = fixture.planner.createPanel()); flush();
        SwingUtilities.invokeAndWait(() -> selectSeed(panel[0], 5374)); flush();
        assertTrue(text(panel[0]).contains("75 Farming · you 60")); assertTrue(text(panel[0]).contains("Needs 75 Farming to plant Magic sapling"));
        when(fixture.nativeState.client.getBoostedSkillLevel(Skill.FARMING)).thenReturn(75); fixture.tick(5);
        SwingUtilities.invokeAndWait(() -> fixture.planner.selectGuide("training:tree-runs")); flush();
        assertTrue(text(panel[0]).contains("75 boosted")); assertFalse(text(panel[0]).contains("Needs 75 Farming to plant"));
    }
    @Test public void addedBushPatchBecomesPartOfHerbRunAndSurvivesValidatedRestore() throws Exception
    {
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); fixture.planner.start(); fixture.tick(6); fixture.planner.selectGuide("training:herb-runs");
        JPanel[] panel = new JPanel[1]; SwingUtilities.invokeAndWait(() -> panel[0] = fixture.planner.createPanel()); flush();
        SwingUtilities.invokeAndWait(() -> { button(panel[0], "▸ Add other patches").doClick(); namedCombo(panel[0], "farm-patch.add-type").setSelectedItem("BUSH"); }); flush();
        assertNotNull(button(panel[0], "▾ Add other patches")); // A render/category change must preserve the open picker.
        SwingUtilities.invokeAndWait(() -> button(panel[0], "Add stop").doClick()); flush();
        SwingUtilities.invokeAndWait(() -> selectSeed(panel[0], 5101)); flush();
        String address = "runewarden|rsprofile.characterA|" + FarmPatchPlanner.KEY;
        String stored = fixture.nativeState.settings.get(address); assertNotNull(stored); assertTrue(stored.contains("BUSH")); assertTrue(stored.contains("5101"));
        fixture.planner.stop(); fixture.planner.start(); fixture.tick(1); SwingUtilities.invokeAndWait(() -> fixture.planner.selectGuide("training:herb-runs")); flush();
        assertTrue(selectedSeed(panel[0], 5101)); assertTrue(text(panel[0]).contains("10 Farming"));
    }
    @Test public void customHopSeedAmountsDistinguishJuteFromHopsAndUnknownNewFibreCrops()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson());
        assertEquals(Integer.valueOf(3), catalog.seed(5306).quantity); // Jute.
        assertEquals(Integer.valueOf(4), catalog.seed(5309).quantity); // Yanillian.
        assertEquals(Integer.valueOf(16), catalog.seed(5309).level);
        assertEquals(Integer.valueOf(10), catalog.seed(5101).level);
        FarmPatchPlannerTest.Fixture fixture = new FarmPatchPlannerTest.Fixture(); FarmPatchPlanner.Route route = new FarmPatchPlanner.Route();
        FarmPatchCatalog.Patch hops = catalog.patches().stream().filter(patch -> patch.type.equals("HOPS")).findFirst().get(); route.stops.add(hops.id); route.seeds.put("HOPS", 5309); route.compost = false;
        assertEquals(4L, fixture.planner.supplies(route).get(5309).longValue()); route.seeds.put("HOPS", 5306); assertEquals(3L, fixture.planner.supplies(route).get(5306).longValue());
    }
    private static javax.swing.JButton button(Container root, String name)
    { for (Component child : root.getComponents()) { if (child instanceof javax.swing.JButton && name.equals(((javax.swing.JButton) child).getText())) return (javax.swing.JButton) child; if (child instanceof Container) { javax.swing.JButton found = button((Container) child, name); if (found != null) return found; } } return null; }
    private static JComboBox<?> namedCombo(Container root, String name)
    { for (Component child : root.getComponents()) { if (child instanceof JComboBox && name.equals(child.getName())) return (JComboBox<?>) child; if (child instanceof Container) { JComboBox<?> found = namedCombo((Container) child, name); if (found != null) return found; } } return null; }
    private static boolean selectedSeed(Container root, int id)
    { for (Component child : root.getComponents()) { if (child instanceof JComboBox) { Object value = ((JComboBox<?>) child).getSelectedItem(); if (value instanceof FarmPatchCatalog.Seed && ((FarmPatchCatalog.Seed) value).id == id) return true; } if (child instanceof Container && selectedSeed((Container) child, id)) return true; } return false; }
    @SuppressWarnings("unchecked") private static void selectSeed(Container root, int id)
    {
        for (Component component : root.getComponents())
        {
            if (component instanceof JComboBox)
            {
                JComboBox<Object> box = (JComboBox<Object>) component;
                for (int i = 0; i < box.getItemCount(); i++) if (box.getItemAt(i) instanceof FarmPatchCatalog.Seed && ((FarmPatchCatalog.Seed) box.getItemAt(i)).id == id) { box.setSelectedIndex(i); return; }
            }
            if (component instanceof Container) selectSeed((Container) component, id);
        }
    }
    private static String text(Container root)
    { StringBuilder result = new StringBuilder(); for (Component component : root.getComponents()) { if (component instanceof GuideText) result.append(((GuideText) component).getText()).append('\n'); if (component instanceof Container) result.append(text((Container) component)); } return result.toString(); }
    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> {}); SwingUtilities.invokeAndWait(() -> {}); }
}
