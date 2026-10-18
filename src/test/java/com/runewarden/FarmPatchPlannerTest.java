/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FarmPatchPlannerTest
{
    @Test public void pinnedSnapshotHasAll107NativePatchesAndExactHerbSeeds()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson()); assertEquals(107, catalog.patches().size());
        assertTrue(catalog.forGuide("training:herb-runs").size() >= 10); assertTrue(catalog.forGuide("training:allotment-runs").size() >= 15);
        assertTrue(catalog.seeds("HERB").stream().anyMatch(seed -> seed.id == 5295 && seed.name.equals("Ranarr seed")));
        assertTrue(catalog.seeds("TREE").stream().anyMatch(seed -> seed.name.equals("Magic sapling")));
        for (FarmPatchCatalog.Patch patch : catalog.patches()) { assertTrue(patch.varbit > 0); assertNotNull(patch.sentRegions); assertFalse(patch.sentRegions.isEmpty()); }
    }
    @Test public void catherbyAndFossilIslandRespectNativeTransmissionBounds()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson());
        FarmPatchCatalog.Patch herb = catalog.forGuide("training:herb-runs").stream().filter(patch -> patch.region.equals("Catherby")).findFirst().get();
        assertTrue(herb.sentAt(new WorldPoint(2825, 3445, 0))); assertFalse(herb.sentAt(new WorldPoint(2825, 3445, 1)));
        FarmPatchCatalog.Patch hardwood = catalog.forGuide("training:hardwood-runs").stream().filter(patch -> patch.region.equals("Fossil Island")).findFirst().get();
        assertFalse(hardwood.sentAt(new WorldPoint(3753, 3869, 0))); assertFalse(hardwood.sentAt(new WorldPoint(3729, 3831, 0)));
    }
    @Test public void remotePatchesRemainUnknownAndRegionEntryWaitsForSettledNativeState()
    {
        Fixture fixture = new Fixture(); fixture.planner.start(); fixture.tick(2); assertTrue(fixture.planner.readings().isEmpty());
        fixture.tick(4); assertFalse(fixture.planner.readings().isEmpty());
        for (FarmPatchPlanner.PatchReading reading : fixture.planner.readings().values()) assertTrue(reading.patch.sentAt(fixture.location.get()));
        assertTrue(fixture.planner.readings().size() < fixture.catalog.patches().size());
    }
    @Test public void growthDeadlineIsOnlyDueToCheckAndNeverManufacturesHarvestableState()
    {
        FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson()); FarmPatchCatalog.Patch herb = catalog.forGuide("training:herb-runs").get(0);
        FarmPatchCatalog.State state = new FarmPatchCatalog.State(); state.crop = "RANARR"; state.state = "GROWING"; state.stages = 5; state.stage = 0; state.tickMinutes = 20;
        FarmPatchPlanner.PatchReading reading = new FarmPatchPlanner.PatchReading(herb, state, 100_000L, true);
        assertTrue(reading.status(100).contains("1h 20m")); assertEquals("Due to check · estimated", reading.status(5000)); assertEquals("GROWING", reading.state.state);
    }
    @Test public void restartRetainsLastSeenButAccountSwitchHidesItImmediately()
    {
        Fixture fixture = new Fixture(); fixture.planner.start(); fixture.tick(6); assertFalse(fixture.planner.readings().isEmpty());
        fixture.planner.stop(); fixture.location.set(new WorldPoint(3200, 3200, 0)); fixture.planner.start(); fixture.tick(1);
        assertFalse(fixture.planner.readings().isEmpty()); for (FarmPatchPlanner.PatchReading reading : fixture.planner.readings().values()) assertFalse(reading.currentSession);
        fixture.nativeState.profile.set("rsprofile.other"); assertTrue(fixture.planner.readings().isEmpty()); fixture.tick(1); assertTrue(fixture.planner.readings().isEmpty());
    }
    @Test public void customPatchSequenceAndCropPreparationPersistAndFit206Pixels() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.planner.start(); fixture.tick(6); fixture.planner.selectGuide("training:herb-runs");
        JPanel[] panel = new JPanel[1]; SwingUtilities.invokeAndWait(() -> panel[0] = fixture.planner.createPanel()); flush();
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> seeds = descendants(panel[0]).stream().filter(component -> component instanceof JComboBox && component.getName()!=null && component.getName().startsWith("farm-patch.seed.")).map(component -> (JComboBox<?>) component).findFirst().get();
            assertTrue(seeds.getItemCount() > 4); seeds.setSelectedIndex(1);
        }); flush();
        AtomicReference<java.util.Set<Integer>> focus = new AtomicReference<>(); fixture.planner.setBankFocusAction(focus::set);
        SwingUtilities.invokeAndWait(() -> button(panel[0], "Focus run supplies in bank").doClick()); flush();
        assertNotNull(focus.get()); assertTrue(focus.get().contains(952)); assertTrue(focus.get().contains(21483)); assertTrue(focus.get().size() >= 3);
        assertTrue(fixture.nativeState.settings.containsKey("runewarden|rsprofile.characterA|" + FarmPatchPlanner.KEY));
        SwingUtilities.invokeAndWait(() -> {
            panel[0].setSize(206, 12000); for (int i = 0; i < 8; i++) layout(panel[0]);
            for (Component component : descendants(panel[0]))
                if (visibleWithin(component, panel[0]) && (component instanceof GuideText || component instanceof JButton || component instanceof JComboBox))
                    assertTrue(component.getClass() + " outside parent", component.getX() >= 0 && component.getX() + component.getWidth() <= component.getParent().getWidth());
            try
            {
                BufferedImage image = new BufferedImage(206, 1500, BufferedImage.TYPE_INT_ARGB); java.awt.Graphics2D graphics = image.createGraphics(); graphics.setColor(SidebarUi.BACKGROUND); graphics.fillRect(0, 0, 206, 1500); panel[0].paint(graphics); graphics.dispose();
                File output = new File("build/reports/farm-patch-planner-206px.png"); output.getParentFile().mkdirs(); ImageIO.write(image, "png", output);
            }
            catch (java.io.IOException failure) { throw new AssertionError(failure); }
        });
    }
    @Test public void queuedOldProfileRouteEditsAreDiscarded() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.planner.start(); fixture.tick(6); fixture.planner.selectGuide("training:herb-runs");
        JPanel[] panel = new JPanel[1]; SwingUtilities.invokeAndWait(() -> panel[0] = fixture.planner.createPanel()); flush();
        List<Runnable> work = new ArrayList<>(); doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(fixture.thread).invokeLater(any(Runnable.class));
        SwingUtilities.invokeAndWait(() -> button(panel[0], "Mark stop checked").doClick());
        fixture.nativeState.profile.set("rsprofile.other"); fixture.tick(1); work.forEach(Runnable::run); flush();
        assertFalse(fixture.nativeState.settings.containsKey("runewarden|rsprofile.other|" + FarmPatchPlanner.KEY));
    }
    @Test public void queuedTravelPreparationCannotWriteToAnotherCharacter() throws Exception
    {
        Fixture fixture=new Fixture(); fixture.planner.start(); fixture.tick(6); fixture.planner.selectGuide("training:herb-runs");
        JPanel[] panel=new JPanel[1]; SwingUtilities.invokeAndWait(()->panel[0]=fixture.planner.createPanel()); flush();
        List<Runnable> work=new ArrayList<>(); doAnswer(call->{work.add(call.getArgument(0));return null;}).when(fixture.thread).invokeLater(any(Runnable.class));
        SwingUtilities.invokeAndWait(()->button(panel[0],"Save travel preparation").doClick()); assertFalse(work.isEmpty());
        fixture.nativeState.profile.set("rsprofile.other"); fixture.tick(1); work.forEach(Runnable::run); flush();
        assertFalse(fixture.nativeState.settings.containsKey("runewarden|rsprofile.other|"+FarmPatchPlanner.KEY));
    }
    static final class Fixture
    {
        final OwnershipLedgerTest.Fixture nativeState = new OwnershipLedgerTest.Fixture();
        final net.runelite.client.callback.ClientThread thread = mock(net.runelite.client.callback.ClientThread.class);
        final AtomicReference<WorldPoint> location = new AtomicReference<>();
        final FarmPatchCatalog catalog = new FarmPatchCatalog(new Gson());
        final FarmPatchPlanner planner;
        Fixture()
        {
            FarmPatchCatalog.Patch patch = catalog.forGuide("training:herb-runs").stream().filter(value -> value.region.equals("Ardougne")).findFirst().get();
            location.set(new WorldPoint((patch.regionId >> 8) * 64 + 32, (patch.regionId & 255) * 64 + 32, 0));
            when(nativeState.client.getLocalPlayer().getWorldLocation()).thenAnswer(call -> location.get());
            when(nativeState.client.getBoostedSkillLevel(Skill.FARMING)).thenReturn(99);
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
            planner = new FarmPatchPlanner(nativeState.client, thread, nativeState.config, new RunewardenDataStore(nativeState.config, new Gson()), catalog, nativeState.ledger);
        }
        void tick(int count) { for (int i = 0; i < count; i++) { nativeState.ledger.onGameTick(); planner.onGameTick(); } }
    }
    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> {}); SwingUtilities.invokeAndWait(() -> {}); }
    private static JButton button(Container root, String text) { for (Component child : descendants(root)) if (child instanceof JButton && text.equals(((JButton) child).getText())) return (JButton) child; throw new AssertionError(text); }
    private static List<Component> descendants(Container root) { List<Component> result = new ArrayList<>(); for (Component child : root.getComponents()) { result.add(child); if (child instanceof Container) result.addAll(descendants((Container) child)); } return result; }
    private static void layout(Container root) { root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static boolean visibleWithin(Component child, Component root) { for (Component now = child; now != null && now != root; now = now.getParent()) if (!now.isVisible()) return false; return true; }
}
