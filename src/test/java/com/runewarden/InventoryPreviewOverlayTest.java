/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class InventoryPreviewOverlayTest
{
    @Test public void offTabPreviewReadsTheNativeInventoryAndClearsAcrossLogout()
    {
        HudStyle.context(false,false,0,0);
        Client client=mock(Client.class);
        ItemManager items=mock(ItemManager.class);
        Widget inventory=mock(Widget.class);
        ItemContainer container=mock(ItemContainer.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(mock(Player.class));
        when(client.getWidget(InterfaceID.Inventory.ITEMS)).thenReturn(inventory);
        when(inventory.isHidden()).thenReturn(true);
        when(client.getItemContainer(InventoryID.INVENTORY)).thenReturn(container);
        Item[] actual=new Item[28];
        actual[0]=new Item(4151,1);
        actual[27]=new Item(995,250000);
        when(container.getItems()).thenReturn(actual);
        InventoryPreviewOverlay overlay=new InventoryPreviewOverlay(client,new RunewardenConfig(){},items);
        BufferedImage image=new BufferedImage(176,272,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=image.createGraphics();
        try
        {
            overlay.onGameTick();
            assertEquals(new Dimension(176,272),overlay.render(graphics));
            verify(items).getImage(4151);
            verify(items).getImage(995);
            when(inventory.isHidden()).thenReturn(false);
            overlay.onGameTick();
            assertNull(overlay.render(graphics));
            when(inventory.isHidden()).thenReturn(true);
            overlay.onGameTick();
            assertNotNull(overlay.render(graphics));
            when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
            overlay.onGameTick();
            assertNull(overlay.render(graphics));
        }
        finally { graphics.dispose(); }
    }

    @Test public void visibilityModesAndQuantitiesKeepTheirSeparateMeanings()
    {
        assertTrue(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.CLOSED_TAB,false,false));
        assertFalse(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.CLOSED_TAB,true,true));
        assertTrue(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.ALWAYS,true,false));
        assertTrue(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.COMBAT,true,true));
        assertFalse(InventoryPreviewOverlay.shouldShow(RunewardenConfig.InventoryMode.COMBAT,false,false));
        assertEquals("99999",InventoryPreviewOverlay.quantity(99999));
        assertEquals("100K",InventoryPreviewOverlay.quantity(100000));
        assertEquals("10M",InventoryPreviewOverlay.quantity(10000000));
    }
}
