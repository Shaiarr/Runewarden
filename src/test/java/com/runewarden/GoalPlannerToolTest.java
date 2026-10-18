/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GoalPlannerToolTest
{
    @Test public void nativeWidthShowsFullWidthQueueAndCompactEditorWithOptionalFieldsCollapsed() throws Exception
    {
        Harness h = new Harness(); h.tool.start(); h.tick();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                assertTrue(net.runelite.client.ui.laf.RuneLiteLAF.setup());
                JPanel panel = h.tool.createPanel(); narrowLayout(panel);
                assertTrue(visible(panel, button(panel, "Add goal")));
                assertEquals(0, components(panel, JTextField.class).stream().filter(field -> visible(panel, field)).count());
                assertFalse(visible(panel, button(panel, "‹")));
                assertTrue(button(panel, "Add goal").getWidth() >= 196);
                savePreview(panel, "goals-empty");
                button(panel, "Add goal").doClick(); narrowLayout(panel);
                JComboBox<?> skills = components(panel, JComboBox.class).stream()
                    .filter(box -> box.getItemCount() > 0 && box.getItemAt(0) instanceof Skill).findFirst().get();
                assertTrue(skills.getWidth() >= 184);
                assertTrue(SwingUtilities.convertPoint(skills, 0, 0, panel).x <= 8);
                assertEquals(1, components(panel, JTextField.class).stream().filter(field -> visible(panel, field)).count());
                assertTrue("Essential editor must fit the sidebar", panel.getPreferredSize().height < 530);
                savePreview(panel, "goals-editor");
                button(panel, "Options ▸").doClick(); narrowLayout(panel);
                assertEquals(5, components(panel, JTextField.class).stream().filter(field -> visible(panel, field)).count());
                button(panel, "Back").doClick(); narrowLayout(panel);
                assertTrue(visible(panel, button(panel, "Add goal")));
            });
        }
        finally { h.tool.stop(); drain(); }
    }

    private static boolean visible(Container root, Component child)
    { for (Component current = child; current != null && current != root; current = current.getParent()) if (!current.isVisible()) return false; return true; }
    private static void narrowLayout(JPanel panel)
    {
        panel.setSize(206, 3000); layout(panel); panel.setSize(206, panel.getPreferredSize().height); layout(panel);
    }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static void savePreview(JPanel panel, String name)
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(206, Math.max(1, panel.getHeight()), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
        java.io.File file = new java.io.File("build/reports/" + name + "-206px.png"); file.getParentFile().mkdirs();
        try { javax.imageio.ImageIO.write(image, "png", file); } catch (java.io.IOException error) { throw new AssertionError(error); }
    }

    @Test public void observedXpAndQuestCompletionAutomaticallyMarkOnlyReachedGoals() throws Exception
    {
        Harness h = new Harness();
        GoalPlan mining = GoalPlan.skill(Skill.MINING, Experience.getXpForLevel(80), h.xp.get());
        GoalPlan quest = GoalPlan.quest("quest:Cook's Assistant"); h.save(mining, quest);
        h.tool.start(); h.tick();
        assertTrue(h.saved().stream().allMatch(goal -> goal.achievedAt == 0));
        h.xp.set(Experience.getXpForLevel(80)); h.questState.set(2); h.tick();
        assertTrue(h.saved().stream().allMatch(goal -> goal.achievedAt > 0));
        long first = h.saved().get(0).achievedAt; h.tick();
        assertEquals(first, h.saved().get(0).achievedAt);
        h.tool.stop(); drain();
    }

    @Test public void addEditAndRemoveControlsPersistCompleteGoalAndCustomMethodSettings() throws Exception
    {
        Harness h = new Harness(); h.tool.start(); h.tick();
        AtomicReference<JPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            panel.set(h.tool.createPanel());
            button(panel.get(), "Add goal").doClick();
            JComboBox<?> skills = components(panel.get(), JComboBox.class).stream()
                .filter(box -> box.getItemCount() > 0 && box.getItemAt(0) instanceof Skill).findFirst().get();
            skills.setSelectedItem(Skill.MINING);
            List<JTextField> fields = components(panel.get(), JTextField.class);
            fields.get(0).setText("80"); fields.get(1).setText("50000"); fields.get(2).setText("1000000");
            fields.get(3).setText("4"); fields.get(4).setText("2");
            button(panel.get(), "Save goal").doClick();
        });
        drain();
        assertEquals(1, h.saved().size());
        GoalPlan saved = h.saved().get(0);
        assertEquals(Skill.MINING, saved.skill()); assertEquals(Experience.getXpForLevel(80), saved.targetXp);
        assertEquals(50_000, saved.customXpPerHour); assertEquals(1_000_000, saved.gpBudget);
        assertEquals(4.0, saved.weeklyHours, 0.001); assertEquals(2, saved.milestoneInterval);
        SwingUtilities.invokeAndWait(() ->
        {
            button(panel.get(), "Edit").doClick();
            components(panel.get(), JTextField.class).get(0).setText("2000000");
            button(panel.get(), "Save changes").doClick();
        });
        drain(); assertEquals(saved.id, h.saved().get(0).id); assertEquals(2_000_000, h.saved().get(0).targetXp);
        SwingUtilities.invokeAndWait(() -> button(panel.get(), "Remove").doClick());
        drain(); assertTrue(h.saved().isEmpty()); h.tool.stop(); drain();
    }

    @Test public void staleVisibleRemoveButtonCannotWriteAfterProfileChange() throws Exception
    {
        Harness h = new Harness(); GoalPlan goal = GoalPlan.skill(Skill.MINING, 13_034_431, h.xp.get()); h.save(goal);
        h.tool.start(); h.tick(); AtomicReference<JPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panel.set(h.tool.createPanel()));
        JButton old = button(panel.get(), "Remove"); h.profile.set("b");
        SwingUtilities.invokeAndWait(old::doClick); drain();
        assertEquals(1, h.saved().size()); assertNull(h.values.get("b"));
        h.tick(); drain();
        assertFalse(components(panel.get(), JButton.class).stream().anyMatch(button -> "Remove".equals(button.getText())));
        h.tool.stop(); drain();
    }

    @Test public void skillOnlyGoalDoesNotPollEveryQuestAndLockedMethodOpensCorrectGuide() throws Exception
    {
        Harness h = new Harness();
        GoalPlan goal = GoalPlan.skill(Skill.MINING, 13_034_431, h.xp.get()); h.save(goal);
        h.tool.start(); h.tick();
        verify(h.client, never()).runScript(anyInt(), any());
        h.tool.stop(); drain();
        GoalPlan combat = GoalPlan.skill(Skill.ATTACK, 13_034_431, 0); combat.methodId = "training:ammonite-crabs"; combat.intensity = "AFK";
        h.save(combat); h.tool.start(); h.tick();
        AtomicReference<String> opened = new AtomicReference<>(); h.tool.setOpenGuideAction(opened::set);
        AtomicReference<JPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { panel.set(h.tool.createPanel()); button(panel.get(), "Open guide").doClick(); });
        assertEquals("training:ammonite-crabs", opened.get());
        assertTrue(components(panel.get(), JButton.class).stream().anyMatch(button -> button.getText().startsWith("Queue ")));
        h.tool.stop(); drain();
    }

    @Test public void exposedGoalTargetsAreDetachedAndExcludeArchivedOrFinishedGoals() throws Exception {
        Harness h = new Harness(); GoalPlan item = GoalPlan.progress("item", "4151", 1, "Whip", List.of(4151));
        GoalPlan collection = GoalPlan.progress("collection", "11286", 1, "Visage", List.of());
        GoalPlan archived = GoalPlan.progress("set", "test", 1, "Set", List.of(26382)); archived.archived = true;
        GoalPlan done = GoalPlan.progress("item", "995", 1, "Coins", List.of(995)); done.achievedAt = 1;
        h.save(item, collection, archived, done); h.tool.start(); h.tick();
        assertEquals(java.util.Set.of(4151,11286), h.tool.goalItemTargets());
        List<GoalPlan> exposed = h.tool.goalPlans(); exposed.get(0).itemIds.clear(); assertTrue(h.tool.goalItemTargets().contains(4151));
        try { exposed.clear(); fail(); } catch (UnsupportedOperationException expected) { }
        h.profile.set("b"); assertTrue(h.tool.goalPlans().isEmpty()); assertNull(h.tool.facts()); h.tool.stop(); drain();
    }
    @Test public void completionMilestonesEmitOnceForNewProgressAndDoNotReplayLoginHistory() throws Exception {
        Harness h = new Harness(); GoalPlan goal = GoalPlan.skill(Skill.MINING,Experience.getXpForLevel(80),h.xp.get());h.save(goal);
        List<GoalPlan> completed = new ArrayList<>(); h.tool.setCompletionAction(completed::add); h.tool.start(); h.tick(); assertTrue(completed.isEmpty());
        h.xp.set(Experience.getXpForLevel(80)); h.tick(); assertEquals(1,completed.size()); h.tick(); assertEquals(1,completed.size());
        h.tool.stop();h.tool.start();h.tick();assertEquals(1,completed.size());h.tool.stop();drain();
    }
    @Test public void typedSkillHandoffCreatesThenFocusesExactGoalAndReusesOnlyExactActiveTarget() throws Exception {
        Harness h=new Harness();h.tool.start();h.tick();List<Runnable> queued=new ArrayList<>();
        doAnswer(call->{queued.add(call.getArgument(0));return null;}).when(h.thread).invokeLater(any(Runnable.class));
        int target=Experience.getXpForLevel(90);h.tool.openSkillTarget(Skill.MINING,target);assertTrue(h.saved().isEmpty());
        h.xp.set(Experience.getXpForLevel(80));queued.remove(0).run();drain();assertEquals(1,h.saved().size());assertEquals(h.xp.get(),h.saved().get(0).startXp);assertEquals(target,h.saved().get(0).targetXp);
        String id=h.saved().get(0).id;h.tool.openSkillTarget(Skill.MINING,target);queued.remove(0).run();drain();assertEquals(1,h.saved().size());assertEquals(id,h.saved().get(0).id);
        h.tool.openSkillTarget(Skill.MINING,Experience.getXpForLevel(99));queued.remove(0).run();drain();assertEquals(2,h.saved().size());h.tool.stop();drain();
    }
    @Test public void typedSkillHandoffRejectsInvalidTargetsAndQueuedOldCharacterWork() throws Exception {
        Harness h=new Harness();h.tool.start();h.tick();List<Runnable> queued=new ArrayList<>();doAnswer(call->{queued.add(call.getArgument(0));return null;}).when(h.thread).invokeLater(any(Runnable.class));
        h.tool.openSkillTarget(Skill.MINING,0);h.tool.openSkillTarget(Skill.MINING,200000001);h.tool.openSkillTarget(Skill.MINING,Long.MAX_VALUE);h.tool.openSkillTarget(Skill.OVERALL,1000);assertTrue(queued.isEmpty());
        h.tool.openSkillTarget(Skill.MINING,Experience.getXpForLevel(90));h.profile.set("b");queued.remove(0).run();drain();assertTrue(h.saved().isEmpty());assertNull(h.values.get("b"));h.tool.stop();drain();
    }
    private static void drain() throws Exception { SwingUtilities.invokeAndWait(() -> {}); }
    private static JButton button(Container panel, String text)
    { return components(panel, JButton.class).stream().filter(button -> text.equals(button.getText())).findFirst().get(); }
    private static <T> List<T> components(Container parent, Class<T> type)
    {
        List<T> result = new ArrayList<>();
        for (Component component : parent.getComponents())
        { if (type.isInstance(component)) result.add(type.cast(component)); if (component instanceof Container) result.addAll(components((Container) component, type)); }
        return result;
    }

    private static final class Harness
    {
        final Gson gson = new Gson(); final Client client = mock(Client.class);
        final ConfigManager config = mock(ConfigManager.class); final ClientThread thread = mock(ClientThread.class);
        final AtomicReference<String> profile = new AtomicReference<>("a");
        final AtomicInteger xp = new AtomicInteger(Experience.getXpForLevel(79)), questState = new AtomicInteger(1);
        final Map<String, String> values = new HashMap<>(); final GoalPlannerTool tool;
        Harness()
        {
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
            Player player = mock(Player.class); when(player.getCombatLevel()).thenReturn(126); when(client.getLocalPlayer()).thenReturn(player);
            when(client.getSkillExperience(any(Skill.class))).thenAnswer(call -> call.getArgument(0) == Skill.MINING ? xp.get() : 0);
            when(client.getRealSkillLevel(any(Skill.class))).thenAnswer(call -> call.getArgument(0) == Skill.MINING ? Experience.getLevelForXp(xp.get()) : 1);
            when(client.getIntStack()).thenAnswer(call -> new int[]{questState.get()});
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq(GoalPlan.KEY)))
                .thenAnswer(call -> values.get(call.getArgument(1)));
            doAnswer(call -> { values.put(call.getArgument(1), call.getArgument(3)); return null; })
                .when(config).setConfiguration(eq(RunewardenConfig.GROUP), anyString(), eq(GoalPlan.KEY), anyString());
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
            GuideCatalog guides = new GuideCatalog(gson);
            tool = new GoalPlannerTool(client, thread, config, gson, new GoalCatalog(guides, new ActivityXpCatalog(gson, guides)));
        }
        void save(GoalPlan... goals) { GoalPlan.Data data = new GoalPlan.Data(); data.goals = List.of(goals); values.put("a", gson.toJson(data)); }
        List<GoalPlan> saved() { return GoalPlan.decode(gson, values.get("a")); }
        void tick() throws Exception { for (int i = 0; i < 10; i++) tool.onGameTick(); drain(); }
    }
}
