/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.StatChanged;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class HunterRumourTest
{
    @Test public void pinnedHubReferenceContainsAllMastersCreaturesAndSpawnGroups()
    {HunterRumourCatalog data=new HunterRumourCatalog(new Gson());assertEquals(6,data.masters().size());assertEquals(30,data.rumours().size());assertTrue(data.rumours().stream().mapToInt(r->r.locations.size()).sum()==438);assertEquals(13126,data.master(13126).id);assertEquals(91,data.master(13126).level());assertEquals(46,data.master(13121).level());assertEquals(39,data.rumour("RED_CHINCHOMPA").locations.size());assertEquals(39,data.rumour("RED_CHINCHOMPA_2").locations.size());assertTrue(data.rumour("RED_CHINCHOMPA").locations.stream().anyMatch(l->l.name.contains("Tlati")));}
    @Test public void dialogueAliasesAndHunterNamesAreMatchedWithoutSubstringFalsePositives()
    {HunterRumourCatalog data=new HunterRumourCatalog(new Gson());assertEquals("RED_CHINCHOMPA_2",data.referencedRumour("Find a red chinchompa").id);assertEquals("RED_CHINCHOMPA",data.referencedRumour("Find a carnivorous chinchompa").id);assertNull(data.referencedMaster("Buy some tobacco"));assertEquals("Aco",data.referencedMaster("Speak to Aco").name);}
    @Test public void trapReferenceUsesRealOutfitThresholdsAndDoesNotBankAnIconAsEquipment()
    {HunterRumourCatalog data=new HunterRumourCatalog(new Gson());assertEquals(100,data.trap("BOX_TRAP").threshold(0));assertEquals(94,data.trap("BOX_TRAP").threshold(4));assertEquals(97,data.trap("BOX_TRAP").threshold(2));assertTrue(data.supplies(data.rumour("SPOTTED_KEBBIT")).contains(995));assertFalse(data.supplies(data.rumour("SPOTTED_KEBBIT")).contains(data.trap("FALCONRY").itemId));assertFalse(data.supplies(data.rumour("HERBIBOAR")).contains(data.trap("NOOSE_HERBIBOAR").itemId));}
    @Test public void nativeWhistleUpdatesCurrentAndKeepsOtherMasterRumours()
    {Fixture f=new Fixture();f.whistle("Sunlight Antelope","Wolf");f.whistle("Grey Chinchompa","Aco");HunterRumourCoach.State state=f.coach.reading();assertTrue(state.verified);assertEquals("GREY_CHINCHOMPA",state.rumour.id);assertEquals(2,state.masters.size());assertEquals("SUNLIGHT_ANTELOPE",state.masters.get(13126));}
    @Test public void publicChatCannotSetAnAssignmentOrCompleteIt()
    {Fixture f=new Fixture();f.message(ChatMessageType.PUBLICCHAT,"Your current rumour target is Sunlight Antelope, from Wolf.");assertNull(f.coach.reading().rumour);f.whistle("Sunlight Antelope","Wolf");f.message(ChatMessageType.PUBLICCHAT,Fixture.RARE);assertFalse(f.coach.reading().ready);f.message(ChatMessageType.GAMEMESSAGE,Fixture.RARE);assertTrue(f.coach.reading().ready);}
    @Test public void nativeGuildDialogueIgnoresIntroAndDoesNotMistakeGilmanOfferForAcceptance()
    {Fixture f=new Fixture();f.position.set(new WorldPoint(1555,9455,0));f.message(ChatMessageType.DIALOG,"Guild Hunter Wolf (Master)|I stopped off for a bit of hunting first, a sunlight antelope.");assertNull(f.coach.reading().rumour);f.message(ChatMessageType.DIALOG,"Guild Hunter Wolf (Master)|Your target is a sunlight antelope.");assertEquals("SUNLIGHT_ANTELOPE",f.coach.reading().rumour.id);f.message(ChatMessageType.DIALOG,"Guild Hunter Gilman (Novice)|A swamp lizard: would you prefer that one, or a new one entirely?");assertEquals("Wolf",f.coach.reading().master.name);assertEquals("SWAMP_LIZARD",f.coach.reading().masters.get(13121));}
    @Test public void matchingXpOnlyCountsForAConfirmedNearbyCurrentCreature()
    {Fixture f=new Fixture();f.whistle("Swamp Lizard","Cervus");f.position.set(f.data.rumour("SWAMP_LIZARD").locations.get(0).point());f.coach.onStatChanged(new StatChanged(Skill.HUNTER,1152,99,99));assertEquals(1,f.coach.reading().catches);f.coach.onStatChanged(new StatChanged(Skill.HUNTER,1200,99,99));assertEquals(1,f.coach.reading().catches);f.position.set(new WorldPoint(3200,3200,0));f.coach.onStatChanged(new StatChanged(Skill.HUNTER,1352,99,99));assertEquals(1,f.coach.reading().catches);}
    @Test public void inventoryRarePieceIsReportedAsCarriedWithoutInventingCompletion()
    {Fixture f=new Fixture();f.whistle("Swamp Lizard","Cervus");f.nativeState.container(net.runelite.api.gameval.InventoryID.INV,new Item[]{new Item(f.data.rumour("SWAMP_LIZARD").rareItem,1)});f.tick(5);assertTrue(f.coach.reading().rareCarried);assertFalse(f.coach.reading().ready);}
    @Test public void wornGuildItemsChangeReferenceThresholdFromActualEquipment()
    {Fixture f=new Fixture();f.whistle("Grey Chinchompa","Aco");f.nativeState.container(net.runelite.api.gameval.InventoryID.WORN,new Item[]{new Item(net.runelite.api.ItemID.GUILD_HUNTER_HEADWEAR,1),new Item(net.runelite.api.ItemID.GUILD_HUNTER_TOP,1)});f.tick(5);assertEquals(2,f.coach.reading().outfitPieces);assertEquals(97,f.coach.reading().referenceThreshold);}
    @Test public void handoffClearsOnlyTheSpeakingMastersRecordAndDoesNotDoubleCount()
    {Fixture f=new Fixture();f.whistle("Sunlight Antelope","Wolf");f.whistle("Grey Chinchompa","Aco");f.position.set(new WorldPoint(1555,9455,0));f.message(ChatMessageType.DIALOG,"Guild Hunter Wolf (Master)|Here's your reward.");assertFalse(f.coach.reading().masters.containsKey(13126));assertEquals("Aco",f.coach.reading().master.name);assertEquals(1,f.coach.reading().completions);f.message(ChatMessageType.DIALOG,"Guild Hunter Wolf (Master)|Here's your reward.");assertEquals(1,f.coach.reading().completions);}
    @Test public void profileChangeImmediatelyHidesNativeAndRememberedRumours()
    {Fixture f=new Fixture();f.whistle("Sunlight Antelope","Wolf");f.nativeState.profile.set("rsprofile.other");assertNull(f.coach.reading().profile);f.tick(1);assertNull(f.coach.reading().rumour);assertTrue(f.coach.reading().masters.isEmpty());}
    @Test public void reconnectUsesLastSeenAndFreshConfirmationDoesNotReuseOldCompletion()
    {Fixture f=new Fixture();f.whistle("Sunlight Antelope","Wolf");f.message(ChatMessageType.GAMEMESSAGE,Fixture.RARE);assertTrue(f.coach.reading().ready);f.coach.stop();f.coach.start();f.tick(1);assertNotNull(f.coach.reading().rumour);assertFalse(f.coach.reading().verified);assertFalse(f.coach.reading().ready);f.whistle("Sunlight Antelope","Wolf");assertTrue(f.coach.reading().verified);assertFalse(f.coach.reading().ready);}
    @Test public void npcTargetsCannotCrossASceneOrProfileBoundary()
    {Fixture f=new Fixture();WorldView view=mock(WorldView.class);net.runelite.api.Scene scene=mock(net.runelite.api.Scene.class);when(view.getScene()).thenReturn(scene);NPC npc=mock(NPC.class);when(npc.getId()).thenReturn(f.data.rumour("SWAMP_LIZARD").npcId);when(npc.getWorldView()).thenReturn(view);@SuppressWarnings("unchecked") net.runelite.api.IndexedObjectSet<NPC>npcs=mock(net.runelite.api.IndexedObjectSet.class);when(npcs.iterator()).thenAnswer(call->List.of(npc).iterator());doReturn(npcs).when(view).npcs();when(f.nativeState.client.getTopLevelWorldView()).thenReturn(view);f.whistle("Swamp Lizard","Cervus");assertEquals(1,f.coach.sceneTargets().size());when(view.getBaseX()).thenReturn(10);assertTrue(f.coach.sceneTargets().isEmpty());}
    @Test public void sidebarAndMaxScaleHudRemainBoundedAndSwingDoesNotReadNativeClient() throws Exception
    {
        Fixture f=new Fixture();f.whistle("Carnivorous Chinchompa","Aco");SwingUtilities.invokeAndWait(()->{JPanel panel=f.coach.createPanel();panel.setSize(206,20000);layout(panel);assertFits(panel);clearInvocations(f.nativeState.client);f.coach.createPanel();verifyNoInteractions(f.nativeState.client);});
        HudPreferences.Style high=new HudPreferences.Style(180,18,90,Color.YELLOW,Color.WHITE,Color.BLACK,HudPreferences.Visibility.ALWAYS,HudPreferences.Frame.SQUARE,HudPreferences.Values.CURRENT_MAX);String encoded=HudPreferences.encode(Map.of("hunter-rumours",high));
        RunewardenConfig config=new RunewardenConfig(){@Override public String hudWidgetSettings(){return encoded;}};when(f.nativeState.client.getCanvasWidth()).thenReturn(300);when(f.nativeState.client.getCanvasHeight()).thenReturn(250);
        HunterRumourOverlay overlay=new HunterRumourOverlay(f.nativeState.client,config,f.coach);BufferedImage image=new BufferedImage(300,250,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D graphics=image.createGraphics();Dimension size=overlay.render(graphics);graphics.dispose();assertNotNull(size);assertTrue(size.width<=284);assertTrue(size.height<=226);assertNotEquals(0,image.getRGB(10,10));overlay.setHudLocked(true);assertFalse(overlay.isMovable());
    }
    @Test public void queuedManualReferenceCannotWriteIntoAnotherProfile() throws Exception
    {Fixture f=new Fixture();JPanel[]panel=new JPanel[1];SwingUtilities.invokeAndWait(()->panel[0]=f.coach.createPanel());List<Runnable>work=new java.util.ArrayList<>();doAnswer(call->{work.add(call.getArgument(0));return null;}).when(f.thread).invokeLater(any(Runnable.class));SwingUtilities.invokeAndWait(()->button(panel[0],"Save manual reference").doClick());assertFalse(work.isEmpty());f.nativeState.profile.set("rsprofile.other");f.tick(1);work.forEach(Runnable::run);assertFalse(f.nativeState.settings.containsKey("runewarden|rsprofile.other|"+HunterRumourCoach.KEY));}
    @Test public void delayedPanelClicksCannotOverwriteTheNextAccountsReferences() throws Exception
    {
        Fixture f=new Fixture();javax.swing.JButton[]oldSave=new javax.swing.JButton[1];
        SwingUtilities.invokeAndWait(()->oldSave[0]=button(f.coach.createPanel(),"Save manual reference"));
        f.nativeState.profile.set("rsprofile.other");f.tick(1);
        SwingUtilities.invokeAndWait(()->oldSave[0].doClick());
        assertTrue(f.coach.reading().masters.isEmpty());
        assertFalse(f.nativeState.settings.containsKey("runewarden|rsprofile.other|"+HunterRumourCoach.KEY));
    }
    @Test public void queuedRouteAndBankActionsKeepTheirOriginalAccount() throws Exception
    {
        Fixture f=new Fixture();f.whistle("Swamp Lizard","Cervus");AtomicInteger routed=new AtomicInteger(),banked=new AtomicInteger();
        f.coach.setRouteAction(point->routed.incrementAndGet());f.coach.setBankFocusAction(ids->banked.incrementAndGet());
        JPanel[]panel=new JPanel[1];SwingUtilities.invokeAndWait(()->panel[0]=f.coach.createPanel());
        List<Runnable>work=new java.util.ArrayList<>();doAnswer(call->{work.add(call.getArgument(0));return null;}).when(f.thread).invokeLater(any(Runnable.class));
        SwingUtilities.invokeAndWait(()->{button(panel[0],"Preview route").doClick();button(panel[0],"Focus hunting supplies in bank").doClick();});
        f.nativeState.profile.set("rsprofile.other");f.tick(1);work.forEach(Runnable::run);
        assertEquals(0,routed.get());assertEquals(0,banked.get());
    }
    @Test public void visiblePanelRefreshPreservesManualChoicesButAccountChangesResetThem() throws Exception
    {
        Fixture f=new Fixture();JPanel[]panel=new JPanel[1];
        try
        {
            SwingUtilities.invokeAndWait(()->{panel[0]=f.coach.createPanel();panel[0].addNotify();assertTrue(panel[0].isShowing());combo(panel[0],"hunter-rumours.manual-master").setSelectedIndex(4);combo(panel[0],"hunter-rumours.manual-rumour").setSelectedIndex(12);});
            f.tick(5);SwingUtilities.invokeAndWait(()->{assertEquals(4,combo(panel[0],"hunter-rumours.manual-master").getSelectedIndex());assertEquals(12,combo(panel[0],"hunter-rumours.manual-rumour").getSelectedIndex());});
            f.nativeState.profile.set("rsprofile.other");f.tick(1);
            SwingUtilities.invokeAndWait(()->{assertEquals(0,combo(panel[0],"hunter-rumours.manual-master").getSelectedIndex());assertEquals(0,combo(panel[0],"hunter-rumours.manual-rumour").getSelectedIndex());});
        }
        finally{SwingUtilities.invokeAndWait(()->{if(panel[0]!=null)panel[0].removeNotify();});f.coach.stop();}
    }
    private static javax.swing.JComboBox<?>combo(Container parent,String name)
    {for(Component child:parent.getComponents()){if(child instanceof javax.swing.JComboBox&&name.equals(child.getName()))return(javax.swing.JComboBox<?>)child;if(child instanceof Container){javax.swing.JComboBox<?>found=combo((Container)child,name);if(found!=null)return found;}}return null;}
    private static void layout(Container parent){for(int pass=0;pass<3;pass++){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container)layout((Container)child);}}
    private static void assertFits(Container parent){for(Component child:parent.getComponents())if(child.isVisible()){assertTrue(child.getClass()+" horizontal clipping",child.getX()+child.getWidth()<=parent.getWidth()+1);if(child instanceof Container&&!(child instanceof javax.swing.JComboBox))assertFits((Container)child);}}
    private static javax.swing.JButton button(Container parent,String text){for(Component child:parent.getComponents()){if(child instanceof javax.swing.JButton&&text.equals(((javax.swing.JButton)child).getText()))return(javax.swing.JButton)child;if(child instanceof Container){javax.swing.JButton found=button((Container)child,text);if(found!=null)return found;}}return null;}
    static final class Fixture
    {
        static final String RARE="You find a rare piece of the creature! You should take it back to the Hunter Guild.";
        final OwnershipLedgerTest.Fixture nativeState=new OwnershipLedgerTest.Fixture();final HunterRumourCatalog data=new HunterRumourCatalog(new Gson());final net.runelite.client.callback.ClientThread thread=mock(net.runelite.client.callback.ClientThread.class);
        final AtomicReference<WorldPoint>position=new AtomicReference<>(new WorldPoint(3200,3200,0));final HunterRumourCoach coach;
        Fixture(){when(nativeState.client.getLocalPlayer().getWorldLocation()).thenAnswer(call->position.get());when(nativeState.client.getRealSkillLevel(Skill.HUNTER)).thenReturn(99);when(nativeState.client.getSkillExperience(Skill.HUNTER)).thenReturn(1000);for(HunterRumourCatalog.Master master:data.masters()){NPCComposition definition=mock(NPCComposition.class);when(definition.getName()).thenReturn("Guild Hunter "+master.name+" ("+master.tier.substring(0,1)+master.tier.substring(1).toLowerCase()+")");when(nativeState.client.getNpcDefinition(master.id)).thenReturn(definition);}doAnswer(call->{((Runnable)call.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));coach=new HunterRumourCoach(nativeState.client,thread,nativeState.config,new RunewardenDataStore(nativeState.config,new Gson()),data,null);coach.start();tick(1);}
        void tick(int count){for(int i=0;i<count;i++)coach.onGameTick();}
        void whistle(String target,String master){message(ChatMessageType.GAMEMESSAGE,"Your current rumour target is "+target+", assigned by "+master+".");}
        void message(ChatMessageType type,String text){coach.onChatMessage(new ChatMessage(null,type,"",text,"",0));}
    }
}
