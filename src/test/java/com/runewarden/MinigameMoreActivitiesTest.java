package com.runewarden;

import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinigameMoreActivitiesTest
{
    @Test public void lobbiesAndPostgameWindowsDoNotActivateLiveCoaches()
    {
        Fixture f = new Fixture(); f.show(InterfaceID.SoulWarsLobby.UNIVERSE, "Waiting");
        f.show(InterfaceID.BrewWaitingRoomOverlay.UNIVERSE, "Waiting"); f.show(InterfaceID.CastlewarsWaitingroom.UNIVERSE, "Waiting");
        f.show(InterfaceID.SoulWarsGameEnd.UNIVERSE, "Results"); f.show(InterfaceID.BrewGameOver.BREW_BACKING_SCROLL, "Results");
        assertNull(f.detect().activity);
    }
    @Test public void soulWarsUsesActualHudTextAndCarriedFragmentTransition()
    {
        Fixture f = new Fixture(); f.show(InterfaceID.SoulWarsGame.CONTENT_MAIN, ""); f.show(InterfaceID.SoulWarsGame.TIME_LEFT, "8:14");
        f.show(InterfaceID.SoulWarsGame.BLUE_HEALTH, "<col=00ffff>75%</col>"); f.inventory(new Item(ItemID.SOUL_FRAGMENT, 3));
        MinigameActivityCatalog.Snapshot view = f.detect(); assertEquals(MinigameActivityCatalog.Activity.SOUL_WARS, view.activity);
        assertEquals("Take fragments to the obelisk", view.steps.get(0).title);
        assertTrue(view.facts.stream().anyMatch(row -> row.label.equals("Blue avatar HP") && row.value.equals("75%")));
        assertTrue(view.steps.get(0).instruction.contains("once your team controls"));
        f.inventory(); assertEquals("Restock at your base", f.detect().steps.get(0).title);
        f.inventory(new Item(ItemID.SOUL_WARS_BANDAGES, 10)); assertEquals("Capture and contribute", f.detect().steps.get(0).title);
    }
    @Test public void troubleBrewingTransitionsFromGatheringToProcessingToDepositing()
    {
        Fixture f = new Fixture(); f.show(InterfaceID.BrewOverlay.CONTENT_TOP, ""); f.show(InterfaceID.BrewOverlay.BUCKET_COUNT, "19");
        f.inventory(); assertEquals("Gather scrapey tree logs", f.detect().steps.get(0).title);
        assertTrue(f.detect().highlightObjectIds.contains(ObjectID.BREW_SCRAPEY_TREE));
        f.inventory(new Item(ItemID.BREW_SCRAPEY_LOGS, 5)); assertEquals("Collect a knife", f.detect().steps.get(0).title);
        f.inventory(new Item(ItemID.BREW_SCRAPEY_LOGS, 5), new Item(ItemID.KNIFE, 1)); assertEquals("Cut logs into bark", f.detect().steps.get(0).title);
        f.inventory(new Item(ItemID.BREW_SCRAPEY_BARK, 5)); assertEquals("Deposit your ingredients", f.detect().steps.get(0).title);
        assertTrue(f.detect().facts.stream().anyMatch(row -> row.label.equals("Water") && row.value.equals("19")));
        assertTrue(f.detect().highlightObjectIds.isEmpty()); // The other team's hoppers are not guessed targets.
    }
    @Test public void enemyAndFriendlyEquippedCastleStandardsHaveDifferentInstructions()
    {
        Fixture f = new Fixture(); f.show(InterfaceID.CastlewarsStatusOverlaySaradomin.HUD, "");
        f.equipment(new Item(ItemID.CASTLEWARS_ZAMORAK_BANNER, 1));
        assertEquals("Return the enemy standard", f.detect().steps.get(0).title);
        assertEquals(java.util.Set.of(ObjectID.CASTLEWARS_SARADOMIN_BANNER_STAND), f.detect().highlightObjectIds);
        f.equipment(new Item(ItemID.CASTLEWARS_SARADOMIN_BANNER, 1));
        assertEquals("Protect your standard", f.detect().steps.get(0).title); assertTrue(f.detect().highlightObjectIds.isEmpty());
        f.equipment(); f.inventory(new Item(ItemID.CASTLEWARS_BANDAGES, 4));
        assertEquals("Attack or defend with your team", f.detect().steps.get(0).title);
    }
    @Test public void ambiguousCastleTeamHudDoesNotChooseAFlag()
    {
        Fixture f = new Fixture(); f.show(InterfaceID.CastlewarsStatusOverlaySaradomin.HUD, ""); f.show(InterfaceID.CastlewarsStatusOverlayZamorak.HUD, "");
        assertNull(f.detect().activity);
    }
    @Test public void driftNetRequiresActualInstancedTemplateArea()
    {
        Fixture f = new Fixture(); when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3744, 10296, 1));
        assertNull(f.detect().activity); f.driftArea(); assertEquals(MinigameActivityCatalog.Activity.DRIFT_NET, f.detect().activity);
        when(f.client.isInInstancedRegion()).thenReturn(false); assertNull(f.detect().activity);
    }
    @Test public void driftFullUnsetAndActiveNetsUseNativeStatesAndRealReplacementCounts()
    {
        Fixture f = new Fixture(); f.driftArea();
        when(f.client.getVarbitValue(VarbitID.FOSSIL_DRIFT_NET1)).thenReturn(3);
        when(f.client.getVarbitValue(VarbitID.FOSSIL_DRIFT_NET1_CATCH)).thenReturn(10);
        assertEquals("Empty net 1", f.detect().steps.get(0).title);
        assertEquals(java.util.Set.of(ObjectID.FOSSIL_DRIFT_NET1_MULTI), f.detect().highlightObjectIds);
        when(f.client.getVarbitValue(VarbitID.FOSSIL_DRIFT_NET1)).thenReturn(1);
        assertEquals("Get replacement drift nets", f.detect().steps.get(0).title);
        f.inventory(new Item(ItemID.FOSSIL_DRIFT_NET, 2)); assertEquals("Set net 2", f.detect().steps.get(0).title);
        when(f.client.getVarbitValue(VarbitID.FOSSIL_DRIFT_NET2)).thenReturn(2); assertEquals("Herd fish shoals", f.detect().steps.get(0).title);
    }
    @Test public void missingOrUnknownInstanceChunksStayUnknownAndClearingHudExits()
    {
        Fixture f = new Fixture(); f.driftArea(); when(f.view.getInstanceTemplateChunks()).thenReturn(null); assertNull(f.detect().activity);
        Widget hud = f.show(InterfaceID.SoulWarsGame.CONTENT_MAIN, ""); assertNotNull(f.detect().activity);
        when(hud.isHidden()).thenReturn(true); assertNull(f.detect().activity);
    }
    @Test public void expandedSupportIdsResolveToExistingBundledGuides()
    {
        GuideCatalog guides = new GuideCatalog(new com.google.gson.Gson());
        for (MinigameActivityCatalog.Snapshot view : MinigameMoreActivities.supportedActivities())
            assertNotNull(view.guideId, guides.entry(view.guideId));
        assertEquals(20, new MinigameActivityCatalog().supportedActivities().size());
    }
    static final class Fixture
    {
        final Client client = mock(Client.class); final Player player = mock(Player.class); WorldView view;
        Fixture()
        { when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player); when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0)); }
        Widget show(int id, String text)
        { Widget widget = mock(Widget.class); when(client.getWidget(id)).thenReturn(widget); when(widget.getText()).thenReturn(text); return widget; }
        void inventory(Item... items) { container(InventoryID.INV, items); }
        void equipment(Item... items) { container(InventoryID.WORN, items); }
        void container(int id, Item[] items)
        { ItemContainer container = mock(ItemContainer.class); when(container.getItems()).thenReturn(items); when(client.getItemContainer(id)).thenReturn(container); }
        void driftArea()
        {
            view = mock(WorldView.class); when(view.getPlane()).thenReturn(1); when(view.isInstance()).thenReturn(true);
            when(client.isInInstancedRegion()).thenReturn(true); when(client.getWorldView(0)).thenReturn(view);
            LocalPoint local = new LocalPoint(64, 64, view); when(player.getLocalLocation()).thenReturn(local);
            int[][][] chunks = new int[4][13][13]; chunks[1][0][0] = 1 << 24 | (3744 / 8) << 14 | (10296 / 8) << 3;
            when(view.getInstanceTemplateChunks()).thenReturn(chunks);
        }
        MinigameActivityCatalog.Snapshot detect() { return MinigameMoreActivities.detect(client); }
    }
}
