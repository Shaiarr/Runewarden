/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinigameActivityExitTest
{
    @Test public void aBankOrHudGapDoesNotCloseTheGuideButAPositiveLaterDepartureDoes()
    {
        Fixture f=new Fixture();MinigameActivityExit exit=new MinigameActivityExit();
        MinigameActivityCatalog.Snapshot game=MinigameNativeGuideProgressTest.snapshot(MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,"training:giants-foundry","Work at the grindstone");
        assertNull(exit.observe(game,f.client));assertNull(exit.observe(MinigameActivityCatalog.Snapshot.EMPTY,f.client));
        when(f.client.getGameState()).thenReturn(GameState.LOADING);assertNull(exit.observe(MinigameActivityCatalog.Snapshot.EMPTY,f.client));
        when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));
        assertSame(game,exit.observe(MinigameActivityCatalog.Snapshot.EMPTY,f.client));
        assertNull(exit.observe(MinigameActivityCatalog.Snapshot.EMPTY,f.client));
    }
    @Test public void anotherNativeActivityIsAnExitButClearingAnAccountDiscardsThePriorOwnership()
    {
        Fixture f=new Fixture();MinigameActivityExit exit=new MinigameActivityExit();
        MinigameActivityCatalog.Snapshot game=MinigameNativeGuideProgressTest.snapshot(MinigameActivityCatalog.Activity.GOTR,"training:guardians-rift","Use an active altar");
        assertNull(exit.observe(game,f.client));
        MinigameActivityCatalog.Snapshot other=MinigameNativeGuideProgressTest.snapshot(MinigameActivityCatalog.Activity.WINTERTODT,"training:wintertodt","Feed your kindling");
        assertSame(game,exit.observe(other,f.client));exit.clear();
        when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));assertNull(exit.observe(MinigameActivityCatalog.Snapshot.EMPTY,f.client));
    }
    @Test public void mageTrainingFloorTransitionsAreInsideTheSameActivity()
    {
        Fixture f=new Fixture();MinigameActivityExit exit=new MinigameActivityExit();
        MinigameActivityCatalog.Snapshot game=MinigameNativeGuideProgressTest.snapshot(MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA,"minigame:mage-training-arena","Enchant and deposit shapes");
        when(f.player.getWorldLocation()).thenReturn(WorldPoint.fromRegion(13462,20,20,0));assertNull(exit.observe(game,f.client));
        when(f.player.getWorldLocation()).thenReturn(WorldPoint.fromRegion(13462,20,20,2));assertNull(exit.observe(MinigameActivityCatalog.Snapshot.EMPTY,f.client));
    }
    private static final class Fixture
    {
        final Client client=mock(Client.class);final Player player=mock(Player.class);final WorldView view=mock(WorldView.class);
        Fixture(){when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(client.getTopLevelWorldView()).thenReturn(view);when(player.getWorldLocation()).thenReturn(new WorldPoint(3362,11487,0));}
    }
}
