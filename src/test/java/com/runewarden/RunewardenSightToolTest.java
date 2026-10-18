/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Arrays;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.KeyCode;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import net.runelite.api.Point;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RunewardenSightToolTest
{
    private Client client;
    private Player player;
    private NPC npc;
    private WorldView view;
    private IndexedObjectSet<NPC> npcs;
    private WorldArea playerArea;
    private WorldArea targetArea;
    private OverlayManager overlays;
    private EventBus events;
    private RunewardenSightTool tool;
    private RunewardenConfig config;

    @SuppressWarnings("unchecked")
    @Before public void prepare()
    {
        client = mock(Client.class);
        player = mock(Player.class);
        npc = mock(NPC.class);
        view = mock(WorldView.class);
        npcs = mock(IndexedObjectSet.class);
        playerArea = mock(WorldArea.class);
        targetArea = mock(WorldArea.class);
        overlays = mock(OverlayManager.class);
        events = mock(EventBus.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(player.getWorldView()).thenReturn(view);
        when(npc.getWorldView()).thenReturn(view);
        when(player.getWorldArea()).thenReturn(playerArea);
        when(npc.getWorldArea()).thenReturn(targetArea);
        when(npc.getName()).thenReturn("<col=ffaa00>Goblin</col>");
        when(npc.getIndex()).thenReturn(7);
        doReturn(npcs).when(view).npcs();
        when(npcs.byIndex(7)).thenReturn(npc);
        when(npcs.iterator()).thenAnswer(invocation -> Arrays.asList(npc).iterator());
        when(view.getCollisionMaps()).thenReturn(new CollisionData[4]);
        when(playerArea.distanceTo(targetArea)).thenReturn(6);
        config = mock(RunewardenConfig.class);
        tool = new RunewardenSightTool(client, mock(ClientThread.class), overlays, events, config);
        tool.start();
    }

    @Test public void preservesAsymmetricPlayerAndNpcVisibility()
    {
        when(playerArea.hasLineOfSightTo(view, targetArea)).thenReturn(true);
        when(targetArea.hasLineOfSightTo(view, playerArea)).thenReturn(false);
        RunewardenSightTool.SightReading reading = tool.measure(npc);
        assertSame(npc, reading.target);
        assertTrue(reading.playerToNpc);
        assertFalse(reading.npcToPlayer);
        assertEquals(6, reading.distance);
        assertEquals("Goblin", reading.targetName);
        assertTrue(reading.message.contains("Your sight → target: CLEAR"));
        assertTrue(reading.message.contains("Target sight → you: BLOCKED"));
        assertFalse(reading.message.toLowerCase().contains("safe"));
    }

    @Test public void hoverKnowledgeUsesTheTransformedNpcId()
    {
        MobKnowledgeCatalog knowledge = new MobKnowledgeCatalog(new com.google.gson.Gson());
        MobKnowledgeCatalog.Card card = knowledge.describe(2043, "Goblin");
        NPCComposition composition = mock(NPCComposition.class);
        when(npc.getId()).thenReturn(2042);
        when(config.hoverMobFacts()).thenReturn(true);
        when(npc.getTransformedComposition()).thenReturn(composition);
        when(composition.getId()).thenReturn(2043);
        RunewardenSightTool withKnowledge = new RunewardenSightTool(client, mock(ClientThread.class), overlays, events, config, knowledge);
        withKnowledge.start();
        RunewardenSightTool.SightReading reading = withKnowledge.measure(npc);
        assertSame(card, reading.knowledge);
        assertTrue(reading.message.contains("Attacks: Typeless"));
        assertFalse(reading.message.contains("Protect from"));
        assertEquals("Magma", reading.knowledge.variant);
        withKnowledge.stop();
    }

    @Test public void usesThePlayersActualWorldViewIncludingChildViews()
    {
        WorldView topLevel = mock(WorldView.class);
        when(client.getTopLevelWorldView()).thenReturn(topLevel);
        tool.measure(npc);
        verify(playerArea).hasLineOfSightTo(view, targetArea);
        verify(targetArea).hasLineOfSightTo(view, playerArea);
        verify(playerArea, never()).hasLineOfSightTo(topLevel, targetArea);
    }

    @Test public void fallsBackToTopLevelViewWhenActorViewIsUnavailable()
    {
        when(player.getWorldView()).thenReturn(null);
        when(npc.getWorldView()).thenReturn(null);
        assertSame(npc, tool.measure(npc).target);
        verify(playerArea).hasLineOfSightTo(view, targetArea);
    }

    @Test public void rejectsDifferentWorldViewsAndPlanesWithoutClaimingBlockedSight()
    {
        when(npc.getWorldView()).thenReturn(mock(WorldView.class));
        assertNull(tool.measure(npc).target);
        verify(playerArea, never()).hasLineOfSightTo(view, targetArea);
        when(npc.getWorldView()).thenReturn(view);
        when(targetArea.getPlane()).thenReturn(1);
        assertNull(tool.measure(npc).target);
        verify(playerArea, never()).hasLineOfSightTo(view, targetArea);
    }

    @Test public void rejectsDespawnedOrReusedNpcIndices()
    {
        when(npcs.byIndex(7)).thenReturn(mock(NPC.class));
        assertNull(tool.measure(npc).target);
        verifyNoInteractions(playerArea);
    }

    @Test public void missingOrChangingCollisionDataIsUnavailableRatherThanClear()
    {
        when(view.getCollisionMaps()).thenReturn(null);
        assertNull(tool.measure(npc).target);
        when(view.getCollisionMaps()).thenReturn(new CollisionData[4]);
        when(playerArea.hasLineOfSightTo(view, targetArea)).thenThrow(new ArrayIndexOutOfBoundsException());
        assertNull(tool.measure(npc).target);
    }

    @Test public void realRuneLiteWorldAreaReadsChangingCollisionFlags()
    {
        Scene scene = mock(Scene.class);
        Tile[][][] tiles = new Tile[4][104][104];
        Tile from = mock(Tile.class), to = mock(Tile.class);
        when(from.getSceneLocation()).thenReturn(new net.runelite.api.Point(10, 10));
        when(to.getSceneLocation()).thenReturn(new net.runelite.api.Point(15, 10));
        tiles[0][10][10] = from; tiles[0][15][10] = to;
        when(scene.getTiles()).thenReturn(tiles);
        when(view.getScene()).thenReturn(scene);
        when(view.getSizeX()).thenReturn(104);
        when(view.getSizeY()).thenReturn(104);
        CollisionData collision = mock(CollisionData.class);
        int[][] flags = new int[104][104];
        when(collision.getFlags()).thenReturn(flags);
        when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision, collision, collision, collision});
        when(player.getWorldArea()).thenReturn(new WorldArea(10, 10, 1, 1, 0));
        when(npc.getWorldArea()).thenReturn(new WorldArea(15, 10, 1, 1, 0));
        assertTrue(tool.measure(npc).playerToNpc);
        assertTrue(tool.measure(npc).npcToPlayer);
        flags[12][10] = CollisionDataFlag.BLOCK_LINE_OF_SIGHT_FULL;
        assertFalse(tool.measure(npc).playerToNpc);
        assertFalse(tool.measure(npc).npcToPlayer);
        flags[12][10] = 0;
        assertTrue(tool.measure(npc).playerToNpc);
        assertEquals(5, tool.measure(npc).distance);
    }

    @Test public void explicitRefreshDoesNotAutomaticallySelectAnNpc() throws Exception
    {
        final JPanel[] panel = new JPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = tool.createPanel());
        tool.refreshTargets();
        SwingUtilities.invokeAndWait(() -> { });
        JPanel controls = (JPanel) panel[0].getComponent(0);
        JComboBox<?> selector = (JComboBox<?>) controls.getComponent(2);
        assertEquals(2, selector.getItemCount());
        assertEquals(0, selector.getSelectedIndex());
        assertNull(tool.reading().target);
        verify(playerArea, never()).hasLineOfSightTo(view, targetArea);
    }

    @Test public void selectedTargetUpdatesUntilItLeavesAndThenRequiresNewSelection()
    {
        when(playerArea.hasLineOfSightTo(view, targetArea)).thenReturn(true, false);
        tool.select(npc);
        assertTrue(tool.reading().playerToNpc);
        tool.onGameTick(new GameTick());
        assertFalse(tool.reading().playerToNpc);
        when(npcs.byIndex(7)).thenReturn(null);
        tool.onGameTick(new GameTick());
        assertNull(tool.reading().target);
        when(npcs.byIndex(7)).thenReturn(npc);
        tool.onGameTick(new GameTick());
        assertNull(tool.reading().target);
    }

    @Test public void stoppedModuleRemovesItsOverlayAndEventsOnlyOnce()
    {
        tool.stop(); tool.stop();
        assertFalse(tool.overlayVisible());
        verify(events, times(1)).register(tool);
        verify(events, times(1)).unregister(tool);
        verify(overlays, times(1)).add(any(RunewardenSightOverlay.class));
        verify(overlays, times(1)).remove(any(RunewardenSightOverlay.class));
    }
    private MenuEntry hoverEntry(NPC target)
    {
        when(config.hoverNpcSight()).thenReturn(true);
        when(client.isKeyPressed(KeyCode.KC_SHIFT)).thenReturn(true);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(250, 180));
        when(client.getCanvasWidth()).thenReturn(765);
        when(client.getCanvasHeight()).thenReturn(503);
        MenuEntry entry = mock(MenuEntry.class);
        when(entry.getType()).thenReturn(MenuAction.NPC_SECOND_OPTION);
        when(entry.getNpc()).thenReturn(target);
        when(client.getMenuEntries()).thenReturn(new MenuEntry[]{entry});
        return entry;
    }

    @Test public void hoveringAnNpcImmediatelyMeasuresItAndLeavingClearsIt()
    {
        hoverEntry(npc);
        when(playerArea.hasLineOfSightTo(view, targetArea)).thenReturn(true);
        tool.onClientTick();
        assertSame(npc, tool.reading().target);
        assertTrue(tool.isHoverReading());
        when(client.getMenuEntries()).thenReturn(new MenuEntry[0]);
        tool.onClientTick();
        assertNull(tool.reading().target);
    }

    @Test public void topWidgetEntryPreventsUnderlyingNpcFromLeakingIntoHover()
    {
        MenuEntry npcEntry = hoverEntry(npc);
        MenuEntry widget = mock(MenuEntry.class);
        when(widget.getType()).thenReturn(MenuAction.CC_OP);
        when(client.getMenuEntries()).thenReturn(new MenuEntry[]{npcEntry, widget});
        tool.onClientTick();
        assertNull(tool.reading().target);
        verifyNoInteractions(playerArea);
    }

    @Test public void openMenuAndPointerOutsideCanvasClearHover()
    {
        hoverEntry(npc);
        tool.onClientTick();
        when(client.isMenuOpen()).thenReturn(true);
        tool.onClientTick();
        assertNull(tool.reading().target);
        when(client.isMenuOpen()).thenReturn(false);
        when(client.getMouseCanvasPosition()).thenReturn(new Point(-1, -1));
        tool.onClientTick();
        assertNull(tool.reading().target);
    }

    @Test public void fallbackNpcIndexRequiresNpcActionAndMatchingWorldView()
    {
        MenuEntry entry = hoverEntry(null);
        when(entry.getWorldViewId()).thenReturn(13);
        when(entry.getIdentifier()).thenReturn(7);
        when(client.getWorldView(13)).thenReturn(view);
        tool.onClientTick();
        assertSame(npc, tool.reading().target);
        when(entry.getType()).thenReturn(MenuAction.GAME_OBJECT_SECOND_OPTION);
        tool.onClientTick();
        assertNull(tool.reading().target);
    }

    @Test public void hoverDoesNotReplaceManualChoiceAndDisablingItRestoresManualMode()
    {
        tool.select(npc);
        MenuEntry entry = hoverEntry(null);
        when(entry.getType()).thenReturn(MenuAction.WALK);
        tool.onClientTick();
        assertNull(tool.reading().target);
        when(config.hoverNpcSight()).thenReturn(false);
        tool.onClientTick();
        assertSame(npc, tool.reading().target);
        assertFalse(tool.isHoverReading());
    }

    @Test public void topWalkStillInspectsTheDeprioritizedNpcBeneathIt()
    {
        MenuEntry attack = hoverEntry(npc);
        when(attack.isDeprioritized()).thenReturn(true);
        MenuEntry walk = mock(MenuEntry.class);
        when(walk.getType()).thenReturn(MenuAction.WALK);
        when(client.getMenuEntries()).thenReturn(new MenuEntry[]{attack, walk});
        tool.onClientTick();
        assertSame(npc, tool.reading().target);
        assertTrue(tool.isHoverReading());
    }

}
