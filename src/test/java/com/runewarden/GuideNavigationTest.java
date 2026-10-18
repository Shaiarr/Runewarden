/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuideNavigationTest
{
    @Test public void routeGuardRejectsLogoutInstancesAndCurrentInteractions()
    {
        Client client = mock(Client.class);
        Player player = mock(Player.class);
        GuideNavigation navigation = new GuideNavigation(client, mock(EventBus.class), mock(PluginManager.class));
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        assertFalse(navigation.canTravel());
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        assertTrue(navigation.canTravel());
        when(client.isInInstancedRegion()).thenReturn(true);
        assertFalse(navigation.canTravel());
        when(client.isInInstancedRegion()).thenReturn(false);
        when(player.getInteracting()).thenReturn(mock(NPC.class));
        assertFalse(navigation.canTravel());
    }
}
