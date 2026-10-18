/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
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

public class GuideObjectiveLifecycleTest
{
    @Test public void openingAnyEntryPointUsesObservedWorkInsteadOfSavedPreparationAndOneBodyInBothViews() throws Exception
    {
        for(int entry=0;entry<4;entry++)
        {
            Fixture f=new Fixture();f.observed.set(f.reading("method:1","poured","Collect the preform","The alloy is already loaded. Take your poured sword.",false,Set.of("prepare:1","prepare:2")));
            when(f.config.getRSProfileConfiguration("runewarden",GuideProgress.positionKey(Fixture.ID),Integer.class)).thenReturn(0);
            final int kind=entry;SwingUtilities.invokeAndWait(()->{
                f.panel=f.guides.createPanel();
                if(kind==0)f.guides.openGuide(Fixture.ID);else if(kind==1)f.guides.openForRun(Fixture.ID);else if(kind==2)f.guides.openAutomaticGuide(Fixture.ID);else f.guides.openSection(Fixture.ID,0);
            });f.settle();
            SwingUtilities.invokeAndWait(()->{
                assertEquals("method:1",f.guides.currentStep().id);assertTrue(f.coach.instruction().contains("Take your poured sword."));
                assertTrue(f.texts().contains("Collect the preform\nThe alloy is already loaded. Take your poured sword."));
                assertFalse(f.texts().contains("Fill the crucible and pour the sword mould."));
            });
            verify(f.config,never()).setRSProfileConfiguration(eq("runewarden"),eq(GuideProgress.completedKey(Fixture.ID)),any());f.close();
        }
    }
    @Test public void repeatedObjectivesReplaceTransientCompletionWithoutPersistingPreviousRounds() throws Exception
    {
        Fixture f=new Fixture();f.observed.set(f.reading("method:2","work1","Hammer the sword","Use the trip hammer.",false,Set.of("prepare:1","prepare:2","method:1")));f.open();
        SwingUtilities.invokeAndWait(()->assertEquals("method:2",f.guides.currentStep().id));
        f.observed.set(f.reading("method:1","load2","Load metal","Load metal for the new commission.",false,Set.of()));f.guides.onGameTick();f.settle();
        SwingUtilities.invokeAndWait(()->{assertEquals("method:1",f.guides.currentStep().id);assertTrue(f.button("Complete step →").isEnabled());assertFalse(f.texts().stream().anyMatch(s->s.startsWith("✓ ")&&s.contains("Method")));});
        verify(f.config,never()).setRSProfileConfiguration(eq("runewarden"),eq(GuideProgress.completedKey(Fixture.ID)),any());f.close();
    }
    @Test public void historicalManualCompletionCannotHideAnActiveNewNativeCycle() throws Exception
    {
        Fixture f=new Fixture();java.util.LinkedHashSet<String> previous=new java.util.LinkedHashSet<>();
        for(GuideCatalog.DisplayStep step:new GuideCatalog(new Gson()).entry(Fixture.ID).flattened())previous.add(step.step.id);
        when(f.config.getRSProfileConfiguration("runewarden",GuideProgress.completedKey(Fixture.ID),String.class)).thenReturn(GuideProgress.encode(previous));
        f.observed.set(f.reading("method:2","new-cycle","Hammer the sword","Work on this new sword.",false,Set.of("prepare:1","prepare:2","method:1")));f.open();
        SwingUtilities.invokeAndWait(()->{assertNotNull(f.guides.selectedGuide());assertEquals("method:2",f.guides.currentStep().id);assertTrue(f.button("Complete step →").isEnabled());});
        f.close();
    }
    @Test public void positivelyLeavingTheMatchingActivityClosesItsGuideButNotAnUnrelatedLesson() throws Exception
    {
        Fixture f=new Fixture();f.open();MinigameActivityCatalog.Snapshot previous=new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,"Giants' Foundry",Fixture.ID,List.of(),List.of(),Set.of(),List.of(),true);
        f.guides.closeActivityGuide(previous);f.settle();assertNull(f.guides.selectedGuide());
        SwingUtilities.invokeAndWait(()->f.guides.openGuide("boss:hueycoatl"));f.settle();f.guides.closeActivityGuide(previous);f.settle();assertEquals("boss:hueycoatl",f.guides.selectedGuide().id);f.close();
    }
    @Test public void terminalNativeProofClosesWithoutCallingTheWholeRunCompletionAction() throws Exception
    {
        Fixture f=new Fixture();AtomicInteger round=new AtomicInteger(),manual=new AtomicInteger(),dismiss=new AtomicInteger();
        f.guides.setNativeCompletionAction(id->round.incrementAndGet());f.guides.setCompletionAction(id->manual.incrementAndGet());f.guides.setDismissAction(dismiss::incrementAndGet);
        f.observed.set(f.reading("method:3","return","Return sword","Hand the finished sword to Kovac.",false,Set.of()));f.open();
        assertNotNull(f.guides.selectedGuide());
        f.observed.set(f.reading(null,"reward","Sword delivered","Commission rewarded.",true,Set.of()));f.guides.onGameTick();f.settle();
        assertNull(f.guides.selectedGuide());assertNull(f.coach.render(f.graphics));assertEquals(1,round.get());assertEquals(0,manual.get());assertEquals(1,dismiss.get());
        SwingUtilities.invokeAndWait(()->f.guides.openAutomaticGuide(Fixture.ID));f.settle();assertNull(f.guides.selectedGuide());f.close();
    }
    @Test public void closeGuideInvalidatesQueuedCapturesAndAutomaticReopensUntilExplicitResume() throws Exception
    {
        Fixture f=new Fixture();AtomicInteger dismissed=new AtomicInteger();f.guides.setDismissAction(dismissed::incrementAndGet);f.open();
        f.observed.set(f.reading("method:2","work","Hammer","Use the hammer.",false,Set.of()));
        SwingUtilities.invokeAndWait(()->{
            Thread worker=new Thread(()->f.guides.openAutomaticGuide(Fixture.ID));worker.start();try{worker.join(2000);}catch(InterruptedException error){throw new AssertionError(error);}
            f.button("Close guide").doClick();
        });f.settle();assertNull(f.guides.selectedGuide());assertNull(f.coach.render(f.graphics));assertEquals(1,dismissed.get());
        SwingUtilities.invokeAndWait(()->f.guides.openAutomaticGuide(Fixture.ID));f.settle();assertNull(f.guides.selectedGuide());
        f.guides.resumeAutomaticGuidance();SwingUtilities.invokeAndWait(()->f.guides.openAutomaticGuide(Fixture.ID));f.settle();assertEquals("method:2",f.guides.currentStep().id);f.close();
    }
    @Test public void navigatingToTheFinalLessonDoesNotFinishButExplicitlyCompletingItCloses() throws Exception
    {
        Fixture f=new Fixture();AtomicInteger done=new AtomicInteger();f.guides.setCompletionAction(id->done.incrementAndGet());f.open();
        SwingUtilities.invokeAndWait(()->{
            int count=f.guides.selectedGuide().flattened().size();for(int index=1;index<count;index++)f.button("Next step").doClick();
            assertNotNull(f.guides.selectedGuide());assertEquals(0,done.get());f.button("Complete step →").doClick();assertNull(f.guides.selectedGuide());assertEquals(1,done.get());
        });f.close();
    }
    @Test public void temporaryNativeHudGapKeepsTheCurrentObjectiveUntilAConfirmedActivityExit()throws Exception
    {
        Fixture f=new Fixture();AtomicReference<MinigameActivityCatalog.Snapshot> current=new AtomicReference<>(new MinigameActivityCatalog.Snapshot(
            MinigameActivityCatalog.Activity.GIANTS_FOUNDRY,"Giants' Foundry",Fixture.ID,List.of(),List.of(new MinigameActivityCatalog.Step("Collect the preform","Take your poured sword.")),Set.of(),List.of(),true)
            .withGuideProgress("method:1","collect",false,Set.of("prepare:1","prepare:2")));
        f.guides.setActivitySupplier(current::get);f.open();assertTrue(f.coach.instruction().contains("Take your poured sword."));
        MinigameActivityCatalog.Snapshot previous=current.get();current.set(MinigameActivityCatalog.Snapshot.EMPTY);f.guides.onGameTick();f.settle();
        assertEquals("method:1",f.guides.currentStep().id);assertTrue(f.coach.instruction().contains("Take your poured sword."));assertFalse(f.texts().contains("Fill the crucible and pour the sword mould."));
        f.guides.closeActivityGuide(previous);f.settle();assertNull(f.guides.selectedGuide());assertNull(f.coach.render(f.graphics));f.close();
    }
    @Test public void observationsForAnotherCharacterCannotMoveOrCloseTheCurrentGuide() throws Exception
    {
        Fixture f=new Fixture();f.observed.set(new GuideLiveProgress.Reading("rsprofile.other",Fixture.ID,null,"completed","Done","Done",true,Set.of()));f.open();
        assertNotNull(f.guides.selectedGuide());assertEquals("prepare:1",f.guides.currentStep().id);f.close();
    }
    @Test public void activityExitClosesItsExplicitVariantEvenWhenTheCurrentRecipeIsUnresolved()throws Exception
    {
        String[] ids={"training:homes-expert","training:blast-gold"};
        MinigameActivityCatalog.Activity[] activities={MinigameActivityCatalog.Activity.MAHOGANY_HOMES,MinigameActivityCatalog.Activity.BLAST_FURNACE};
        for(int i=0;i<ids.length;i++)
        {
            Fixture f=new Fixture();String id=ids[i];SwingUtilities.invokeAndWait(()->{f.panel=f.guides.createPanel();f.guides.openGuide(id);});f.settle();assertNotNull(f.guides.selectedGuide());
            MinigameActivityCatalog.Snapshot exited=new MinigameActivityCatalog.Snapshot(activities[i],"Activity",null,List.of(),List.of(),Set.of(),List.of(),true);
            assertFalse("Unresolved recipe cannot supply this variant's progress",GuideCompletion.matchesActivity(f.guides.selectedGuide(),exited));
            f.guides.closeActivityGuide(exited);f.settle();assertNull(f.guides.selectedGuide());f.close();
        }
        Fixture other=new Fixture();other.open();other.guides.closeActivityGuide(new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.BLAST_FURNACE,"Blast Furnace",null,List.of(),List.of(),Set.of(),List.of(),true));
        other.settle();assertNotNull("Leaving another activity cannot close an unrelated guide",other.guides.selectedGuide());other.close();
    }
    private static final class Fixture
    {
        static final String ID="training:giants-foundry";
        final Client client=mock(Client.class);final ClientThread thread=mock(ClientThread.class);final ConfigManager config=mock(ConfigManager.class);
        final Queue<Runnable> queued=new ArrayDeque<>();final AtomicReference<GuideLiveProgress.Reading> observed=new AtomicReference<>(GuideLiveProgress.Reading.UNKNOWN);
        final GuideCoachOverlay coach=new GuideCoachOverlay(client);final Graphics2D graphics=new BufferedImage(500,500,BufferedImage.TYPE_INT_ARGB).createGraphics();
        final RunewardenGuides guides;JComponent panel;
        Fixture()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);when(client.getIntStack()).thenReturn(new int[]{0});when(config.getRSProfileKey()).thenReturn("rsprofile.current");
            doAnswer(call->{queued.add(call.getArgument(0));return null;}).when(thread).invokeLater(any(Runnable.class));
            guides=new RunewardenGuides(client,thread,config,new GuideCatalog(new Gson()),new GuideNavigation(client,mock(EventBus.class),mock(PluginManager.class)),new GuideMarkerOverlay(client),coach,mock(OverlayManager.class));
            guides.setProgressSupplier(id->{assertFalse("Native progress must never be read on Swing",SwingUtilities.isEventDispatchThread());return observed.get();});
        }
        GuideLiveProgress.Reading reading(String step,String key,String title,String body,boolean done,Set<String> confirmed)
        {return new GuideLiveProgress.Reading("rsprofile.current",ID,step,key,title,body,done,confirmed);}
        void open()throws Exception{SwingUtilities.invokeAndWait(()->{panel=guides.createPanel();guides.openGuide(ID);});settle();}
        void settle()throws Exception{for(int pass=0;pass<4;pass++){for(Runnable action;(action=queued.poll())!=null;)action.run();SwingUtilities.invokeAndWait(()->{});}}
        List<String> texts(){List<String> values=new ArrayList<>();collect(panel,values);return values;}
        void collect(Container parent,List<String> values){for(Component c:parent.getComponents()){if(c instanceof GuideText)values.add(((GuideText)c).getText());if(c instanceof Container)collect((Container)c,values);}}
        JButton button(String title){return find(panel,title);}
        JButton find(Container parent,String title){for(Component c:parent.getComponents()){if(c instanceof JButton&&title.equals(((JButton)c).getText()))return(JButton)c;if(c instanceof Container){JButton result=find((Container)c,title);if(result!=null)return result;}}return null;}
        void close(){guides.clearSelection();graphics.dispose();}
    }
}
