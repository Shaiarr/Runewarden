/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.events.PluginMessage;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Real plugin service graph and lifecycle; only the native client/RuneLite host boundary is mocked. */
public class RunewardenPluginIntegrationTest
{
    @Test public void worldHopperIsWiredIntoTheSidebarAndOnlyItsExplicitCommandCanRequestAHop() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();f.tick();
            net.runelite.http.api.worlds.WorldResult worlds=new net.runelite.http.api.worlds.WorldResult();
            worlds.setWorlds(List.of(
                net.runelite.http.api.worlds.World.builder().id(301).address("oldschool1.runescape.com").players(400)
                    .activity("-").region(net.runelite.http.api.worlds.WorldRegion.UNITED_STATES_OF_AMERICA)
                    .types(java.util.EnumSet.noneOf(net.runelite.http.api.worlds.WorldType.class)).build(),
                net.runelite.http.api.worlds.World.builder().id(302).address("oldschool2.runescape.com").players(500)
                    .activity("-").region(net.runelite.http.api.worlds.WorldRegion.UNITED_STATES_OF_AMERICA)
                    .types(java.util.EnumSet.noneOf(net.runelite.http.api.worlds.WorldType.class)).build()));
            f.plugin.onWorldsFetch(new net.runelite.client.events.WorldsFetch(worlds));f.drain();flush();
            SwingUtilities.invokeAndWait(()->f.panel().selectModule("World hopper"));
            assertEquals("World hopper",f.selectedModule());
            assertEquals(2,f.get(WorldHopperTool.class).snapshot().rows.size());
            f.assertNoGameActions();
            f.disabled("World hopper");f.get(ModuleRuntime.class).changed();
            f.plugin.onCommandExecuted(new net.runelite.api.events.CommandExecuted("rwhop",new String[]{"302"}));f.drain();
            verify(f.client,never()).createWorld();
            f.get(ModuleRuntime.class).enable("World hopper");f.drain();
            f.plugin.onWorldsFetch(new net.runelite.client.events.WorldsFetch(worlds));f.drain();
            net.runelite.api.World target=mock(net.runelite.api.World.class);when(target.getId()).thenReturn(302);when(f.client.createWorld()).thenReturn(target);
            f.plugin.onCommandExecuted(new net.runelite.api.events.CommandExecuted("rwhop",new String[]{"302"}));
            verify(f.client,never()).createWorld();f.drain();verify(target).setId(302);
            f.tick();verify(f.client).openWorldHopper();
            when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Worldswitcher.BUTTONS)).thenReturn(mock(net.runelite.api.widgets.Widget.class));
            f.tick();verify(f.client).hopToWorld(target);f.tick();verify(f.client,times(1)).hopToWorld(any());
            assertFalse(f.get(ModuleRuntime.class).report(),f.get(ModuleRuntime.class).report().contains("error="));
        }
        finally{f.shutdown();}
    }

    @Test public void nativeActionEvidenceReachesTheHudAndModuleSuspensionClearsIt() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();f.tick();
            when(f.player.getAnimation()).thenReturn(net.runelite.api.gameval.AnimationID.HUMAN_CASTHIGHLVLALCHEMY);
            net.runelite.api.events.AnimationChanged animation=new net.runelite.api.events.AnimationChanged();animation.setActor(f.player);
            f.plugin.onAnimationChanged(animation);
            ActionProgressTool.Snapshot observed=f.get(ActionProgressTool.class).snapshot();
            assertTrue(observed.cycle());assertTrue(observed.exactCycle);assertEquals(3000,observed.cycleEnd-observed.cycleStart);
            f.disabled("Action progress");f.get(ModuleRuntime.class).changed();
            assertFalse(f.get(ActionProgressTool.class).snapshot().cycle());
            f.plugin.onAnimationChanged(animation);assertFalse(f.get(ActionProgressTool.class).snapshot().cycle());
            f.assertNoGameActions();
        }
        finally { f.shutdown(); }
    }
    @Test public void realNativeAnimationRendersThroughTheRegisteredProgressOverlay() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();f.tick();
            net.runelite.api.Animation definition=mock(net.runelite.api.Animation.class);when(definition.getFrameLengths()).thenReturn(new int[]{10,20,30});
            when(f.client.loadAnimation(net.runelite.api.gameval.AnimationID.HUMAN_GLASSBLOWING)).thenReturn(definition);when(f.player.getAnimation()).thenReturn(net.runelite.api.gameval.AnimationID.HUMAN_GLASSBLOWING);when(f.player.getAnimationFrame()).thenReturn(99);
            net.runelite.api.events.AnimationChanged event=new net.runelite.api.events.AnimationChanged();event.setActor(f.player);f.plugin.onAnimationChanged(event);
            Overlay overlay=f.get(ActionProgressTool.class).overlay();assertTrue(f.liveOverlays.contains(overlay));
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(400,300,java.awt.image.BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=image.createGraphics();
            try{assertNotNull(overlay.render(g));assertNotEquals(0,image.getRGB(20,20));}finally{g.dispose();}
            f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void nativeCachedAnimationAndFrameWrapReachTheRegisteredHudAtClientTickFrequency() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();f.tick();
            net.runelite.api.Animation maya=mock(net.runelite.api.Animation.class);
            when(maya.isMayaAnim()).thenReturn(true);when(maya.getNumFrames()).thenReturn(60);
            when(f.client.loadAnimation(net.runelite.api.gameval.AnimationID.HUMAN_GLASSBLOWING)).thenReturn(maya);when(f.player.getAnimation()).thenReturn(net.runelite.api.gameval.AnimationID.HUMAN_GLASSBLOWING);
            when(f.player.getAnimationFrame()).thenReturn(30);
            f.plugin.onPostClientTick(new net.runelite.api.events.PostClientTick());
            ActionProgressTool.Snapshot first=f.get(ActionProgressTool.class).snapshot();
            assertTrue(first.cycle());assertEquals(1200,first.cycleEnd-first.cycleStart);assertEquals(first.observedAt-600,first.cycleStart);
            when(f.player.getAnimationFrame()).thenReturn(59);f.plugin.onPostClientTick(new net.runelite.api.events.PostClientTick());
            when(f.player.getAnimationFrame()).thenReturn(2);f.plugin.onPostClientTick(new net.runelite.api.events.PostClientTick());
            ActionProgressTool.Snapshot wrapped=f.get(ActionProgressTool.class).snapshot();
            assertTrue(wrapped.cycle());assertEquals(wrapped.observedAt-40,wrapped.cycleStart);
            Overlay overlay=f.get(ActionProgressTool.class).overlay();assertTrue(f.liveOverlays.contains(overlay));
            java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(400,300,java.awt.image.BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D graphics=image.createGraphics();
            try{assertNotNull(overlay.render(graphics));}finally{graphics.dispose();}
            f.disabled("Action progress");f.get(ModuleRuntime.class).changed();f.plugin.onPostClientTick(new net.runelite.api.events.PostClientTick());
            assertFalse(f.get(ActionProgressTool.class).snapshot().cycle());f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void foundryGuideIsOptInReadsExistingWorkAndEndRunCannotReopenAfterABankGap() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();f.tick();SharedActivitySession session=f.get(SharedActivitySession.class);session.setAutoDetect(true);
            net.runelite.api.widgets.Widget foundry=mock(net.runelite.api.widgets.Widget.class);
            when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3366,11494,0));
            when(f.client.getWidget(754,51)).thenReturn(foundry);
            when(f.client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_1)).thenReturn(4);
            when(f.client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_COMMISSION_WORD_2)).thenReturn(5);
            when(f.client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_STEEL_COUNT)).thenReturn(14);
            when(f.client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_MITHRIL_COUNT)).thenReturn(14);
            f.tick();f.drain();flush();f.tick();RunewardenGuides guides=f.get(RunewardenGuides.class);
            assertEquals(MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,f.get(MinigameCoach.class).reading().activity);
            assertNull("Entering or detecting a run must not opt the player into a guide",guides.selectedGuide());
            SwingUtilities.invokeAndWait(()->button(f.panel(),"Open current guide").doClick());f.drain();flush();
            assertNotNull(guides.selectedGuide());assertEquals("training:giants-foundry",guides.selectedGuide().id);assertEquals("prepare:2",guides.currentStep().id);
            when(f.client.getVarbitValue(13914)).thenReturn(2);f.tick();assertEquals("method:1",guides.currentStep().id);
            String ended=session.snapshot().runId;SwingUtilities.invokeAndWait(()->button(f.panel(),"End run").doClick());
            assertNull("End run dismisses synchronously, before its client-thread journal update",guides.selectedGuide());f.drain();flush();
            assertTrue(f.descendants(f.panel()).stream().anyMatch(component->"minigameCurrentInstruction".equals(component.getName())&&component instanceof GuideText&&((GuideText)component).getText().isEmpty()&&!component.isVisible()));
            assertTrue(f.descendants(f.panel()).stream().anyMatch(component->"minigameLearningPlan".equals(component.getName())&&!component.isVisible()));
            assertEquals(SharedActivitySession.State.FINISHED,session.snapshot().state);
            net.runelite.api.widgets.Widget bank=mock(net.runelite.api.widgets.Widget.class);
            when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS)).thenReturn(bank);when(foundry.isHidden()).thenReturn(true);f.tick();
            when(bank.isHidden()).thenReturn(true);when(foundry.isHidden()).thenReturn(false);f.tick();f.tick();
            assertEquals(ended,session.snapshot().runId);assertEquals(SharedActivitySession.State.FINISHED,session.snapshot().state);
            assertNull(guides.selectedGuide());assertTrue(f.get(MinigameCoach.class).guidanceDismissed());
            assertTrue(session.startSelected());f.drain();flush();f.tick();assertNull("Starting another measured run still leaves the guide optional",guides.selectedGuide());assertFalse(f.get(MinigameCoach.class).guidanceDismissed());
            SwingUtilities.invokeAndWait(()->button(f.panel(),"Open current guide").doClick());f.drain();flush();assertNotNull(guides.selectedGuide());
            assertFalse(f.get(ModuleRuntime.class).report(),f.get(ModuleRuntime.class).report().contains("error="));f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void theGlobalEndRunClearsAnIdleGuideAndItsConfirmedRouteAndShowsTheRunningBuild() throws Exception
    {
        Fixture f=new Fixture();try
        {
            shortestpath.ShortestPathPlugin receiver=new shortestpath.ShortestPathPlugin();
            when(f.get(net.runelite.client.plugins.PluginManager.class).getPlugins()).thenReturn(List.of(receiver));
            when(f.get(net.runelite.client.plugins.PluginManager.class).isPluginActive(receiver)).thenReturn(true);
            List<PluginMessage> sent=new ArrayList<>();
            doAnswer(call->{Object event=call.getArgument(0);if(event instanceof PluginMessage)sent.add((PluginMessage)event);return null;}).when(f.get(net.runelite.client.eventbus.EventBus.class)).post(any());
            f.start();f.drain();f.login();f.tick();RunewardenGuides guides=f.get(RunewardenGuides.class);
            GuideCatalog.Step destination=f.get(GuideCatalog.class).entry("training:giants-foundry").flattened().get(0).step;
            destination.location=new GuideCatalog.Location();destination.location.x=3205;destination.location.y=3205;
            SwingUtilities.invokeAndWait(()->guides.openGuide("training:giants-foundry"));f.drain();flush();f.drain();flush();f.tick();
            PluginMessage path=sent.stream().filter(message->message.getName().equals("path")).findFirst().orElseThrow();
            PluginMessage confirmation=sent.stream().filter(message->message.getName().equals("getTarget")).reduce((a,b)->b).orElseThrow();
            f.plugin.onPluginMessage(new PluginMessage("shortestpath","currentTarget",Map.of("id",confirmation.getData().get("id"),"set",true,"target",path.getData().get("target"))));
            assertNull(f.get(SharedActivitySession.class).snapshot().context);sent.clear();
            SwingUtilities.invokeAndWait(()->button(f.panel(),"End run").doClick());assertNull(guides.selectedGuide());f.drain();flush();
            // Public receiver replies still reach the route owner while the Travel module is paused.
            f.disabled("Travel");f.get(ModuleRuntime.class).changed();f.drain();
            PluginMessage clear=sent.stream().filter(message->message.getName().equals("getTarget")).reduce((a,b)->b).orElseThrow();
            f.plugin.onPluginMessage(new PluginMessage("shortestpath","currentTarget",Map.of("id",clear.getData().get("id"),"set",true,"target",path.getData().get("target"))));
            assertTrue(sent.stream().anyMatch(message->message.getName().equals("clear")));
            // Search the live panel hierarchy rather than a launcher file or diagnostic report.
            assertTrue(f.descendants(f.panel()).stream().anyMatch(component->component instanceof javax.swing.JLabel&&"runewarden.build.version".equals(component.getName())&&RunewardenVersion.VERSION.equals(((javax.swing.JLabel)component).getText())));
            f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void aRewardedFoundrySwordClosesTheGuideWhileTheTrainingRunContinues() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();f.tick();
            when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3366,11494,0));
            net.runelite.api.widgets.Widget foundry=mock(net.runelite.api.widgets.Widget.class);
            when(f.client.getWidget(754,51)).thenReturn(foundry);
            when(f.client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION)).thenReturn(1000);
            f.container(InventoryID.WORN,new Item[]{new Item(net.runelite.api.gameval.ItemID.GIANTS_FOUNDRY_PREFORM,1)});
            when(f.client.getSkillExperience(Skill.SMITHING)).thenReturn(900);
            SharedActivitySession session=f.get(SharedActivitySession.class);RunewardenGuides guides=f.get(RunewardenGuides.class);
            session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"swords","Smithing target","training:giants-foundry",null,Skill.SMITHING,100_000));
            f.tick();assertNull(guides.selectedGuide());
            SwingUtilities.invokeAndWait(()->guides.openGuide("training:giants-foundry"));f.drain();flush();
            assertEquals("method:3",guides.currentStep().id);String identity=session.snapshot().runId;
            f.container(InventoryID.WORN,new Item[0]);
            when(f.client.getVarbitValue(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION)).thenReturn(0);
            when(f.client.getVarpValue(net.runelite.api.gameval.VarPlayerID.GIANTS_FOUNDRY_REWARD_SHOP_POINTS)).thenReturn(100);
            f.tick();f.drain();flush();
            assertNull(guides.selectedGuide());assertNull(guides.currentStep());
            assertTrue("A commission is not the end of a longer XP session",session.snapshot().running());assertEquals(identity,session.snapshot().runId);
            f.tick();assertNull("Native refresh must not reopen a completed guide",guides.selectedGuide());
            assertTrue(f.get(GuideBankFilter.class).requiredItemIds().isEmpty());f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void leavingAMinigameClosesItsExplicitGuideEvenWithTheMinigameModuleDisabled() throws Exception
    {
        for(boolean disabled:new boolean[]{false,true})
        {
            Fixture f=new Fixture();try
            {
                if(disabled)f.disabled("Minigames");f.start();f.drain();f.login();f.tick();
                when(f.client.getTopLevelWorldView()).thenReturn(mock(net.runelite.api.WorldView.class));
                when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3366,11494,0));
                net.runelite.api.widgets.Widget foundry=mock(net.runelite.api.widgets.Widget.class);when(f.client.getWidget(754,51)).thenReturn(foundry);
                RunewardenGuides guides=f.get(RunewardenGuides.class);
                SwingUtilities.invokeAndWait(()->guides.openGuide("training:giants-foundry"));f.drain();flush();f.tick();assertNotNull(guides.selectedGuide());
                when(foundry.isHidden()).thenReturn(true);f.tick();assertNotNull("Hiding the interface on the same tile is not proof of exit",guides.selectedGuide());
                when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3361,3147,0));f.tick();f.drain();flush();
                assertNull("A real exit must dismiss the active minigame guide",guides.selectedGuide());
                assertNull(f.get(GuideMarkerOverlay.class).selectedPack());assertTrue(f.get(GuideBankFilter.class).requiredItemIds().isEmpty());
                when(f.player.getWorldLocation()).thenReturn(new WorldPoint(3366,11494,0));when(foundry.isHidden()).thenReturn(false);f.tick();
                assertNull("Reentering never silently reopens a guide",guides.selectedGuide());f.assertNoGameActions();
            }
            finally{f.shutdown();}
        }
    }
    @Test public void endingTrainingAndLootRunsImmediatelyDismissesGuidesEvenWithoutAnActiveRun() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();f.tick();SharedActivitySession session=f.get(SharedActivitySession.class);RunewardenGuides guides=f.get(RunewardenGuides.class);
            session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"test","Training","training:giants-foundry",null,Skill.SMITHING,1000));f.drain();flush();
            SwingUtilities.invokeAndWait(()->guides.openGuide("training:giants-foundry"));f.drain();flush();assertNotNull(guides.selectedGuide());
            f.get(TrainingProgressTool.class).finishTracking();f.drain();assertNull(guides.selectedGuide());flush();
            assertEquals(SharedActivitySession.State.FINISHED,session.snapshot().state);assertNull(guides.currentStep());
            SwingUtilities.invokeAndWait(()->guides.openGuide("training:giants-foundry"));assertNotNull(guides.selectedGuide());
            RunLootTool loot=f.get(RunLootTool.class);RunLootTool.Reading observed=loot.reading();loot.finishRun(observed.profile,observed.epoch);
            assertNull(guides.selectedGuide());flush();assertNull(guides.selectedGuide());assertTrue(f.get(GuideBankFilter.class).requiredItemIds().isEmpty());
            f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void queuedRunStartCannotReopenABossLessonAfterDeathOrArenaExit() throws Exception
    {
        for (boolean death : new boolean[]{true, false})
        {
            Fixture f = new Fixture();
            try
            {
                f.start(); f.drain(); f.login(); f.tick();
                net.runelite.api.WorldView view = mock(net.runelite.api.WorldView.class);
                when(f.client.getTopLevelWorldView()).thenReturn(view);
                RunewardenGuides guides = f.get(RunewardenGuides.class);
                SharedActivitySession session = f.get(SharedActivitySession.class);
                SwingUtilities.invokeAndWait(() -> {
                    guides.openGuide("encounter:the-gauntlet");
                    when(f.client.getVarbitValue(VarbitID.PLAYER_IN_GAUNTLET)).thenReturn(1);
                    guides.onGameTick(); assertNotNull(guides.selectedGuide());
                    assertTrue(session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.BOSS,
                        "gauntlet-test", "The Gauntlet", "encounter:the-gauntlet", null, null, 0)));
                    // Hold the Swing event queue so the real root callback is still pending.
                    if (death) f.plugin.onActorDeath(new net.runelite.api.events.ActorDeath(f.player));
                    else
                    {
                        when(f.client.getVarbitValue(VarbitID.PLAYER_IN_GAUNTLET)).thenReturn(0);
                        guides.onGameTick();
                    }
                    assertNull(guides.selectedGuide());
                });
                flush(); f.drain(); flush();
                assertNull("A stale run-start callback must not reopen the dismissed boss lesson", guides.selectedGuide());
                assertNull(f.get(GuideMarkerOverlay.class).selectedPack()); f.assertNoGameActions();
            }
            finally { f.shutdown(); }
        }
    }

    @Test public void reachingTheActualXpTargetFinishesAndClosesItsGuide() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();f.tick();SharedActivitySession session=f.get(SharedActivitySession.class);
            when(f.client.getSkillExperience(Skill.SMITHING)).thenReturn(900);
            session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.TRAINING,"target","Smithing goal","training:giants-foundry",null,Skill.SMITHING,1000));f.drain();flush();f.tick();
            assertTrue(session.snapshot().running());when(f.client.getSkillExperience(Skill.SMITHING)).thenReturn(1000);f.tick();
            assertEquals(SharedActivitySession.State.FINISHED,session.snapshot().state);assertNull(f.get(RunewardenGuides.class).selectedGuide());
        }
        finally{f.shutdown();}
    }
    @Test public void fullStartupShutdownAndRapidRestartBalanceHostResourcesAndHonorPersistedDisabledModules() throws Exception
    {
        Fixture f=new Fixture();f.disabled("Bank & XP","Skilling coach","Command dashboard");
        try
        {
            f.start();ModuleRuntime runtime=f.get(ModuleRuntime.class);
            assertFalse(runtime.enabled("Bank & XP"));assertFalse(runtime.enabled("Skilling coach"));assertFalse(runtime.enabled("Command dashboard"));
            f.plugin.shutDown();f.started=false; // Deliberately leave old client-thread tasks queued across the restart.
            f.start();f.drain();flush();assertEquals(5,f.liveKeys.size());
            assertFalse(runtime.enabled("Bank & XP"));assertFalse(runtime.enabled("Skilling coach"));assertFalse(runtime.enabled("Command dashboard"));
            f.login();for(int tick=0;tick<4;tick++)f.tick();
            f.get(GuideBankFilter.class).setExternalTargets(Set.of(385));assertTrue(f.get(GuideBankFilter.class).requiredItemIds().isEmpty());
            assertFalse(runtime.enabled("Bank & XP"));
            assertFalse(runtime.report(),runtime.report().contains("error="));
            f.shutdown();
            ArgumentCaptor<NavigationButton> added=ArgumentCaptor.forClass(NavigationButton.class),removed=ArgumentCaptor.forClass(NavigationButton.class);
            verify(f.get(ClientToolbar.class),times(2)).addNavigation(added.capture());verify(f.get(ClientToolbar.class),times(2)).removeNavigation(removed.capture());
            assertEquals(added.getAllValues(),removed.getAllValues());assertNotSame(added.getAllValues().get(0),added.getAllValues().get(1));
            verify(f.get(KeyManager.class),times(10)).registerKeyListener(any());verify(f.get(KeyManager.class),times(10)).unregisterKeyListener(any());
            verify(f.get(net.runelite.client.input.MouseManager.class),times(2)).registerMouseListener(same(f.get(HudResizeController.class)));
            verify(f.get(net.runelite.client.input.MouseManager.class),times(2)).unregisterMouseListener(same(f.get(HudResizeController.class)));
            ArgumentCaptor<Overlay> overlayAdds=ArgumentCaptor.forClass(Overlay.class),overlayRemoves=ArgumentCaptor.forClass(Overlay.class);
            verify(f.get(OverlayManager.class),atLeastOnce()).add(overlayAdds.capture());verify(f.get(OverlayManager.class),atLeastOnce()).remove(overlayRemoves.capture());
            assertTrue("Every overlay registered by the plugin must be removed",identityCounts(overlayRemoves.getAllValues()).keySet().containsAll(identityCounts(overlayAdds.getAllValues()).keySet()));
            assertTrue("No overlay remains registered after shutdown",f.liveOverlays.isEmpty());
            assertTrue("No key listener remains registered after shutdown",f.liveKeys.isEmpty());
            f.assertNoGameActions();clearInvocations(f.client);f.plugin.onGameTick(new GameTick());verifyNoInteractions(f.client);
        }
        finally {f.shutdown();}
    }
    @Test public void rootWiresNativeSupplyAndEffectEvidenceIntoTheActualDepartureChecklist() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.item(3030,"Super restore(1)");f.item(1712,"Amulet of glory(4)");f.container(InventoryID.INV,new Item[]{new Item(3030,1),new Item(1712,1)});
            when(f.client.getVarbitValue(VarbitID.ANTIFIRE_POTION)).thenReturn(10);
            f.start();f.drain();f.login();f.tick();
            LoadoutReadinessTool loadouts=f.get(LoadoutReadinessTool.class);
            loadouts.importPlans("{\"version\":2,\"plans\":[{\"id\":\"dragon\",\"name\":\"Dragon trip\",\"requirements\":[],\"departureChecks\":[{\"id\":\"tele\",\"label\":\"Teleport ready\",\"kind\":\"CHARGES\",\"itemIds\":[1712],\"minimum\":4},{\"id\":\"fire\",\"label\":\"Antifire active\",\"kind\":\"EFFECT\",\"effectKey\":\"antifire\",\"minimum\":0}]}]}");
            f.drain();flush();f.tick();assertNotNull(loadouts.readiness());assertTrue(loadouts.readiness().ready);
            assertEquals(Long.valueOf(4),f.get(SupplyAwarenessTool.class).chargeTotals().get(1712));
            assertTrue(f.get(FieldCoach.class).snapshot().effects.stream().anyMatch(row->row.label.equals("Super restore")&&row.warning));
            when(f.client.getVarbitValue(VarbitID.ANTIFIRE_POTION)).thenReturn(0);f.tick();assertFalse(loadouts.readiness().ready);assertEquals(LoadoutReadiness.CheckState.MISSING,loadouts.readiness().checks.get(1).state);
            f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void rootGoalAndClueBankActionsUseTheSharedFilterWithoutSwitchingTheNativeGameState() throws Exception
    {
        Fixture f=new Fixture();try
        {
            net.runelite.client.plugins.cluescrolls.ClueScrollPlugin nativeClues=mock(net.runelite.client.plugins.cluescrolls.ClueScrollPlugin.class);
            net.runelite.client.plugins.cluescrolls.clues.EmoteClue clue=net.runelite.client.plugins.cluescrolls.clues.EmoteClue.forText("Bow upstairs in the Edgeville Monastery. Equip a completed prayer book.");assertNotNull(clue);
            when(f.get(net.runelite.client.plugins.PluginManager.class).getPlugins()).thenReturn(Collections.singletonList(nativeClues));when(f.get(net.runelite.client.plugins.PluginManager.class).isPluginEnabled(nativeClues)).thenReturn(true);when(nativeClues.getClue()).thenReturn(clue);
            f.start();f.drain();f.login();for(int tick=0;tick<4;tick++)f.tick();
            GoalPlannerTool goals=f.get(GoalPlannerTool.class);goals.addProgressGoal("item","385",10,"Pack sharks",List.of(385));f.drain();flush();assertEquals(1,goals.goalPlans().size());
            SwingUtilities.invokeAndWait(()->button(f.panel(),"Bank supplies").doClick());f.drain();flush();assertEquals(Set.of(385),f.get(GuideBankFilter.class).requiredItemIds());assertEquals("Bank & XP",f.selectedModule());
            assertFalse(f.get(ClueCoachTool.class).snapshot().status + " / " + f.get(ClueCoachTool.class).snapshot().instruction,f.get(ClueCoachTool.class).snapshot().requirements.isEmpty());
            net.runelite.api.widgets.Widget bank=mock(net.runelite.api.widgets.Widget.class);
            when(f.client.getWidget(net.runelite.api.gameval.InterfaceID.Bankmain.ITEMS)).thenReturn(bank);f.tick();
            assertTrue(f.get(GuideBankFilter.class).requiredItemIds().contains(net.runelite.api.gameval.ItemID.SARADOMINBOOK_COMPLETE));
            assertEquals("Bank & XP",f.selectedModule());f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void queuedRootGoalFocusCannotReviveOldWorkAfterSameProfileRestart() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();for(int tick=0;tick<4;tick++)f.tick();
            f.get(GoalPlannerTool.class).addProgressGoal("item","385",10,"Pack sharks",List.of(385));f.drain();flush();
            SwingUtilities.invokeAndWait(()->button(f.panel(),"Bank supplies").doClick());assertFalse(f.pending.isEmpty());
            f.plugin.shutDown();f.started=false;f.start();f.drain();flush();
            assertTrue("Queued old goal focus must not affect the new plugin lifecycle",f.get(GuideBankFilter.class).requiredItemIds().isEmpty());f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void queuedSharedFocusCannotReturnAfterSameProfileSessionTransition() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();for(int tick=0;tick<4;tick++)f.tick();
            f.get(GoalPlannerTool.class).addProgressGoal("item","385",10,"Pack sharks",List.of(385));f.drain();flush();
            SwingUtilities.invokeAndWait(()->button(f.panel(),"Bank supplies").doClick());assertFalse(f.pending.isEmpty());
            net.runelite.api.events.GameStateChanged hop=new net.runelite.api.events.GameStateChanged();hop.setGameState(GameState.HOPPING);
            f.state.set(GameState.HOPPING);f.plugin.onGameStateChanged(hop);
            f.login();f.drain();flush();
            assertTrue("The previous session's queued focus must stay cleared",f.get(GuideBankFilter.class).requiredItemIds().isEmpty());
            f.assertNoGameActions();
        }
        finally {f.shutdown();}
    }
    @Test public void discoveredTrainingPlanStartsWithItsExactMethodAndFinalTargetAndRecordsTypedMilestones() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();for(int tick=0;tick<4;tick++)f.tick();
            WorkflowPlan plan=trainingPlan(f);WorkflowPlansTool plans=f.get(WorkflowPlansTool.class);plans.add(plan);f.drain();flush();
            showPlansSnapshot(plans);SwingUtilities.invokeAndWait(()->button(f.panel(),"Start this plan").doClick());f.drain();flush();
            SharedActivitySession session=f.get(SharedActivitySession.class);SharedActivitySession.Snapshot started=session.snapshot();
            assertTrue(started.running());assertEquals(SharedActivitySession.Kind.TRAINING,started.context.kind);assertEquals("plan:"+plan.id,started.context.id);
            assertEquals("activity:wintertodt-fletch",started.context.methodId);assertEquals("training:wintertodt",started.context.guideId);assertEquals(Skill.FIREMAKING,started.context.skill);
            assertEquals(net.runelite.api.Experience.getXpForLevel(80),started.context.targetXp);assertEquals("Activity hub",f.selectedModule());
            WorkflowPlan.Step firstSkill=plan.steps.stream().filter(step->step.kind==WorkflowPlan.Kind.SKILL).findFirst().orElseThrow();
            assertEquals(net.runelite.api.Experience.getXpForLevel(65),firstSkill.target);
            when(f.client.getSkillExperience(Skill.FIREMAKING)).thenReturn((int)firstSkill.target);when(f.client.getRealSkillLevel(Skill.FIREMAKING)).thenReturn(65);when(f.client.getBoostedSkillLevel(Skill.FIREMAKING)).thenReturn(65);f.tick();
            assertTrue(plans.selected().steps.stream().anyMatch(step->step.id.equals(firstSkill.id)&&step.completed&&step.evidence.equals("Observed in game")));
            assertTrue(session.currentRecord().milestones.stream().anyMatch(milestone->milestone.id.equals("plan:"+plan.id+":"+firstSkill.id)));
            assertTrue(session.snapshot().running());assertEquals(started.runId,session.snapshot().runId);assertEquals(net.runelite.api.Experience.getXpForLevel(80),session.snapshot().context.targetXp);
            for(WorkflowPlan.Step step:plan.steps)if(!step.id.equals(firstSkill.id))plans.complete(plan.id,step.id,true);f.drain();flush();
            assertEquals(SharedActivitySession.State.FINISHED,session.snapshot().state);
            assertEquals(plan.steps.size(),session.currentRecord().milestones.stream().filter(milestone->milestone.id.startsWith("plan:"+plan.id+":")).count());
            f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    @Test public void queuedPlanCompletionCannotFinishALaterRunOfThatPlanOrAnUnrelatedActivity() throws Exception
    {
        Fixture f=new Fixture();try
        {
            f.start();f.drain();f.login();for(int tick=0;tick<4;tick++)f.tick();WorkflowPlan plan=trainingPlan(f);
            WorkflowPlansTool plans=f.get(WorkflowPlansTool.class);plans.add(plan);f.drain();flush();showPlansSnapshot(plans);
            SwingUtilities.invokeAndWait(()->button(f.panel(),"Start this plan").doClick());f.drain();flush();
            // Starting a linked run also opens its guide; settle those independent config reads
            // before arranging the old plan-completion callback whose ownership is under test.
            f.drain();flush();f.drain();flush();f.drain();
            SharedActivitySession session=f.get(SharedActivitySession.class);SharedActivitySession.Context context=session.snapshot().context;String original=session.snapshot().runId;
            plans.complete(plan.id,plan.steps.get(0).id,true);assertFalse(f.pending.isEmpty());assertTrue(f.pending.remove().getAsBoolean()); // Persist the step and queue its root completion callback.
            assertFalse(f.pending.isEmpty());session.startActivity(context);String replacement=session.snapshot().runId;assertNotEquals(original,replacement);f.drain();
            assertTrue(session.snapshot().running());assertEquals(replacement,session.snapshot().runId);assertTrue(session.currentRecord().milestones.isEmpty());
            session.startActivity(new SharedActivitySession.Context(SharedActivitySession.Kind.OTHER,"manual:other","Another activity",null,null,null,0));String unrelated=session.snapshot().runId;
            for(int index=1;index<plan.steps.size();index++)plans.complete(plan.id,plan.steps.get(index).id,true);f.drain();flush();
            assertEquals(plan.steps.size(),plans.selected().completed());assertTrue(session.snapshot().running());assertEquals(unrelated,session.snapshot().runId);assertTrue(session.currentRecord().milestones.isEmpty());
            f.assertNoGameActions();
        }
        finally{f.shutdown();}
    }
    private static WorkflowPlan trainingPlan(Fixture f)
    {
        Map<Skill,Integer> xp=new java.util.EnumMap<>(Skill.class),levels=new java.util.EnumMap<>(Skill.class);
        for(Skill skill:Skill.values())if(skill!=Skill.OVERALL){xp.put(skill,13_034_431);levels.put(skill,99);}
        xp.put(Skill.FIREMAKING,net.runelite.api.Experience.getXpForLevel(60));levels.put(Skill.FIREMAKING,60);
        when(f.client.getSkillExperience(Skill.FIREMAKING)).thenReturn(xp.get(Skill.FIREMAKING));when(f.client.getRealSkillLevel(Skill.FIREMAKING)).thenReturn(60);when(f.client.getBoostedSkillLevel(Skill.FIREMAKING)).thenReturn(60);
        GoalCatalog.Facts facts=new GoalCatalog.Facts(xp,levels,Map.of(),126);OpportunityPlannerTool planner=f.get(OpportunityPlannerTool.class);OpportunityPlannerTool.Preferences preferences=new OpportunityPlannerTool.Preferences();preferences.skill="Firemaking";preferences.targetLevel=80;
        OpportunityPlannerTool.Recommendation chosen=planner.recommendations(preferences,facts,List.of(),"Wintertodt").stream().filter(row->row.method!=null&&row.method.id.equals("activity:wintertodt-fletch")).findFirst().orElseThrow();
        return planner.build(chosen,facts,80);
    }
    private static void showPlansSnapshot(WorkflowPlansTool plans) throws Exception
    {
        // A headless host has no showing hierarchy. Render our own saved snapshot, then exercise the real button/root callback.
        java.lang.reflect.Method render=WorkflowPlansTool.class.getDeclaredMethod("render");render.setAccessible(true);
        SwingUtilities.invokeAndWait(()->{try{render.invoke(plans);}catch(ReflectiveOperationException failure){throw new AssertionError(failure);}});
    }
    private static JButton button(Container container,String text)
    {
        for(Component child:container.getComponents())
        {if(child instanceof JButton&&text.equals(((JButton)child).getText()))return (JButton)child;if(child instanceof Container){JButton found=button((Container)child,text);if(found!=null)return found;}}
        return null;
    }
    private static Map<Overlay,Integer> identityCounts(List<Overlay> overlays)
    {Map<Overlay,Integer> counts=new IdentityHashMap<>();for(Overlay overlay:overlays)counts.merge(overlay,1,Integer::sum);return counts;}
    private static void flush() throws Exception {SwingUtilities.invokeAndWait(()->{});SwingUtilities.invokeAndWait(()->{});}
    private static final class Fixture
    {
        final SidebarLayoutTest.Fixture graph=new SidebarLayoutTest.Fixture();
        final Client client=graph.get(Client.class);final ClientThread thread=graph.get(ClientThread.class);final ConfigManager config=graph.get(ConfigManager.class);
        final RunewardenPlugin plugin=new RunewardenPlugin();final Player player=mock(Player.class);
        final AtomicReference<String> profile=new AtomicReference<>("rsprofile.integration.A");
        final AtomicReference<GameState> state=new AtomicReference<>(GameState.LOGIN_SCREEN);final AtomicInteger ticks=new AtomicInteger(1);
        final Map<String,String> settings=new HashMap<>();final Queue<BooleanSupplier> pending=new java.util.concurrent.ConcurrentLinkedQueue<>();
        final Map<Integer,ItemContainer> containers=new HashMap<>();final Map<Integer,ItemComposition> definitions=new HashMap<>();
        final Set<Overlay> liveOverlays=Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<net.runelite.client.input.KeyListener> liveKeys=Collections.newSetFromMap(new IdentityHashMap<>());
        boolean started;
        Fixture() throws Exception
        {
            when(client.getGameState()).thenAnswer(call->state.get());when(client.getLocalPlayer()).thenReturn(player);when(client.isClientThread()).thenReturn(true);
            // Quest.getState uses RuneLite's native script result stack, which always exists in a client.
            int[] scriptResults=new int[1024];scriptResults[0]=1;when(client.getIntStack()).thenReturn(scriptResults);
            when(client.getTickCount()).thenAnswer(call->ticks.get());when(client.getWorld()).thenReturn(301);
            when(player.getName()).thenReturn("Integration Player");when(player.getWorldLocation()).thenReturn(new WorldPoint(3200,3200,0));
            when(config.getRSProfileKey()).thenAnswer(call->profile.get());
            when(config.getConfiguration(anyString(),anyString())).thenAnswer(call->settings.get(call.getArgument(0)+"|"+call.getArgument(1)));
            when(config.getConfiguration(anyString(),anyString(),anyString())).thenAnswer(call->settings.get(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2)));
            doAnswer(call->{settings.put(call.getArgument(0)+"|"+call.getArgument(1),String.valueOf((Object)call.getArgument(2)));return null;}).when(config).setConfiguration(anyString(),anyString(),any());
            doAnswer(call->{settings.put(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2),String.valueOf((Object)call.getArgument(3)));return null;}).when(config).setConfiguration(anyString(),anyString(),anyString(),any());
            doAnswer(call->{settings.remove(call.getArgument(0)+"|"+call.getArgument(1));return null;}).when(config).unsetConfiguration(anyString(),anyString());
            doAnswer(call->{settings.remove(call.getArgument(0)+"|"+call.getArgument(1)+"|"+call.getArgument(2));return null;}).when(config).unsetConfiguration(anyString(),anyString(),anyString());
            doAnswer(call->{Runnable work=call.getArgument(0);pending.add(()->{work.run();return true;});return null;}).when(thread).invokeLater(any(Runnable.class));
            doAnswer(call->{pending.add(call.getArgument(0));return null;}).when(thread).invokeLater(any(BooleanSupplier.class));
            doAnswer(call->{Runnable work=call.getArgument(0);pending.add(()->{work.run();return true;});return null;}).when(thread).invoke(any(Runnable.class));
            doAnswer(call->{pending.add(call.getArgument(0));return null;}).when(thread).invoke(any(BooleanSupplier.class));
            doAnswer(call->{Runnable work=call.getArgument(0);pending.add(()->{work.run();return true;});return null;}).when(thread).invokeAtTickEnd(any(Runnable.class));
            doAnswer(call->{liveKeys.add(call.getArgument(0));return null;}).when(get(KeyManager.class)).registerKeyListener(any());
            doAnswer(call->{liveKeys.remove(call.getArgument(0));return null;}).when(get(KeyManager.class)).unregisterKeyListener(any());
            doAnswer(call->liveOverlays.add(call.getArgument(0))).when(get(OverlayManager.class)).add(any(Overlay.class));
            doAnswer(call->liveOverlays.remove(call.getArgument(0))).when(get(OverlayManager.class)).remove(any(Overlay.class));
            when(client.getItemContainer(anyInt())).thenAnswer(call->containers.get(call.getArgument(0)));
            when(get(ItemManager.class).canonicalize(anyInt())).thenAnswer(call->call.getArgument(0));
            when(get(ItemManager.class).getItemComposition(anyInt())).thenAnswer(call->definitions.get(call.getArgument(0)));
            when(get(ItemManager.class).getImage(anyInt())).thenAnswer(call->new net.runelite.client.util.AsyncBufferedImage(thread,36,32,java.awt.image.BufferedImage.TYPE_INT_ARGB));
            for(Skill skill:Skill.values())if(skill!=Skill.OVERALL){when(client.getRealSkillLevel(skill)).thenReturn(99);when(client.getBoostedSkillLevel(skill)).thenReturn(99);when(client.getSkillExperience(skill)).thenReturn(13_034_431);}
            container(InventoryID.INV,new Item[0]);Item[] emptyEquipment=new Item[14];java.util.Arrays.fill(emptyEquipment,new Item(-1,0));container(InventoryID.WORN,emptyEquipment);
            for(Field field:RunewardenPlugin.class.getDeclaredFields())if(field.isAnnotationPresent(javax.inject.Inject.class))
            {field.setAccessible(true);field.set(plugin,get(field.getType()));}
        }
        <T>T get(Class<T> type){return graph.get(type);}
        void disabled(String... names){for(String name:names)settings.put(ModuleRuntime.GROUP+"|"+name.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]",""),"false");}
        void container(int id,Item[] content){ItemContainer container=mock(ItemContainer.class);when(container.getItems()).thenReturn(content);containers.put(id,container);}
        void item(int id,String name){ItemComposition definition=mock(ItemComposition.class);when(definition.getId()).thenReturn(id);when(definition.getName()).thenReturn(name);definitions.put(id,definition);}
        void start() throws Exception {SwingUtilities.invokeAndWait(plugin::startUp);started=true;}
        RunewardenPanel panel(){ArgumentCaptor<NavigationButton> navigation=ArgumentCaptor.forClass(NavigationButton.class);verify(get(ClientToolbar.class),atLeastOnce()).addNavigation(navigation.capture());return (RunewardenPanel)navigation.getAllValues().get(navigation.getAllValues().size()-1).getPanel();}
        String selectedModule(){for(Component component:descendants(panel()))if(component instanceof JComboBox&&"runewarden.module.selector".equals(component.getName()))return String.valueOf(((JComboBox<?>)component).getSelectedItem());throw new AssertionError("Missing module selector");}
        private List<Component> descendants(Container root){List<Component> result=new ArrayList<>();for(Component child:root.getComponents()){result.add(child);if(child instanceof Container)result.addAll(descendants((Container)child));}return result;}
        void login(){state.set(GameState.LOGGED_IN);}
        void tick() throws Exception {ticks.incrementAndGet();plugin.onGameTick(new GameTick());drain();flush();}
        void drain()
        {
            int rounds=0;
            while(!pending.isEmpty()&&rounds++<1000){BooleanSupplier action=pending.remove();if(!action.getAsBoolean())pending.add(action);}
            assertTrue("Client queue did not settle",pending.isEmpty());
        }
        void shutdown() throws Exception {if(started){plugin.shutDown();started=false;}drain();flush();}
        void assertNoGameActions()
        {for(org.mockito.invocation.Invocation invocation:mockingDetails(client).getInvocations())assertFalse("Unexpected game action: "+invocation.getMethod().getName(),Set.of("menuAction","invokeMenuAction","interact","setVarbit","setVarpValue","openWorldHopper").contains(invocation.getMethod().getName()));}
    }
}
