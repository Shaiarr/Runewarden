/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class FarmRunsToolTest
{
    @Test public void pickerUsesStandaloneRunTypesAndNativeTimerGroupsOpenCorrespondingRuns() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                assertEquals(FarmPatchPlanner.runTypes().size(), f.picker().getItemCount());
                assertFalse(f.tool.openGroup("special"));assertFalse(f.tool.openGroup("grape"));assertFalse(f.tool.openGroup("contract"));
                assertEquals("herb",f.planner.currentRun().runId);
                f.tool.openGroup("fruit-tree"); f.tool.refresh(); assertEquals("fruit", f.planner.currentRun().runId);
                f.tool.openGroup("bird-house"); f.tool.refresh(); assertEquals("birdhouse", f.planner.currentRun().runId);
                f.tool.openGroup("flower"); f.tool.refresh(); assertEquals("allotment", f.planner.currentRun().runId);
            });
        }
        finally { f.close(); }
    }
    @Test public void refreshKeepsPickerAndStopControlsWhileRunActionsStayExplicit() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                JComboBox<?> crops = named(f.panel, "runewarden.farm-runs.crop", JComboBox.class);
                JCheckBox include = controls(f.panel, JCheckBox.class).stream().filter(box -> !"Done".equals(box.getText())).findFirst().get();
                f.tool.refresh();
                assertSame(crops, named(f.panel, "runewarden.farm-runs.crop", JComboBox.class));
                assertSame(include, controls(f.panel, JCheckBox.class).stream().filter(box -> !"Done".equals(box.getText())).findFirst().get());
                assertFalse(f.planner.currentRun().active);
                button(f.panel, "Start run").doClick(); f.tool.refresh();
                assertTrue(f.planner.currentRun().active); assertFalse(crops.isEnabled()); assertFalse(f.picker().isEnabled());
                button(f.panel, "End run").doClick(); f.tool.refresh();
                assertFalse(f.planner.currentRun().active); assertTrue(crops.isEnabled());
            });
        }
        finally { f.close(); }
    }
    @Test public void oldLocationCheckboxCannotEditNextAccountsPlan() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                JCheckBox old = controls(f.panel, JCheckBox.class).stream().filter(box -> !"Done".equals(box.getText())).findFirst().get();
                f.profile.set("account-b"); for (int i = 0; i < 6; i++) f.tool.onGameTick();
                FarmPatchPlanner.RunReading before = f.planner.currentRun();
                long selected = before.stops.stream().filter(stop -> stop.selected).count();
                old.doClick();
                assertEquals(selected, f.planner.currentRun().stops.stream().filter(stop -> stop.selected).count());
                assertEquals("account-b", f.planner.currentRun().profile);
            });
        }
        finally { f.close(); }
    }
    @Test public void preparationLabelsMatchAllFourFocusedRunsAndBirdhousesHideCompost() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                for (String[] run : new String[][] {{"herb", "Seed", "Seeds per patch"},
                    {"bird-house", "Birdhouse type", "Birdhouses per location"},
                    {"tree", "Sapling", "Saplings per patch"}, {"fruit-tree", "Sapling", "Saplings per patch"}})
                {
                    f.tool.openGroup(run[0]); f.tool.refresh();
                    assertEquals(run[1], named(f.panel, "runewarden.farm-runs.crop-label", GuideText.class).getText());
                    assertEquals(run[2], named(f.panel, "runewarden.farm-runs.quantity-label", GuideText.class).getText());
                    assertEquals("herb".equals(run[0]), named(f.panel, "runewarden.farm-runs.quantity", javax.swing.JSpinner.class).isVisible());
                    assertEquals(!"bird-house".equals(run[0]), named(f.panel, "runewarden.farm-runs.compost", JComboBox.class).getParent().isVisible());
                    assertTrue(named(f.panel, "runewarden.farm-runs.preparation", JPanel.class).isVisible());
                    assertEquals("PREPARE", named(f.panel, "runewarden.farm-runs.phase", GuideText.class).getText());
                }
            });
        }
        finally { f.close(); }
    }
    @Test public void currentLocationAdvancesAndCompletionReturnsToPreparationWithoutRebuildingControls() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                JPanel preparation = named(f.panel, "runewarden.farm-runs.preparation", JPanel.class);
                JPanel current = named(f.panel, "runewarden.farm-runs.current-stop", JPanel.class);
                JPanel locations = named(f.panel, "runewarden.farm-runs.locations", JPanel.class);
                JProgressBar progress = named(f.panel, "runewarden.farm-runs.progress", JProgressBar.class);
                JCheckBox first = controls(f.panel, JCheckBox.class).get(0);
                button(f.panel, "Start run").doClick(); f.tool.refresh();
                assertFalse(preparation.isVisible()); assertTrue(current.isVisible()); assertFalse(locations.isVisible());
                assertEquals("ON THE RUN", named(f.panel, "runewarden.farm-runs.phase", GuideText.class).getText());
                assertEquals(f.planner.currentRun().nextStopLabel, named(f.panel, "runewarden.farm-runs.current-title", GuideText.class).getText());
                named(f.panel, "runewarden.farm-runs.show-locations", JButton.class).doClick();
                assertTrue(locations.isVisible());
                String firstId = f.planner.currentRun().nextStopId;
                named(f.panel, "runewarden.farm-runs.current-done", JButton.class).doClick(); f.tool.refresh();
                assertEquals(1, progress.getValue()); assertNotEquals(firstId, f.planner.currentRun().nextStopId);
                assertSame(first, controls(f.panel, JCheckBox.class).get(0));
                assertTrue("Expanded location view stays open while progressing", locations.isVisible());
                int count = f.planner.currentRun().totalStops;
                for (int remaining = count; remaining > 0 && f.planner.currentRun().active; remaining--)
                { named(f.panel, "runewarden.farm-runs.current-skip", JButton.class).doClick(); f.tool.refresh(); }
                assertFalse(f.planner.currentRun().active);
                assertEquals(count, progress.getValue()); assertFalse(current.isVisible()); assertTrue(preparation.isVisible());
                assertSame(progress, named(f.panel, "runewarden.farm-runs.progress", JProgressBar.class));
                assertSame(first, controls(f.panel, JCheckBox.class).get(0));
            });
        }
        finally { f.close(); }
    }
    @Test public void narrowPreparationAndActiveViewsRenderWithNativeRuneLiteTheme() throws Exception
    {
        AtomicReference<javax.swing.LookAndFeel> previous = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
        {
            previous.set(javax.swing.UIManager.getLookAndFeel());
            assertTrue(net.runelite.client.ui.laf.RuneLiteLAF.setup());
        });
        Fixture f = new Fixture();
        try
        {
            f.start();
            SwingUtilities.invokeAndWait(() ->
            {
                try
                {
                    render(f.panel, "preparation");
                    button(f.panel, "Start run").doClick(); f.tool.refresh();
                    assertTrue(f.planner.currentRun().active); render(f.panel, "active");
                }
                catch (java.io.IOException error) { throw new AssertionError(error); }
            });
        }
        finally
        {
            f.close();
            SwingUtilities.invokeAndWait(() ->
            {
                try { javax.swing.UIManager.setLookAndFeel(previous.get()); }
                catch (Exception error) { throw new AssertionError(error); }
            });
        }
    }
    private static void render(JPanel panel, String state) throws java.io.IOException
    {
        JPanel holder = new JPanel(new java.awt.BorderLayout()); holder.setBackground(SidebarUi.BACKGROUND);
        holder.setBorder(javax.swing.BorderFactory.createEmptyBorder(8, 8, 8, 8)); holder.add(panel);
        holder.setSize(242, 2000);
        for (int pass = 0; pass < 8; pass++) layout(holder);
        holder.setSize(242, holder.getPreferredSize().height);
        for (int pass = 0; pass < 8; pass++) layout(holder);
        assertTrue(panel.getWidth() >= 220);
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(242, holder.getHeight() + 42, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setColor(SidebarUi.BACKGROUND); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(SidebarUi.GOLD); graphics.setFont(GuideText.FONT.deriveFont(11f));
            graphics.drawString("MOCK CLIENT · " + state.toUpperCase(java.util.Locale.ROOT), 8, 16);
            graphics.setFont(GuideText.FONT.deriveFont(10f)); graphics.drawString("Actual controls · not a gameplay capture", 8, 31);
            graphics.translate(0, 42); holder.printAll(graphics);
        }
        finally { graphics.dispose(); }
        java.io.File output = new java.io.File("build/previews/harvest-runs-" + state + ".png");
        assertTrue(output.getParentFile().isDirectory() || output.getParentFile().mkdirs());
        assertTrue(javax.imageio.ImageIO.write(image, "png", output));
    }
    private static void layout(Container root)
    {
        root.doLayout();
        for (Component child : root.getComponents()) if (child instanceof Container) layout((Container) child);
    }
    @Test public void overviewQuickPreparationCannotReplaceAnActiveRun() throws Exception
    {
        Fixture f = new Fixture(); f.start(); AtomicInteger opened = new AtomicInteger();
        try
        {
            AtomicReference<JPanel> summary = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() ->
            {
                f.tool.setOpenAction(opened::incrementAndGet); summary.set(f.tool.createSummaryPanel());
                named(summary.get(), "runewarden.farm-summary.fruit", JButton.class).doClick();
                assertEquals("fruit", f.planner.currentRun().runId); assertFalse(f.planner.currentRun().active);
            });
            SwingUtilities.invokeAndWait(() -> assertEquals(1, opened.get()));
            SwingUtilities.invokeAndWait(() ->
            {
                f.tool.openGroup("herb"); f.tool.refresh(); button(f.panel, "Start run").doClick(); f.tool.refresh();
                long started = f.planner.currentRun().startedAt;
                JButton herb = named(summary.get(), "runewarden.farm-summary.herb", JButton.class);
                assertTrue(herb.isEnabled());
                for (String id : new String[] {"birdhouse", "tree", "fruit"})
                {
                    JButton other = named(summary.get(), "runewarden.farm-summary." + id, JButton.class);
                    assertFalse(other.isEnabled()); other.doClick();
                }
                herb.doClick();
                assertTrue(f.planner.currentRun().active); assertEquals("herb", f.planner.currentRun().runId);
                assertEquals(started, f.planner.currentRun().startedAt);
                assertTrue(named(summary.get(), "runewarden.farm-summary.status", GuideText.class).getText().contains(f.planner.currentRun().nextStopLabel));
            });
            SwingUtilities.invokeAndWait(() -> assertEquals(2, opened.get()));
        }
        finally { f.close(); }
    }
    @Test public void summaryAndDetailedProgressRemainDistinctAndTrackTheSameRun() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                JPanel summary = f.tool.createSummaryPanel();
                JProgressBar overview = controls(summary, JProgressBar.class).get(0);
                JProgressBar detailed = named(f.panel, "runewarden.farm-runs.progress", JProgressBar.class);
                assertNotSame(overview, detailed);
                assertEquals(detailed.getForeground(), overview.getForeground());
                assertEquals(detailed.getBackground(), overview.getBackground());
                assertEquals(detailed.getMaximumSize(), overview.getMaximumSize());
                assertTrue(overview.isStringPainted()); assertTrue(detailed.isStringPainted());
                button(f.panel, "Start run").doClick(); f.tool.refresh();
                assertTrue(overview.isVisible());
                named(f.panel, "runewarden.farm-runs.current-done", JButton.class).doClick(); f.tool.refresh();
                assertEquals(1, detailed.getValue()); assertEquals(detailed.getValue(), overview.getValue());
                assertEquals(detailed.getMaximum(), overview.getMaximum());
                assertEquals(detailed.getString(), overview.getString());
                button(f.panel, "End run").doClick(); f.tool.refresh();
                assertFalse(overview.isVisible()); assertTrue(detailed.isVisible());
            });
        }
        finally { f.close(); }
    }
    @Test public void retainedLocationControlsUseTheNewEpochAfterEndingARun() throws Exception
    {
        Fixture f = new Fixture(); f.start();
        try
        {
            SwingUtilities.invokeAndWait(() ->
            {
                FarmPatchPlanner.StopReading first = f.planner.currentRun().stops.stream().filter(stop -> stop.selected).findFirst().get();
                JCheckBox include = controls(f.panel, JCheckBox.class).stream().filter(box -> first.label.equals(box.getText())).findFirst().get();
                JButton down = button(include.getParent(), "↓");
                button(f.panel, "Start run").doClick(); f.tool.refresh();
                button(f.panel, "End run").doClick(); f.tool.refresh();
                assertSame(include, controls(f.panel, JCheckBox.class).stream().filter(box -> first.label.equals(box.getText())).findFirst().get());
                assertTrue(down.isEnabled()); down.doClick(); f.tool.refresh();
                List<FarmPatchPlanner.StopReading> selected = new ArrayList<>();
                for (FarmPatchPlanner.StopReading stop : f.planner.currentRun().stops) if (stop.selected) selected.add(stop);
                assertEquals("Reorder callback must use the post-run epoch", first.id, selected.get(1).id);
                JCheckBox current = controls(f.panel, JCheckBox.class).stream().filter(box -> first.label.equals(box.getText())).findFirst().get();
                current.doClick(); f.tool.refresh();
                assertFalse(f.planner.currentRun().stops.stream().filter(stop -> first.id.equals(stop.id)).findFirst().get().selected);
            });
        }
        finally { f.close(); }
    }
    private static JButton button(Container root, String text)
    { return controls(root, JButton.class).stream().filter(button -> text.equals(button.getText())).findFirst().get(); }
    private static <T extends Component> T named(Container root, String name, Class<T> type)
    { return controls(root, type).stream().filter(component -> name.equals(component.getName())).findFirst().get(); }
    private static <T> List<T> controls(Container root, Class<T> type)
    {
        List<T> found = new ArrayList<>();
        for (Component component : root.getComponents())
        {
            if (type.isInstance(component)) found.add(type.cast(component));
            if (component instanceof Container) found.addAll(controls((Container) component, type));
        }
        return found;
    }
    private static final class Fixture
    {
        final AtomicReference<String> profile = new AtomicReference<>("account-a");
        final Client client = mock(Client.class);
        final ClientThread thread = mock(ClientThread.class);
        final ConfigManager config = mock(ConfigManager.class);
        final Map<String, String> values = new HashMap<>();
        final FarmPatchPlanner planner;
        final FarmRunsTool tool;
        JPanel panel;
        Fixture()
        {
            Player player = mock(Player.class);
            when(client.getGameState()).thenReturn(GameState.LOGGED_IN); when(client.getLocalPlayer()).thenReturn(player);
            when(player.getWorldLocation()).thenReturn(new WorldPoint(2809, 3463, 0));
            when(client.getRealSkillLevel(Skill.FARMING)).thenReturn(99);
            when(client.getBoostedSkillLevel(Skill.FARMING)).thenReturn(99);
            when(client.getBoostedSkillLevel(Skill.HUNTER)).thenReturn(99);
            when(config.getRSProfileKey()).thenAnswer(call -> profile.get());
            when(config.getConfiguration(anyString(), anyString(), anyString()))
                .thenAnswer(call -> values.get(call.getArgument(1) + ":" + call.getArgument(2)));
            doAnswer(call -> { values.put(call.getArgument(1) + ":" + call.getArgument(2), call.getArgument(3).toString()); return null; })
                .when(config).setConfiguration(anyString(), anyString(), anyString(), anyString());
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
            planner = new FarmPatchPlanner(client, thread, config, new Gson(), new FarmPatchCatalog(new Gson()));
            tool = new FarmRunsTool(planner, null);
        }
        void start() throws Exception
        {
            tool.start(); for (int i = 0; i < 6; i++) tool.onGameTick();
            SwingUtilities.invokeAndWait(() -> panel = tool.createPanel());
        }
        JComboBox<?> picker() { return named(panel, "runewarden.farm-runs.selector", JComboBox.class); }
        void close() throws Exception { tool.stop(); SwingUtilities.invokeAndWait(() -> {}); }
    }
}
