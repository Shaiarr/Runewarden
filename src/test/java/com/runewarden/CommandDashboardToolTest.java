package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Rectangle;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.EnumSet;
import java.util.HashSet;
import javax.imageio.ImageIO;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CommandDashboardToolTest
{
    @Test public void nativeEquipmentGridUsesRealSlotsAndExcludesAppearanceIndices()
    {
        assertEquals(11, CommandDashboardTool.LAYOUT.size());
        HashSet<Integer> indices = new HashSet<>();
        HashSet<String> positions = new HashSet<>();
        for (CommandDashboardTool.SlotPosition slot : CommandDashboardTool.LAYOUT)
        {
            assertTrue(indices.add(slot.slot.getSlotIdx()));
            assertTrue(positions.add(slot.column + ":" + slot.row));
            assertTrue(slot.column >= 0 && slot.column < 3);
            assertTrue(slot.row >= 0 && slot.row < 5);
        }
        assertFalse(indices.contains(EquipmentInventorySlot.ARMS.getSlotIdx()));
        assertFalse(indices.contains(EquipmentInventorySlot.HAIR.getSlotIdx()));
        assertFalse(indices.contains(EquipmentInventorySlot.JAW.getSlotIdx()));
        assertTrue(indices.contains(EquipmentInventorySlot.AMMO.getSlotIdx()));
        assertTrue(indices.contains(EquipmentInventorySlot.RING.getSlotIdx()));
    }

    @Test public void readsWornContainerAndCorrectQuantitySpriteWithoutReadingBankOrInventory()
    {
        Client client = mock(Client.class);
        ItemManager items = mock(ItemManager.class);
        ItemContainer worn = mock(ItemContainer.class);
        RunewardenConfig config = new RunewardenConfig() {};
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(client.getWorld()).thenReturn(349);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL));
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
        when(worn.getItem(EquipmentInventorySlot.HEAD.getSlotIdx())).thenReturn(new Item(100, 1));
        when(worn.getItem(EquipmentInventorySlot.AMMO.getSlotIdx())).thenReturn(new Item(101, 23456));
        ItemComposition helm = mock(ItemComposition.class);
        when(helm.getName()).thenReturn("Test helm");
        when(items.getItemComposition(100)).thenReturn(helm);
        CommandDashboardTool tool = new CommandDashboardTool(client, items, mock(OverlayManager.class), config);
        tool.start();
        try
        {
            tool.onGameTick();
            CommandDashboardTool.Dashboard reading = tool.reading();
            assertTrue(reading.loggedIn);
            assertEquals(349, reading.world);
            assertEquals("Skill total · Members", reading.worldDescription);
            assertEquals(11, reading.equipment.size());
            CommandDashboardTool.WornSlot head = reading.equipment.stream()
                .filter(slot -> slot.position.slot == EquipmentInventorySlot.HEAD).findFirst().get();
            assertEquals("Test helm", head.name);
            assertEquals(100, head.id);
            verify(items).getImage(100, 1, false);
            verify(items).getImage(101, 23456, true);
            verify(client, atLeastOnce()).getItemContainer(InventoryID.WORN);
            verify(client, never()).getItemContainer(InventoryID.BANK);
            verify(client, never()).getItemContainer(InventoryID.INV);
            try { reading.equipment.clear(); fail("Snapshot must be immutable"); }
            catch (UnsupportedOperationException expected) { }
        }
        finally { tool.stop(); }
    }

    @Test public void clearsEquipmentWorldAndMaxHitAcrossLogout()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS));
        CommandDashboardTool tool = new CommandDashboardTool(client, mock(ItemManager.class), mock(OverlayManager.class), new RunewardenConfig() {});
        tool.start();
        try
        {
            tool.onGameTick();
            assertTrue(tool.reading().loggedIn);
            tool.setMaxHit(new CommandDashboardTool.MaxHitReading(42, "Normal melee · base"));
            assertEquals(Integer.valueOf(42), tool.reading().maxHit.value);
            when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            tool.onGameTick();
            assertFalse(tool.reading().loggedIn);
            assertEquals(0, tool.reading().world);
            assertTrue(tool.reading().equipment.isEmpty());
            assertNull(tool.reading().maxHit.value);
            tool.setMaxHit(new CommandDashboardTool.MaxHitReading(99, "Late previous-account calculation"));
            assertNull(tool.reading().maxHit.value);
        }
        finally { tool.stop(); }
    }

    @Test public void worldLabelsPrioritizeActualDangerAndSeasonalWorldFlags()
    {
        assertEquals("High-risk PvP", CommandDashboardTool.worldLabel(EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.HIGH_RISK)));
        assertEquals("Deadman", CommandDashboardTool.worldLabel(EnumSet.of(WorldType.DEADMAN, WorldType.SEASONAL)));
        assertEquals("Seasonal world", CommandDashboardTool.worldLabel(EnumSet.of(WorldType.SEASONAL)));
        assertEquals("Free world", CommandDashboardTool.worldLabel(EnumSet.noneOf(WorldType.class)));
    }

    @Test public void dashboardClockAndGearMoveAsOneOverlayAndCanBeDisabled() throws Exception
    {
        RunewardenConfig config = new RunewardenConfig() { };
        Client client = loggedInClient();
        CommandDashboardTool tool = new CommandDashboardTool(client, mock(ItemManager.class), mock(OverlayManager.class), config);
        CommandDashboardOverlay overlay = new CommandDashboardOverlay(tool, config);
        BufferedImage image = new BufferedImage(368, 608, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.scale(2, 2);
        assertNull(overlay.render(graphics));
        tool.start();
        try
        {
            assertNull("No empty or stale equipment block while logged out", overlay.render(graphics));
            tool.onGameTick();
            tool.setMaxHit(new CommandDashboardTool.MaxHitReading(42, "Normal melee · base"));
            assertEquals(new Dimension(184, 304), overlay.render(graphics));
            assertTrue(overlay.isMovable());
            assertFalse(overlay.isResizable());
            File output = new File("build/reports/command-dashboard-compact.png");
            output.getParentFile().mkdirs();
            ImageIO.write(image, "png", output);
            when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            tool.onGameTick();
            assertNull(overlay.render(graphics));
        }
        finally { graphics.dispose(); tool.stop(); }
    }

    @Test public void clockAndEquipmentCanBeHiddenIndependentlyWithoutLeavingDeadSpace() throws Exception
    {
        assertRenderDimensions(new RunewardenConfig() {
            @Override public boolean dashboardEquipment() { return false; }
        }, new Dimension(184, 109), "command-dashboard-metrics.png");
        assertRenderDimensions(new RunewardenConfig() {
            @Override public boolean clockHud() { return false; }
        }, new Dimension(184, 286), "command-dashboard-no-clock.png");
        assertRenderDimensions(new RunewardenConfig() {
            @Override public boolean hudCompact() { return false; }
        }, new Dimension(208, 355), "command-dashboard-comfortable.png");
        assertRenderDimensions(new RunewardenConfig() {
            @Override public boolean dashboardHud() { return false; }
        }, null, null);
    }

    @Test public void allEquipmentSlotsFitWithoutOverlapAndPreserveNativeSpriteDimensions()
    {
        for (boolean compact : new boolean[] {true, false})
        {
            CommandDashboardOverlay.Layout layout = new CommandDashboardOverlay.Layout(compact, true, true);
            List<Rectangle> occupied = new ArrayList<>();
            for (CommandDashboardTool.SlotPosition position : CommandDashboardTool.LAYOUT)
            {
                Rectangle slot = layout.slot(position);
                assertTrue(slot.width >= 36);
                assertTrue(slot.height >= 32);
                assertTrue(slot.x >= layout.margin);
                assertTrue(slot.y >= layout.gridY);
                assertTrue(slot.x + slot.width < layout.width - layout.margin);
                assertTrue(slot.y + slot.height < layout.height);
                for (Rectangle other : occupied) assertFalse(slot.intersects(other));
                occupied.add(slot);
            }
        }
    }

    @Test public void nativeEquipmentSpriteRendersAtItsOriginalPixels()
    {
        AsyncBufferedImage sprite = new AsyncBufferedImage(mock(ClientThread.class), 36, 32, BufferedImage.TYPE_INT_ARGB);
        sprite.setRGB(0, 0, Color.MAGENTA.getRGB());
        sprite.setRGB(35, 31, Color.CYAN.getRGB());
        RunewardenConfig config = new RunewardenConfig() { };
        Client client = loggedInClient();
        ItemManager items = mock(ItemManager.class);
        ItemContainer worn = mock(ItemContainer.class);
        when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
        when(worn.getItem(EquipmentInventorySlot.HEAD.getSlotIdx())).thenReturn(new Item(100, 1));
        when(items.getImage(100, 1, false)).thenReturn(sprite);
        CommandDashboardTool tool = new CommandDashboardTool(client, items, mock(OverlayManager.class), config);
        tool.start();
        try
        {
            tool.onGameTick();
            BufferedImage output = new BufferedImage(184, 304, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = output.createGraphics();
            try { new CommandDashboardOverlay(tool, config).render(graphics); }
            finally { graphics.dispose(); }
            Rectangle head = new CommandDashboardOverlay.Layout(true, true, true).slot(CommandDashboardTool.LAYOUT.get(0));
            int x = head.x + (head.width - 36) / 2;
            int y = head.y + (head.height - 32) / 2;
            assertEquals(Color.MAGENTA.getRGB(), output.getRGB(x, y));
            assertEquals(Color.CYAN.getRGB(), output.getRGB(x + 35, y + 31));
            assertNotEquals(Color.MAGENTA.getRGB(), output.getRGB(x - 1, y));
        }
        finally { tool.stop(); }
    }

    @Test public void clockRespectsLocalZoneUtcAndDaylightSaving()
    {
        Instant instant = Instant.parse("2026-10-16T17:18:14Z");
        assertEquals("19:18:14", CommandDashboardOverlay.clockText(instant, false, ZoneId.of("Europe/Brussels")));
        assertEquals("17:18:14", CommandDashboardOverlay.clockText(instant, true, ZoneId.of("Europe/Brussels")));
        assertEquals("18:18:14", CommandDashboardOverlay.clockText(Instant.parse("2026-12-16T17:18:14Z"), false, ZoneId.of("Europe/Brussels")));
    }

    private static Client loggedInClient()
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(client.getWorld()).thenReturn(349);
        when(client.getWorldType()).thenReturn(EnumSet.of(WorldType.MEMBERS, WorldType.SKILL_TOTAL));
        return client;
    }

    private static void assertRenderDimensions(RunewardenConfig config, Dimension expected, String file) throws Exception
    {
        CommandDashboardTool tool = new CommandDashboardTool(loggedInClient(), mock(ItemManager.class), mock(OverlayManager.class), config);
        tool.start();
        try
        {
            tool.onGameTick();
            tool.setMaxHit(new CommandDashboardTool.MaxHitReading(42, "Normal melee · base"));
            BufferedImage image = new BufferedImage(expected == null ? 1 : expected.width * 2,
                expected == null ? 1 : expected.height * 2, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try
            {
                graphics.scale(2, 2);
                assertEquals(expected, new CommandDashboardOverlay(tool, config).render(graphics));
            }
            finally { graphics.dispose(); }
            if (file != null)
            {
                File output = new File("build/reports/" + file);
                output.getParentFile().mkdirs();
                ImageIO.write(image, "png", output);
            }
        }
        finally { tool.stop(); }
    }
}
