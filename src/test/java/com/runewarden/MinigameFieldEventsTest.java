/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinigameFieldEventsTest
{
    Client client;ConfigManager config;WorldView view;MinigameFieldEvents events;final AtomicLong clock=new AtomicLong(100000);final AtomicReference<String> profile=new AtomicReference<>("rsprofile-a");List<NPC> npcs=new ArrayList<>();Item[] items={};
    @Before @SuppressWarnings("unchecked") public void setup()
    {
        client=mock(Client.class);config=mock(ConfigManager.class);view=mock(WorldView.class);when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getWorld()).thenReturn(301);when(client.getTopLevelWorldView()).thenReturn(view);when(config.getRSProfileKey()).thenAnswer(e->profile.get());when(client.getBoostedSkillLevel(Skill.RUNECRAFT)).thenReturn(99);
        IndexedObjectSet<NPC> actors=mock(IndexedObjectSet.class);doReturn(actors).when(view).npcs();when(actors.iterator()).thenAnswer(e->npcs.iterator());ItemContainer inventory=mock(ItemContainer.class);when(inventory.getItems()).thenAnswer(e->items);when(client.getItemContainer(93)).thenReturn(inventory);events=new MinigameFieldEvents(client,config,new RunewardenDataStore(config,new Gson()),clock::get);events.reload();
    }
    @Test public void allTwelveRunesHaveDistinctNativeObjectSpriteAndActualRuneIdentities()
    {
        Set<Integer> objects=new HashSet<>(),sprites=new HashSet<>(),items=new HashSet<>();assertEquals(12,MinigameFieldEvents.RUNES.length);for(MinigameFieldEvents.Rune rune:MinigameFieldEvents.RUNES){assertTrue(objects.add(rune.object));assertTrue(sprites.add(rune.sprite));assertTrue(items.add(rune.item));assertSame(rune,MinigameFieldEvents.sprite(rune.sprite));assertSame(rune,MinigameFieldEvents.object(rune.object));}assertNull(MinigameFieldEvents.sprite(0));
    }
    @Test public void initialActiveAltarRetainsUnknownExpiryRatherThanStartingAFullTimer()
    {
        widget(746,23,4353);GameObject air=guardian(43701);events.observe(air,false);MinigameActivityCatalog.Snapshot result=events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertTrue(fact(result,"Elemental altar","Air · remaining unknown"));assertEquals(-1,events.markers().get(0).remaining(clock.get()));assertEquals(556,events.markers().get(0).rune);
    }
    @Test public void observedRuneSpriteTransitionStartsOneThirtyThreeTickWindowAndExpiresWithoutRestart()
    {
        Widget sprite=widget(746,23,0);events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));GameObject air=guardian(43701);events.observe(air,true);when(sprite.getSpriteId()).thenReturn(4353);events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertEquals(19800,events.markers().get(0).remaining(clock.get()));clock.addAndGet(600);events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertEquals(19200,events.markers().get(0).remaining(clock.get()));clock.addAndGet(30000);events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertEquals("?",events.markers().get(0).timer(clock.get()));
    }
    @Test public void inactiveNativeGuardianAnimationAndMismatchedRuneCategoryNeverHighlight()
    {
        widget(746,23,4354);events.observe(guardian(43705),true);events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertTrue(events.markers().isEmpty());widget(746,23,4353);GameObject air=object(43701);events.observe(air,true);events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertTrue(events.markers().isEmpty());
    }
    @Test public void lowRunecraftLevelIsMarkedWhileCurrentEssenceAddsOnlyTheActualActiveAltar()
    {
        items=new Item[]{new Item(26879,20)};widget(746,20,4364);when(client.getBoostedSkillLevel(Skill.RUNECRAFT)).thenReturn(70);events.observe(guardian(43708),true);MinigameActivityCatalog.Snapshot result=events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertTrue(fact(result,"Catalytic altar","requires 77"));assertEquals(MinigameFieldEvents.RED,events.markers().get(0).color);assertTrue(result.highlightObjectIds.contains(43708));assertFalse(result.highlightObjectIds.contains(43707));
    }
    @Test public void portalExpiryRequiresAWitnessedHiddenToVisibleTransition()
    {
        Widget portal=widget(746,28,0);events.observe(object(43729),false);events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertEquals(-1,events.markers().get(0).remaining(clock.get()));when(portal.isHidden()).thenReturn(true);events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertTrue(events.markers().isEmpty());when(portal.isHidden()).thenReturn(false);events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertEquals(25800,events.markers().get(0).remaining(clock.get()));
    }
    @Test public void supplyHighlightsRequireActualInventoryAndKnownNativeGuardianCapacity()
    {
        items=new Item[]{new Item(26886,1),new Item(1755,1),new Item(26880,1)};Widget count=widget(746,30,0);when(count.getText()).thenReturn("9/10");MinigameActivityCatalog.Snapshot result=events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertTrue(result.highlightObjectIds.containsAll(Set.of(43722,43723,43732)));assertTrue(result.highlightNpcIds.contains(11403));
        when(count.getText()).thenReturn("10/10");items=new Item[]{new Item(26882,1),new Item(26886,1),new Item(1755,1)};result=events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertFalse(result.highlightObjectIds.contains(43732));assertFalse(result.highlightObjectIds.contains(43722));when(count.getText()).thenReturn("unknown");result=events.enrich(snapshot(MinigameActivityCatalog.Activity.GOTR));assertFalse(result.highlightObjectIds.contains(43722));
    }
    @Test public void doubleSpotTimerUsesTheActualSpawnAndUnknownForPreexistingNpc()
    {
        widget(437,2,0);NPC spot=npc(10569);events.npcSpawned(spot);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertEquals(24000,events.markers().get(0).remaining(clock.get()));clock.addAndGet(1000);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertEquals(23000,events.markers().get(0).remaining(clock.get()));
        events.clear();events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertEquals(-1,events.markers().get(0).remaining(clock.get()));npcs.clear();events.npcDespawned(spot);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertTrue(events.markers().isEmpty());
    }
    @Test public void spawnedFireCloudAndSpreadHaveTheirOwnPinnedWindows()
    {
        widget(437,2,0);GameObject fire=object(37582),cloud=object(41006),spread=object(41007);events.observe(fire,true);events.observe(cloud,true);events.observe(spread,true);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertEquals(24000,marker(fire).duration);assertEquals(9600,marker(cloud).duration);assertEquals(1200,marker(spread).duration);events.remove(cloud);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertFalse(events.markers().stream().anyMatch(m->m.object==cloud));
    }
    @Test public void enablingBesideExistingFireDoesNotInventItsSpawnOrSpreadDeadline()
    {
        widget(437,2,0);GameObject fire=object(37582);events.observe(fire,false);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertEquals(-1,marker(fire).remaining(clock.get()));assertEquals("?",marker(fire).timer(clock.get()));
    }
    @Test public void nativeWaveMessageTetherBitfieldAndBrokenMastsChooseCurrentSafetyColour()
    {
        widget(437,2,0);GameObject intact=object(41352),broken=object(40996);events.observe(intact,false);events.observe(broken,false);events.message(ChatMessageType.PUBLICCHAT,"A colossal wave closes in...");events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertTrue(events.markers().isEmpty());
        events.message(ChatMessageType.GAMEMESSAGE,"A colossal wave closes in...");MinigameActivityCatalog.Snapshot result=events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertEquals(7800,marker(intact).duration);assertEquals(MinigameFieldEvents.RED,marker(broken).color);assertEquals("Tether for the incoming wave",result.steps.get(0).title);
        when(client.getVarbitValue(11895)).thenReturn(4);result=events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertEquals(MinigameFieldEvents.GREEN,marker(intact).color);assertTrue(fact(result,"Wave safety","Tethered"));events.message(ChatMessageType.GAMEMESSAGE,"You remain safely secured as the wave washes over you.");events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertTrue(events.markers().isEmpty());
    }
    @Test public void waveDeadlineExpiresWithoutAnyHitChatAndRestoresTheNormalLesson()
    {
        widget(437,2,0);GameObject intact=object(41352);events.observe(intact,false);events.message(ChatMessageType.GAMEMESSAGE,"A colossal wave closes in...");events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));clock.addAndGet(7800);MinigameActivityCatalog.Snapshot result=events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertEquals("Current lesson",result.steps.get(0).title);assertTrue(events.markers().isEmpty());assertFalse(fact(result,"Wave safety","UNTETHERED"));assertFalse(fact(result,"Wave impact","?"));
    }
    @Test public void expiredEstimatesBecomeUnknownWhileTheObservedObjectCanRemainHighlighted()
    {
        widget(437,2,0);GameObject cloud=object(41006);events.observe(cloud,true);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));clock.addAndGet(9600);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertEquals(-1,marker(cloud).remaining(clock.get()));assertEquals("?",marker(cloud).timer(clock.get()));
    }
    @Test public void carriedDamageIsExactForRawCookedAndCrystalFishAndUsesLongArithmetic()
    {
        items=new Item[]{new Item(25564,2),new Item(25565,3),new Item(25566,4)};MinigameActivityCatalog.Snapshot result=events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertTrue(fact(result,"Carried damage","105"));items=new Item[]{new Item(25565,Integer.MAX_VALUE)};result=events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertTrue(fact(result,"Carried damage","32212254705"));
    }
    @Test public void eventTimersDisappearImmediatelyAcrossAccountsWorldsAndActivityBoundaries()
    {
        widget(437,2,0);events.observe(object(37582),true);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertFalse(events.markers().isEmpty());profile.set("rsprofile-b");assertTrue(events.markers().isEmpty());events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));assertTrue(events.markers().isEmpty());events.observe(object(37582),true);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));when(client.getWorld()).thenReturn(302);assertTrue(events.markers().isEmpty());events.enrich(MinigameActivityCatalog.Snapshot.EMPTY);assertTrue(events.markers().isEmpty());
    }
    @Test public void sceneMarkersNeverRenderObjectsOrNpcsOnAnotherPlane()
    {
        widget(437,2,0);GameObject fire=object(37582);when(fire.getPlane()).thenReturn(1);events.observe(fire,true);NPC spot=npc(10569);when(spot.getWorldLocation()).thenReturn(new WorldPoint(3500,9500,1));events.npcSpawned(spot);events.enrich(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS));java.awt.Graphics2D g=new java.awt.image.BufferedImage(320,240,java.awt.image.BufferedImage.TYPE_INT_ARGB).createGraphics();try{events.render(g,true,true,null);}finally{g.dispose();}verify(fire,never()).getCanvasTextLocation(any(),anyString(),anyInt());verify(spot,never()).getCanvasTextLocation(any(),anyString(),anyInt());
    }
    @Test public void timerModesHaveSafeUnknownSecondsAndCeilingTickLabels()
    {
        MinigameFieldEvents.Marker m=new MinigameFieldEvents.Marker(null,null,"",MinigameFieldEvents.GOLD,MinigameFieldEvents.Mode.TICKS,100000,7800,0);assertEquals("~13t",m.timer(100000));assertEquals("~13t",m.timer(100001));assertEquals("~1t",m.timer(107799));assertEquals("?",m.timer(107800));
    }
    @Test public void timerControlsAreEditableAndCannotMutateAfterModuleStop() throws Exception
    {
        SwingUtilities.invokeAndWait(()->{JComboBox<?> mode=(JComboBox<?>)named(events.controls(),"minigameFieldfire");mode.setSelectedItem(MinigameFieldEvents.Mode.TICKS);assertEquals(MinigameFieldEvents.Mode.TICKS,events.preferences().fire);((JCheckBox)named(events.controls(),"minigameFieldIcons")).doClick();assertFalse(events.preferences().runeIcons);});events.setEnabled(false);SwingUtilities.invokeAndWait(()->assertFalse(named(events.controls(),"minigameFieldfire").isEnabled()));
    }
    private MinigameFieldEvents.Marker marker(GameObject object){return events.markers().stream().filter(m->m.object==object).findFirst().orElseThrow();}
    private boolean fact(MinigameActivityCatalog.Snapshot result,String label,String part){return result.facts.stream().anyMatch(f->f.label.equals(label)&&f.value.contains(part));}
    private static MinigameActivityCatalog.Snapshot snapshot(MinigameActivityCatalog.Activity activity){return new MinigameActivityCatalog.Snapshot(activity,"Test","test",List.of(),List.of(new MinigameActivityCatalog.Step("Current lesson","Current instruction")),Set.of(),List.of(),true);}
    private GameObject object(int id){GameObject object=mock(GameObject.class);when(object.getId()).thenReturn(id);when(object.getWorldView()).thenReturn(view);when(object.getWorldLocation()).thenReturn(new WorldPoint(3500,9500,0));return object;}
    private GameObject guardian(int id){GameObject guardian=object(id);DynamicObject render=mock(DynamicObject.class);Animation animation=mock(Animation.class);when(animation.getId()).thenReturn(9363);when(render.getAnimation()).thenReturn(animation);when(guardian.getRenderable()).thenReturn(render);return guardian;}
    private Widget widget(int group,int child,int sprite){Widget widget=mock(Widget.class);when(widget.getSpriteId()).thenReturn(sprite);when(client.getWidget(group,child)).thenReturn(widget);return widget;}
    private NPC npc(int id){NPC npc=mock(NPC.class);when(npc.getId()).thenReturn(id);when(npc.getWorldView()).thenReturn(view);npcs.add(npc);return npc;}
    private static Component named(Container parent,String name){for(Component child:parent.getComponents()){if(name.equals(child.getName()))return child;if(child instanceof Container){Component found=named((Container)child,name);if(found!=null)return found;}}return null;}
}
