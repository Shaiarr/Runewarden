/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MinigameCoachTest
{
    @Test public void observedEntryActivatesTheHudWithoutOpeningOrReplacingAnyGuide() throws Exception
    {
        Fixture fixture = new Fixture("{\"autoGuide\":true}"); String[] selected = {"quest-a"};
        fixture.coach.setGuideActions(() -> selected[0], id -> selected[0] = id);
        fixture.coach.start(); fixture.next.set(snapshot(Collections.emptySet())); fixture.coach.onGameTick(); flush();
        assertEquals("quest-a", selected[0]); assertEquals(MinigameActivityCatalog.Activity.GOTR, fixture.coach.reading().activity);
        Graphics2D g = new BufferedImage(500, 330, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            java.awt.Dimension size = fixture.coach.hud().render(g);
            assertNotNull(size); assertTrue(size.width <= 500); assertTrue(size.height <= 330);
            fixture.next.set(MinigameActivityCatalog.Snapshot.EMPTY); fixture.coach.onGameTick(); flush();
            assertNull(fixture.coach.hud().render(g)); assertEquals("quest-a", selected[0]);
        }
        finally { g.dispose(); fixture.coach.stop(); flush(); }
    }

    @Test public void aManualGuideSelectionSurvivesActivityExit() throws Exception
    {
        Fixture fixture = new Fixture("{\"autoGuide\":true}"); String[] selected = {"quest-a"};
        fixture.coach.setGuideActions(() -> selected[0], id -> selected[0] = id);
        fixture.coach.start(); fixture.next.set(snapshot(Collections.emptySet())); fixture.coach.onGameTick(); flush();
        selected[0] = "manual-guide"; fixture.next.set(MinigameActivityCatalog.Snapshot.EMPTY); fixture.coach.onGameTick(); flush();
        assertEquals("manual-guide", selected[0]); fixture.coach.stop(); flush();
    }

    @Test public void accountClearRejectsAnAlreadyQueuedAutomaticGuideSelection() throws Exception
    {
        Fixture fixture = new Fixture("{\"autoGuide\":true}"); List<String> actions = new ArrayList<>();
        fixture.coach.setGuideActions(() -> "quest-a", actions::add); fixture.coach.start(); fixture.next.set(snapshot(Collections.emptySet()));
        SwingUtilities.invokeAndWait(() -> { fixture.coach.onGameTick(); fixture.coach.clear(); }); flush();
        assertTrue(actions.isEmpty()); assertNull(fixture.coach.reading().activity); fixture.coach.stop(); flush();
    }

    @Test public void fullyDisabledCoachDoesNotReadTheClientOrRunDetection() throws Exception
    {
        Fixture fixture = new Fixture("{\"autoHud\":false,\"autoGuide\":false,\"highlights\":false,\"labels\":false}");
        fixture.coach.start(); fixture.coach.onGameTick(); fixture.coach.onGameTick();
        assertEquals(0, fixture.detections.get()); verifyNoInteractions(fixture.client);
        fixture.coach.stop(); flush();
    }

    @Test public void sceneEvidenceSeedsOnceBeforeDetectionAndDoesNotRescanEveryOutsideTick() throws Exception
    {
        Fixture fixture = new Fixture(null); int id = fixture.catalog.allHighlightObjectIds().iterator().next();
        WorldView view = mock(WorldView.class); Scene scene = mock(Scene.class); Tile tile = mock(Tile.class); GameObject object = mock(GameObject.class);
        when(fixture.client.getTopLevelWorldView()).thenReturn(view); when(view.getScene()).thenReturn(scene);
        when(scene.getTiles()).thenReturn(new Tile[][][] {{{tile}}}); when(tile.getGameObjects()).thenReturn(new GameObject[] {object});
        when(object.getId()).thenReturn(id); when(object.getWorldView()).thenReturn(view); when(object.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
        fixture.coach.start(); fixture.coach.onGameTick(); fixture.coach.onGameTick();
        assertEquals(1, ((MinigameSceneOverlay) fixture.coach.scene()).observedCount());
        verify(scene, times(1)).getTiles(); assertEquals(2, fixture.detections.get());
        fixture.coach.onGameObjectDespawned(object); assertEquals(0, ((MinigameSceneOverlay) fixture.coach.scene()).observedCount());
        fixture.coach.onSceneChanged(); fixture.coach.onGameTick(); verify(scene, times(2)).getTiles();
        fixture.coach.stop(); flush();
    }

    @Test public void supportPanelAndCheckboxesFitTheActual206PixelSidebar() throws Exception
    {
        Fixture fixture = new Fixture(null); fixture.coach.start(); fixture.next.set(snapshot(Collections.emptySet())); fixture.coach.onGameTick();
        SwingUtilities.invokeAndWait(() ->
        {
            JPanel panel = fixture.coach.createPanel();
            ((javax.swing.JButton) named(panel, "minigameActivitySettings")).doClick();
            for (int pass = 0; pass < 3; pass++) { panel.setSize(206, panel.getPreferredSize().height); layout(panel); }
            for (Component child : panel.getComponents())
            {
                assertEquals(0, child.getX()); assertTrue(child.getWidth() <= 206);
                if (child instanceof javax.swing.JCheckBox) assertTrue(((javax.swing.JCheckBox) child).getPreferredSize().width <= 206);
            }
            assertCheckboxesFit(panel);
            assertVerticalBounds(panel);
        });
        fixture.coach.stop(); flush();
    }

    @Test public void disablingOneActivityHidesItsCoachAndRestoresItsOwnedGuide() throws Exception
    {
        Fixture fixture = new Fixture("{\"autoGuide\":true}"); String[] selected = {"quest-a"};
        fixture.coach.setGuideActions(() -> selected[0], id -> selected[0] = id);
        fixture.coach.start(); fixture.next.set(snapshot(Collections.emptySet())); fixture.coach.onGameTick(); flush();
        assertEquals("quest-a", selected[0]);
        SwingUtilities.invokeAndWait(() ->
        {
            JPanel panel = fixture.coach.createPanel();
            ((javax.swing.JCheckBox) named(panel, "minigameEnabledGOTR")).doClick();
        });
        fixture.coach.onGameTick(); flush();
        assertNull(fixture.coach.reading().activity); assertEquals("quest-a", selected[0]);
        assertTrue(fixture.coach.preferences().disabledActivities.contains("GOTR"));
        SwingUtilities.invokeAndWait(() -> ((javax.swing.JCheckBox) named(fixture.coach.createPanel(), "minigameEnabledGOTR")).doClick());
        fixture.coach.onGameTick(); flush();
        assertEquals(MinigameActivityCatalog.Activity.GOTR, fixture.coach.reading().activity);
        fixture.coach.stop(); flush();
    }

    @Test public void invalidActivityPreferencesFallBackAndNullLegacySetsStaySafe() throws Exception
    {
        Fixture fixture = new Fixture("{\"disabledActivities\":[\"WINTERTODT\",\"UNKNOWN\"],\"scalePercent\":999}");
        fixture.coach.start();
        assertTrue(fixture.coach.preferences().disabledActivities.isEmpty());
        assertEquals(100, fixture.coach.preferences().scalePercent);
        fixture.coach.stop(); flush();
        Fixture legacy = new Fixture("{\"disabledActivities\":null}"); legacy.coach.start();
        assertTrue(legacy.coach.preferences().disabledActivities.isEmpty()); legacy.coach.stop(); flush();
    }

    @Test public void aMethodResolvedAfterEntryDoesNotOpenAnyGuide() throws Exception
    {
        Fixture fixture = new Fixture("{\"autoGuide\":true}"); String[] selected = {"quest-a"};
        fixture.coach.setGuideActions(() -> selected[0], id -> selected[0] = id); fixture.coach.start();
        fixture.next.set(withGuide(null)); fixture.coach.onGameTick(); flush(); assertEquals("quest-a", selected[0]);
        fixture.next.set(withGuide("training:blast-steel")); fixture.coach.onGameTick(); flush(); assertEquals("quest-a", selected[0]);
        fixture.next.set(withGuide("training:blast-mithril")); fixture.coach.onGameTick(); flush(); assertEquals("quest-a", selected[0]);
        fixture.next.set(MinigameActivityCatalog.Snapshot.EMPTY); fixture.coach.onGameTick(); flush(); assertEquals("quest-a", selected[0]);
        fixture.coach.stop(); flush();
    }

    @Test public void manualOpenIsTheOnlyActionThatOpensAGuide() throws Exception
    {
        Fixture fixture = new Fixture("{\"autoGuide\":true}"); String[] selected = {"quest-a"};
        AtomicReference<String> opened = new AtomicReference<>();
        fixture.coach.setGuideActions(() -> selected[0], id -> selected[0] = id);
        fixture.coach.setOpenGuideAction(opened::set); fixture.coach.start();
        fixture.next.set(snapshot(Collections.emptySet())); fixture.coach.onGameTick(); flush();
        assertEquals("quest-a", selected[0]); assertNull(opened.get());
        SwingUtilities.invokeAndWait(() -> ((javax.swing.JButton) named(fixture.coach.createPanel(), "openMinigameGuide")).doClick());
        assertEquals("minigame-gotr", opened.get()); fixture.coach.stop(); flush();
    }

    @Test public void learningPlanCompleteAdvancesAndUndoRestoresTheCheckedStep() throws Exception
    {
        Fixture fixture=new Fixture(null);fixture.coach.start();fixture.next.set(snapshot(Collections.emptySet()));fixture.coach.onGameTick();flush();
        SwingUtilities.invokeAndWait(()->
        {
            JPanel panel=fixture.coach.createPanel();
            javax.swing.JComboBox<?> choice=(javax.swing.JComboBox<?>)named(panel,"minigameLessonChoice");
            choice.setSelectedItem(MinigameLessonCatalog.get(MinigameActivityCatalog.Activity.PEST_CONTROL));
            javax.swing.JButton complete=(javax.swing.JButton)named(panel,"minigameLessonComplete");
            complete.doClick();
            assertFalse(((javax.swing.JCheckBox)named(panel,"minigameFollowLesson")).isSelected());
            assertTrue(allText(panel).contains("1 / 6 checked off"));assertTrue(allText(panel).contains("STEP 2 / 6"));
            ((javax.swing.JButton)named(panel,"minigameLessonUndo")).doClick();
            assertTrue(allText(panel).contains("0 / 6 checked off"));assertTrue(allText(panel).contains("STEP 1 / 6"));
        });
        fixture.coach.onGameTick();flush();
        SwingUtilities.invokeAndWait(()->assertEquals(MinigameActivityCatalog.Activity.PEST_CONTROL,
            ((MinigameLessonCatalog.Lesson)((javax.swing.JComboBox<?>)named(fixture.coach.createPanel(),"minigameLessonChoice")).getSelectedItem()).activity));
        fixture.coach.clear();flush();assertFalse(fixture.coach.sessionView().active());fixture.coach.stop();flush();
    }
    @Test public void everyLearningPlanCanBeCompletedAt206PixelsWithoutReadingClientOnSwing() throws Exception
    {
        Fixture fixture=new Fixture(null);fixture.coach.start();
        SwingUtilities.invokeAndWait(()->
        {
            JPanel panel=fixture.coach.createPanel();
            ((javax.swing.JButton)named(panel,"minigameActivitySettings")).doClick(0);
            javax.swing.JComboBox<?> choice=(javax.swing.JComboBox<?>)named(panel,"minigameLessonChoice");
            for(MinigameLessonCatalog.Lesson lesson:MinigameLessonCatalog.all())
            {
                if(!named(panel,"minigameLearningPlan").isVisible())
                    ((javax.swing.JButton)named(panel,"minigameResumeCoach")).doClick(0);
                choice.setSelectedItem(lesson);
                assertTrue("An explicitly resumed lesson must be visible",named(panel,"minigameLearningPlan").isVisible());
                for(int i=0;i<lesson.milestones.size();i++)
                {
                    for(int pass=0;pass<3;pass++){panel.setSize(206,panel.getPreferredSize().height);layout(panel);}
                    assertVerticalBounds(panel);assertCheckboxesFit(panel);
                    ((javax.swing.JButton)named(panel,"minigameLessonComplete")).doClick(0);
                }
                assertTrue(allText(panel).contains("PLAN COMPLETE"));
                assertFalse("Completing the final milestone closes the lesson",named(panel,"minigameLearningPlan").isVisible());
                assertTrue(named(panel,"minigameResumeCoach").isVisible());
            }
        });
        verifyNoInteractions(fixture.client);fixture.coach.stop();flush();
    }
    @Test public void publishedMinigameContextIsProfileBoundBeforeTheNextProducerTick() throws Exception
    {
        Fixture fixture=new Fixture(null);AtomicReference<String> profile=new AtomicReference<>("rsprofile-a");
        when(fixture.config.getRSProfileKey()).thenAnswer(e->profile.get());fixture.coach.start();fixture.next.set(snapshot(Collections.emptySet()));fixture.coach.onGameTick();
        assertTrue(fixture.coach.sessionView().active());assertEquals("rsprofile-a",fixture.coach.sessionView().profile);
        profile.set("rsprofile-b");assertFalse(fixture.coach.sessionView().active());fixture.coach.stop();flush();
    }
    private static String allText(Container parent)
    {
        StringBuilder text=new StringBuilder();for(Component child:parent.getComponents())
        {if(child instanceof javax.swing.JLabel)text.append(((javax.swing.JLabel)child).getText());
            if(child instanceof GuideText)text.append(((GuideText)child).getText());
            if(child instanceof Container)text.append(allText((Container)child));}return text.toString();
    }

    private static MinigameActivityCatalog.Snapshot withGuide(String guide)
    {
        return new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.BLAST_FURNACE, "Blast Furnace", guide,
            Collections.emptyList(), Collections.emptyList(), Collections.emptySet(), Collections.emptyList(), true);
    }

    private static MinigameActivityCatalog.Snapshot snapshot(Set<Integer> ids)
    {
        return new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.GOTR, "Guardians of the Rift", "minigame-gotr",
            List.of(new MinigameActivityCatalog.Fact("Warmth", "85%"), new MinigameActivityCatalog.Fact("Points", "325")),
            List.of(new MinigameActivityCatalog.Step("Feed the brazier", "Put your prepared bruma kindling into the lit brazier.")),
            ids, Collections.emptyList(), true);
    }
    @Test public void endingTheRunKeepsItsGuideAndCoachDismissedUntilRestartOrAnotherActivity() throws Exception
    {
        Fixture f=new Fixture("{\"autoGuide\":true}");String[] selected={null};f.coach.setGuideActions(()->selected[0],id->selected[0]=id);
        java.awt.Graphics2D g=new BufferedImage(600,400,BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            f.coach.start();f.next.set(snapshot(Collections.emptySet()));f.coach.onGameTick();flush();assertNotNull(f.coach.hud().render(g));
            f.coach.dismissGuide();selected[0]=null;flush();
            for(int i=0;i<3;i++){f.coach.onGameTick();flush();assertNull(f.coach.hud().render(g));assertNull(selected[0]);}
            f.coach.resumeGuide();flush();f.coach.onGameTick();assertNotNull(f.coach.hud().render(g));
            f.coach.dismissGuide();flush();f.next.set(MinigameActivityCatalog.Snapshot.EMPTY);f.coach.onGameTick();flush();
            f.next.set(snapshot(Collections.emptySet()));f.coach.onGameTick();flush();assertNull(f.coach.hud().render(g));
            f.next.set(new MinigameActivityCatalog.Snapshot(MinigameActivityCatalog.Activity.WINTERTODT,"Wintertodt","training:wintertodt",List.of(),List.of(),Set.of(),List.of(),true));
            f.coach.onGameTick();flush();assertNotNull(f.coach.hud().render(g));assertNull(selected[0]);
        }
        finally{g.dispose();f.coach.stop();flush();}
    }
    @Test public void endingTheRunClearsSidebarInstructionsAndLearningUntilAnExplicitResume() throws Exception
    {
        Fixture f=new Fixture(null);f.coach.start();f.next.set(snapshot(Collections.emptySet()));f.coach.onGameTick();flush();
        try
        {
            SwingUtilities.invokeAndWait(()->{
                JPanel panel=f.coach.createPanel();GuideText task=(GuideText)named(panel,"minigameCurrentInstruction");
                assertTrue(task.getText().contains("prepared bruma kindling"));assertTrue(named(panel,"minigameLearningPlan").isVisible());
                f.coach.dismissGuide();
                assertEquals("",task.getText());assertFalse(task.isVisible());assertFalse(named(panel,"minigameLearningPlan").isVisible());
                assertFalse(named(panel,"openMinigameGuide").isVisible());assertTrue(named(panel,"minigameResumeCoach").isVisible());
                assertTrue(allText(panel).contains("Run ended"));assertTrue(allText(panel).contains("Guardians of the Rift"));
            });
            f.next.set(MinigameActivityCatalog.Snapshot.EMPTY);f.coach.onGameTick();flush();f.next.set(snapshot(Collections.emptySet()));f.coach.onGameTick();flush();
            SwingUtilities.invokeAndWait(()->{
                JPanel panel=f.coach.createPanel();assertEquals("",((GuideText)named(panel,"minigameCurrentInstruction")).getText());
                assertFalse(named(panel,"minigameLearningPlan").isVisible());
                ((javax.swing.JButton)named(panel,"minigameResumeCoach")).doClick(0);
                assertTrue(((GuideText)named(panel,"minigameCurrentInstruction")).getText().contains("prepared bruma kindling"));
                assertTrue(named(panel,"minigameLearningPlan").isVisible());assertTrue(named(panel,"openMinigameGuide").isVisible());
                assertFalse(named(panel,"minigameResumeCoach").isVisible());
            });
            assertEquals(MinigameActivityCatalog.Activity.GOTR,f.coach.reading().activity);assertFalse(f.coach.guidanceDismissed());
        }
        finally{f.coach.stop();flush();}
    }
    private static final class Fixture
    {
        final Client client = mock(Client.class);
        final ConfigManager config = mock(ConfigManager.class);
        final MinigameActivityCatalog catalog = new MinigameActivityCatalog();
        final AtomicReference<MinigameActivityCatalog.Snapshot> next = new AtomicReference<>(MinigameActivityCatalog.Snapshot.EMPTY);
        final AtomicInteger detections = new AtomicInteger();
        final MinigameCoach coach;
        Fixture(String preferences)
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(mock(Player.class));
            when(client.getCanvasWidth()).thenReturn(500); when(client.getCanvasHeight()).thenReturn(330);
            when(config.getConfiguration(MinigameCoach.GROUP, MinigameCoach.KEY)).thenReturn(preferences);
            coach = new MinigameCoach(client, catalog, config, new Gson(), new RunewardenConfig() {}, () -> { detections.incrementAndGet(); return next.get(); });
        }
    }
    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }
    private static void layout(Container container)
    {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
    }
    private static Component named(Container parent, String name)
    {
        for (Component child : parent.getComponents())
        {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container) { Component found = named((Container) child, name); if (found != null) return found; }
        }
        return null;
    }
    private static void assertCheckboxesFit(Container container)
    {
        for (Component child : container.getComponents())
        {
            if (child instanceof javax.swing.JCheckBox) assertTrue(child.getName()+" ("+((javax.swing.JCheckBox)child).getText()+") needs "+child.getPreferredSize().width+"px in "+container.getWidth()+"px",
                child.getPreferredSize().width <= container.getWidth());
            if (child instanceof Container) assertCheckboxesFit((Container) child);
        }
    }
    private static void assertVerticalBounds(Container container)
    {
        for (Component child : container.getComponents())
        {
            if (!child.isVisible()) continue;
            assertTrue(child.getName() + " exceeds parent height", child.getY() + child.getHeight() <= container.getHeight());
            if (child instanceof Container) assertVerticalBounds((Container) child);
        }
    }
}
