/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinigameActivityCatalogTest
{
    private MinigameActivityCatalog catalog;
    private Client client;
    private Player player;
    private WorldPoint position;

    @Before public void setUp()
    {
        catalog = new MinigameActivityCatalog(); client = mock(Client.class); player = mock(Player.class);
        position = new WorldPoint(3200,3200,0);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenAnswer(ignored -> position);
    }

    @Test public void beingNearAnActivityRegionWithoutAnActiveInterfaceDoesNotActivate()
    {
        when(client.getMapRegions()).thenReturn(new int[]{6462,14484,12076});
        assertSame(MinigameActivityCatalog.Snapshot.EMPTY,catalog.detect(client));
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        hud(396,2,"Waiting");
        assertSame(MinigameActivityCatalog.Snapshot.EMPTY,catalog.detect(client));
    }

    @Test public void hiddenDedicatedInterfaceDoesNotActivate()
    {
        Widget widget=hud(396,2,"Waiting"); when(widget.isHidden()).thenReturn(true);
        assertNull(catalog.detect(client).activity);
    }

    @Test public void wintertodtReadsNativeValuesAndRepairsBeforeFeeding()
    {
        hud(396,2,"Fighting"); hud(396,15,"<col=ffffff>82%</col>"); inventory(20696);
        GameObject broken=observe(29313,position);
        MinigameActivityCatalog.Snapshot snapshot=catalog.detect(client);
        assertEquals(MinigameActivityCatalog.Activity.WINTERTODT,snapshot.activity);
        assertEquals("82%",fact(snapshot,"Warmth")); assertEquals(Set.of(29313),snapshot.highlightObjectIds);
        catalog.onGameObjectDespawned(broken);
        assertEquals(Set.of(29314),catalog.detect(client).highlightObjectIds);
    }

    @Test public void wintertodtLightsAnUnlitBrazierBeforeFeedingIt()
    {
        hud(396,2,"Fighting"); inventory(20696); observe(29312,position);
        MinigameActivityCatalog.Snapshot snapshot=catalog.detect(client);
        assertEquals("Light the brazier",snapshot.steps.get(0).title);
        assertEquals(Set.of(29312),snapshot.highlightObjectIds);
    }

    @Test public void guardiansMiningNeverLabelsTheCellTableAsRemainsAndRespectsAgilityGate()
    {
        hud(746,2,""); when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(55);
        Set<Integer> low=catalog.detect(client).highlightObjectIds;
        assertTrue(low.contains(43717)); assertTrue(low.contains(43718));
        assertFalse(low.contains(43719)); assertFalse(low.contains(43720)); assertFalse(low.contains(43732));
        when(client.getRealSkillLevel(Skill.AGILITY)).thenReturn(56);
        assertTrue(catalog.detect(client).highlightObjectIds.contains(43719));
    }

    @Test public void hugeRemainsRequireBeingOnThePortalShelf()
    {
        hud(746,2,""); position=new WorldPoint(3595,9505,0); observe(43720,new WorldPoint(3592,9505,0));
        assertFalse(catalog.detect(client).highlightObjectIds.contains(43720));
        position=new WorldPoint(3591,9505,0);
        assertEquals(Set.of(43720),catalog.detect(client).highlightObjectIds);
    }

    @Test public void guardianInventoryMovesTheCoachFromFragmentsToEssenceToStones()
    {
        hud(746,2,""); inventory(26878);
        assertEquals(Set.of(43754),catalog.detect(client).highlightObjectIds);
        inventory(26879); assertEquals("Use an active altar",catalog.detect(client).steps.get(0).title);
        inventory(26880); assertEquals("Empower the Great Guardian",catalog.detect(client).steps.get(0).title);
    }

    @Test public void temporossDistinguishesRawCookedAndCrystallisedNativeItemIds()
    {
        hud(437,2,""); hud(437,19,"100%"); inventory(25564);
        assertEquals(Set.of(41236),catalog.detect(client).highlightObjectIds);
        inventory(25565); assertEquals("Load cooked harpoonfish",catalog.detect(client).steps.get(0).title);
        inventory(25566); assertEquals("Load cooked harpoonfish",catalog.detect(client).steps.get(0).title);
        hud(437,19,"0%"); assertEquals("Fish the spirit pool",catalog.detect(client).steps.get(0).title);
    }

    @Test public void nativeTetheredStateTakesPrecedenceOverFishingAdvice()
    {
        hud(437,2,""); when(client.getVarbitValue(11895)).thenReturn(1); inventory(25565);
        assertEquals("Remain tethered",catalog.detect(client).steps.get(0).title);
    }

    @Test public void foundryUsesTheNativeStageSpritesAndGaugeWidthForHeatAdvice()
    {
        hud(754,51,""); equippedPreform(); // Native mould state is zero after pickup.
        when(client.getVarbitValue(13949)).thenReturn(100);
        Widget bar=hud(754,75,""), hammer=mock(Widget.class);
        when(hammer.getSpriteId()).thenReturn(4442); when(bar.getChildren()).thenReturn(new Widget[]{null,hammer});
        when(hud(754,9,"").getWidth()).thenReturn(300);
        when(hud(754,20,"").getWidth()).thenReturn(60);
        when(client.getVarbitValue(13948)).thenReturn(400);
        assertEquals(Set.of(44631),catalog.detect(client).highlightObjectIds);
        when(client.getVarbitValue(13948)).thenReturn(830);
        assertEquals(Set.of(44619),catalog.detect(client).highlightObjectIds);
        when(client.getVarbitValue(13948)).thenReturn(990);
        assertEquals(Set.of(44632),catalog.detect(client).highlightObjectIds);
    }

    @Test public void foundryDoesNotInventHeatThresholdWhenNativeGaugeIsMissing()
    {
        hud(754,51,""); equippedPreform(); // A missing gauge must not erase an equipped preform.
        assertEquals(Set.of(44619,44620,44621),catalog.detect(client).highlightObjectIds);
        when(client.getVarbitValue(13949)).thenReturn(1000);
        assertEquals("Return the sword",catalog.detect(client).steps.get(0).title);
    }

    @Test public void mixologyUsesNativeOneBasedOrderAndModifierAndCorrectItemMapping()
    {
        hud(882,2,""); when(client.getVarbitValue(11315)).thenReturn(6);
        when(client.getVarbitValue(11316)).thenReturn(3);
        assertTrue(catalog.detect(client).steps.get(0).instruction.startsWith("Aga + Aga + Lye"));
        inventory(30015);
        MinigameActivityCatalog.Snapshot processing=catalog.detect(client);
        assertEquals(Set.of(55391),processing.highlightObjectIds);
        assertEquals(new WorldPoint(1391,9325,0),processing.referenceTiles.get(0));
        inventory(30025);
        assertEquals(Set.of(54917),catalog.detect(client).highlightObjectIds);
    }

    @Test public void invalidMixologyOrderRemainsASetupStepWithoutInventedRecipe()
    {
        hud(882,2,""); when(client.getVarbitValue(11315)).thenReturn(99);
        assertEquals("Prepare the mixing station",catalog.detect(client).steps.get(0).title);
    }

    @Test public void homesRequireNativeAssignmentAndAnActualContractHouse()
    {
        position=new WorldPoint(3238,3486,0); observe(39981,position); action(39981,"Remove");
        assertNull(catalog.detect(client).activity);
        when(client.getVarpValue(2747)).thenReturn(1); when(client.getVarbitValue(10554)).thenReturn(3);
        MinigameActivityCatalog.Snapshot snapshot=catalog.detect(client);
        assertEquals(MinigameActivityCatalog.Activity.MAHOGANY_HOMES,snapshot.activity);
        assertEquals("Bob",fact(snapshot,"House"));
        assertEquals("Remove damaged furniture",snapshot.steps.get(0).title);
        assertTrue(snapshot.highlightObjectIds.contains(39981));
        position=new WorldPoint(3245,3486,0); assertNull(catalog.detect(client).activity);
    }

    @Test public void homesFurnitureBitsInAnotherHouseAreInsufficientWithoutTheActualNativeAction()
    {
        position=new WorldPoint(3238,3486,0); observe(39981,position);
        when(client.getVarpValue(2747)).thenReturn(1); when(client.getVarbitValue(10554)).thenReturn(3);
        assertNull(catalog.detect(client).activity);
        action(39981,"Examine"); assertNull(catalog.detect(client).activity);
        action(39981,"Remove"); assertEquals(MinigameActivityCatalog.Activity.MAHOGANY_HOMES,catalog.detect(client).activity);
        when(client.getVarbitValue(10554)).thenReturn(6);
        assertNull(catalog.detect(client).activity);
    }

    @Test public void pestControlReadsTheNativeKnightAndTimeFields()
    {
        hud(408,14,"200"); hud(408,6,"190"); hud(408,5,"12:30");
        MinigameActivityCatalog.Snapshot snapshot=catalog.detect(client);
        assertEquals(MinigameActivityCatalog.Activity.PEST_CONTROL,snapshot.activity);
        assertEquals("190",fact(snapshot,"Void Knight")); assertEquals("12:30",fact(snapshot,"Time"));
    }

    @Test public void blastFurnaceUsesOnlyNearbyUniqueStationEvidenceAndReadyBars()
    {
        GameObject belt=observe(9100,position); when(client.getVarbitValue(947)).thenReturn(27);
        MinigameActivityCatalog.Snapshot snapshot=catalog.detect(client);
        assertEquals(MinigameActivityCatalog.Activity.BLAST_FURNACE,snapshot.activity);
        assertEquals("27",fact(snapshot,"Bars in dispenser")); assertTrue(snapshot.highlightObjectIds.contains(9092));
        catalog.onGameObjectDespawned(belt); assertNull(catalog.detect(client).activity);
    }

    @Test public void titheNeedsAnInstanceAndPrioritisesObservedDryPlants()
    {
        observe(27384,position); observe(27393,new WorldPoint(3201,3200,0));
        assertNull(catalog.detect(client).activity);
        when(client.isInInstancedRegion()).thenReturn(true);
        MinigameActivityCatalog.Snapshot snapshot=catalog.detect(client);
        assertEquals(MinigameActivityCatalog.Activity.TITHE_FARM,snapshot.activity);
        assertEquals("Water dry plants",snapshot.steps.get(0).title);
        assertEquals("1 dry · 1 grown · 0 dead",fact(snapshot,"Nearby plants"));
        catalog.clearScene(); assertNull(catalog.detect(client).activity);
    }

    @Test public void decorativeTitheSceneryNeverActivatesTheCoach()
    {
        when(client.isInInstancedRegion()).thenReturn(true); observe(27417,position);
        assertNull(catalog.detect(client).activity);
    }

    @Test public void barbarianAssaultUsesTheActiveRoleInterfaceAndNativeCalls()
    {
        when(client.isInInstancedRegion()).thenReturn(true); hud(487,5,"Wave 7");
        hud(487,7,"Tofu"); hud(487,9,"Red eggs");
        MinigameActivityCatalog.Snapshot snapshot=catalog.detect(client);
        assertEquals(MinigameActivityCatalog.Activity.BARBARIAN_ASSAULT,snapshot.activity);
        assertEquals("Defender",fact(snapshot,"Role")); assertEquals("Tofu",fact(snapshot,"Heard call"));
        assertEquals("Red eggs",fact(snapshot,"Call to send"));
    }

    @Test public void sceneEvidenceStorageIsBoundedAndCanBeReleased()
    {
        GameObject first=null;
        for(int i=0;i<512;i++) { GameObject object=observe(29311,new WorldPoint(8000+i,8000,0)); if(i==0)first=object; }
        GameObject belt=observe(9100,position); assertNull(catalog.detect(client).activity);
        catalog.onGameObjectDespawned(first); catalog.onGameObjectSpawned(belt);
        assertEquals(MinigameActivityCatalog.Activity.BLAST_FURNACE,catalog.detect(client).activity);
        catalog.clearScene(); assertNull(catalog.detect(client).activity);
    }

    @Test public void registryAndObjectUnionCoverCoreAndExtraContextsWithoutDuplicateActivities()
    {
        List<MinigameActivityCatalog.Snapshot> definitions=catalog.supportedActivities();
        assertEquals(20,definitions.size());
        assertEquals(20,definitions.stream().map(value->value.activity).distinct().count());
        for(MinigameActivityCatalog.Snapshot definition:definitions) { assertTrue(definition.liveSupported); }
        assertTrue(catalog.allHighlightObjectIds().containsAll(Set.of(29312,43717,43719,43720,44621,9100,27383,39981,55395)));
        assertTrue(catalog.allHighlightObjectIds().containsAll(MinigameExtraActivities.allHighlightObjectIds()));
    }

    @Test public void unknownHomesTierNeverSelectsAnExpertBankGuide()
    {
        position=new WorldPoint(3238,3486,0); observe(39981,position); action(39981,"Remove"); when(client.getVarpValue(2747)).thenReturn(1);
        when(client.getVarbitValue(10554)).thenReturn(3);
        assertNull(catalog.detect(client).guideId);
        when(client.getVarbitValue(10555)).thenReturn(6);
        assertEquals("training:homes-novice",catalog.detect(client).guideId);
    }

    @Test public void blastGuideRequiresAnUnambiguousNativeBatch()
    {
        observe(9100,position); assertNull(catalog.detect(client).guideId);
        inventory(440); assertNull(catalog.detect(client).guideId);
        inventory(447); assertEquals("training:blast-mithril",catalog.detect(client).guideId);
        when(client.getVarbitValue(947)).thenReturn(27); assertEquals("training:blast-mithril",catalog.detect(client).guideId);
        inventory(444); assertEquals("training:blast-gold",catalog.detect(client).guideId);
    }

    @Test public void blastFurnaceRetainsTheObservedMethodDuringEmptyBankTripsAndClearsOnExit()
    {
        observe(9100,position); inventory(444);
        assertEquals("training:blast-gold",catalog.detect(client).guideId);
        inventory(-1); assertEquals("training:blast-gold",catalog.detect(client).guideId);
        position=new WorldPoint(3300,3300,0); assertNull(catalog.detect(client).activity);
        position=new WorldPoint(3200,3200,0); assertNull(catalog.detect(client).guideId);
        inventory(447); assertEquals("training:blast-mithril",catalog.detect(client).guideId);
        catalog.clearScene(); observe(9100,position); inventory(-1);
        assertNull(catalog.detect(client).guideId);
    }

    @Test public void publicSnapshotsDefensivelyCopyCallerCollections()
    {
        List<MinigameActivityCatalog.Fact> facts=new ArrayList<>(); facts.add(new MinigameActivityCatalog.Fact("A","B"));
        MinigameActivityCatalog.Snapshot snapshot=new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.GOTR,"Rift","guide",facts,Collections.emptyList(),Set.of(1),Collections.emptyList(),true);
        facts.clear(); assertEquals(1,snapshot.facts.size());
        try { snapshot.highlightObjectIds.add(2); fail("Mutable snapshot"); } catch(UnsupportedOperationException expected) { }
    }

    private void action(int id,String action)
    { ObjectComposition definition=mock(ObjectComposition.class); when(definition.getActions()).thenReturn(new String[]{action}); when(client.getObjectDefinition(id)).thenReturn(definition); }
    private Widget hud(int group,int child,String text)
    { Widget widget=mock(Widget.class); when(widget.getText()).thenReturn(text); when(client.getWidget(group,child)).thenReturn(widget); return widget; }
    private void inventory(int id)
    { ItemContainer container=mock(ItemContainer.class); when(container.getItems()).thenReturn(new Item[]{new Item(id,1)}); when(client.getItemContainer(net.runelite.api.gameval.InventoryID.INV)).thenReturn(container); }
    private GameObject observe(int id,WorldPoint point)
    { GameObject object=mock(GameObject.class); when(object.getId()).thenReturn(id); when(object.getWorldLocation()).thenReturn(point); catalog.onGameObjectSpawned(object); return object; }
    private static String fact(MinigameActivityCatalog.Snapshot snapshot,String label)
    { return snapshot.facts.stream().filter(value->value.label.equals(label)).findFirst().orElseThrow(AssertionError::new).value; }
    private void equippedPreform()
    {
        ItemContainer worn=mock(ItemContainer.class);when(worn.getItems()).thenReturn(new Item[]{new Item(net.runelite.api.gameval.ItemID.GIANTS_FOUNDRY_PREFORM,1)});
        when(client.getItemContainer(net.runelite.api.gameval.InventoryID.WORN)).thenReturn(worn);
    }
}
