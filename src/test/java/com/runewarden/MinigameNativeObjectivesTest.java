/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.List;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.Widget;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinigameNativeObjectivesTest
{
    private Client client;private Player player;private MinigameActivityCatalog catalog;
    @Before public void setup()
    {client=mock(Client.class);player=mock(Player.class);catalog=new MinigameActivityCatalog();when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));}
    @Test public void guardianFragmentTargetIsARealBatchAndNeverOverridesCraftedEssenceOrStones()
    {
        hud(746,2,"");catalog.setOptions(100,25,60);inventory(new Item(26878,45));
        assertEquals("Mine your fragment batch",current().title);assertTrue(current().instruction.contains("55 more"));
        inventory(new Item(26878,100));assertEquals("Craft guardian essence",current().title);
        inventory(new Item(26878,40),new Item(26879,1));assertEquals("Use an active altar",current().title);
        inventory(new Item(26878,40),new Item(26880,1));assertEquals("Empower the Great Guardian",current().title);
    }
    @Test public void nativeWarmthThresholdTakesPrecedenceOverFuelAndUsesTheActuallyCarriedPotion()
    {
        hud(396,2,"Fighting");hud(396,15,"24%");inventory(new Item(ItemID.WINT_BRUMA_KINDLING,5),new Item(ItemID.WINT_POTION2,1));
        assertEquals("Restore warmth",current().title);assertTrue(current().instruction.contains("Drink"));
        hud(396,15,"65%");assertEquals("Feed your kindling",current().title);
        hud(396,15,"");assertEquals("Feed your kindling",current().title);
    }
    @Test public void defenderUsesNativeDetailChildSevenAndTheCallIsActuallyInItsInstruction()
    {
        when(client.isInInstancedRegion()).thenReturn(true);hud(487,5,"Wave 7");hud(487,7,"Tofu");hud(487,9,"Red eggs");
        hud(487,8,"OLD WRONG LABEL");hud(487,10,"OLD WRONG HORN SPRITE");
        assertEquals("Bait runners with Tofu",current().title);assertTrue(current().instruction.contains("Call Red eggs"));
        assertFalse(current().instruction.contains("OLD WRONG"));assertTrue(catalog.detect(client).highlightNpcIds.contains(NpcID.BARBASSAULT_PEN_RUNNER_LV1));
        observe(ObjectID.BARBASSAULT_TRAP_BROKEN);assertEquals("Repair the runner trap",current().title);
    }
    @Test public void healerHasNamedNativeTeammateHealthAndCalledPoison()
    {
        when(client.isInInstancedRegion()).thenReturn(true);hud(488,5,"Wave 3");hud(488,7,"Poisoned worms");hud(488,9,"Tofu");
        hud(488,17,"Team mate");hud(488,18,"35/70");inventory(new Item(ItemID.BARBASSAULT_POISION_02,4));
        MinigameActivityCatalog.Snapshot s=catalog.detect(client);assertEquals("Poison with Poisoned worms",s.steps.get(0).title);
        assertEquals("Team mate · 35/70",fact(s,"Teammate 1"));assertEquals("4",fact(s,"Poison food"));
    }
    @Test public void healerEmptyKnownInventoryUsesTheSupplyMachineAndQueenUsesThePoisonPool()
    {
        when(client.isInInstancedRegion()).thenReturn(true);hud(488,5,"Wave 7");hud(488,7,"Poisoned tofu");hud(488,9,"Worms");inventory();
        assertEquals("Restock healer supplies",current().title);assertTrue(catalog.detect(client).highlightObjectIds.contains(ObjectID.BARASSAULT_VENDING_MACHINE_HEALER));
        hud(488,5,"Wave 10");inventory(new Item(ItemID.BARBASSAULT_EGG_04,1));assertEquals("Poison the queen egg",current().title);
        assertTrue(catalog.detect(client).highlightObjectIds.contains(ObjectID.BARBASSAULT_CRATER_POISION));
        assertFalse(catalog.detect(client).highlightObjectIds.contains(ObjectID.BARBASSAULT_NATURAL_SPRING));
    }
    @Test public void aMissingReceivedCallDoesNotGuessStyleFoodOrEggs()
    {
        when(client.isInInstancedRegion()).thenReturn(true);hud(485,5,"Wave 1");hud(485,8,"---");hud(485,10,"Blue eggs");
        assertEquals("Send the call and wait for yours",current().title);assertTrue(catalog.detect(client).highlightNpcIds.isEmpty());
    }
    @Test public void queenStepRequiresWaveTenAndTheExactCarriedEggForm()
    {
        when(client.isInInstancedRegion()).thenReturn(true);hud(485,5,"Wave 9");hud(485,8,"Accurate");hud(485,10,"Blue eggs");
        inventory(new Item(ItemID.BARBASSAULT_EGG_POISON,1));assertEquals("Attack using Accurate",current().title);
        hud(485,5,"Wave 10");assertEquals("Add spikes to the poisoned egg",current().title);
        inventory(new Item(ItemID.BARBASSAULT_EGG_POISON_SPIKES,1));assertEquals("Pass the spiked egg to the Defender",current().title);
        inventory(new Item(ItemID.BARBASSAULT_EGG_HEATED_POISON_SPIKES,1));assertEquals("Hand off the omega egg",current().title);
    }
    @Test public void collectorWithCompletedOmegaLoadsItInsteadOfHandingItToThemselves()
    {
        when(client.isInInstancedRegion()).thenReturn(true);hud(486,5,"Wave 10");hud(486,7,"Red eggs");hud(486,9,"Accurate");
        inventory(new Item(ItemID.BARBASSAULT_EGG_HEATED_POISON_SPIKES,1));
        assertEquals("Load and fire the omega egg",current().title);assertTrue(catalog.detect(client).highlightObjectIds.contains(ObjectID.BARBASSAULT_EGG_HOPPER));
    }
    @Test public void pestPortalShieldAndHealthDoNotInventAnOpenPortal()
    {
        hud(408,14,"");hud(408,21,"200");hud(408,22,"0");
        MinigameActivityCatalog.Snapshot s=catalog.detect(client);assertEquals("200 HP · shield not observed",fact(s,"Purple portal"));
        assertEquals("Destroyed",fact(s,"Blue portal"));assertEquals("Defend the Void Knight",s.steps.get(0).title);
    }
    @Test public void pestCurrentPortalFormsAndNearbySpinnersChangeTheObjectiveWithoutPrediction()
    {
        hud(408,14,"");hud(408,21,"150");
        NPC portal=npc(NpcID.PEST_PORTAL_1_ACTIVE,3204,3200);npcs(portal);
        assertEquals("Attack the purple portal",current().title);assertEquals("Open · 150 HP",fact(catalog.detect(client),"Purple portal"));
        NPC shield=npc(NpcID.PEST_PORTAL_SHIELD_1_ACTIVE,3204,3200);npcs(shield);
        assertEquals("Defend the Void Knight",current().title);assertEquals("Shielded · 150 HP",fact(catalog.detect(client),"Purple portal"));
        NPC spinner=npc(NpcID.PEST_SPINNER_1,3205,3200);npcs(portal,spinner);assertEquals("Clear nearby spinners",current().title);
        npcs(portal);assertEquals("Attack the purple portal",current().title);
    }
    @Test public void nearbyIncapacitatedPyromancerNeedsAnActualCarriedPotion()
    {
        hud(396,2,"Fighting");hud(396,15,"85%");NPC wizard=npc(NpcID.WINT_WIZARD_DOWN,3201,3200);npcs(wizard);
        inventory(new Item(ItemID.WINT_BRUMA_KINDLING,2));assertEquals("Feed your kindling",current().title);
        inventory(new Item(ItemID.WINT_BRUMA_KINDLING,2),new Item(ItemID.WINT_POTION1,1));assertEquals("Revive the pyromancer",current().title);
    }
    @Test public void volcanicReminderUsesAnExistingValidClockAndConfigurableCutoff()
    {
        Widget root=mock(Widget.class);when(client.getWidget(InterfaceID.FossilVolcanicMine.UNIVERSE)).thenReturn(root);
        widget(InterfaceID.FossilVolcanicMine.TIME,"0:59");assertEquals("Return to the exit",current().title);
        widget(InterfaceID.FossilVolcanicMine.TIME,"8:00");assertEquals("Mine and watch your vent role",current().title);
        widget(InterfaceID.FossilVolcanicMine.VENT_A,"?");assertEquals("Check the assigned vents",current().title);
        widget(InterfaceID.FossilVolcanicMine.TIME,"unknown");assertFalse(current().title.contains("exit"));
    }
    @Test public void blastCircuitFollowsObservedRockFormsAndRegistersThoseEvidenceIds()
    {
        widget(InterfaceID.LovakengjBlastMiningHud.UNIVERSE,"");inventory(new Item(ItemID.LOVAKENGJ_DYNAMITE_POT,5));
        GameObject chiseled=observe(ObjectID.BLAST_MINING_WALL_CHISELED_01);assertEquals("Load dynamite",current().title);
        catalog.onGameObjectDespawned(chiseled);GameObject loaded=observe(ObjectID.BLAST_MINING_WALL_POT_01);assertEquals("Light and retreat",current().title);
        catalog.onGameObjectDespawned(loaded);observe(ObjectID.BLAST_MINING_WALL_DESTROYED_01);assertEquals("Collect blasted ore",current().title);
        assertEquals("5",fact(catalog.detect(client),"Dynamite carried"));
    }
    @Test public void clockParserRejectsAmbiguousUnitsAndMalformedSeconds()
    {
        assertEquals(Integer.valueOf(65),MinigameNativeObjectives.clockSeconds("Time remaining: 1:05"));
        assertNull(MinigameNativeObjectives.clockSeconds("65"));assertNull(MinigameNativeObjectives.clockSeconds("1:99"));
        assertNull(MinigameNativeObjectives.percent("Warmth unavailable"));assertNull(MinigameNativeObjectives.percent("150%"));
    }
    @Test public void temporossUsesActualPoolAndShoalFormsForReferences()
    {
        hud(437,2,"");hud(437,19,"100%");assertTrue(catalog.detect(client).highlightNpcIds.contains(NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL));
        hud(437,19,"0%");assertEquals(Set.of(NpcID.TEMPOROSS_P2_FISHINGSPOT),catalog.detect(client).highlightNpcIds);
        when(client.getVarbitValue(11895)).thenReturn(1);assertTrue(catalog.detect(client).highlightNpcIds.isEmpty());
    }
    @Test public void titheFruitIdsAreUnnotedNativeFruitAndReadyFruitCanBeDeposited()
    {
        when(client.isInInstancedRegion()).thenReturn(true);observe(27383);inventory(new Item(ItemID.HOSIDIUS_TITHE_FRUIT_A,10));
        assertEquals("Deposit your harvested fruit",current().title);assertEquals("10",fact(catalog.detect(client),"Fruit carried"));
    }
    private NPC npc(int id,int x,int y){NPC n=mock(NPC.class);when(n.getId()).thenReturn(id);when(n.getWorldLocation()).thenReturn(new WorldPoint(x,y,0));return n;}
    @SuppressWarnings("unchecked") private void npcs(NPC... values)
    {WorldView view=mock(WorldView.class);IndexedObjectSet<NPC> set=mock(IndexedObjectSet.class);when(set.iterator()).thenAnswer(e->java.util.Arrays.asList(values).iterator());doReturn(set).when(view).npcs();when(client.getTopLevelWorldView()).thenReturn(view);}
    private MinigameActivityCatalog.Step current(){return catalog.detect(client).steps.get(0);}
    private Widget hud(int group,int child,String text){Widget w=mock(Widget.class);when(w.getText()).thenReturn(text);when(client.getWidget(group,child)).thenReturn(w);return w;}
    private void widget(int id,String text){Widget w=mock(Widget.class);when(w.getText()).thenReturn(text);when(client.getWidget(id)).thenReturn(w);}
    private void inventory(Item...items){ItemContainer c=mock(ItemContainer.class);when(c.getItems()).thenReturn(items);when(client.getItemContainer(InventoryID.INV)).thenReturn(c);}
    private GameObject observe(int id){GameObject o=mock(GameObject.class);when(o.getId()).thenReturn(id);when(o.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));catalog.onGameObjectSpawned(o);return o;}
    private static String fact(MinigameActivityCatalog.Snapshot s,String label){return s.facts.stream().filter(f->f.label.equals(label)).findFirst().orElseThrow(AssertionError::new).value;}
}
