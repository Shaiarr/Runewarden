/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises actual guidance presentation and lifecycle guards without optional mocking dependencies. */
public class MinigameReleasePolicyTest
{
    @Test public void allTwentyAuthoredNativeContextsKeepGuidanceAvailable()
    {
        assertEquals(20, MinigameActivityCatalog.Activity.values().length);
        assertFalse(MinigameReleasePolicy.allowsAutomaticGuidance(null));
        for (MinigameActivityCatalog.Activity activity : MinigameActivityCatalog.Activity.values())
        {
            assertTrue(activity.name(), MinigameReleasePolicy.allowsAutomaticGuidance(activity));
            assertFalse(activity.name(), MinigameReleasePolicy.nativeFactsOnly(activity));
        }
    }

    @Test public void everyCatalogSnapshotRetainsItsObjectivesAndObservedTargets()
    {
        for (MinigameActivityCatalog.Activity activity : MinigameActivityCatalog.Activity.values())
        {
            MinigameActivityCatalog.Snapshot raw = snapshot(activity);
            MinigameActivityCatalog.Snapshot result = MinigameReleasePolicy.releaseSnapshot(raw);
            assertEquals(activity, result.activity);
            assertEquals(raw.title, result.title);
            assertEquals(raw.guideId, result.guideId);
            assertEquals(raw.facts, result.facts);
            assertEquals(raw.steps, result.steps);
            assertEquals(raw.highlightObjectIds, result.highlightObjectIds);
            assertEquals(raw.highlightNpcIds, result.highlightNpcIds);
            assertEquals(raw.referenceTiles, result.referenceTiles);
            assertSame(raw, result);
            assertSame(result, MinigameReleasePolicy.releaseSnapshot(result));
            assertFalse(raw.steps.isEmpty());
        }
    }

    @Test public void absentOrUnrecognizedActivityDoesNotPublishAnObjective()
    {
        assertSame(MinigameActivityCatalog.Snapshot.EMPTY, MinigameReleasePolicy.releaseSnapshot(null));
        assertSame(MinigameActivityCatalog.Snapshot.EMPTY, MinigameReleasePolicy.releaseSnapshot(MinigameActivityCatalog.Snapshot.EMPTY));
        MinigameActivityCatalog.Snapshot unrecognized = new MinigameActivityCatalog.Snapshot(null, "Other content", "unknown",
            List.of(), List.of(new MinigameActivityCatalog.Step("Unknown objective", "Unknown instruction")), Set.of(1), List.of(), false);
        assertSame(MinigameActivityCatalog.Snapshot.EMPTY, MinigameReleasePolicy.releaseSnapshot(unrecognized));
    }

    @Test public void ordinaryChecklistFollowingIsNotBlockedByAnActivityName()
    {
        for (String id : new String[] {"training:wintertodt", "training:tempoross", "training:pest-control-experience",
            "minigame:barbarian-assault", "training:nightmare-zone-absorptions", "training:nightmare-zone-dharok",
            "minigame:soul-wars", "minigame:castle-wars", "training:volcanic-mine"})
        {
            GuideCatalog.Entry entry = new GuideCatalog.Entry(); entry.id = id;
            entry.category = id.startsWith("training:") ? "training" : "minigame";
            assertTrue(id, GuideCompletion.supportsAutomatic(entry));
        }
        GuideCatalog.Entry unrelated = new GuideCatalog.Entry(); unrelated.id = "custom-reference";
        unrelated.title = "Wintertodt: practice"; unrelated.category = "training";
        assertTrue(GuideCompletion.supportsAutomatic(unrelated));
    }

    @Test public void actualProducerPublishesNativeFactsForAllTwentyWithoutOpeningAnyGuide() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.coach.preferences().autoGuide = true;
            for (MinigameActivityCatalog.Activity activity : MinigameActivityCatalog.Activity.values())
            {
                f.next.set(snapshot(activity)); f.coach.onGameTick(); flush();
                MinigameActivityCatalog.Snapshot released = f.coach.reading();
                assertEquals(activity, released.activity);
                assertEquals("325", released.facts.get(0).value);
                assertEquals("Observed activity objective", released.steps.get(0).title);
                assertEquals("quest-before", f.selected.get());
                assertTrue(f.actions.isEmpty());
                assertTrue(f.coach.sessionView().active());
                assertEquals(Integer.valueOf(325), f.coach.sessionView().scoreValue);
                assertEquals("Observed activity objective", f.coach.sessionView().phaseId);
            }
        }
        finally { f.close(); }
    }

    @Test public void switchingActivitiesNeverReplacesTheManuallySelectedGuide() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.coach.preferences().autoGuide = true;
            f.next.set(snapshot(MinigameActivityCatalog.Activity.GOTR)); f.coach.onGameTick(); flush();
            assertEquals("quest-before", f.selected.get());
            f.next.set(snapshot(MinigameActivityCatalog.Activity.WINTERTODT)); f.coach.onGameTick(); flush();
            assertEquals("quest-before", f.selected.get());
            f.next.set(MinigameActivityCatalog.Snapshot.EMPTY); f.coach.onGameTick(); flush();
            assertNull(f.coach.reading().activity);
            assertEquals("quest-before", f.selected.get());
            f.next.set(snapshot(MinigameActivityCatalog.Activity.GIANTS_FOUNDRY)); f.coach.onGameTick(); flush();
            assertEquals("quest-before", f.selected.get());
            f.next.set(MinigameActivityCatalog.Snapshot.EMPTY); f.coach.onGameTick(); flush();
            assertEquals("quest-before", f.selected.get());
        }
        finally { f.close(); }
    }

    @Test public void queuedOldGuidesAndSceneRefreshCannotRestoreThePreviousActivity() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.coach.preferences().autoGuide = true;
            MinigameActivityCatalog.Snapshot ordinary = snapshot(MinigameActivityCatalog.Activity.GOTR);
            SwingUtilities.invokeAndWait(() ->
            {
                f.next.set(ordinary); f.coach.onGameTick();
                f.next.set(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS)); f.coach.onGameTick();
            });
            flush(); assertTrue(f.actions.isEmpty());
            MinigameSceneOverlay scene = (MinigameSceneOverlay) f.coach.scene();
            GameObject object = proxy(GameObject.class, (method, args) -> method.equals("getId") ? 29311
                : method.equals("getWorldLocation") ? new WorldPoint(3200, 3200, 0) : null);
            scene.observe(object); scene.refresh(ordinary);
            assertEquals(0, scene.targetCount()); assertEquals(0, scene.npcTargetCount());
            f.next.set(ordinary); f.coach.onGameTick(); flush(); assertEquals(1, scene.targetCount());
            f.next.set(snapshot(MinigameActivityCatalog.Activity.BARBARIAN_ASSAULT)); f.coach.onGameTick(); flush();
            assertEquals(1, scene.targetCount());
            scene.refresh(ordinary); assertEquals(0, scene.targetCount());
        }
        finally { f.close(); }
    }

    @Test public void profileChangeRejectsAlreadyQueuedGuideAndImmediatelyHidesTheOldSnapshot() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.coach.preferences().autoGuide = true;
            SwingUtilities.invokeAndWait(() ->
            {
                f.next.set(snapshot(MinigameActivityCatalog.Activity.GOTR)); f.coach.onGameTick();
                try { setField(f.config, "rsProfileKey", "rsprofile-b"); }
                catch (Exception e) { throw new AssertionError(e); }
            });
            flush(); assertTrue(f.actions.isEmpty()); assertNull(f.coach.reading().activity);
        }
        finally { f.close(); }
    }

    @Test public void stopRejectsAnAlreadyQueuedAutomaticGuide() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.coach.preferences().autoGuide = true;
            SwingUtilities.invokeAndWait(() -> {
                f.next.set(snapshot(MinigameActivityCatalog.Activity.WINTERTODT));
                f.coach.onGameTick();
                f.coach.stop();
            });
            flush();
            assertTrue(f.actions.isEmpty());
            assertNull(f.coach.reading().activity);
        }
        finally { f.close(); }
    }

    @Test public void warmthAndMineExitControlsShowRetainedThresholdsAndStopWithTheCoach() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.coach.preferences().warmthThreshold = 40;
            f.coach.preferences().volcanicExitSeconds = 90;
            SwingUtilities.invokeAndWait(() -> {
                JPanel panel = f.coach.createPanel();
                JSpinner warmth = (JSpinner) named(panel, "minigameWarmthThreshold");
                JSpinner exit = (JSpinner) named(panel, "minigameVolcanicExit");
                assertEquals(40, ((Number) warmth.getValue()).intValue());
                assertEquals(90, ((Number) exit.getValue()).intValue());
                assertTrue(warmth.isEnabled());
                assertTrue(exit.isEnabled());
            });
            f.coach.stop(); flush();
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(named(f.coach.createPanel(), "minigameWarmthThreshold").isEnabled());
                assertFalse(named(f.coach.createPanel(), "minigameVolcanicExit").isEnabled());
            });
        }
        finally { f.close(); }
    }

    @Test public void manualLessonProgressAndOptionalObservedLessonFollowingBothRemainAvailable() throws Exception
    {
        Fixture f = new Fixture();
        try
        {
            f.next.set(snapshot(MinigameActivityCatalog.Activity.TEMPOROSS)); f.coach.onGameTick(); flush();
            SwingUtilities.invokeAndWait(() ->
            {
                JPanel panel = f.coach.createPanel();
                JComboBox<?> choices = (JComboBox<?>) named(panel, "minigameLessonChoice");
                assertEquals(MinigameActivityCatalog.Activity.TEMPOROSS, ((MinigameLessonCatalog.Lesson) choices.getSelectedItem()).activity);
                ((JCheckBox) named(panel, "minigameFollowLesson")).doClick(0);
                choices.setSelectedItem(MinigameLessonCatalog.get(MinigameActivityCatalog.Activity.GOTR));
                choices.setSelectedItem(MinigameLessonCatalog.get(MinigameActivityCatalog.Activity.TEMPOROSS));
                ((JButton) named(panel, "minigameLessonComplete")).doClick(0);
                assertTrue(named(panel, "minigameFollowLesson").isEnabled());
            });
            f.coach.onGameTick(); flush();
            SwingUtilities.invokeAndWait(() ->
            {
                assertTrue(allText(f.coach.createPanel()).contains("1 / 7 checked off"));
                assertTrue(allText(f.coach.createPanel()).contains("STEP 2 / 7"));
                ((JCheckBox) named(f.coach.createPanel(), "minigameFollowLesson")).doClick(0);
                ((JButton) named(f.coach.createPanel(), "openMinigameGuide")).doClick(0);
            });
            assertEquals("guide:TEMPOROSS", f.manuallyOpened.get());
            MinigameActivityCatalog.Snapshot previous = f.next.get();
            f.next.set(new MinigameActivityCatalog.Snapshot(previous.activity, previous.title, previous.guideId, previous.facts,
                List.of(new MinigameActivityCatalog.Step("Cook harpoonfish", "Cook the carried raw fish")), previous.highlightObjectIds,
                previous.referenceTiles, previous.liveSupported, previous.highlightNpcIds));
            f.coach.onGameTick(); flush();
            SwingUtilities.invokeAndWait(() -> {
                // Headless panels are not showing; request the same refresh a visible sidebar receives.
                try
                {
                    java.lang.reflect.Method refresh = MinigameCoach.class.getDeclaredMethod("refreshPanel");
                    refresh.setAccessible(true); refresh.invoke(f.coach);
                }
                catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
                String displayed=allText(f.coach.createPanel());
                assertTrue(displayed,displayed.contains("LIVE TASK"));
                assertTrue(displayed,displayed.contains("Cook harpoonfish"));
                assertTrue(displayed,displayed.contains("Cook the carried raw fish"));
                assertTrue("Following an observed objective does not complete preceding milestones",
                    allText(f.coach.createPanel()).contains("1 / 7 checked off"));
            });
        }
        finally { f.close(); }
    }

    @Test public void hudConsumerDisplaysTheKnownNativeObjectiveForAllTwentyActivities() throws Exception
    {
        java.lang.reflect.Method layout = MinigameHudOverlay.class.getDeclaredMethod("layout", Graphics2D.class, MinigameActivityCatalog.Snapshot.class, boolean.class, RunewardenConfig.TimerDisplay.class, List.class);
        layout.setAccessible(true);
        Graphics2D graphics = new BufferedImage(600, 400, BufferedImage.TYPE_INT_ARGB).createGraphics();
        try
        {
            for (MinigameActivityCatalog.Activity activity : MinigameActivityCatalog.Activity.values())
            {
                Object card = layout.invoke(null, graphics, snapshot(activity), false, RunewardenConfig.TimerDisplay.BOTH, List.of());
                assertFalse(((List<?>) field(card, "action")).isEmpty());
                assertFalse(((List<?>) field(card, "body")).isEmpty());
                assertFalse(((List<?>) field(card, "facts")).isEmpty());
            }
        }
        finally { graphics.dispose(); }
    }

    private static MinigameActivityCatalog.Snapshot snapshot(MinigameActivityCatalog.Activity activity)
    {
        return new MinigameActivityCatalog.Snapshot(activity, activity.toString(), "guide:" + activity,
            List.of(new MinigameActivityCatalog.Fact("Points", "325")),
            List.of(new MinigameActivityCatalog.Step("Observed activity objective", "Continue the current resource or inventory objective")),
            Set.of(29311), List.of(new WorldPoint(3200, 3200, 0)), true, Set.of(1));
    }

    private interface Answer { Object get(String method, Object[] args); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, Answer answer)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (object, method, args) ->
        {
            if (method.getName().equals("hashCode")) return System.identityHashCode(object);
            if (method.getName().equals("equals")) return object == args[0];
            Object value = answer.get(method.getName(), args);
            if (value != null || !method.getReturnType().isPrimitive()) return value;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == long.class) return 0L;
            return 0;
        });
    }

    private static final class Fixture
    {
        final ConfigManager config;
        final MinigameCoach coach;
        final AtomicReference<MinigameActivityCatalog.Snapshot> next = new AtomicReference<>(MinigameActivityCatalog.Snapshot.EMPTY);
        final AtomicReference<String> selected = new AtomicReference<>("quest-before"), manuallyOpened = new AtomicReference<>();
        final List<String> actions = new ArrayList<>();
        Fixture() throws Exception
        {
            // Native ConfigManager has no public constructor. This test-only fixture creates its
            // in-memory data without starting the native client's scheduling or file services.
            Gson gson = new Gson();
            Gson nativeFixtureGson = new GsonBuilder().setExclusionStrategies(new ExclusionStrategy()
            {
                @Override public boolean shouldSkipField(FieldAttributes field) { return true; }
                @Override public boolean shouldSkipClass(Class<?> type) { return false; }
            }).create();
            config = nativeFixtureGson.fromJson("{}", ConfigManager.class);
            Class<?> data = Class.forName("net.runelite.client.config.ConfigData");
            Object configData = nativeFixtureGson.fromJson("{}", data);
            setField(configData, "properties", new java.util.concurrent.ConcurrentHashMap<String, String>());
            setField(config, "configProfile", configData);
            setField(config, "rsProfileConfigProfile", configData);
            setField(config, "rsProfileKey", "rsprofile-a");
            Player player = proxy(Player.class, (method, args) -> null);
            Client client = proxy(Client.class, (method, args) ->
            {
                if (method.equals("getGameState")) return GameState.LOGGED_IN;
                if (method.equals("getLocalPlayer")) return player;
                if (method.equals("getCanvasWidth")) return 600;
                if (method.equals("getCanvasHeight")) return 400;
                return null;
            });
            coach = new MinigameCoach(client, new MinigameActivityCatalog(), config, gson, new RunewardenConfig() { }, next::get);
            coach.setGuideActions(selected::get, id -> { selected.set(id); actions.add(id); });
            coach.setOpenGuideAction(manuallyOpened::set); coach.start();
        }
        void close() throws Exception { coach.stop(); flush(); }
    }
    private static Object field(Object object, String name) throws Exception
    { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static void setField(Object object, String name, Object value) throws Exception
    { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value); }
    private static void flush() throws Exception { SwingUtilities.invokeAndWait(() -> { }); }
    private static java.awt.Component named(Container parent, String name)
    {
        for (java.awt.Component child : parent.getComponents())
        { if (name.equals(child.getName())) return child; if (child instanceof Container) { java.awt.Component found = named((Container) child, name); if (found != null) return found; } }
        return null;
    }
    private static String allText(Container parent)
    {
        StringBuilder text = new StringBuilder();
        for (java.awt.Component child : parent.getComponents())
        { if (child instanceof GuideText) text.append(((GuideText) child).getText()); if (child instanceof javax.swing.JLabel) text.append(((javax.swing.JLabel) child).getText()); if (child instanceof Container) text.append(allText((Container) child)); }
        return text.toString();
    }
}
