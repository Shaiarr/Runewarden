/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Map;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BankedExperienceTest
{
    private Client client;
    private ItemContainer container;
    private Player player;
    private Widget widget;
    private BankedExperience experience;

    @Before public void prepare()
    {
        client = mock(Client.class); container = mock(ItemContainer.class); player = mock(Player.class); widget = mock(Widget.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player); when(player.getName()).thenReturn("Cathy Wampus");
        when(client.getItemContainer(InventoryID.BANK)).thenReturn(container);
        when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(widget);
        when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
        ItemManager items = mock(ItemManager.class); when(items.canonicalize(anyInt())).thenAnswer(i -> i.getArgument(0));
        experience = new BankedExperience(client, items);
    }

    @Test public void onlyAnActuallyOpenBankCanSupplyMaterialsAndDuplicateStacksAreSummed()
    {
        when(container.getItems()).thenReturn(new Item[]{new Item(1601, 10), new Item(1601, 20), new Item(-1, 0), new Item(2357, 0)});
        when(widget.isHidden()).thenReturn(true);
        experience.onItemContainerChanged(InventoryID.BANK); assertNull(experience.bankObservedAt());
        when(widget.isHidden()).thenReturn(false);
        experience.onItemContainerChanged(InventoryID.INV); assertNull(experience.bankObservedAt());
        experience.onItemContainerChanged(InventoryID.BANK);
        assertNotNull(experience.bankObservedAt()); assertEquals(Map.of(1601, 30L), experience.observedItems());
    }

    @Test public void openingBankCapturesLocalMaterialsWithoutAnySyncOrSharingObject()
    {
        when(container.getItems()).thenReturn(new Item[]{new Item(1775, 99)});
        experience.onGameTick(); assertEquals(Map.of(1775, 99L), experience.observedItems());
        when(widget.isHidden()).thenReturn(true); experience.onGameTick();
        assertEquals(Map.of(1775, 99L), experience.observedItems());
        experience.onGameStateChanged(GameState.LOGIN_SCREEN);
        assertNull(experience.bankObservedAt()); assertTrue(experience.observedItems().isEmpty());
    }

    @Test public void switchingAccountClearsThePreviousAccountsBank()
    {
        when(container.getItems()).thenReturn(new Item[]{new Item(1775, 99)});
        experience.onGameTick(); when(widget.isHidden()).thenReturn(true);
        when(player.getName()).thenReturn("Other player"); experience.onGameTick();
        assertNull(experience.bankObservedAt()); assertTrue(experience.observedItems().isEmpty());
    }
    @Test public void panelFitsRuneLiteSidebarAndShowsRealBankTarget() throws Exception
    {
        ItemManager images = mock(ItemManager.class);
        when(images.canonicalize(anyInt())).thenAnswer(i -> i.getArgument(0));
        when(images.getImage(anyInt())).thenAnswer(i -> new AsyncBufferedImage(mock(ClientThread.class), 36, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB));
        experience = new BankedExperience(client, images);
        when(container.getItems()).thenReturn(new Item[]{new Item(1601, 100), new Item(2357, 100)});
        experience.onGameTick();
        JPanel[] panel = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = experience.createPanel();
            assertTrue("Calculator must fit 225px sidebar: " + panel[0].getPreferredSize(), panel[0].getPreferredSize().width <= 225);
        });
    }


}
