/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.vars.AccountType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SailingCoachTest
{
    Client client;Player player;WorldView top,local;WorldEntity boat;WorldEntityConfig boatConfig;ConfigManager config;WorldMapPointManager map;SailingCoach coach;
    Map<Integer,Integer> varbits=new HashMap<>(),varps=new HashMap<>();AtomicReference<String> profile=new AtomicReference<>("rsprofile-a");AtomicInteger tick=new AtomicInteger(100);
    @Before @SuppressWarnings("unchecked") public void setup()
    {
        client=mock(Client.class);player=mock(Player.class);top=mock(WorldView.class);local=mock(WorldView.class);boat=mock(WorldEntity.class);boatConfig=mock(WorldEntityConfig.class);config=mock(ConfigManager.class);map=mock(WorldMapPointManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getWorld()).thenReturn(301);when(client.getLocalPlayer()).thenReturn(player);when(client.getTopLevelWorldView()).thenReturn(top);when(client.getCanvasWidth()).thenReturn(500);when(client.getCanvasHeight()).thenReturn(330);
        when(config.getRSProfileKey()).thenAnswer(e->profile.get());when(client.getVarbitValue(anyInt())).thenAnswer(e->varbits.getOrDefault(e.getArgument(0),0));when(client.getVarpValue(anyInt())).thenAnswer(e->varps.getOrDefault(e.getArgument(0),0));when(client.getTickCount()).thenAnswer(e->tick.get());
        when(top.isTopLevel()).thenReturn(true);when(top.getId()).thenReturn(-1);when(local.getId()).thenReturn(4);when(player.getWorldView()).thenReturn(local);when(top.getBaseX()).thenReturn(3000);when(top.getBaseY()).thenReturn(3000);
        IndexedObjectSet<WorldEntity> entities=mock(IndexedObjectSet.class);doReturn(entities).when(top).worldEntities();when(entities.byIndex(4)).thenReturn(boat);when(boat.getWorldView()).thenReturn(local);when(boat.getConfig()).thenReturn(boatConfig);when(boatConfig.getId()).thenReturn(1);when(boat.getTargetLocation()).thenReturn(new LocalPoint(3200,3200,-1));
        varbits.put(19177,100);varbits.put(19181,80);varbits.put(11629,5);varbits.put(19178,3);coach=new SailingCoach(client,config,new Gson(),new RunewardenDataStore(config,new Gson()),new RunewardenConfig(){},map);coach.start();
    }
    @Test public void onlyValidatedNativeBoatEntitiesActivateTheSailingHud()
    {
        coach.onGameTick();assertTrue(coach.reading().active);assertEquals("Sailing command",coach.reading().title);when(boatConfig.getId()).thenReturn(99);coach.onGameTick();assertFalse(coach.reading().active);
        when(boatConfig.getId()).thenReturn(1);when(boat.getWorldView()).thenReturn(top);coach.onGameTick();assertFalse(coach.reading().active);when(player.getWorldView()).thenReturn(top);assertNull(SailingCoach.boat(top,top));
    }
    @Test public void nativeNavigationUsesTickPositionsAndResolvesARebasedSceneWithoutPhantomSpeed()
    {
        when(boat.getTargetOrientation()).thenReturn(1024);coach.onGameTick();assertTrue(fact("Heading · N     Speed · Unknown"));tick.incrementAndGet();when(boat.getTargetLocation()).thenReturn(new LocalPoint(3360,3200,-1));coach.onGameTick();assertTrue(fact("Speed · 1.25 tiles/tick"));
        when(top.getBaseX()).thenReturn(3008);when(boat.getTargetLocation()).thenReturn(new LocalPoint(2336,3200,-1));coach.onGameTick();assertTrue(fact("Speed · 0.00 tiles/tick"));
    }
    @Test public void sailsHighlightOnlyWhenTheNativeTrimActionIsAvailableOnTheCurrentBoat()
    {
        GameObject sail=object(SailingCoachRules.SAILS.iterator().next(),local);coach.onGameTick();assertFalse(coach.reading().targets.stream().anyMatch(t->t.object==sail));when(sail.isOpShown(0)).thenReturn(true);coach.onGameTick();assertTrue(coach.reading().targets.stream().anyMatch(t->t.object==sail&&t.label.equals("TRIM SAILS")));
        GameObject other=object(SailingCoachRules.SAILS.iterator().next(),mock(WorldView.class));when(other.isOpShown(0)).thenReturn(true);coach.onGameTick();assertFalse(coach.reading().targets.stream().anyMatch(t->t.object==other));
    }
    @Test public void hullReminderUsesCurrentHpThresholdAndRejectsImpossibleNativeValues() throws Exception
    {
        GameObject hull=object(SailingCoachRules.HULLS.iterator().next(),local);varbits.put(19181,30);coach.onGameTick();assertTrue(coach.reading().objective.startsWith("LOW HULL"));assertTrue(coach.reading().targets.stream().anyMatch(t->t.object==hull));
        varbits.put(19181,101);coach.onGameTick();assertTrue(fact("Hull · Unknown"));assertFalse(coach.reading().targets.stream().anyMatch(t->t.object==hull));varbits.put(19181,20);
        SwingUtilities.invokeAndWait(()->((JCheckBox)named(coach.createPanel(),"sailingCoachrepair")).doClick());coach.onGameTick();assertFalse(coach.reading().objective.startsWith("LOW HULL"));
    }
    @Test public void cargoShowsNativeCapacityAndActualContentsOnlyWhileTheHoldIsOpen()
    {
        object(ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE,local);coach.onGameTick();assertTrue(fact("Cargo hold · capacity 240"));
        Widget hold=mock(Widget.class);when(client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE)).thenReturn(hold);varps.put(5204,InventoryID.SAILING_BOAT_1_CARGOHOLD);ItemContainer inv=mock(ItemContainer.class);when(client.getItemContainer(InventoryID.SAILING_BOAT_1_CARGOHOLD)).thenReturn(inv);when(inv.getItems()).thenReturn(new Item[]{new Item(100,200),new Item(101,3)});
        ItemComposition stack=mock(ItemComposition.class),loose=mock(ItemComposition.class);when(stack.isStackable()).thenReturn(true);when(client.getItemDefinition(100)).thenReturn(stack);when(client.getItemDefinition(101)).thenReturn(loose);coach.onGameTick();assertTrue(fact("Cargo hold · 4 / 240 slots"));
        when(client.getAccountType()).thenReturn(AccountType.ULTIMATE_IRONMAN);coach.onGameTick();assertTrue(fact("Cargo hold · 4 / 10 slots"));when(hold.isHidden()).thenReturn(true);coach.onGameTick();assertTrue(fact("Cargo hold · capacity 10"));
    }
    @Test public void nativeCourierCratesDoNotConsumeHoldCapacityAndUnknownContainersAreNotGuessed()
    {
        task(0,8,8587,8597,10,30000);object(ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT,local);Widget hold=mock(Widget.class);when(client.getWidget(InterfaceID.SailingBoatCargohold.UNIVERSE)).thenReturn(hold);varps.put(5204,InventoryID.SAILING_BOAT_1_CARGOHOLD);ItemContainer inv=mock(ItemContainer.class);when(client.getItemContainer(InventoryID.SAILING_BOAT_1_CARGOHOLD)).thenReturn(inv);when(inv.getItems()).thenReturn(new Item[]{new Item(30000,10)});coach.onGameTick();assertTrue(fact("Cargo hold · 0 / 30 slots"));
        varps.put(5204,999);coach.onGameTick();assertTrue(fact("Cargo hold · contents not observed"));
    }
    @Test public void conflictingObservedHoldTiersDoNotInventACapacity()
    {
        object(ObjectID.SAILING_BOAT_CARGO_HOLD_OAK_RAFT,local);object(ObjectID.SAILING_BOAT_CARGO_HOLD_ROSEWOOD_LARGE,local);coach.onGameTick();assertTrue(fact("Cargo hold · not observed"));
    }
    @Test public void trialSuppliesRequireAnActuallyVisibleNativeMorph()
    {
        trial(2,0,0,2);int id=SailingCoachRules.LOST_CARGO.iterator().next();GameObject crate=object(id,top);ObjectComposition definition=mock(ObjectComposition.class);when(client.getObjectDefinition(id)).thenReturn(definition);when(definition.getImpostorIds()).thenReturn(new int[]{-1,1});coach.onGameTick();assertFalse(coach.reading().targets.stream().anyMatch(t->t.object==crate));
        when(definition.getImpostor()).thenReturn(mock(ObjectComposition.class));coach.onGameTick();assertTrue(coach.reading().targets.stream().anyMatch(t->t.object==crate));
    }
    @Test public void selectedNativeTaskWorksAshoreAndManualRouteIsProfileAndDestinationGuarded() throws Exception
    {
        when(player.getWorldView()).thenReturn(top);task(0,8,8587,8597,10,30000);task(1,9,8597,8590,10,30001);coach.onGameTick();assertEquals("Port tasks",coach.reading().title);assertTrue(coach.canRoute(SailingCoachRules.port(8587).point));assertFalse(coach.canRoute(SailingCoachRules.port(8590).point));verify(map).add(any(WorldMapPoint.class));
        SwingUtilities.invokeAndWait(()->((JComboBox<?>)named(coach.createPanel(),"sailingCoachTask")).setSelectedIndex(1));assertTrue(coach.canRoute(SailingCoachRules.port(8597).point));assertFalse(coach.canRoute(SailingCoachRules.port(8587).point));profile.set("rsprofile-b");assertFalse(coach.canRoute(SailingCoachRules.port(8597).point));assertFalse(coach.reading().active);
    }
    @Test public void mapDestinationMovesFromCollectionToDeliveryAndIsRemovedOnStop()
    {
        task(0,8,8587,8597,10,30000);coach.onGameTick();varbits.put(19575,10);coach.onGameTick();verify(map,times(2)).add(any(WorldMapPoint.class));verify(map).remove(any(WorldMapPoint.class));coach.stop();verify(map,times(2)).remove(any(WorldMapPoint.class));assertFalse(coach.reading().active);
    }
    @Test public void missingAccountIdentityNeverPublishesAnOrphanedMapPoint()
    {
        profile.set(null);task(0,8,8587,8597,10,30000);coach.onGameTick();assertFalse(coach.reading().active);verify(map,never()).add(any(WorldMapPoint.class));
    }
    @Test public void trialTimingRemainsUnknownWhenEnabledMidRunAndTargetsFollowTheNativeRumState()
    {
        trial(2,2,0,0);varps.put(4987,12000);Widget progress=widget(InterfaceID.SailingBtHud.BT_TRACKER_PROGRESS,"1 / 3 Rums delivered");GameObject pickup=object(ObjectID.SAILING_BT_TEMPOR_TANTRUM_SOUTH_LOC_PARENT,top),drop=object(ObjectID.SAILING_BT_TEMPOR_TANTRUM_NORTH_LOC_PARENT,top);coach.onGameTick();assertTrue(fact("Tempor Tantrum · Swordfish"));assertFalse(fact("Observed run"));assertTrue(coach.reading().targets.stream().anyMatch(t->t.object==pickup));assertFalse(coach.reading().targets.stream().anyMatch(t->t.object==drop));
        Widget rum=widget(InterfaceID.SailingBtHud.BT_PARTIAL_GFX,"");when(rum.getSpriteId()).thenReturn(7022);coach.onGameTick();assertTrue(coach.reading().targets.stream().anyMatch(t->t.object==drop));assertFalse(fact("Observed run"));
    }
    @Test public void witnessedTrialStartTracksSplitsAndCompletionRequiresNativeKc()
    {
        coach.onGameTick();trial(2,2,0,0);Widget progress=widget(InterfaceID.SailingBtHud.BT_TRACKER_PROGRESS,"0 / 3");coach.onGameTick();tick.set(101);varps.put(4987,12000);coach.onGameTick();tick.set(111);when(progress.getText()).thenReturn("1 / 3");coach.onGameTick();assertTrue(fact("Observed run · 6.0s"));assertTrue(fact("Latest split · 1 · 6.0s"));
        varps.put(4998,1);varbits.put(18410,0);coach.onGameTick();assertTrue(fact("Tempor Tantrum · completed"));
    }
    @Test public void leavingTheBoatFinalisesObservedTrialWithoutClaimingAnUnseenCompletion()
    {
        coach.onGameTick();trial(2,2,0,0);task(0,8,8587,8597,10,30000);coach.onGameTick();varbits.put(18410,0);when(player.getWorldView()).thenReturn(top);coach.onGameTick();assertTrue(fact("completion not observed"));
    }
    @Test public void ambiguousOrFutureTrialValuesDoNotProduceTrialAdvice()
    {
        trial(2,2,2,0);coach.onGameTick();assertFalse(fact("Trial progress"));assertFalse(fact("Tempor Tantrum"));trial(8,2,0,0);coach.onGameTick();assertFalse(fact("Tempor Tantrum"));
    }
    @Test public void finalJubblyOnlyHighlightsEmptyOutcropsAheadOfItsNativePosition()
    {
        trial(2,0,2,0);widget(InterfaceID.SailingBtHud.BT_TRACKER_PROGRESS,"2 / 3 Jubblies lured");Widget middle=widget(InterfaceID.SailingBtHud.BT_MIDDLE_CONTENT,"");Widget jubbly=mock(Widget.class),empty=mock(Widget.class);when(jubbly.getSpriteId()).thenReturn(6998);when(middle.getChild(3)).thenReturn(jubbly);when(middle.getChild(2)).thenReturn(empty);when(middle.getChild(4)).thenReturn(empty);
        GameObject behind=object(ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_2_PARENT,top),ahead=object(ObjectID.SAILING_BT_JUBBLY_JIVE_PILLAR_CLICKBOX_4_PARENT,top);coach.onGameTick();assertFalse(coach.reading().targets.stream().anyMatch(t->t.object==behind));assertTrue(coach.reading().targets.stream().anyMatch(t->t.object==ahead));
    }
    @Test public void wrongWorldAndProfileSwitchHidePreviousStateBeforeTheNextTick()
    {
        coach.onGameTick();when(client.getWorld()).thenReturn(302);assertFalse(coach.reading().active);coach.onGameTick();assertEquals(302,coach.reading().world);profile.set("rsprofile-b");assertFalse(coach.reading().active);coach.onGameTick();assertEquals("rsprofile-b",coach.reading().profile);
    }
    @Test public void hudFitsTheCanvasAndOwnDisplayOptionsPersistThroughGuardedStore() throws Exception
    {
        coach.onGameTick();Graphics2D g=new BufferedImage(500,330,BufferedImage.TYPE_INT_ARGB).createGraphics();try{java.awt.Dimension size=coach.hud().render(g);assertNotNull(size);assertTrue(size.width<=500);assertTrue(size.height<=330);}finally{g.dispose();}
        SwingUtilities.invokeAndWait(()->{JCheckBox hud=(JCheckBox)named(coach.createPanel(),"sailingCoachhud");hud.doClick();assertFalse(coach.preferences().hud);});verify(config,atLeastOnce()).setConfiguration(eq(SailingCoach.GROUP),eq(SailingCoach.KEY),any());
    }
    private boolean fact(String part){return coach.reading().facts.stream().anyMatch(s->s.contains(part));}
    private void trial(int in,int tempor,int jubbly,int gwenith){varbits.put(18410,in);varbits.put(18427,tempor);varbits.put(18432,jubbly);varbits.put(18437,gwenith);}
    private void task(int slot,int id,int from,int to,int amount,int item){varbits.put(19574+slot*3,id);SailingCoachTasksTest.courier(client,id,800+slot,from,to,amount,item);}
    private Widget widget(int id,String text){Widget widget=mock(Widget.class);when(widget.getText()).thenReturn(text);when(client.getWidget(id)).thenReturn(widget);return widget;}
    private GameObject object(int id,WorldView view){GameObject object=mock(GameObject.class);when(object.getId()).thenReturn(id);when(object.getWorldView()).thenReturn(view);GameObjectSpawned event=new GameObjectSpawned();event.setGameObject(object);coach.onGameObjectSpawned(event);return object;}
    private static Component named(Container parent,String name){for(Component child:parent.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container){Component found=named((Container)child,name);if(found!=null)return found;}}return null;}
}
