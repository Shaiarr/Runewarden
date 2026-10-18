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
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Item;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class LoadoutReadinessToolTest
{
    @Test public void savedNativeWornAndInventoryRoundTripAsExactProfileLoadout() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.start();
        SwingUtilities.invokeAndWait(() -> button(fixture.panel, "Save worn + inventory").doClick()); flush();
        assertEquals(1, fixture.tool.presets().size());
        LoadoutPlan plan = fixture.tool.selected(); assertEquals(2, plan.requirements.size());
        assertEquals(Integer.valueOf(4151), plan.equipment().get(3));
        String json = fixture.tool.exportPlans(); fixture.tool.stop(); fixture.tool.start(); fixture.tool.onGameTick(); flush();
        assertEquals(1, fixture.tool.presets().size()); assertEquals(json, fixture.tool.exportPlans());
    }
    @Test public void malformedFutureAndUnboundedImportsCannotOverwriteSavedPresets() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.start();
        fixture.tool.importPlans("{\"version\":1,\"plans\":[{\"id\":\"one\",\"name\":\"One\",\"requirements\":[],\"spellbook\":0}]}"); flush();
        assertEquals(1, fixture.tool.presets().size());
        String before = fixture.tool.exportPlans();
        fixture.tool.importPlans("{\"version\":99,\"plans\":[]}");
        fixture.tool.importPlans("{\"version\":1,\"plans\":[{\"id\":\"two\",\"name\":\"Bad\",\"requirements\":[{\"alternatives\":[385],\"quantity\":0,\"location\":\"INVENTORY\",\"slot\":-1}]}]}");
        fixture.tool.importPlans("{"); flush();
        assertEquals(before, fixture.tool.exportPlans());
    }
    @Test public void oldCharacterCallbacksCannotChangeNewCharacterLoadouts() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.start();
        List<Runnable> queued = new ArrayList<>(); doAnswer(call -> { queued.add(call.getArgument(0)); return null; }).when(fixture.thread).invokeLater(any(Runnable.class));
        SwingUtilities.invokeAndWait(() -> button(fixture.panel, "Save worn + inventory").doClick());
        fixture.nativeState.profile.set("rsprofile.characterB"); fixture.nativeState.ledger.onGameTick(); fixture.tool.onGameTick();
        queued.forEach(Runnable::run); flush(); assertTrue(fixture.tool.presets().isEmpty());
    }
    @Test public void focusActionReceivesOnlyMissingExplicitAlternativeIds() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.start();
        fixture.tool.importPlans("{\"version\":1,\"plans\":[{\"id\":\"one\",\"name\":\"Food\",\"requirements\":[{\"alternatives\":[385,379],\"quantity\":12,\"location\":\"INVENTORY\",\"slot\":-1}]}]}"); flush();
        AtomicReference<java.util.Set<Integer>> focus = new AtomicReference<>(); fixture.tool.setBankFocusAction(focus::set);
        SwingUtilities.invokeAndWait(() -> button(fixture.panel, "Focus supplies in bank").doClick()); flush();
        assertNotNull(focus.get()); assertTrue(focus.get().contains(385)); assertTrue(focus.get().contains(379));
    }
    @Test public void populatedReadinessFits206PixelSidebarAndRendersSnapshot() throws Exception
    {
        Fixture fixture = new Fixture(); fixture.start();
        SwingUtilities.invokeAndWait(() -> button(fixture.panel, "Save worn + inventory").doClick()); flush();
        SwingUtilities.invokeAndWait(() -> {
            fixture.panel.setSize(206, 2600); for (int i = 0; i < 8; i++) layout(fixture.panel);
            for (Component component : descendants(fixture.panel))
            {
                if (!visibleWithin(component, fixture.panel) || !(component instanceof GuideText || component instanceof JButton || component instanceof javax.swing.JComboBox || component instanceof JTextField)) continue;
                assertTrue(component.getClass() + " extends beyond parent", component.getX() >= 0 && component.getX() + component.getWidth() <= component.getParent().getWidth());
            }
            try
            {
                int height = Math.min(1800, fixture.panel.getPreferredSize().height); fixture.panel.setSize(206, height); layout(fixture.panel);
                BufferedImage image = new BufferedImage(206, height, BufferedImage.TYPE_INT_ARGB); java.awt.Graphics2D graphics = image.createGraphics();
                graphics.setColor(SidebarUi.BACKGROUND); graphics.fillRect(0, 0, 206, height); fixture.panel.paint(graphics); graphics.dispose();
                File output = new File("build/reports/loadout-readiness-206px.png"); output.getParentFile().mkdirs(); ImageIO.write(image, "png", output);
            }
            catch (java.io.IOException failure) { throw new AssertionError(failure); }
        });
    }
    @Test public void configuredChecksSurviveSharingCaptureUpdatesAndItemEdits() throws Exception
    {
        Fixture f=new Fixture();f.start();
        f.tool.importPlans("{\"version\":1,\"plans\":[{\"id\":\"check\",\"name\":\"Checked setup\",\"requirements\":[],\"departureChecks\":[{\"id\":\"manual\",\"label\":\"Check weapon charges\",\"kind\":\"MANUAL\",\"minimum\":0}]}]}");flush();
        assertEquals(1,f.tool.selected().checks().size());
        SwingUtilities.invokeAndWait(()->button(f.panel,"Update").doClick());flush();
        assertEquals(1,f.tool.selected().checks().size());assertEquals(2,f.tool.selected().requirements.size());
        String exported=f.tool.exportPlans();assertTrue(exported.contains("Check weapon charges"));f.tool.stop();f.tool.start();f.tool.onGameTick();flush();
        assertEquals(exported,f.tool.exportPlans());assertEquals(LoadoutReadiness.CheckState.UNKNOWN,f.tool.readiness().checks.get(0).state);
    }
    @Test public void manualCheckConfirmationsAreEphemeralAndNeverExported() throws Exception
    {
        Fixture f=new Fixture();f.start();f.tool.importPlans("{\"version\":1,\"plans\":[{\"id\":\"check\",\"name\":\"Setup\",\"requirements\":[],\"departureChecks\":[{\"id\":\"manual\",\"label\":\"Check charges\",\"kind\":\"MANUAL\",\"minimum\":0}]}]}");flush();
        String before=f.tool.exportPlans();assertFalse(f.tool.readiness().ready);
        f.tool.confirmDepartureCheck("check","manual",true);flush();assertTrue(f.tool.readiness().ready);assertEquals(before,f.tool.exportPlans());
        f.tool.clear();f.tool.onGameTick();flush();assertFalse(f.tool.readiness().ready);
        f.tool.confirmDepartureCheck("check","manual",true);flush();assertTrue(f.tool.readiness().ready);
        f.tool.stop();f.tool.start();f.tool.onGameTick();flush();assertFalse(f.tool.readiness().ready);
    }
    @Test public void nativeChargeAndEffectSnapshotsDetermineDepartureStateWithoutSwingClientReads() throws Exception
    {
        Fixture f=new Fixture();f.nativeState.container(InventoryID.INV,new Item[]{new Item(1712,1)});f.start();
        f.tool.setChargeFacts(()->java.util.Map.of(1712,4L),()->java.util.Map.of(1712,4));
        AtomicReference<java.util.Set<String>> active=new AtomicReference<>(java.util.Set.of());
        f.tool.setActiveEffects(active::get,()->java.util.Set.of("antifire"));
        f.tool.importPlans("{\"version\":1,\"plans\":[{\"id\":\"check\",\"name\":\"Setup\",\"requirements\":[],\"departureChecks\":[{\"id\":\"glory\",\"label\":\"Teleport charges\",\"kind\":\"CHARGES\",\"itemIds\":[1712],\"minimum\":4},{\"id\":\"fire\",\"label\":\"Antifire\",\"kind\":\"EFFECT\",\"effectKey\":\"antifire\",\"minimum\":0}]}]}");flush();f.tool.onGameTick();
        assertFalse(f.tool.readiness().ready);assertEquals(LoadoutReadiness.CheckState.READY,f.tool.readiness().checks.get(0).state);assertEquals(LoadoutReadiness.CheckState.MISSING,f.tool.readiness().checks.get(1).state);
        active.set(java.util.Set.of("antifire"));f.tool.onGameTick();assertTrue(f.tool.readiness().ready);
        clearInvocations(f.nativeState.client);SwingUtilities.invokeAndWait(()->assertTrue(f.tool.readiness().ready));verify(f.nativeState.client,never()).getGameState();verify(f.nativeState.client,never()).getLocalPlayer();
    }
    @Test public void staleManualConfirmationCannotApplyToTheNextCharacter() throws Exception
    {
        Fixture f=new Fixture();f.start();f.tool.importPlans("{\"version\":1,\"plans\":[{\"id\":\"check\",\"name\":\"Setup\",\"requirements\":[],\"departureChecks\":[{\"id\":\"manual\",\"label\":\"Check charges\",\"kind\":\"MANUAL\",\"minimum\":0}]}]}");flush();
        List<Runnable> queued=new ArrayList<>();doAnswer(call->{queued.add(call.getArgument(0));return null;}).when(f.thread).invokeLater(any(Runnable.class));
        f.tool.confirmDepartureCheck("check","manual",true);f.nativeState.profile.set("rsprofile.characterB");f.nativeState.ledger.onGameTick();f.tool.onGameTick();queued.forEach(Runnable::run);flush();assertNull(f.tool.readiness());
    }
    private static final class Fixture
    {
        final OwnershipLedgerTest.Fixture nativeState = new OwnershipLedgerTest.Fixture();
        final ClientThread thread = mock(ClientThread.class);
        final LoadoutReadinessTool tool;
        JPanel panel;
        Fixture()
        {
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
            ItemManager items = mock(ItemManager.class); when(items.getImage(anyInt())).thenAnswer(call -> new AsyncBufferedImage(thread, 36, 32, BufferedImage.TYPE_INT_ARGB));
            Gson gson = new Gson(); tool = new LoadoutReadinessTool(nativeState.ledger, nativeState.client, thread, nativeState.config, gson, items, new RunewardenDataStore(nativeState.config, gson));
            Item[] worn = new Item[14]; java.util.Arrays.fill(worn, new Item(-1, 0)); worn[3] = new Item(4151, 1);
            nativeState.container(InventoryID.WORN, worn); nativeState.container(InventoryID.INV, new Item[]{new Item(385, 8)});
        }
        void start() throws Exception
        { tool.start(); nativeState.ledger.onGameTick(); tool.onGameTick(); SwingUtilities.invokeAndWait(() -> panel = tool.createPanel()); flush(); }
    }
    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> {}); SwingUtilities.invokeAndWait(() -> {}); }
    private static JButton button(Container root, String text)
    { for (Component child : descendants(root)) if (child instanceof JButton && text.equals(((JButton) child).getText())) return (JButton) child; throw new AssertionError(text); }
    private static List<Component> descendants(Container root)
    { List<Component> result = new ArrayList<>(); for (Component child : root.getComponents()) { result.add(child); if (child instanceof Container) result.addAll(descendants((Container) child)); } return result; }
    private static void layout(Container root) { root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static boolean visibleWithin(Component child, Component root)
    { for (Component now = child; now != null && now != root; now = now.getParent()) if (!now.isVisible()) return false; return true; }
}
