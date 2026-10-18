/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class BossGuideLifecycleTest
{
    @Test public void manualGuideRemainsOpenBeforeArenaEntryAndClosesAfterRealInstanceExit()
    {
        Fixture f = new Fixture("waves:inferno");
        f.world(3200, 3200); assertFalse(f.tick());
        f.world(2438, 5167); assertFalse(f.tick()); // Walking to an entrance is not a completed run.
        f.instanceSource(9043, 0); assertFalse(f.tick());
        when(f.client.getGameState()).thenReturn(GameState.LOADING); assertFalse(f.tick());
        when(f.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(f.view.isInstance()).thenReturn(false); f.world(2440, 5170); assertTrue(f.tick());
    }

    @Test public void firstWaveAndAllFightCavesGuideVariantsUseTheNativePlayerTemplateNotLoadedNeighbourRegions()
    {
        for (String id : new String[]{"boss:fight-caves", "encounter:fight-caves", "encounter:tztok-jad", "waves:fight-caves:15"})
        {
            Fixture f = new Fixture(id); when(f.view.getMapRegions()).thenReturn(new int[]{9551, 9808});
            f.instanceSource(9808, 0); assertFalse(id, f.tick());
            f.instanceSource(9551, 0); assertFalse(id, f.tick());
            f.instanceSource(9808, 0); assertTrue(id, f.tick());
        }
    }

    @Test public void npcDespawnOrDeathBetweenKillsDoesNotDismissTheSelectedBoss()
    {
        Fixture f = new Fixture("encounter:scurrius"); f.world(3220, 3220); f.target("Scurrius"); assertFalse(f.tick());
        when(f.player.getInteracting()).thenReturn(null); assertFalse(f.tick()); assertFalse(f.tick());
        f.world(3300, 3200); assertTrue(f.tick());
    }

    @Test public void nativeGauntletMembershipCoversPreparationAndClosesOnExit()
    {
        for (String id : new String[]{"encounter:the-gauntlet", "encounter:the-corrupted-gauntlet", "encounter:crystalline-hunllef", "encounter:corrupted-hunllef"})
        {
            Fixture f = new Fixture(id); assertFalse(f.tick());
            when(f.client.getVarbitValue(VarbitID.PLAYER_IN_GAUNTLET)).thenReturn(1); assertFalse(f.tick());
            f.world(3280, 6000); assertFalse(f.tick()); // Procedural room changes do not end the activity.
            when(f.client.getVarbitValue(VarbitID.PLAYER_IN_GAUNTLET)).thenReturn(0); assertTrue(f.tick());
        }
    }

    @Test public void closedNativeChamberDetectsARealDoorExitWithinTheSameRegion()
    {
        Fixture f = new Fixture("encounter:scurrius");
        f.room(12, 12, 24, 24); f.world(3220, 3220); f.target("Scurrius"); assertFalse(f.tick());
        when(f.player.getInteracting()).thenReturn(null);
        f.world(3222, 3220); assertFalse(f.tick());
        f.world(3225, 3220); assertTrue(f.tick());
    }

    @Test public void passiveEntryAndDoorExitDoNotRequireAttackingTheBoss()
    {
        Fixture f = new Fixture("encounter:scurrius"); f.room(12, 12, 24, 24);
        f.world(3220, 3220); when(f.boss.getName()).thenReturn("Scurrius"); when(f.boss.getWorldLocation()).thenReturn(new WorldPoint(3222, 3220, 0));
        @SuppressWarnings("unchecked") net.runelite.api.IndexedObjectSet<NPC> npcs = mock(net.runelite.api.IndexedObjectSet.class);
        doReturn(npcs).when(f.view).npcs(); when(npcs.iterator()).thenAnswer(call -> java.util.List.of(f.boss).iterator());
        assertNull(f.player.getInteracting()); assertFalse(f.tick());
        f.world(3225, 3220); assertTrue(f.tick());
    }

    @Test public void wholeRaidMembershipRetainsLessonsAcrossRoomsAndOnlyClosesOnLeaving()
    {
        for (String id : new String[]{"encounter:chambers-of-xeric", "encounter:chambers-of-xeric-challenge-mode", "encounter:theatre-of-blood", "encounter:theatre-of-blood-hard-mode"})
        {
            Fixture f = new Fixture(id); int varbit = id.contains("chambers") ? VarbitID.RAIDS_CLIENT_INDUNGEON : VarbitID.TOB_CLIENT_PARTYSTATUS;
            int inside = id.contains("chambers") ? 1 : 2;
            assertFalse(f.tick()); when(f.client.getVarbitValue(varbit)).thenReturn(inside); assertFalse(f.tick());
            f.world(3300, 3300); assertFalse(f.tick());
            when(f.client.getVarbitValue(varbit)).thenReturn(0); assertTrue(f.tick());
        }
    }

    @Test public void outsideClosedDoorCannotBeMistakenForTheBossRoom()
    {
        Fixture f = new Fixture("encounter:scurrius"); f.room(12, 12, 24, 24);
        f.world(3226, 3220); f.target("Scurrius", new WorldPoint(3220, 3220, 0));
        assertFalse(f.tick()); f.world(3227, 3220); assertFalse(f.tick());
        f.world(3300, 3300); assertFalse("Never entered: teleporting away keeps the preparation guide", f.tick());
        assertTrue(BossGuideLifecycle.closedChamber(f.view, new LocalPoint((26 << 7) + 64, (20 << 7) + 64), new WorldPoint(3220, 3220, 0)).isEmpty());
    }

    @Test public void openOutdoorCollisionNeverInventsARadiusOrSceneEdgeArena()
    {
        Fixture f = new Fixture("encounter:chaos-elemental"); f.room(0, 0, 103, 103);
        f.world(3220, 3220); f.target("Chaos Elemental"); assertFalse(f.tick());
        assertTrue(BossGuideLifecycle.closedChamber(f.view, f.player.getLocalLocation(), f.boss.getWorldLocation()).isEmpty());
        when(f.player.getInteracting()).thenReturn(null); f.world(3250, 3220); assertFalse(f.tick());
        f.world(3300, 3220); assertTrue(f.tick());
    }

    @Test public void incompleteInstanceChunksAreUnknownRatherThanExitEvidence()
    {
        Fixture f = new Fixture("waves:colosseum"); f.instanceSource(7216, 0); assertFalse(f.tick());
        when(f.view.getInstanceTemplateChunks()).thenReturn(null); assertFalse(f.tick());
        when(f.view.getInstanceTemplateChunks()).thenReturn(new int[0][][]); assertFalse(f.tick());
        f.instanceSource(9043, 0); assertTrue(f.tick());
    }

    @Test public void sotetsegMazeTemplateDoesNotDismissTheLessonButLeavingTheNativeRaidDoes()
    {
        Fixture f = new Fixture("encounter:sotetseg");
        when(f.client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(2);
        f.instanceSource(12613, 0); assertFalse("Preparing at an earlier boss does not arm Sotetseg", f.tick());
        f.instanceSource(13123, 0); assertFalse(f.tick());
        f.instanceSource(13379, 3); assertFalse("An internal source-template change is not an arena exit", f.tick());
        when(f.client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(1); assertTrue(f.tick());
        when(f.client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS)).thenReturn(2);
        f.instanceSource(12612, 1); assertTrue("Entering the sourced Xarpus room ends the Sotetseg lesson", f.tick());
    }

    @Test public void tombsWholeRaidUsesTheApprovedNativeRoomAndWidgetContractWithTransitionGrace()
    {
        for (String id : new String[]{"encounter:tombs-of-amascut", "encounter:tombs-of-amascut-expert-mode"})
        {
            Fixture f = new Fixture(id); assertFalse(f.tick());
            f.instanceSource(14160, 0); assertFalse(f.tick()); // Nexus arms before any boss is attacked.
            f.instanceSource(15700, 0); assertFalse(f.tick());
            f.instanceSource(13454, 0); assertFalse(f.tick()); assertFalse(f.tick());
            f.instanceSource(14676, 1); assertFalse("Two transitional ticks cannot end a live raid", f.tick());
            f.instanceSource(14672, 0); assertFalse("Reward tomb remains part of this raid", f.tick());
            when(f.view.isInstance()).thenReturn(false); f.world(3200, 3200);
            net.runelite.api.widgets.Widget status = mock(net.runelite.api.widgets.Widget.class);
            when(f.client.getWidget(481, 40)).thenReturn(status); assertFalse("Visible native raid status is independent evidence", f.tick());
            when(status.isHidden()).thenReturn(true); assertFalse(f.tick()); assertFalse(f.tick()); assertTrue(f.tick());
        }
    }

    @Test public void zebakBossGuideDoesNotArmInItsPuzzleMarkerRegion()
    {
        Fixture f = new Fixture("encounter:zebak"); f.instanceSource(15698, 0); assertFalse(f.tick());
        f.instanceSource(13454, 0); assertFalse("Leaving a preparation puzzle is not leaving the boss arena", f.tick());
        f.instanceSource(15700, 0); assertFalse(f.tick());
        f.instanceSource(14160, 0); assertTrue(f.tick());
    }

    @Test public void replacingOrClearingABossGuideDisarmsThePreviousArena()
    {
        Fixture f = new Fixture("waves:inferno"); f.instanceSource(9043, 0); assertFalse(f.tick());
        f.guide = f.catalog.entry("waves:colosseum"); f.world(3200, 3200); when(f.view.isInstance()).thenReturn(false); assertFalse(f.tick());
        f.instanceSource(7216, 0); assertFalse(f.tick());
        f.lifecycle.reset(); when(f.view.isInstance()).thenReturn(false); f.world(3200, 3200); assertFalse(f.tick());
        assertFalse(f.lifecycle.leftArena(f.catalog.entry("training:giants-foundry")));
    }

    @Test public void actualGuideExitClearsCoachMarkersAndInvalidatesQueuedRefreshes() throws Exception
    {
        Fixture f = new Fixture("waves:inferno"); RunewardenGuides guides = f.guides();
        SwingUtilities.invokeAndWait(() -> { guides.createPanel(); guides.openGuide("waves:inferno"); });
        assertNotNull(f.markers.selectedPack()); assertNotNull(guides.selectedGuide());
        f.instanceSource(9043, 0); guides.onGameTick();
        when(f.view.isInstance()).thenReturn(false); f.world(3200, 3200); guides.onGameTick();
        assertNull(guides.selectedGuide()); assertNull(f.markers.selectedPack());
        SwingUtilities.invokeAndWait(() -> assertNull(guides.currentStep()));
    }

    @Test public void actualLocalPlayerDeathDismissesOnlyBossGuides() throws Exception
    {
        Fixture f = new Fixture("waves:inferno"); RunewardenGuides guides = f.guides();
        SwingUtilities.invokeAndWait(() -> { guides.createPanel(); guides.openGuide("waves:inferno"); });
        guides.onActorDeath(new net.runelite.api.events.ActorDeath(f.boss)); assertNotNull(guides.selectedGuide());
        guides.onActorDeath(new net.runelite.api.events.ActorDeath(f.player)); assertNull(guides.selectedGuide());
        SwingUtilities.invokeAndWait(() -> guides.openGuide("training:giants-foundry"));
        guides.onActorDeath(new net.runelite.api.events.ActorDeath(f.player)); assertNotNull(guides.selectedGuide());
    }

    private static final class Fixture
    {
        final Client client = mock(Client.class); final Player player = mock(Player.class); final NPC boss = mock(NPC.class);
        final WorldView view = mock(WorldView.class); final Scene scene = mock(Scene.class);
        final GuideCatalog catalog = new GuideCatalog(new Gson()); final BossGuideLifecycle lifecycle = new BossGuideLifecycle(client, catalog);
        GuideCatalog.Entry guide; GuideMarkerOverlay markers;
        Fixture(String id)
        {
            guide = catalog.entry(id); assertNotNull(id, guide);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
            when(client.getTopLevelWorldView()).thenReturn(view); when(view.getScene()).thenReturn(scene);
            when(view.getBaseX()).thenReturn(3200); when(view.getBaseY()).thenReturn(3200);
            world(3200, 3200); room(0, 0, 103, 103);
        }
        boolean tick() { return lifecycle.leftArena(guide); }
        void world(int x, int y)
        {
            when(player.getWorldLocation()).thenReturn(new WorldPoint(x, y, 0));
            when(player.getLocalLocation()).thenReturn(new LocalPoint(((x - 3200) << 7) + 64, ((y - 3200) << 7) + 64));
        }
        void target(String name) { target(name, player.getWorldLocation()); }
        void target(String name, WorldPoint point)
        { when(boss.getName()).thenReturn(name); when(boss.getWorldLocation()).thenReturn(point); when(player.getInteracting()).thenReturn(boss); }
        void instanceSource(int region, int plane)
        {
            int[][][] chunks = new int[4][13][13];
            for (int[][] level : chunks) for (int[] row : level) Arrays.fill(row, -1);
            int x = (region >> 8) * 8, y = (region & 255) * 8;
            chunks[0][0][0] = (plane << 24) | (x << 14) | (y << 3);
            when(view.isInstance()).thenReturn(true); when(scene.isInstance()).thenReturn(true);
            when(view.getInstanceTemplateChunks()).thenReturn(chunks); when(scene.getInstanceTemplateChunks()).thenReturn(chunks);
            when(player.getLocalLocation()).thenReturn(new LocalPoint(64, 64));
        }
        void room(int minX, int minY, int maxX, int maxY)
        {
            CollisionData map = mock(CollisionData.class); int[][] flags = new int[104][104];
            for (int[] row : flags) Arrays.fill(row, CollisionDataFlag.BLOCK_MOVEMENT_FULL);
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) flags[x][y] = 0;
            when(map.getFlags()).thenReturn(flags); when(view.getCollisionMaps()).thenReturn(new CollisionData[]{map});
        }
        RunewardenGuides guides()
        {
            ClientThread thread = mock(ClientThread.class); ConfigManager config = mock(ConfigManager.class);
            when(config.getRSProfileKey()).thenReturn("rsprofile.test"); when(client.getIntStack()).thenReturn(new int[]{0});
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
            GuideNavigation navigation = new GuideNavigation(client, mock(net.runelite.client.eventbus.EventBus.class),
                mock(net.runelite.client.plugins.PluginManager.class), null, config, null);
            markers = new GuideMarkerOverlay(client);
            return new RunewardenGuides(client, thread, config, catalog, navigation, markers, new GuideCoachOverlay(client), mock(OverlayManager.class));
        }
    }
}
