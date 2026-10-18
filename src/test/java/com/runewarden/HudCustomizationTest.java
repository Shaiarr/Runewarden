/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HudCustomizationTest
{
    @Test public void sharedLayoutKeepsOnlyKnownBoundedPresentationAndPositions()
    {
        Map<String, HudPreferences.Style> styles = new LinkedHashMap<>();
        styles.put("inventory", new HudPreferences.Style(900, 80, -4, Color.RED, Color.WHITE, Color.BLACK,
            HudPreferences.Visibility.COMBAT, HudPreferences.Frame.SQUARE, HudPreferences.Values.PERCENT));
        String layout = HudPreferences.encode(styles);
        String positions = "RWP1|inventory:0=MTIzOjQ1Ng|";
        String[] restored = HudLayoutPositions.unshare(HudLayoutPositions.share(layout, positions));
        assertEquals(layout, restored[0]); assertEquals(positions, restored[1]);
        HudPreferences.Style result = HudPreferences.decode(restored[0]).get("inventory");
        assertEquals(180, result.scale); assertEquals(18, result.font); assertEquals(10, result.opacity);
        assertEquals(Color.RED, result.accent);
        assertEquals("123:456", HudLayoutPositions.decode(restored[1]).get("inventory:0"));
        assertFalse(restored[0].contains("profile"));
    }

    @Test public void unsupportedAndUnknownSharedSettingsAreRejected()
    {
        for (String value : new String[] {"RW2|", "RW1|foreign=100,12,85,000000,ffffff,000000,ALWAYS,SQUARE,CURRENT|",
            "RW1|guide=100,12,85,javascript,ffffff,000000,ALWAYS,SQUARE,CURRENT|"})
        {
            try { HudPreferences.decode(value); fail(value); }
            catch (IllegalArgumentException expected) { }
        }
        try { HudLayoutPositions.decode("RWP1|otherplugin:0=MTIzOjQ1Ng|"); fail(); }
        catch (IllegalArgumentException expected) { }
        try { HudLayoutPositions.unshare("RUNEWARDEN-LAYOUT:notbase64.notbase64"); fail(); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void independentWidgetStyleChangesActualRenderingBoundsAndColour()
    {
        Map<String, HudPreferences.Style> styles = new LinkedHashMap<>();
        styles.put("inventory", new HudPreferences.Style(125, 12, 100, Color.RED, Color.WHITE, Color.BLUE,
            HudPreferences.Visibility.ALWAYS, HudPreferences.Frame.SQUARE, HudPreferences.Values.CURRENT));
        String encoded = HudPreferences.encode(styles);
        RunewardenConfig config = new RunewardenConfig() { @Override public String hudWidgetSettings() { return encoded; } };
        HudPreferences.Style inventory = HudStyle.settings(config, "inventory"), guide = HudStyle.settings(config, "guide");
        assertEquals(new Dimension(220, 340), HudStyle.dimension(inventory, 176, 272));
        assertEquals(100, guide.scale);
        BufferedImage image = new BufferedImage(300, 400, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = HudStyle.canvas(image.createGraphics(), inventory);
        try { HudStyle.panel(g, 176, 272, inventory); }
        finally { g.dispose(); }
        assertEquals(Color.BLUE.getRGB(), image.getRGB(100, 100));
        assertEquals(0, image.getRGB(230, 100));
    }

    @Test public void inventoryMirrorsExactly28SlotsAndClearsAcrossLogout()
    {
        Client client = mock(Client.class); Player player = mock(Player.class); ItemManager items = mock(ItemManager.class);
        ItemContainer container = mock(ItemContainer.class);
        Item[] actual = new Item[30]; actual[0] = new Item(995, 120_000); actual[27] = new Item(1511, 1); actual[28] = new Item(1337, 1);
        when(container.getItems()).thenReturn(actual);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
        when(player.getHealthRatio()).thenReturn(-1);
        InventoryPreviewOverlay overlay = new InventoryPreviewOverlay(client, new RunewardenConfig() {}, items);
        HudStyle.context(false, false, 0, 0); overlay.onGameTick();
        Graphics2D g = new BufferedImage(300, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            assertEquals(new Dimension(176, 272), overlay.render(g));
            verify(items).getImage(995); verify(items).getImage(1511); verify(items, never()).getImage(1337);
            when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            assertNull(overlay.render(g)); overlay.onGameTick();
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            assertNull(overlay.render(g));
        }
        finally { g.dispose(); }
    }

    @Test public void closedTabModeAndBankVisibilityAreIndependent()
    {
        assertTrue(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.CLOSED_TAB, false, false));
        assertFalse(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.CLOSED_TAB, true, true));
        assertTrue(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.ALWAYS, true, false));
        assertFalse(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.COMBAT, false, false));
        assertTrue(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.COMBAT, true, true));
        Client client = mock(Client.class); Player player = mock(Player.class); Widget widget = mock(Widget.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
        when(client.getWidget(InterfaceID.Inventory.ITEMS)).thenReturn(widget);
        when(widget.isHidden()).thenReturn(true);
        InventoryPreviewOverlay overlay = new InventoryPreviewOverlay(client, new RunewardenConfig() {}, mock(ItemManager.class));
        overlay.onGameTick(); HudStyle.context(false, true, 0, 0);
        Graphics2D g = new BufferedImage(300, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try { assertNull(overlay.render(g)); }
        finally { g.dispose(); HudStyle.context(false, false, 0, 0); }
    }

    @Test public void tickHeartbeatUsesActualEventsAndSoundIsOptIn()
    {
        Client client = mock(Client.class); ConfigManager manager = mock(ConfigManager.class); Player player = mock(Player.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
        when(player.getHealthRatio()).thenReturn(-1);
        HudSession silent = new HudSession(client, new RunewardenConfig() {}, manager);
        silent.start(); silent.onGameTick(); silent.onGameTick();
        assertTrue(HudStyle.tickNanos() > 0); assertEquals(1, HudStyle.tick());
        verify(client, never()).playSoundEffect(anyInt(), anyInt()); silent.stop(); assertEquals(0, HudStyle.tickNanos());
        RunewardenConfig sound = new RunewardenConfig() { @Override public boolean tickSound() { return true; } @Override public int tickCycle() { return 2; } };
        HudSession audible = new HudSession(client, sound, manager); audible.start(); audible.onGameTick(); audible.onGameTick(); audible.onGameTick();
        verify(client, times(1)).playSoundEffect(anyInt(), eq(25));
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); audible.onGameTick();
        assertEquals(0, HudStyle.tickNanos()); audible.stop();
    }

    @Test public void styleVisibilityUsesCombatContextAndStackCountsRemainPrecise()
    {
        HudPreferences.Style combat = new HudPreferences.Style(100, 12, 85, Color.YELLOW, Color.WHITE, Color.BLACK,
            HudPreferences.Visibility.COMBAT, HudPreferences.Frame.ROUNDED, HudPreferences.Values.CURRENT_MAX);
        HudStyle.context(false, false, 0, 0); assertFalse(HudStyle.visible(combat));
        HudStyle.context(true, false, 0, 0); assertTrue(HudStyle.visible(combat));
        assertEquals("99999", InventoryPreviewOverlay.quantity(99999));
        assertEquals("100K", InventoryPreviewOverlay.quantity(100000));
        assertEquals("10M", InventoryPreviewOverlay.quantity(10000000));
        HudStyle.context(false, false, 0, 0);
    }

    @Test public void talkingToNonCombatNpcDoesNotActivateCombatOnlyWidgets()
    {
        Player player = mock(Player.class); net.runelite.api.NPC banker = mock(net.runelite.api.NPC.class);
        when(player.getHealthRatio()).thenReturn(-1); when(player.getInteracting()).thenReturn(banker);
        when(banker.getCombatLevel()).thenReturn(0); assertFalse(HudStyle.inCombat(player));
        when(banker.getCombatLevel()).thenReturn(82); assertTrue(HudStyle.inCombat(player));
    }

    @Test public void characterStyleSwitchCannotOverwriteThePreviousCharactersLayout()
    {
        Client client = mock(Client.class); Player player = mock(Player.class); ConfigManager manager = mock(ConfigManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
        final String[] profile = {"A"}, layout = {""};
        Map<String, String> saved = new LinkedHashMap<>();
        String first = HudPreferences.preset("PvM", 85), second = HudPreferences.preset("AFK", 75);
        saved.put("A", first); saved.put("B", second);
        when(manager.getRSProfileKey()).thenAnswer(call -> profile[0]);
        when(manager.getRSProfileConfiguration(RunewardenConfig.GROUP, "hudCharacterLayout")).thenAnswer(call -> saved.get(profile[0]));
        doAnswer(call -> { layout[0] = call.getArgument(2); return null; }).when(manager)
            .setConfiguration(eq(RunewardenConfig.GROUP), eq("hudWidgetSettings"), any());
        doAnswer(call -> { saved.put(profile[0], call.getArgument(2)); return null; }).when(manager)
            .setRSProfileConfiguration(eq(RunewardenConfig.GROUP), eq("hudCharacterLayout"), any());
        RunewardenConfig config = new RunewardenConfig() { @Override public String hudWidgetSettings() { return layout[0]; } };
        HudSession session = new HudSession(client, config, manager); session.start(); session.onGameTick();
        assertEquals(first, layout[0]); profile[0] = "B"; session.onGameTick();
        assertEquals(second, layout[0]); assertEquals(first, saved.get("A"));
        layout[0] = HudPreferences.preset("Skilling", 60); session.onGameTick();
        assertEquals(layout[0], saved.get("B")); assertEquals(first, saved.get("A")); session.stop();
    }

    @Test public void disabledInventoryDoesNoClientOrItemWork()
    {
        Client client = mock(Client.class); ItemManager items = mock(ItemManager.class);
        RunewardenConfig config = new RunewardenConfig() { @Override public boolean inventoryPreview() { return false; } };
        InventoryPreviewOverlay overlay = new InventoryPreviewOverlay(client, config, items);
        overlay.onGameTick(); overlay.onGameTick(); verifyNoInteractions(client, items);
    }

    @Test public void hiddenInventoryDoesNotReadContainersOrRequestImages()
    {
        Client client = mock(Client.class); ItemManager items = mock(ItemManager.class);
        Player player = mock(Player.class); Widget inventory = mock(Widget.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
        when(client.getWidget(InterfaceID.Inventory.ITEMS)).thenReturn(inventory);
        when(inventory.isHidden()).thenReturn(false); when(player.getHealthRatio()).thenReturn(-1);
        HudStyle.context(false, false, 0, 0);
        InventoryPreviewOverlay overlay = new InventoryPreviewOverlay(client, new RunewardenConfig() {}, items);
        overlay.onGameTick(); overlay.onGameTick();
        verify(client, never()).getItemContainer(InventoryID.INVENTORY); verifyNoInteractions(items);
    }

    @Test public void disabledDashboardPerformsNoClientOrItemWork()
    {
        Client client = mock(Client.class); ItemManager items = mock(ItemManager.class);
        RunewardenConfig config = new RunewardenConfig() { @Override public boolean dashboardHud() { return false; } };
        CommandDashboardTool dashboard = new CommandDashboardTool(client, items, mock(net.runelite.client.ui.overlay.OverlayManager.class), config);
        dashboard.start(); dashboard.onGameTick(); dashboard.onGameTick();
        assertFalse(dashboard.reading().loggedIn); verifyNoInteractions(client, items); dashboard.stop();
    }

    @Test public void hiddenWornGridDoesNotReadSlotNamesOrImages()
    {
        Client client = mock(Client.class); ItemManager items = mock(ItemManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        RunewardenConfig config = new RunewardenConfig() { @Override public boolean dashboardEquipment() { return false; } };
        CommandDashboardTool dashboard = new CommandDashboardTool(client, items, mock(net.runelite.client.ui.overlay.OverlayManager.class), config);
        dashboard.start(); dashboard.onGameTick();
        assertTrue(dashboard.reading().loggedIn); assertTrue(dashboard.reading().equipment.isEmpty());
        // The legacy max-hit fallback may read worn once; the equipment grid must not read it a second time.
        verify(client, times(1)).getItemContainer(InventoryID.EQUIPMENT);
        verifyNoInteractions(items); dashboard.stop();
    }

    @Test public void fullyDisabledSkillingCoachSkipsClientAndItemReadsAcrossEvents()
    {
        Client client = mock(Client.class); ItemManager items = mock(ItemManager.class);
        RunewardenConfig config = new RunewardenConfig() {
            @Override public boolean nodeTimers() { return false; }
            @Override public boolean skillingOutfitReminder() { return false; }
        };
        SkillingCoach coach = new SkillingCoach(client, items, mock(ConfigManager.class), config);
        coach.onGameTick(new net.runelite.api.events.GameTick());
        coach.onAnimationChanged(new net.runelite.api.events.AnimationChanged());
        coach.onMenuOptionClicked(new net.runelite.api.events.MenuOptionClicked(mock(net.runelite.api.MenuEntry.class)));
        coach.onStatChanged(new net.runelite.api.events.StatChanged(net.runelite.api.Skill.MINING, 10, 1, 1));
        coach.onScriptPreFired(new net.runelite.api.events.ScriptPreFired(1));
        assertNull(coach.reminder()); assertTrue(coach.nodes().isEmpty()); verifyNoInteractions(client, items);
    }

    @Test public void disablingOutfitsPreservesNodeObservationWithoutEquipmentChecks()
    {
        Client client = mock(Client.class); ItemManager items = mock(ItemManager.class); Player player = mock(Player.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
        RunewardenConfig config = new RunewardenConfig() { @Override public boolean skillingOutfitReminder() { return false; } };
        SkillingCoach coach = new SkillingCoach(client, items, mock(ConfigManager.class), config);
        coach.startUp(); coach.onStatChanged(new net.runelite.api.events.StatChanged(net.runelite.api.Skill.MINING, 10, 1, 1));
        coach.onGameTick(new net.runelite.api.events.GameTick());
        assertNull(coach.reminder()); verify(client, never()).getItemContainer(any(InventoryID.class)); verifyNoInteractions(items);
    }
}
