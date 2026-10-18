/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinigameSpecialistsTest
{
    private Client client;private Player player;private MinigameActivityCatalog catalog;
    @Before public void setup()
    {
        client=mock(Client.class);player=mock(Player.class);catalog=new MinigameActivityCatalog();
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));
    }
    @Test public void homesAssignmentActivatesTravelWithoutGuessingFurnitureVarps()
    {
        catalog.onHomeMessage("Expert Contract: Go see Bob in north-east Varrock, opposite the church.");
        MinigameActivityCatalog.Snapshot snapshot=catalog.detect(client);
        assertEquals(MinigameActivityCatalog.Activity.MAHOGANY_HOMES,snapshot.activity);assertEquals("Travel to Bob",snapshot.steps.get(0).title);
        assertEquals(new WorldPoint(3238,3486,0),catalog.homeDestination());assertEquals("training:homes-expert",snapshot.guideId);
        hud(746,2,"");assertEquals(MinigameActivityCatalog.Activity.GOTR,catalog.detect(client).activity);
        catalog.clearAccount();assertNull(catalog.homeDestination());
    }
    @Test public void nativeReminderDialogueCanRecoverAssignment()
    {
        widget((231<<16)|6,"You're currently on a Novice Contract. Go see Sarah along the south wall of Varrock. You can get another job once you have furnished her home.");
        assertEquals("Travel to Sarah",catalog.detect(client).steps.get(0).title);assertEquals("training:homes-novice",catalog.detect(client).guideId);
    }
    @Test public void winterRawRootMethodDoesNotOverrideLowWarmth()
    {
        hud(396,2,"Fighting");hud(396,15,"85%");inventory(new Item(ItemID.WINT_BRUMA_ROOT,5));
        catalog.setOptions(100,25,60,"Team overview",false,true,1);
        assertEquals("Feed bruma roots",catalog.detect(client).steps.get(0).title);
        hud(396,15,"20%");assertEquals("Restore warmth",catalog.detect(client).steps.get(0).title);
    }
    @Test public void temporossBatchNeverOverridesTetherOrSpiritPhase()
    {
        hud(437,2,"");hud(437,19,"100%");inventory(new Item(ItemID.TEMPOROSS_RAW_HARPOONFISH,4));
        catalog.setOptions(100,25,60,"Team overview",true,false,8);
        assertEquals("Catch harpoonfish",catalog.detect(client).steps.get(0).title);
        inventory(new Item(ItemID.TEMPOROSS_RAW_HARPOONFISH,8));assertEquals("Load raw harpoonfish",catalog.detect(client).steps.get(0).title);
        hud(437,19,"0%");assertEquals("Fish the spirit pool",catalog.detect(client).steps.get(0).title);
        when(client.getVarbitValue(11895)).thenReturn(1);assertEquals("Remain tethered",catalog.detect(client).steps.get(0).title);
    }
    @Test public void selectedVolcanicVentIgnoresUnassignedUnknownVentAndRetainsExitPriority()
    {
        widget(InterfaceID.FossilVolcanicMine.UNIVERSE,"");widget(InterfaceID.FossilVolcanicMine.TIME,"8:00");
        widget(InterfaceID.FossilVolcanicMine.VENT_A,"?");widget(InterfaceID.FossilVolcanicMine.VENT_B,"50%");
        catalog.setOptions(100,25,60,"Vent B",true,true,1);
        assertEquals("Maintain Vent B and mine",catalog.detect(client).steps.get(0).title);
        widget(InterfaceID.FossilVolcanicMine.VENT_B,"?");assertEquals("Check Vent B",catalog.detect(client).steps.get(0).title);
        widget(InterfaceID.FossilVolcanicMine.TIME,"0:45");assertEquals("Return to the exit",catalog.detect(client).steps.get(0).title);
    }
    @Test public void assignmentPersistenceAndRoutingStayOnTheCorrectAccount() throws Exception
    {
        ConfigManager config=mock(ConfigManager.class);AtomicReference<String> profile=new AtomicReference<>("rsprofile-accounta");
        when(config.getRSProfileKey()).thenAnswer(e->profile.get());Map<String,String> values=new HashMap<>();
        when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(e->values.get(e.getArgument(0)+"|"+e.getArgument(1)+"|"+e.getArgument(2)));
        doAnswer(e->{values.put(e.getArgument(0)+"|"+e.getArgument(1)+"|"+e.getArgument(2),e.getArgument(3));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),anyString());
        MinigameCoach coach=new MinigameCoach(client,catalog,config,new Gson(),new RunewardenConfig(){});AtomicReference<WorldPoint> routed=new AtomicReference<>();coach.setRouteAction(routed::set);coach.start();
        try
        {
            ChatMessage chat=new ChatMessage();chat.setType(ChatMessageType.PUBLICCHAT);chat.setMessage("Expert Contract: Go see Bob in Varrock.");coach.onChatMessage(chat);assertNull(catalog.homeDestination());
            chat.setType(ChatMessageType.GAMEMESSAGE);coach.onChatMessage(chat);coach.onGameTick();assertEquals("Bob",catalog.homeState().customer);assertFalse(values.isEmpty());
            SwingUtilities.invokeAndWait(()->((JButton)named(coach.createPanel(),"minigameHomeRoute")).doClick());assertNotNull(routed.get());routed.set(null);
            profile.set("rsprofile-accountb");assertNull(coach.reading().activity);assertFalse(coach.canRouteHome(new WorldPoint(3238,3486,0)));
            SwingUtilities.invokeAndWait(()->((JButton)named(coach.createPanel(),"minigameHomeRoute")).doClick());assertNull(routed.get());
            coach.onGameTick();assertNull(catalog.homeDestination());
            profile.set("rsprofile-accounta");coach.onGameTick();assertEquals("Bob",catalog.homeState().customer);
            coach.clear();assertNull(catalog.homeDestination());
        }
        finally{coach.stop();SwingUtilities.invokeAndWait(()->{});}
    }
    @Test @SuppressWarnings("unchecked") public void nativeMazeUsesConfirmedGuardianAndActualCollisionThenPausesWhileMoving()
    {
        net.runelite.api.WorldView view=mock(net.runelite.api.WorldView.class);
        net.runelite.api.Scene scene=mock(net.runelite.api.Scene.class);when(view.getScene()).thenReturn(scene);when(view.getSizeX()).thenReturn(104);when(view.getSizeY()).thenReturn(104);when(client.getTopLevelWorldView()).thenReturn(view);
        net.runelite.api.CollisionData collision=mock(net.runelite.api.CollisionData.class);int[][] flags=new int[104][104];
        for(int[] row:flags)java.util.Arrays.fill(row,0xffffff);for(int x=5;x<=9;x++)for(int y=5;y<=9;y++)flags[x][y]=0;
        when(collision.getFlags()).thenReturn(flags);when(view.getCollisionMaps()).thenReturn(new net.runelite.api.CollisionData[]{collision});
        net.runelite.api.Tile[][][] tiles=new net.runelite.api.Tile[1][12][12];
        for(int[] point:new int[][]{{4,4},{4,10},{10,4},{10,10}})
        {
            net.runelite.api.Tile tile=mock(net.runelite.api.Tile.class);net.runelite.api.WallObject wall=mock(net.runelite.api.WallObject.class);
            when(wall.getId()).thenReturn(net.runelite.api.gameval.ObjectID.MAGICTRAINING_MINIWALL);when(wall.getWorldLocation()).thenReturn(new WorldPoint(point[0],point[1],0));
            when(tile.getWallObject()).thenReturn(wall);tiles[0][point[0]][point[1]]=tile;
        }
        net.runelite.api.Tile finishTile=mock(net.runelite.api.Tile.class);net.runelite.api.GroundObject finish=mock(net.runelite.api.GroundObject.class);
        when(finish.getId()).thenReturn(net.runelite.api.gameval.ObjectID.MAGICTRAINING_MINI_STATUE_PITFALL);when(finish.getWorldLocation()).thenReturn(new WorldPoint(9,9,0));
        when(finishTile.getGroundObject()).thenReturn(finish);tiles[0][9][9]=finishTile;when(scene.getTiles()).thenReturn(tiles);
        net.runelite.api.NPC guardian=mock(net.runelite.api.NPC.class);when(guardian.getId()).thenReturn(net.runelite.api.gameval.NpcID.MAGICTRAINING_GUARD_MAZE_INCOMPLETE);when(guardian.getWorldLocation()).thenReturn(new WorldPoint(5,5,0));
        net.runelite.api.IndexedObjectSet<net.runelite.api.NPC> npcs=mock(net.runelite.api.IndexedObjectSet.class);
        when(npcs.iterator()).thenAnswer(e->java.util.List.of(guardian).iterator());doReturn(npcs).when(view).npcs();
        MinigameActivityCatalog.Snapshot original=new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.MAGE_TRAINING_ARENA,"Mage Training Arena",null,
            java.util.List.of(new MinigameActivityCatalog.Fact("Room","Telekinetic Theatre")),java.util.List.of(new MinigameActivityCatalog.Step("Move the maze guardian","Cast Telekinetic Grab")),java.util.Set.of(),java.util.List.of(),true);
        MinigameTelekinetic helper=new MinigameTelekinetic();MinigameActivityCatalog.Snapshot solved=helper.enrich(original,client);
        assertTrue(solved.facts.stream().anyMatch(f->f.label.equals("Pulls remaining")&&f.value.equals("2")));assertEquals(1,solved.referenceTiles.size());
        assertTrue(solved.steps.get(0).title.contains("North"));
        when(guardian.getId()).thenReturn(net.runelite.api.gameval.NpcID.MAGICTRAINING_GUARD_MAZE_MOVING);
        MinigameActivityCatalog.Snapshot moving=helper.enrich(original,client);assertEquals("Guardian moving",moving.steps.get(0).title);assertTrue(moving.referenceTiles.isEmpty());
        when(guardian.getId()).thenReturn(net.runelite.api.gameval.NpcID.MAGICTRAINING_GUARD_MAZE_COMPLETE);
        assertEquals("Maze complete",helper.enrich(original,client).steps.get(0).title);
        when(guardian.getId()).thenReturn(net.runelite.api.gameval.NpcID.MAGICTRAINING_GUARD_MAZE_INCOMPLETE);
        when(npcs.iterator()).thenAnswer(e->java.util.List.of(guardian,guardian).iterator());assertSame(original,helper.enrich(original,client));
        helper.clear();
    }
    private Widget hud(int group,int child,String value){Widget widget=mock(Widget.class);when(widget.getText()).thenReturn(value);when(client.getWidget(group,child)).thenReturn(widget);return widget;}
    private void widget(int id,String value){Widget widget=mock(Widget.class);when(widget.getText()).thenReturn(value);when(client.getWidget(id)).thenReturn(widget);}
    private void inventory(Item...items){ItemContainer inventory=mock(ItemContainer.class);when(inventory.getItems()).thenReturn(items);when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);}
    private static Component named(Container parent,String name)
    {for(Component child:parent.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container){Component result=named((Container)child,name);if(result!=null)return result;}}return null;}
}
