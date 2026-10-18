/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.HashSet;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinigameExtraActivitiesTest
{
    @Test public void nightmareRegionDoesNotConfuseTheKingBlackDragonLairOrWiderLoadedScene()
    {
        Fixture f = new Fixture(9033, 0);
        when(f.client.getMapRegions()).thenReturn(new int[] {9033});
        assertNull(f.detect().activity);
        f.location(9033, 1);
        when(f.client.getVarbitValue(VarbitID.NZONE_CURRENTPOINTS)).thenReturn(1234);
        when(f.client.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS)).thenReturn(400);
        when(f.client.getVarpValue(VarPlayerID.NZONE_REWARDPOINTS)).thenReturn(9000);
        MinigameActivityCatalog.Snapshot dream = f.detect();
        assertEquals(MinigameActivityCatalog.Activity.NIGHTMARE_ZONE, dream.activity);
        assertEquals("1234", fact(dream, "Dream points")); assertEquals("400", fact(dream, "Absorption"));
        assertEquals("9000", fact(dream, "Banked points")); assertTrue(dream.steps.get(0).instruction.contains("absorption"));
        assertEquals("training:nightmare-zone-absorptions", dream.guideId);
        when(f.client.getVarbitValue(VarbitID.NZONE_ABSORB_POTION_EFFECTS)).thenReturn(0);
        assertNull(f.detect().guideId); // Zero absorption cannot identify a prayer/food/absorption training method.
        when(f.client.getMapRegions()).thenReturn(new int[] {9033, 9034});
        assertNull(f.detect().activity);
    }

    @Test public void pyramidNeedsBothItsRegionAndAnObservedRunningTimerThenChangesItsNextStep()
    {
        Fixture f = new Fixture(7749, 0); assertNull(f.detect().activity);
        when(f.client.getVarbitValue(VarbitID.NTK_PLAYER_TIMER_COUNT)).thenReturn(75);
        when(f.client.getVarbitValue(VarbitID.NTK_ROOM_NUMBER)).thenReturn(3);
        when(f.client.getVarbitValue(VarbitID.NTK_THIEVING_REQUIRED)).thenReturn(41);
        when(f.client.getVarbitValue(VarbitID.NTK_TRAP_ACTIVE)).thenReturn(1);
        f.widget(InterfaceID.NtkOverlay.TIMER_TEXT1, "<col=ffffff>4:15</col>");
        MinigameActivityCatalog.Snapshot inside = f.detect();
        assertEquals(MinigameActivityCatalog.Activity.PYRAMID_PLUNDER, inside.activity);
        assertEquals("3", fact(inside, "Room")); assertEquals("41 Thieving", fact(inside, "Room requirement"));
        assertEquals("4:15", fact(inside, "Timer")); assertEquals("Pass the spear trap", inside.steps.get(0).title);
        when(f.client.getVarbitValue(VarbitID.NTK_TRAP_ACTIVE)).thenReturn(0);
        assertEquals("Loot or move to the next room", f.detect().steps.get(0).title);
        f.location(7748, 0); assertNull(f.detect().activity);
    }

    @Test public void mageTrainingRoomUsesOfficialPlaneMappingAndCompleteNativeAlchemyRows()
    {
        Fixture f = new Fixture(13462, 0);
        assertEquals("Enchanting Chamber", fact(f.detect(), "Room"));
        f.location(13462, 1); assertEquals("Creature Graveyard", fact(f.detect(), "Room"));
        f.location(13462, 2);
        String[] names = {"Leather boots", "Adamant helm", "Adamant kiteshield", "Emerald", "Rune longsword"};
        for (int i = 0; i < names.length; ++i)
        {
            f.widget(InterfaceID.MAGICTRAINING_ALCHEM << 16 | 7 + i, names[i]);
            f.widget(InterfaceID.MAGICTRAINING_ALCHEM << 16 | 12 + i, i == 3 ? "<col=ffff00>30</col>" : "5");
        }
        MinigameActivityCatalog.Snapshot alchemy = f.detect();
        assertEquals("Alchemist's Playground", fact(alchemy, "Room")); assertEquals("Emerald", fact(alchemy, "30-coin item"));
        assertEquals("Alchemise Emerald", alchemy.steps.get(0).title);
        f.widget(InterfaceID.MAGICTRAINING_ALCHEM << 16 | 12, "30");
        assertNull(fact(f.detect(), "30-coin item")); // Conflicting or partially updated native tables are not guessed.
        f.widget(InterfaceID.MAGICTRAINING_ALCHEM << 16 | 12, "?");
        assertEquals("Check the current alchemy values", f.detect().steps.get(0).title);
        f.location(13462, 3); assertNull(f.detect().activity);
    }

    @Test public void telekineticNeedsVisibleNativeInterfaceAndDoesNotClaimToSolveTheMaze()
    {
        Fixture f = new Fixture(12850, 0);
        Widget root = f.widget(InterfaceID.MagictrainingTele.UNIVERSE, "");
        f.widget(InterfaceID.MagictrainingTele.MAGICTRAIN_MAZE_NUMBER, "Maze: 7");
        MinigameActivityCatalog.Snapshot reading = f.detect();
        assertEquals(MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA, reading.activity);
        assertEquals("Telekinetic Theatre", fact(reading, "Room")); assertEquals("Maze: 7", fact(reading, "Maze"));
        assertTrue(reading.referenceTiles.isEmpty());
        when(root.isHidden()).thenReturn(true); assertNull(f.detect().activity);
    }

    @Test public void volcanicFactsAreTheCurrentNativeStringsWithoutTimerOrPressureConversions()
    {
        Fixture f = new Fixture(1234, 0);
        f.widget(InterfaceID.FossilVolcanicMine.UNIVERSE, "");
        f.widget(InterfaceID.FossilVolcanicMine.TIME, "02:34");
        f.widget(InterfaceID.FossilVolcanicMine.STABILITY, "<col=00ff00>71%</col>");
        f.widget(InterfaceID.FossilVolcanicMine.VENT_A, "A: 42%");
        f.widget(InterfaceID.FossilVolcanicMine.CHAMBER_A, "Blocked");
        MinigameActivityCatalog.Snapshot reading = f.detect();
        assertEquals(MinigameActivityCatalog.Activity.VOLCANIC_MINE, reading.activity);
        assertEquals("02:34", fact(reading, "Time")); assertEquals("71%", fact(reading, "Stability"));
        assertEquals("A: 42%", fact(reading, "Vent A")); assertEquals("Blocked", fact(reading, "Chamber A"));
        assertNull(fact(reading, "Vent B"));
        assertTrue(reading.highlightObjectIds.contains(ObjectID.FOSSIL_MINING_BOULDER_GIANT));
        assertTrue(reading.referenceTiles.isEmpty());
    }

    @Test public void blastStoredOreSurvivesNativeDataChildHiddenByTheBuiltInOverlay()
    {
        Fixture f = new Fixture(1234, 0);
        f.widget(InterfaceID.LovakengjBlastMiningHud.UNIVERSE, "");
        Widget data = f.widget(InterfaceID.LovakengjBlastMiningHud.DATA, ""); when(data.isHidden()).thenReturn(true);
        when(f.client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_COAL_BIGGER)).thenReturn(140);
        when(f.client.getVarbitValue(VarbitID.LOVAKENGJ_ORE_RUNITE_BIGGER)).thenReturn(9);
        MinigameActivityCatalog.Snapshot reading = f.detect();
        assertEquals(MinigameActivityCatalog.Activity.BLAST_MINE, reading.activity);
        assertEquals("140", fact(reading, "Coal stored")); assertEquals("9", fact(reading, "Runite stored"));
        assertFalse(reading.highlightObjectIds.contains(ObjectID.BLAST_MINING_WALL_BURNING_01));
    }

    @Test public void trawlerNeedsItsCurrentInterfaceRatherThanStaleActivityVarbits()
    {
        Fixture f = new Fixture(1234, 0);
        when(f.client.getVarbitValue(VarbitID.TRAWLER_ACTIVITY)).thenReturn(255); assertNull(f.detect().activity);
        f.widget(InterfaceID.TrawlerOverlay.UNIVERSE, "");
        f.widget(InterfaceID.TrawlerOverlay.TIME, "Time left: 3:02");
        f.widget(InterfaceID.TrawlerOverlay.WATER, "Water: 12%");
        f.widget(InterfaceID.TrawlerOverlay.NET, "Net: intact");
        MinigameActivityCatalog.Snapshot reading = f.detect();
        assertEquals(MinigameActivityCatalog.Activity.FISHING_TRAWLER, reading.activity);
        assertEquals("Time left: 3:02", fact(reading, "Time")); assertEquals("Water: 12%", fact(reading, "Water"));
        assertEquals("Net: intact", fact(reading, "Net"));
        when(f.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); assertNull(f.detect().activity);
    }

    @Test public void supportRegistryAndEvidenceIdsAreCompleteImmutableAndDoNotInventReferenceTiles()
    {
        Set<MinigameActivityCatalog.Activity> activities = new HashSet<>();
        for (MinigameActivityCatalog.Snapshot definition : MinigameExtraActivities.supportedActivities())
        {
            assertTrue(activities.add(definition.activity)); assertTrue(definition.liveSupported);
            assertFalse(definition.steps.isEmpty()); assertNotNull(definition.guideId);
            assertTrue(MinigameExtraActivities.allHighlightObjectIds().containsAll(definition.highlightObjectIds));
            assertTrue(definition.referenceTiles.isEmpty());
        }
        assertEquals(6, activities.size());
        try { MinigameExtraActivities.allHighlightObjectIds().add(999999); fail("immutable evidence registry"); }
        catch (UnsupportedOperationException expected) { }
        assertNull(MinigameExtraActivities.detect(null).activity);
    }

    private static String fact(MinigameActivityCatalog.Snapshot snapshot, String label)
    {
        for (MinigameActivityCatalog.Fact fact : snapshot.facts) if (label.equals(fact.label)) return fact.value;
        return null;
    }
    private static final class Fixture
    {
        final Client client = mock(Client.class); final Player player = mock(Player.class);
        Fixture(int region, int plane)
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
            location(region, plane);
        }
        void location(int region, int plane)
        { when(player.getWorldLocation()).thenReturn(new WorldPoint((region >> 8) * 64 + 30, (region & 255) * 64 + 30, plane)); }
        Widget widget(int id, String text)
        { Widget w = mock(Widget.class); when(w.getText()).thenReturn(text); when(client.getWidget(id)).thenReturn(w); return w; }
        MinigameActivityCatalog.Snapshot detect() { return MinigameExtraActivities.detect(client); }
    }
}
