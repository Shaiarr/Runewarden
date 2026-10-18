/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.concurrent.ScheduledExecutorService;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class ItemKnowledgeLifecycleTest
{
    private static ItemKnowledgeCatalog catalog;
    private static SkillingResourceCatalog resources;
    private ItemManager items;
    private Client client;
    private ItemKnowledgeTool tool;

    @BeforeClass public static void catalog()
    {
        Gson gson = new Gson(); catalog = new ItemKnowledgeCatalog(gson, new GuideCatalog(gson));
        catalog.load(); resources = new SkillingResourceCatalog(gson);
    }

    @Before public void setup()
    {
        client = mock(Client.class); items = mock(ItemManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getItemCount()).thenReturn(1000);
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ItemSourceService sources = new ItemSourceService(mock(OkHttpClient.class), new Gson(), executor);
        tool = new ItemKnowledgeTool(client, mock(ClientThread.class), items, catalog, resources, sources, executor);
        tool.start();
    }

    @Test public void unusedDatabaseNeverWarmsNativeItemDefinitions()
    {
        tool.onGameTick(); tool.onGameTick();
        verify(client, never()).getItemCount();
        verify(items, never()).getItemComposition(anyInt());
        tool.stop();
    }

    @Test public void visibleIndexingHasABoundedBudgetAndStopsImmediatelyWhenHidden() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> tool.onVisibilityChanged(true));
        tool.onGameTick();
        verify(items, times(96)).getItemComposition(anyInt());
        SwingUtilities.invokeAndWait(() -> tool.onVisibilityChanged(false));
        tool.onGameTick(); tool.onGameTick();
        verify(items, times(96)).getItemComposition(anyInt());
        SwingUtilities.invokeAndWait(() -> tool.onVisibilityChanged(true));
        tool.onGameTick();
        verify(items, times(192)).getItemComposition(anyInt());
        tool.stop();
    }

    @Test public void explicitItemOpeningStillResolvesItsDefinitionWithoutScanningTheCatalog() throws Exception
    {
        ItemComposition coins = mock(ItemComposition.class);
        when(coins.getMembersName()).thenReturn("Coins");
        when(items.getItemComposition(995)).thenReturn(coins);
        when(items.canonicalize(995)).thenReturn(995);
        when(items.getItemPrice(995)).thenReturn(5_000_000_000L);
        tool.openItem(995); SwingUtilities.invokeAndWait(() -> { });
        tool.onGameTick();
        verify(client, never()).getItemCount();
        verify(items, times(2)).getItemComposition(995);
        verify(items, times(2)).getItemComposition(anyInt());
        tool.stop();
    }
}
