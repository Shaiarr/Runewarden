/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuideLifecycleTest
{
    @Test public void completingTheLastStepClosesTheGuideAndOwnedHud() throws Exception
    {
        Fixture f=new Fixture();AtomicReference<String> finished=new AtomicReference<>();f.guides.setCompletionAction(finished::set);
        SwingUtilities.invokeAndWait(()->{
            f.open();int count=f.guides.selectedGuide().flattened().size();assertNotNull(f.coach.render(f.graphics));
            for(int i=0;i<count;i++)button(f.panel,"Complete step →").doClick();
            assertNull(f.guides.selectedGuide());assertNull(f.guides.currentStep());assertNull(f.coach.render(f.graphics));
            assertEquals("training:giants-foundry",finished.get());
            f.guides.openForRun("training:giants-foundry");assertEquals("prepare:1",f.guides.currentStep().id);
            assertTrue(button(f.panel,"Complete step →").isEnabled());assertNotNull(f.coach.render(f.graphics));
        });f.graphics.dispose();
    }
    @Test public void startingARepeatableRunRestartsAPreviouslyCompletedSavedGuide() throws Exception
    {
        Fixture f=new Fixture();String id="training:giants-foundry";
        java.util.LinkedHashSet<String> done=new java.util.LinkedHashSet<>();
        for(GuideCatalog.DisplayStep step:new GuideCatalog(new Gson()).entry(id).flattened())done.add(step.step.id);
        when(f.config.getRSProfileConfiguration("runewarden",GuideProgress.completedKey(id),String.class)).thenReturn(GuideProgress.encode(done));
        SwingUtilities.invokeAndWait(()->{f.panel=f.guides.createPanel();f.guides.openForRun(id);});
        SwingUtilities.invokeAndWait(()->{assertNotNull(f.guides.selectedGuide());assertEquals("prepare:1",f.guides.currentStep().id);assertTrue(button(f.panel,"Complete step →").isEnabled());});
        verify(f.config).setRSProfileConfiguration("runewarden",GuideProgress.completedKey(id),"");f.graphics.dispose();
    }
    @Test public void backgroundDismissalIsImmediateAndCannotBlankANewerGuide() throws Exception
    {
        Fixture f=new Fixture();
        SwingUtilities.invokeAndWait(()->{
            f.open();Thread dismiss=new Thread(f.guides::clearSelection);dismiss.start();
            try{dismiss.join(2000);}catch(InterruptedException e){throw new AssertionError(e);}
            assertFalse(dismiss.isAlive());assertNull(f.guides.selectedGuide());assertNull(f.coach.render(f.graphics));
            f.guides.openGuide("training:wintertodt");assertNotNull(f.guides.selectedGuide());
        });
        SwingUtilities.invokeAndWait(()->assertEquals("training:wintertodt",f.guides.selectedGuide().id));f.graphics.dispose();
    }
    @Test public void selectedActivityGuideFollowsNativeTaskChangesWithoutCompletingUnobservedChecklistSteps() throws Exception
    {
        Fixture f=new Fixture();AtomicReference<MinigameActivityCatalog.Snapshot> activity=new AtomicReference<>(MinigameActivityCatalog.Snapshot.EMPTY);
        f.guides.setActivitySupplier(activity::get);SwingUtilities.invokeAndWait(f::open);
        activity.set(task("Pour the mould","Fill the crucible and pour the sword."));f.guides.onGameTick();
        SwingUtilities.invokeAndWait(()->assertTrue(contains(f.panel,"Fill the crucible and pour the sword.")));
        activity.set(task("Work at the trip hammer","Hammer the sword in the working band."));f.guides.onGameTick();
        SwingUtilities.invokeAndWait(()->{assertTrue(contains(f.panel,"Hammer the sword in the working band."));assertEquals("method:2",f.guides.currentStep().id);assertNotNull(f.coach.render(f.graphics));});
        f.guides.clearSelection();f.guides.onGameTick();SwingUtilities.invokeAndWait(()->assertNull(f.coach.render(f.graphics)));f.graphics.dispose();
    }
    @Test public void endRunInvalidatesEveryOpenRequestAlreadyQueuedByTheClientThread() throws Exception
    {
        for(int action=0;action<3;action++)
        {
            Fixture f=new Fixture();final int request=action;
            SwingUtilities.invokeAndWait(()->{
                f.open();
                onBackground(()->{
                    if(request==0)f.guides.openGuide("training:wintertodt");
                    else if(request==1)f.guides.openForRun("training:wintertodt");
                    else f.guides.openSection("training:wintertodt",1);
                });
                onBackground(f.guides::clearSelection);
                assertNull(f.guides.selectedGuide());assertNull(f.coach.render(f.graphics));
            });
            SwingUtilities.invokeAndWait(()->{assertNull(f.guides.selectedGuide());assertNull(f.coach.render(f.graphics));});
            f.graphics.dispose();
        }
    }
    @Test public void selectedStepAutomaticallyRoutesAndDismissalClearsOnlyTheGuideRoute() throws Exception
    {
        Fixture f=new Fixture();SwingUtilities.invokeAndWait(f::open);SwingUtilities.invokeAndWait(()->{});
        SwingUtilities.invokeAndWait(()->{
            GuideCatalog.Entry entry=f.guides.selectedGuide();GuideCatalog.Step first=f.guides.currentStep();
            assertEquals(first.location.point(),targetPoint(f.paths().get(0)));
            button(f.panel,"Next step").doClick();
            assertEquals(2,f.paths().size());assertEquals(f.guides.currentStep().location.point(),targetPoint(f.paths().get(1)));
            f.guides.clearSelection();
            net.runelite.client.events.PluginMessage check=f.sent().get(f.sent().size()-1);
            assertEquals("getTarget",check.getName());
            f.navigation.onPluginMessage(new net.runelite.client.events.PluginMessage("shortestpath","currentTarget",java.util.Map.of("id",check.getData().get("id"),"set",true,"target",f.navigation.snapshot().target)));
            assertEquals("clear",f.sent().get(f.sent().size()-1).getName());
        });f.graphics.dispose();
    }
    @Test public void anInitiallyBlockedGuideRouteStartsOnceWhenTheNpcConversationEnds() throws Exception
    {
        Fixture f=new Fixture();doReturn(mock(net.runelite.api.NPC.class)).when(f.player).getInteracting();
        SwingUtilities.invokeAndWait(()->{f.panel=f.guides.createPanel();f.guides.openGuide("training:giants-foundry");});SwingUtilities.invokeAndWait(()->{});
        verify(f.events,never()).post(any());
        doReturn(null).when(f.player).getInteracting();f.guides.onGameTick();f.guides.onGameTick();
        assertEquals(1,f.paths().size());f.graphics.dispose();
    }
    @Test public void enablingShortestPathRoutesTheAlreadySelectedStepOnce() throws Exception
    {
        Fixture f=new Fixture();SwingUtilities.invokeAndWait(f::open);
        f.active.set(false);SwingUtilities.invokeAndWait(()->f.guides.openGuide("training:giants-foundry"));SwingUtilities.invokeAndWait(()->{});
        clearInvocations(f.events);f.active.set(true);
        f.guides.onGameTick();f.guides.onGameTick();
        assertEquals(1,f.paths().size());f.graphics.dispose();
    }
    @Test public void blockedGuideRetriesWhenUnlockFactsBecomeReadyWithoutAButtonOrReceiverTransition() throws Exception
    {
        Fixture f=new Fixture();java.util.concurrent.atomic.AtomicInteger mining=new java.util.concurrent.atomic.AtomicInteger(1);
        f.catalog.entry("training:giants-foundry").requirementDetails=List.of(new Gson().fromJson("{\"kind\":\"skill\",\"key\":\"mining\",\"value\":55,\"label\":\"55 Mining\"}",com.google.gson.JsonObject.class));
        f.navigation.setFactsSupplier(()->new GoalCatalog.Facts(java.util.Map.of(),java.util.Map.of(net.runelite.api.Skill.MINING,mining.get()),java.util.Map.of(),100));
        SwingUtilities.invokeAndWait(f::open);SwingUtilities.invokeAndWait(()->{});assertEquals("Unlocks needed",f.navigation.snapshot().status);
        verify(f.events,never()).post(any());mining.set(55);f.guides.onGameTick();f.guides.onGameTick();
        assertEquals(1,f.paths().size());f.graphics.dispose();
    }
    @Test public void aManualReplacementWhileTravelIsBlockedPreventsAutomaticRetryFromOverwritingIt() throws Exception
    {
        Fixture f=new Fixture();doReturn(mock(net.runelite.api.NPC.class)).when(f.player).getInteracting();
        SwingUtilities.invokeAndWait(f::open);SwingUtilities.invokeAndWait(()->{});verify(f.events,never()).post(any());
        f.navigation.onPluginMessage(new net.runelite.client.events.PluginMessage("shortestpath","path",java.util.Map.of("target",new net.runelite.api.coords.WorldPoint(3300,3300,0))));
        doReturn(null).when(f.player).getInteracting();f.guides.onGameTick();f.guides.onGameTick();
        verify(f.events,never()).post(any());f.graphics.dispose();
    }
    @Test public void restartingTravelAutomaticallyRestoresTheCurrentGuideRouteOnce() throws Exception
    {
        Fixture f=new Fixture();SwingUtilities.invokeAndWait(f::open);SwingUtilities.invokeAndWait(()->{});assertEquals(1,f.paths().size());
        f.navigation.stop();f.navigation.start();f.guides.onGameTick();f.guides.onGameTick();
        assertEquals(2,f.paths().size());f.graphics.dispose();
    }
    @Test public void activeFarmTravelCanSuspendGuideRouteAttemptsWithoutLosingTheSelectedStep() throws Exception
    {
        Fixture f=new Fixture();java.util.concurrent.atomic.AtomicBoolean permitted=new java.util.concurrent.atomic.AtomicBoolean(false);
        f.guides.setAutomaticTravelAllowed(permitted::get);SwingUtilities.invokeAndWait(f::open);SwingUtilities.invokeAndWait(()->{});f.guides.onGameTick();
        verify(f.events,never()).post(any());assertNotNull(f.guides.currentStep());
        permitted.set(true);f.guides.onGameTick();f.guides.onGameTick();assertEquals(1,f.paths().size());f.graphics.dispose();
    }

    private static Object targetPoint(net.runelite.client.events.PluginMessage message)
    {Object target=message.getData().get("target");return target instanceof java.util.Map?((java.util.Map<?,?>)target).get("point"):target;}
    private static void onBackground(Runnable action)
    {
        Thread thread=new Thread(action);thread.start();
        try{thread.join(2000);}catch(InterruptedException error){throw new AssertionError(error);}
        assertFalse(thread.isAlive());
    }
    @Test public void realFoundryNativeStagesMoveTheSelectedChecklistAndPreserveUnobservedMilestones() throws Exception
    {
        Fixture f=new Fixture();MinigameActivityCatalog detector=new MinigameActivityCatalog();java.util.Map<Integer,Integer> values=new java.util.HashMap<>();
        net.runelite.api.widgets.Widget foundry=mock(net.runelite.api.widgets.Widget.class);
        doReturn(foundry).when(f.client).getWidget(754,51);
        doAnswer(call->values.getOrDefault(call.getArgument(0),0)).when(f.client).getVarbitValue(anyInt());
        values.put(13907,4);values.put(13908,5);values.put(13933,14);values.put(13934,14);
        net.runelite.api.ItemContainer worn=mock(net.runelite.api.ItemContainer.class);doReturn(new net.runelite.api.Item[0]).when(worn).getItems();
        doReturn(worn).when(f.client).getItemContainer(net.runelite.api.gameval.InventoryID.WORN);
        AtomicReference<MinigameActivityCatalog.Snapshot> nativeReading=new AtomicReference<>(detector.detect(f.client));
        f.guides.setActivitySupplier(nativeReading::get);SwingUtilities.invokeAndWait(f::open);SwingUtilities.invokeAndWait(()->{});
        SwingUtilities.invokeAndWait(()->{assertEquals("prepare:2",f.guides.currentStep().id);assertTrue(contains(f.panel,"Set the mould"));});
        values.put(13914,2);nativeReading.set(detector.detect(f.client));f.guides.onGameTick();
        SwingUtilities.invokeAndWait(()->{assertEquals("method:1",f.guides.currentStep().id);assertTrue(contains(f.panel,"Collect the preform"));});
        doReturn(new net.runelite.api.Item[]{new net.runelite.api.Item(net.runelite.api.gameval.ItemID.GIANTS_FOUNDRY_PREFORM,1)}).when(worn).getItems();
        values.put(13914,0);values.put(13949,1000);nativeReading.set(detector.detect(f.client));f.guides.onGameTick();
        SwingUtilities.invokeAndWait(()->{assertEquals("method:3",f.guides.currentStep().id);assertTrue(contains(f.panel,"Return the sword"));assertNotNull(f.guides.selectedGuide());});
        verify(f.config,never()).setRSProfileConfiguration(eq("runewarden"),eq(GuideProgress.completedKey("training:giants-foundry")),any());f.graphics.dispose();
    }
    @Test public void onlyAttributedWorkForTheExactLinkedRunAdvancesAPreparationGuide() throws Exception
    {
        Fixture f=new Fixture();AtomicReference<TrainingProgressTool.Reading> reading=new AtomicReference<>();f.guides.setTrainingSupplier(reading::get);
        SwingUtilities.invokeAndWait(()->{f.panel=f.guides.createPanel();f.guides.openGuide("training:diamond-bracelets");});
        reading.set(training("training:diamond-bracelets",0,1));f.guides.onGameTick();
        SwingUtilities.invokeAndWait(()->assertEquals("prepare:1",f.guides.currentStep().id));
        reading.set(training("training:other-method",100,1));f.guides.onGameTick();
        SwingUtilities.invokeAndWait(()->assertEquals("prepare:1",f.guides.currentStep().id));
        reading.set(training("training:diamond-bracelets",100,1));f.guides.onGameTick();
        SwingUtilities.invokeAndWait(()->assertEquals("method:1",f.guides.currentStep().id));
        verify(f.config,never()).setRSProfileConfiguration(eq("runewarden"),eq(GuideProgress.completedKey("training:diamond-bracelets")),any());
        f.guides.clearSelection();f.guides.onGameTick();SwingUtilities.invokeAndWait(()->assertNull(f.guides.currentStep()));f.graphics.dispose();
    }
    private static TrainingProgressTool.Reading training(String guide,long attributed,long actions)
    {
        SharedActivitySession.Context context=new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"training:diamond-bracelets","Diamond bracelets",guide,"crafting:diamond_bracelet",net.runelite.api.Skill.CRAFTING,10000);
        SharedActivitySession.Snapshot session=new SharedActivitySession.Snapshot("rsprofile.test","run",context,context,SharedActivitySession.State.RUNNING,6000,3000,1);
        return new TrainingProgressTool.Reading(session,100,attributed,1,actions,3000,1000,TrainingProgressTool.StopCause.WORKING);
    }
    @Test public void endRunDismissalAlsoFiresForAnIdleOrAlreadyFinishedSession()
    {
        ActivitySessionTest.Fixture f=new ActivitySessionTest.Fixture();java.util.concurrent.atomic.AtomicInteger count=new java.util.concurrent.atomic.AtomicInteger();
        f.session.setFinishAction(s->count.incrementAndGet());f.session.finish();assertEquals(1,count.get());
        f.session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.OTHER,"test","Test",null,null,null,0));f.session.finish();f.session.finish();assertEquals(3,count.get());assertEquals(1,f.session.history().size());
    }
    private static MinigameActivityCatalog.Snapshot task(String title,String text)
    {return new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,"Giants' Foundry","training:giants-foundry",List.of(),List.of(new MinigameActivityCatalog.Step(title,text)),Set.of(),List.of(),true);}
    private static JButton button(Container root,String title)
    {for(Component c:root.getComponents()){if(c instanceof JButton&&title.equals(((JButton)c).getText()))return(JButton)c;if(c instanceof Container){JButton found=button((Container)c,title);if(found!=null)return found;}}return null;}
    private static boolean contains(Container root,String text)
    {for(Component c:root.getComponents()){if(c instanceof GuideText&&((GuideText)c).getText().contains(text))return true;if(c instanceof Container&&contains((Container)c,text))return true;}return false;}
    private static final class Fixture
    {
        final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);final ConfigManager config=mock(ConfigManager.class);
        final GuideCoachOverlay coach=new GuideCoachOverlay(client);final Graphics2D graphics=new BufferedImage(500,500,BufferedImage.TYPE_INT_ARGB).createGraphics();
        final EventBus events=mock(EventBus.class);final PluginManager plugins=mock(PluginManager.class);
        final java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true);
        final net.runelite.api.Player player=mock(net.runelite.api.Player.class);
        final GuideNavigation navigation;
        final RunewardenGuides guides;JComponent panel;
        final GuideCatalog catalog = new GuideCatalog(new Gson());
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getIntStack()).thenReturn(new int[]{0});
            when(client.getLocalPlayer()).thenReturn(player);when(player.getWorldLocation()).thenReturn(new net.runelite.api.coords.WorldPoint(3200,3200,0));
            when(config.getRSProfileKey()).thenReturn("rsprofile.test");
            net.runelite.client.plugins.Plugin shortestPath=new shortestpath.ShortestPathPlugin();when(plugins.getPlugins()).thenReturn(List.of(shortestPath));when(plugins.isPluginActive(shortestPath)).thenAnswer(c->active.get());
            navigation=new GuideNavigation(client,events,plugins,null,config,null);navigation.start();navigation.onGameTick();
            doAnswer(c->{((Runnable)c.getArgument(0)).run();return null;}).when(thread).invokeLater(any(Runnable.class));
            GuideCatalog.Entry training = catalog.entry("training:giants-foundry");
            for (int i = 0; i < 2; i++)
            {
                GuideCatalog.Step step = training.flattened().get(i).step; step.location = new GuideCatalog.Location();
                step.location.x = 3205 + i; step.location.y = 3205; step.location.plane = 0;
            }
            guides=new RunewardenGuides(client,thread,config,catalog,navigation,new GuideMarkerOverlay(client),coach,mock(OverlayManager.class));
        }
        void open(){panel=guides.createPanel();guides.openGuide("training:giants-foundry");}
        List<net.runelite.client.events.PluginMessage> sent()
        {
            org.mockito.ArgumentCaptor<Object> capture=org.mockito.ArgumentCaptor.forClass(Object.class);verify(events,atLeastOnce()).post(capture.capture());
            List<net.runelite.client.events.PluginMessage> sent=new java.util.ArrayList<>();for(Object message:capture.getAllValues())if(message instanceof net.runelite.client.events.PluginMessage)sent.add((net.runelite.client.events.PluginMessage)message);return sent;
        }
        List<net.runelite.client.events.PluginMessage> paths(){List<net.runelite.client.events.PluginMessage> paths=new java.util.ArrayList<>();for(net.runelite.client.events.PluginMessage message:sent())if("path".equals(message.getName()))paths.add(message);return paths;}
    }
}
