package com.runewarden;

import java.util.List;
import java.util.Set;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SkillingCoachTest
{
    @Test public void knownLifetimesExcludeNormalRandomAndUnknownSpecies()
    {
        assertEquals(45, NodeTimerRules.treeLifetimeTicks("Oak"));
        assertEquals(190, NodeTimerRules.treeLifetimeTicks("Yew"));
        assertEquals(390, NodeTimerRules.treeLifetimeTicks("Magic tree"));
        assertEquals(440, NodeTimerRules.treeLifetimeTicks("Redwood"));
        assertEquals(0, NodeTimerRules.treeLifetimeTicks("Tree"));
        assertEquals(0, NodeTimerRules.treeLifetimeTicks("Blisterwood tree"));
        assertEquals(0, NodeTimerRules.treeLifetimeTicks("Rosewood tree")); // no unverified Sailing duration
        assertTrue(NodeTimerRules.PLANTED_TREES.contains(ObjectID.FARMING_TREE_PATCH_1));
        assertTrue(NodeTimerRules.PLANTED_TREES.contains(ObjectID.TEAK_TREE_FULLYGROWN));
    }
    @Test public void countdownRoundsUpAndNeverGoesNegativeOrUsesPopulation()
    {
        assertEquals(8_400, NodeTimerRules.remainingMillis(1_000, 14, 1_000));
        assertEquals(0, NodeTimerRules.remainingMillis(1_000, 14, 20_000));
        assertEquals("9s", NodeTimerRules.countdown(8_400));
        assertEquals("1:01", NodeTimerRules.countdown(60_001));
        SkillingCoach.Node rock = new SkillingCoach.Node(new WorldPoint(3200,3200,0), 0,1,1,0,"Iron",1000,0,false);
        assertEquals("Until depleted", rock.label(99999));
        SkillingCoach.Node tree = new SkillingCoach.Node(rock.point,0,1,1,0,"Yew",1000,190,false);
        assertEquals("Life ~ 1:54", tree.label(1000));
        assertEquals("Next log may deplete", tree.label(200000));
    }
    @Test public void acceptsVerifiedGameTimersIncludingSailingAndMotherlode()
    {
        assertTrue(NodeTimerRules.TREE_RESPAWNS.contains(ObjectID.ROSEWOOD_TREE_UPDATE_STUMP));
        assertTrue(NodeTimerRules.ROCK_RESPAWNS.contains(ObjectID.MOTHERLODE_DEPLETED_SINGLE));
        assertTrue(NodeTimerRules.ROCK_RESPAWNS.contains(ObjectID.AMETHYSTROCK_EMPTY));
        assertFalse(NodeTimerRules.ROCK_RESPAWNS.contains(ObjectID.TREESTUMP));
        assertTrue(NodeTimerRules.CHOPPING.contains(AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE));
        assertTrue(NodeTimerRules.MINING.contains(AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE));
        assertFalse(NodeTimerRules.CHOPPING.contains(AnimationID.HUMAN_OPENHEAVYCHEST));
    }
    @Test public void equippedVariantsMixedAnglersAndCombinedSmithGlovesAreAccepted()
    {
        assertTrue(SkillingOutfits.missing(SkillingOutfits.OUTFITS.get(Skill.MINING),
            List.of("Golden prospector helmet", "Prospector jacket", "Prospector legs (s)", "Prospector boots"), Set.of()).isEmpty());
        assertTrue(SkillingOutfits.missing(SkillingOutfits.OUTFITS.get(Skill.FISHING),
            List.of("Spirit angler headband", "Angler top", "Spirit angler waders", "Angler boots"), Set.of()).isEmpty());
        assertTrue(SkillingOutfits.missing(SkillingOutfits.OUTFITS.get(Skill.SMITHING),
            List.of("Smiths' tunic", "Smiths' trousers", "Smiths' boots", "Smiths' gloves (i)"), Set.of()).isEmpty());
        assertEquals(4,SkillingOutfits.missing(SkillingOutfits.OUTFITS.get(Skill.FIREMAKING),List.of("Warm gloves"),Set.of()).size());
    }
    @Test public void serverOutfitBenefitsAcceptForestryPouchWithoutEquippingClothes()
    {
        assertTrue(SkillingOutfits.missing(SkillingOutfits.OUTFITS.get(Skill.WOODCUTTING), List.of(), Set.of(
            VarbitID.WORN_ITEMS_LUMBERJACK_HEAD, VarbitID.WORN_ITEMS_LUMBERJACK_BODY,
            VarbitID.WORN_ITEMS_LUMBERJACK_LEGS, VarbitID.WORN_ITEMS_LUMBERJACK_BOOTS)).isEmpty());
        assertEquals(1, SkillingOutfits.missing(SkillingOutfits.OUTFITS.get(Skill.WOODCUTTING),List.of(),Set.of(
            VarbitID.WORN_ITEMS_LUMBERJACK_HEAD,VarbitID.WORN_ITEMS_LUMBERJACK_BODY,VarbitID.WORN_ITEMS_LUMBERJACK_LEGS)).size());
    }
    @Test public void contextualOutfitReminderDismissesAndClearsOnDeathAndHop()
    {
        Client client=mock(Client.class); Player player=mock(Player.class); ItemManager items=mock(ItemManager.class);
        when(client.getLocalPlayer()).thenReturn(player); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        ItemContainer equipment=mock(ItemContainer.class); when(equipment.getItems()).thenReturn(new Item[0]);
        when(client.getItemContainer(InventoryID.EQUIPMENT)).thenReturn(equipment);
        SkillingCoach coach=new SkillingCoach(client,items,mock(ConfigManager.class),new RunewardenConfig() {});
        coach.startUp();
        StatChanged xp=new StatChanged(Skill.MINING,100,1,1);
        coach.onStatChanged(xp); coach.onGameTick(new GameTick());
        assertNotNull(coach.reminder()); assertEquals(Skill.MINING,coach.reminder().skill);
        coach.dismissOutfitReminder(); coach.onGameTick(new GameTick()); assertNull(coach.reminder());
        GameStateChanged hop=new GameStateChanged(); hop.setGameState(GameState.HOPPING); coach.onGameStateChanged(hop);
        xp=new StatChanged(Skill.MINING,200,1,1); coach.onStatChanged(xp); coach.onGameTick(new GameTick()); assertNotNull(coach.reminder());
        ActorDeath death=new ActorDeath(player); coach.onActorDeath(death); assertNull(coach.reminder()); assertTrue(coach.nodes().isEmpty());
    }
    @Test public void noRogueReminderForChestLootingOrExperienceInitialisation()
    {
        Client client=mock(Client.class); Player player=mock(Player.class); when(client.getLocalPlayer()).thenReturn(player);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getSkillExperience(Skill.THIEVING)).thenReturn(1000);
        SkillingCoach coach=new SkillingCoach(client,mock(ItemManager.class),mock(ConfigManager.class),new RunewardenConfig() {}); coach.startUp();
        StatChanged xp=new StatChanged(Skill.THIEVING,1000,1,1);
        coach.onStatChanged(xp);coach.onGameTick(new GameTick()); assertNull(coach.reminder());
        xp=new StatChanged(Skill.THIEVING,1100,1,1);coach.onStatChanged(xp);coach.onGameTick(new GameTick());assertNull(coach.reminder());
    }
    @Test public void serverRespawnEventUsesSuppliedTicksAndClearsOnSceneChangeAndDeath()
    {
        Client client=mock(Client.class); Player player=mock(Player.class); WorldView view=mock(WorldView.class); Scene scene=mock(Scene.class);
        when(client.getLocalPlayer()).thenReturn(player); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(player.getWorldView()).thenReturn(view); when(view.getBaseX()).thenReturn(3200); when(view.getBaseY()).thenReturn(3200);
        when(view.getSizeX()).thenReturn(104); when(view.getSizeY()).thenReturn(104); when(view.getScene()).thenReturn(scene);
        when(scene.getTiles()).thenReturn(new Tile[4][104][104]);
        SkillingCoach coach=new SkillingCoach(client,mock(ItemManager.class),mock(ConfigManager.class),new RunewardenConfig() {});coach.startUp();
        ScriptEvent script=mock(ScriptEvent.class);
        when(script.getArguments()).thenReturn(new Object[]{ScriptID.ADD_OVERLAYTIMER_LOC,(3210<<14)|3212,ObjectID.ROSEWOOD_TREE_UPDATE_STUMP,0,0,199});
        ScriptPreFired event=new ScriptPreFired(ScriptID.ADD_OVERLAYTIMER_LOC);event.setScriptEvent(script);coach.onScriptPreFired(event);
        assertEquals(1,coach.nodes().size());assertEquals(199,coach.nodes().get(0).ticks);
        assertEquals(new WorldPoint(3210,3212,0),coach.nodes().get(0).point);
        assertTrue(coach.nodes().get(0).respawn);
        coach.onScriptPreFired(event);assertEquals(1,coach.nodes().size()); // duplicate replaces, never stacks
        GameStateChanged loading=new GameStateChanged();loading.setGameState(GameState.LOADING);coach.onGameStateChanged(loading);assertTrue(coach.nodes().isEmpty());
        coach.onScriptPreFired(event);assertEquals(1,coach.nodes().size());coach.onActorDeath(new ActorDeath(player));assertTrue(coach.nodes().isEmpty());
        when(script.getArguments()).thenReturn(new Object[]{ScriptID.ADD_OVERLAYTIMER_LOC,(3210<<14)|3212,ObjectID.TREESTUMP,0,0,-1});
        coach.onScriptPreFired(event);assertTrue(coach.nodes().isEmpty());
        when(script.getArguments()).thenReturn(new Object[]{ScriptID.ADD_OVERLAYTIMER_LOC,(6000<<14)|6000,ObjectID.TREESTUMP,0,0,10});
        coach.onScriptPreFired(event);assertTrue(coach.nodes().isEmpty());
    }
}
