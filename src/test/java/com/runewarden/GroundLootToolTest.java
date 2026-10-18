/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemLayer;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileItem;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.WorldViewUnloaded;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GroundLootToolTest
{
    @Test public void enablingInsideARegionSeedsOnceAndSpawnReplayDoesNotDuplicate() throws Exception
    {
        Fixture f = new Fixture(null); TileItem ore = f.item(440, 8, TileItem.OWNERSHIP_SELF, 200, 150); f.ground.add(ore);
        f.tool.start(); f.tool.onGameTick();
        assertEquals(1, f.tool.reading().total); assertEquals(8, f.tool.reading().entries.get(0).quantity);
        f.tool.onItemSpawned(new ItemSpawned(f.tile, ore)); f.tool.onGameTick(); f.tool.onGameTick();
        assertEquals(1, f.tool.reading().tracked); verify(f.scene, times(1)).getTiles(); f.stop();
    }

    @Test public void guideSuppliesAndManualTargetsBeatPriceAndNotedItemsMatchTheirCanonicalTarget() throws Exception
    {
        Fixture f = new Fixture("{\"valueFloor\":999999,\"targets\":{\"946\":\"Knife\"}}");
        f.ground.add(f.item(441, 5, TileItem.OWNERSHIP_SELF, 200, 150));
        f.ground.add(f.item(946, 1, TileItem.OWNERSHIP_NONE, 220, 0));
        f.ground.add(f.item(995, 10, TileItem.OWNERSHIP_SELF, 300, 150));
        when(f.items.canonicalize(441)).thenReturn(440); when(f.items.getItemPrice(441)).thenReturn(0L);
        AtomicInteger supplierCalls = new AtomicInteger(); f.tool.setTargetItems(() -> { supplierCalls.incrementAndGet(); return Set.of(440); });
        f.tool.start(); f.tool.onGameTick();
        assertEquals(2, f.tool.reading().total); assertTrue(f.tool.reading().entries.get(0).guideTarget);
        assertEquals(441, f.tool.reading().entries.get(0).id); assertEquals(0, f.tool.reading().entries.get(0).value);
        assertTrue(f.tool.reading().entries.get(1).manualTarget); assertEquals(1, supplierCalls.get()); f.stop();
    }

    @Test public void identicalItemsKeepSeparateOwnershipQuantitiesAndActualDeadlines() throws Exception
    {
        Fixture f = new Fixture("{\"valueFloor\":0}");
        TileItem own = f.item(440, 2, TileItem.OWNERSHIP_SELF, 120, 110);
        TileItem other = f.item(440, 7, TileItem.OWNERSHIP_OTHER, 200, 0);
        f.ground.add(own); f.ground.add(other); f.tool.start(); f.tool.onGameTick();
        assertEquals(2, f.tool.reading().entries.size());
        assertEquals(2, f.tool.reading().entries.get(0).quantity); assertEquals(120, f.tool.reading().entries.get(0).despawnTick);
        assertEquals(7, f.tool.reading().entries.get(1).quantity); assertEquals(TileItem.OWNERSHIP_OTHER, f.tool.reading().entries.get(1).ownership);
        f.tick.set(120); f.tool.onGameTick();
        assertEquals(1, f.tool.reading().total); assertEquals(7, f.tool.reading().entries.get(0).quantity);
        assertEquals(200, f.tool.reading().entries.get(0).despawnTick); f.stop();
    }

    @Test public void quantityChangeAndDespawnOnlyUpdateTheObservedNativeItem() throws Exception
    {
        Fixture f = new Fixture("{\"valueFloor\":0}"); TileItem one = f.item(440, 5, 1, 200, 0), two = f.item(440, 10, 1, 250, 0);
        f.ground.add(one); f.ground.add(two); f.tool.start(); f.tool.onGameTick();
        f.tool.onItemQuantityChanged(new ItemQuantityChanged(one, f.tile, 5, 2)); f.tool.onGameTick();
        assertEquals(2, f.tool.reading().entries.get(0).quantity); assertEquals(10, f.tool.reading().entries.get(1).quantity);
        f.tool.onItemDespawned(new ItemDespawned(f.tile, one)); f.tool.onGameTick();
        assertEquals(1, f.tool.reading().tracked); assertEquals(10, f.tool.reading().entries.get(0).quantity); f.stop();
    }

    @Test public void ironmanTakeabilityAndOwnOnlyRespectNativeOwnership() throws Exception
    {
        Fixture f = new Fixture(null);
        for (int owner = 0; owner < 4; owner++) f.ground.add(f.item(440, 3, owner, 200 + owner, 0));
        f.tool.start(); f.tool.onGameTick(); assertEquals(4, f.tool.reading().total);
        when(f.client.getVarbitValue(VarbitID.IRONMAN)).thenReturn(1); f.tool.onGameTick();
        assertEquals(3, f.tool.reading().total); assertFalse(f.tool.reading().entries.stream().anyMatch(e -> e.ownership == TileItem.OWNERSHIP_OTHER));
        f.saved.set("{\"ownOnly\":true}"); f.tool.reloadPreferences(); f.tool.onGameTick();
        assertEquals(2, f.tool.reading().total); assertTrue(f.tool.reading().entries.stream().allMatch(e -> e.ownership == 1 || e.ownership == 3));
        assertFalse(GroundLootTool.takeable(999, false)); f.stop();
    }

    @Test public void nativeDeadlineChangesAreRefreshedAndUnknownDeadlinesRemainUnrated() throws Exception
    {
        Fixture f = new Fixture(null); TileItem ore = f.item(440, 3, 1, 0, 0); f.ground.add(ore); f.tool.start(); f.tool.onGameTick();
        assertFalse(GroundLootOverlay.detail(f.tool.reading().entries.get(0), 100, true).contains("Ends"));
        when(ore.getDespawnTime()).thenReturn(210); when(ore.getVisibleTime()).thenReturn(140);
        f.tool.onGameTick(); String detail = GroundLootOverlay.detail(f.tool.reading().entries.get(0), 100, true);
        assertTrue(detail.contains("Ends 1:06")); assertTrue(detail.contains("public 24s"));
        when(ore.isPrivate()).thenReturn(true); f.tool.onGameTick();
        assertFalse(GroundLootOverlay.detail(f.tool.reading().entries.get(0), 100, true).contains("public"));
        assertEquals("Yours", GroundLootOverlay.detail(f.tool.reading().entries.get(0), 100, false));
        assertEquals("1s", GroundLootTool.countdown(1)); assertEquals("", GroundLootTool.countdown(-1)); f.stop();
    }

    @Test public void logoutLoadingAndAccountSwitchNeverRetainTheOldScene() throws Exception
    {
        Fixture f = new Fixture(null); f.ground.add(f.item(440, 3, 1, 200, 0)); f.tool.start(); f.tool.onGameTick(); assertEquals(1, f.tool.reading().total);
        GameStateChanged loading = new GameStateChanged(); loading.setGameState(GameState.LOADING); f.tool.onGameStateChanged(loading);
        assertTrue(f.tool.reading().entries.isEmpty()); f.ground.clear(); f.profile.set("character-b"); f.tool.onGameTick(); assertEquals(0, f.tool.reading().tracked);
        f.ground.add(f.item(440, 8, 1, 200, 0)); f.tool.clearScene(); f.tool.onGameTick(); assertEquals(1, f.tool.reading().total);
        when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); f.tool.onGameTick(); assertTrue(f.tool.reading().entries.isEmpty()); f.stop();
    }

    @Test public void loadingBaseAndWorldViewChangesTriggerExactlyOneNewSnapshot() throws Exception
    {
        Fixture f = new Fixture(null); f.ground.add(f.item(440, 3, 1, 200, 0)); f.tool.start(); f.tool.onGameTick(); f.tool.onGameTick();
        verify(f.scene, times(1)).getTiles(); when(f.view.getBaseX()).thenReturn(3264); f.ground.clear(); f.tool.onGameTick(); f.tool.onGameTick();
        assertEquals(0, f.tool.reading().tracked); verify(f.scene, times(2)).getTiles();
        f.tool.onWorldViewUnloaded(new WorldViewUnloaded(f.view)); assertTrue(f.tool.reading().entries.isEmpty()); f.stop();
    }

    @Test public void disablingAnEmptySceneStillReseedsItemsThatAppearedWhileDisabled() throws Exception
    {
        Fixture f = new Fixture(null); f.tool.start(); f.tool.onGameTick();
        f.saved.set("{\"enabled\":false}"); f.tool.reloadPreferences(); clearInvocations(f.client); f.tool.onGameTick(); verifyNoInteractions(f.client);
        f.ground.add(f.item(440, 3, 1, 200, 0));
        f.saved.set("{\"enabled\":true}"); f.tool.reloadPreferences(); f.tool.onGameTick(); assertEquals(1, f.tool.reading().total);
        verify(f.scene, times(2)).getTiles(); f.stop();
    }

    @Test public void displayBoundsKeepGuideItemsAheadOfValuableStacksAndDoNotAllocateAnUnboundedSnapshot() throws Exception
    {
        Fixture f = new Fixture(null);
        for (int index = 0; index < 180; index++) f.ground.add(f.item(440, 5, 1, 200 + index, 0));
        f.ground.add(f.item(946, 1, 1, 1000, 0)); when(f.items.getItemPrice(946)).thenReturn(0L);
        f.tool.setTargetItems(() -> Set.of(946)); f.tool.start(); f.tool.onGameTick();
        assertEquals(181, f.tool.reading().total); assertEquals(GroundLootTool.MAX_ROWS, f.tool.reading().entries.size());
        assertEquals(946, f.tool.reading().entries.get(0).id); assertTrue(f.tool.reading().entries.get(0).guideTarget); f.stop();
    }

    @Test public void staleItemDetailButtonsCannotOpenAnOldCharactersSelection() throws Exception
    {
        Fixture f = new Fixture(null); f.ground.add(f.item(440, 3, 1, 200, 0)); AtomicInteger opened = new AtomicInteger();
        f.tool.setOpenItemAction(opened::set); f.tool.start(); f.tool.onGameTick();
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = f.tool.createPanel(); JButton button = button(panel, "Item details"); assertNotNull(button);
            f.profile.set("character-b"); button.doClick(); assertEquals(0, opened.get());
        }); f.stop();
    }

    @Test public void actual206PixelSidebarWrapsEveryReadableFieldAndAllControlsFit() throws Exception
    {
        Fixture f = new Fixture("{\"targets\":{\"946\":\"A long deliberately wrapped manual supply target name\"}}");
        f.ground.add(f.item(440, 3, 1, 200, 0)); f.tool.start(); f.tool.onGameTick();
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = f.tool.createPanel(); JButton disclosure = button(panel, "▸ Display options"); assertNotNull(disclosure); disclosure.doClick(); JButton beamOptions=button(panel,"▸ Loot beams");assertNotNull(beamOptions);beamOptions.doClick();
            for (int pass = 0; pass < 10; pass++) { panel.setSize(206, panel.getPreferredSize().height); layout(panel); }
            assertFits(panel);
            Graphics2D graphics = new BufferedImage(206, panel.getHeight(), BufferedImage.TYPE_INT_ARGB).createGraphics();
            try { panel.paint(graphics); } finally { graphics.dispose(); }
        }); f.stop();
    }

    @Test public void targetsCanBeRemovedWithoutClearingTheRestAndOnlyOwnedConfigIsWritten() throws Exception
    {
        Fixture f = new Fixture("{\"targets\":{\"946\":\"Knife\",\"440\":\"Iron ore\"}}"); f.tool.start();
        SwingUtilities.invokeAndWait(() -> { JPanel panel = f.tool.createPanel(); button(panel, "Remove").doClick(); });
        assertEquals(1, f.tool.preferences().targets.size()); assertTrue(f.tool.preferences().targets.containsKey(440));
        verify(f.config).setConfiguration(eq(GroundLootTool.GROUP), eq(GroundLootTool.KEY), anyString()); f.stop();
    }

    @Test public void invalidLocalPreferencesAndLargeValuesHaveSafeBounds() throws Exception
    {
        Fixture f = new Fixture("{\"valueFloor\":-1,\"targets\":{\"-1\":\"Bad\",\"946\":\"<col=00ff00>Knife</col>\"}}");
        assertEquals(25000, f.tool.preferences().valueFloor); assertTrue(f.tool.preferences().targets.isEmpty());
        assertEquals("{\"valueFloor\":-1,\"targets\":{\"-1\":\"Bad\",\"946\":\"<col=00ff00>Knife</col>\"}}", f.saved.get());
        f.saved.set("{broken json"); f.tool.reloadPreferences(); assertEquals(25000, f.tool.preferences().valueFloor);
        assertEquals(Long.MAX_VALUE, GroundLootTool.multiply(Long.MAX_VALUE, 2)); f.stop();
    }

    static final class Fixture
    {
        final Client client = mock(Client.class); final ConfigManager config = mock(ConfigManager.class); final ItemManager items = mock(ItemManager.class);
        final WorldView view = mock(WorldView.class); final Scene scene = mock(Scene.class); final Tile tile = mock(Tile.class); final ItemLayer layer = mock(ItemLayer.class);
        final List<TileItem> ground = new ArrayList<>(); final AtomicInteger tick = new AtomicInteger(100);
        final AtomicReference<String> saved = new AtomicReference<>(), profile = new AtomicReference<>("character-a");
        final GroundLootTool tool;
        Fixture(String preferences)
        {
            saved.set(preferences); when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getTickCount()).thenAnswer(call -> tick.get()); when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(GroundLootTool.GROUP, GroundLootTool.KEY)).thenAnswer(call -> saved.get());
            when(client.getTopLevelWorldView()).thenReturn(view); when(view.getScene()).thenReturn(scene); when(scene.getTiles()).thenReturn(new Tile[][][] {{{tile}}});
            when(tile.getGroundItems()).thenReturn(ground); when(tile.getItemLayer()).thenReturn(layer); when(layer.getWorldView()).thenReturn(view);
            LocalPoint location = new LocalPoint(64, 64, view);
            when(tile.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0)); when(tile.getLocalLocation()).thenReturn(location);
            when(items.getItemPrice(anyInt())).thenReturn(10000L); when(items.canonicalize(anyInt())).thenAnswer(call -> call.getArgument(0));
            when(items.getItemComposition(anyInt())).thenAnswer(call -> { ItemComposition definition = mock(ItemComposition.class); when(definition.getName()).thenReturn("Iron ore"); return definition; });
            tool = new GroundLootTool(client, config, items, new Gson());
        }
        TileItem item(int id, int quantity, int ownership, int despawn, int visible)
        {
            TileItem result = mock(TileItem.class); when(result.getId()).thenReturn(id); when(result.getQuantity()).thenReturn(quantity);
            when(result.getOwnership()).thenReturn(ownership); when(result.getDespawnTime()).thenReturn(despawn); when(result.getVisibleTime()).thenReturn(visible); return result;
        }
        void stop() throws Exception { tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }
    private static JButton button(Container container, String title)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof JButton && title.equals(((JButton) child).getText())) return (JButton) child;
            if (child instanceof Container) { JButton found = button((Container) child, title); if (found != null) return found; }
        }
        return null;
    }
    private static void layout(Container container)
    { container.doLayout(); for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static void assertFits(Container container)
    {
        for (Component child : container.getComponents())
        {
            if (!child.isVisible()) continue;
            assertTrue(child.getClass() + " escapes right edge", child.getX() + child.getWidth() <= container.getWidth());
            if (child instanceof GuideText) assertTrue("clipped wrapped text", child.getHeight() >= child.getPreferredSize().height);
            if (child instanceof JCheckBox) assertTrue("clipped checkbox", child.getPreferredSize().width <= container.getWidth());
            if (child instanceof Container) assertFits((Container) child);
        }
    }
}
