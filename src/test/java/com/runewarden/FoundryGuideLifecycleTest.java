/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/** Actual native varbits/containers reach the selected guide rather than only a separate minigame card. */
public class FoundryGuideLifecycleTest
{
    @Test public void alreadyLoadedAlloysOpenAtTheCurrentMouldWithoutAWithdrawInstruction()throws Exception
    {
        Fixture f=new Fixture();f.nativeState.commission();f.nativeState.metals(14,14);f.open();
        assertEquals("prepare:2",f.guides.currentStep().id);assertTrue(f.coach.instruction().contains("28-metal alloy is loaded"));
        assertFalse(f.coach.instruction().contains("Bring bars"));assertFalse(f.coach.instruction().contains("Fill the crucible"));
        f.guides.closeGuide();f.settle();assertNull(f.guides.selectedGuide());
    }
    @Test public void savedCompleteChecklistCannotBeatFreshEquippedSwordFactsEvenWithInterleavedQueues()throws Exception
    {
        Fixture f=new Fixture();f.nativeState.commission();f.nativeState.equipPreform();f.nativeState.hammerGauge();
        f.nativeState.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,100);f.nativeState.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_TEMPERATURE,830);
        java.util.LinkedHashSet<String> done=new java.util.LinkedHashSet<>();for(GuideCatalog.DisplayStep step:new GuideCatalog(new Gson()).entry(Fixture.ID).flattened())done.add(step.step.id);
        when(f.config.getRSProfileConfiguration("runewarden",GuideProgress.completedKey(Fixture.ID),String.class)).thenReturn(GuideProgress.encode(done));
        SwingUtilities.invokeAndWait(()->{f.guides.createPanel();f.guides.openGuide(Fixture.ID);});
        // Flush Swing after every single client task: a saved-full restore must never win a queue race.
        for(int cycle=0;cycle<12;cycle++){Runnable action=f.queued.poll();if(action!=null)action.run();SwingUtilities.invokeAndWait(()->{});}
        assertNotNull(f.guides.selectedGuide());assertEquals("method:2",f.guides.currentStep().id);assertTrue(f.coach.instruction().contains("trip hammer"));
        f.nativeState.bits.put(VarbitID.GIANTS_FOUNDRY_PREFORM_COMPLETION,1000);f.refresh();f.guides.onGameTick();f.settle();
        assertEquals("method:3",f.guides.currentStep().id);assertNotNull(f.guides.selectedGuide());
        f.guides.closeGuide();f.settle();
    }
    private static final class Fixture
    {
        static final String ID="training:giants-foundry";
        final FoundryNativeProgressTest.Fixture nativeState=new FoundryNativeProgressTest.Fixture();
        final ConfigManager config=mock(ConfigManager.class);final ClientThread thread=mock(ClientThread.class);final Queue<Runnable> queued=new ArrayDeque<>();
        final AtomicReference<MinigameActivityCatalog.Snapshot> current=new AtomicReference<>(MinigameActivityCatalog.Snapshot.EMPTY);
        final GuideCoachOverlay coach=new GuideCoachOverlay(nativeState.client);final RunewardenGuides guides;
        Fixture()
        {
            when(nativeState.client.getIntStack()).thenReturn(new int[]{0});
            when(config.getRSProfileKey()).thenReturn("rsprofile.foundry");doAnswer(call->{queued.add(call.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            guides=new RunewardenGuides(nativeState.client,thread,config,new GuideCatalog(new Gson()),new GuideNavigation(nativeState.client,mock(EventBus.class),mock(PluginManager.class)),new GuideMarkerOverlay(nativeState.client),coach,mock(OverlayManager.class));
            guides.setActivitySupplier(current::get);guides.setRefreshProgressAction(this::refresh);
        }
        void refresh(){assertFalse(SwingUtilities.isEventDispatchThread());current.set(nativeState.read());}
        void open()throws Exception{SwingUtilities.invokeAndWait(()->{guides.createPanel();guides.openGuide(ID);});settle();}
        void settle()throws Exception{for(int pass=0;pass<4;pass++){for(Runnable action;(action=queued.poll())!=null;)action.run();SwingUtilities.invokeAndWait(()->{});}}
    }
}
