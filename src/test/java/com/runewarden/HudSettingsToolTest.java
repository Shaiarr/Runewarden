/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class HudSettingsToolTest
{
    @Test public void openingAndExternalPreferenceRefreshNeverWriteConfiguration() throws Exception
    {
        Fixture fixture = new Fixture();
        SwingUtilities.invokeAndWait(() ->
        {
            fixture.tool.start();
            JPanel panel = fixture.tool.createPanel();
            assertTrue(find(panel, "hudCompact", JCheckBox.class).isSelected());
            assertEquals(85, find(panel, "hudOpacity", JSlider.class).getValue());
            fixture.config.compact = false;
            fixture.config.opacity = 63;
            fixture.config.locked = true;
            fixture.tool.onConfigChanged();
            assertFalse(find(panel, "hudCompact", JCheckBox.class).isSelected());
            assertEquals(63, find(panel, "hudOpacity", JSlider.class).getValue());
            for (Overlay overlay : fixture.owned) assertFalse(overlay.isMovable());
            fixture.config.locked = false;
            fixture.tool.onConfigChanged();
            for (Overlay overlay : fixture.owned) assertTrue(overlay.isMovable());
            fixture.tool.stop();
        });
        verifyNoInteractions(fixture.manager);
    }

    @Test public void opacityIsSavedOnceWhenDraggingEndsAndCheckboxUsesTheSharedPreference() throws Exception
    {
        Fixture fixture = new Fixture();
        SwingUtilities.invokeAndWait(() ->
        {
            fixture.tool.start();
            JPanel panel = fixture.tool.createPanel();
            JSlider opacity = find(panel, "hudOpacity", JSlider.class);
            opacity.setValueIsAdjusting(true);
            opacity.setValue(51);
            opacity.setValue(72);
            verifyNoInteractions(fixture.manager);
            opacity.setValueIsAdjusting(false);
            verify(fixture.manager).setConfiguration(RunewardenConfig.GROUP, "hudOpacity", 72);
            find(panel, "hudCompact", JCheckBox.class).doClick();
            verify(fixture.manager).setConfiguration(RunewardenConfig.GROUP, "hudCompact", false);
            fixture.tool.stop();
        });
        verifyNoMoreInteractions(fixture.manager);
    }

    @Test public void resetAffectsExactlyTheSixOwnedHudOverlaysAndPreservesMovementLock() throws Exception
    {
        Fixture fixture = new Fixture();
        fixture.config.locked = true;
        SwingUtilities.invokeAndWait(() ->
        {
            fixture.tool.start();
            JPanel panel = fixture.tool.createPanel();
            find(panel, "resetHudPositions", JButton.class).doClick();
            for (Overlay overlay : fixture.owned)
            {
                verify(fixture.overlays).resetOverlay(overlay);
                assertFalse(overlay.isMovable());
            }
            fixture.tool.stop();
        });
        verifyNoMoreInteractions(fixture.overlays);
        verifyNoInteractions(fixture.manager);
    }

    @Test public void fightCavesEntranceIsConfigurableWithoutOtherArenaAutoControls() throws Exception
    {
        Fixture fixture = new Fixture();
        SwingUtilities.invokeAndWait(() -> {
            fixture.tool.start(); JPanel panel = fixture.tool.createPanel();
            assertNotNull(find(panel, "waveEntryCoach", JCheckBox.class));
            for (String key : new String[]{"infernoOpeningOnEntry", "colosseumOpeningOnEntry"})
                assertNull(find(panel, key, JCheckBox.class));
            javax.swing.JComboBox<?> widget = find(panel, "hudWidget", javax.swing.JComboBox.class);
            java.util.List<Object> widgets = new java.util.ArrayList<>();
            for (int i = 0; i < widget.getItemCount(); i++) widgets.add(widget.getItemAt(i));
            assertTrue(widgets.contains("entrance"));
            fixture.tool.stop();
        });
    }

    @Test public void resetRestoresDraggedSizesIncludingSceneCardsWithoutDiscardingColoursOrFonts() throws Exception
    {
        Fixture fixture=new Fixture();
        java.util.Map<String,HudPreferences.Style> styles=new java.util.LinkedHashMap<>();
        HudPreferences.Style custom=new HudPreferences.Style(160,14,70,java.awt.Color.RED,java.awt.Color.WHITE,java.awt.Color.BLACK,
            HudPreferences.Visibility.ALWAYS,HudPreferences.Frame.SQUARE,HudPreferences.Values.CURRENT);
        styles.put("guide",custom);styles.put("farm-patches",custom);styles.put("node-timers",custom);styles.put("reward-popup",custom);
        fixture.config.layout=HudPreferences.encode(styles);
        SwingUtilities.invokeAndWait(()->
        {
            fixture.tool.start();JPanel panel=fixture.tool.createPanel();
            find(panel,"resetHudPositions",JButton.class).doClick();fixture.tool.stop();
        });
        org.mockito.ArgumentCaptor<String> captured=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(fixture.manager).setConfiguration(org.mockito.ArgumentMatchers.eq(RunewardenConfig.GROUP),org.mockito.ArgumentMatchers.eq("hudWidgetSettings"),captured.capture());
        java.util.Map<String,HudPreferences.Style> reset=HudPreferences.decode(captured.getValue());
        for(String widget:styles.keySet())
        {assertEquals(100,reset.get(widget).scale);assertEquals(14,reset.get(widget).font);assertEquals(java.awt.Color.RED,reset.get(widget).accent);}
    }

    @Test public void stoppedControlsCannotWriteOrResetEvenWhenAnOldActionIsDelivered() throws Exception
    {
        Fixture fixture = new Fixture();
        SwingUtilities.invokeAndWait(() ->
        {
            fixture.tool.start();
            JPanel panel = fixture.tool.createPanel();
            fixture.tool.stop();
            JCheckBox checkbox = find(panel, "hudCompact", JCheckBox.class);
            JButton reset = find(panel, "resetHudPositions", JButton.class);
            assertFalse(checkbox.isEnabled());
            assertFalse(reset.isEnabled());
            checkbox.setEnabled(true);
            reset.setEnabled(true);
            checkbox.doClick();
            reset.doClick();
            find(panel, "hudOpacity", JSlider.class).setValue(40);
        });
        verifyNoInteractions(fixture.manager, fixture.overlays);
    }

    @Test public void clickTrainingCueDefaultsOnAndCanBeDisabledWithoutChangingTickAppearance() throws Exception
    {
        Fixture fixture = new Fixture();
        SwingUtilities.invokeAndWait(() ->
        {
            fixture.tool.start(); JPanel panel = fixture.tool.createPanel();
            JCheckBox cue = find(panel, "clickTrainingCue", JCheckBox.class);
            assertTrue(cue.isSelected()); cue.doClick();
            verify(fixture.manager).setConfiguration(RunewardenConfig.GROUP, "clickTrainingCue", false);
            fixture.tool.stop();
        });
        verifyNoMoreInteractions(fixture.manager);
    }

    @Test public void actual206PixelSidebarKeepsEveryHudControlInOneLeftAlignedColumn() throws Exception
    {
        Fixture fixture = new Fixture();
        SwingUtilities.invokeAndWait(() ->
        {
            fixture.tool.start(); JPanel panel = fixture.tool.createPanel();
            find(panel, "saveHudPreset", JButton.class).doClick();
            for (int pass = 0; pass < 3; pass++)
            {
                panel.setSize(206, panel.getPreferredSize().height);
                layout(panel);
            }
            for (Component component : panel.getComponents())
            {
                assertEquals(component.getClass().getName(), 0, component.getX());
                assertTrue(component.getClass().getName() + " exceeds 206px", component.getWidth() <= 206);
                assertTrue(component.getClass().getName() + " has no width", component.getWidth() >= 0);
            }
            assertEquals(206, find(panel, "hudWidget", javax.swing.JComboBox.class).getWidth());
            assertEquals(206, find(panel, "applyHudWidget", JButton.class).getWidth());
            GuideText status = find(panel, "hudLayoutStatus", GuideText.class);
            assertEquals(206, status.getWidth());
            assertTrue(status.getPreferredSize().height <= status.getHeight());
            fixture.tool.stop();
        });
    }

    private static void layout(Container container)
    {
        container.doLayout();
        for (Component component : container.getComponents()) if (component instanceof Container) layout((Container) component);
    }

    private static <T extends Component> T find(Container container, String name, Class<T> type)
    {
        for (Component component : container.getComponents())
        {
            if (name.equals(component.getName()) && type.isInstance(component)) return type.cast(component);
            if (component instanceof Container)
            {
                T nested = find((Container) component, name, type);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static final class Preferences implements RunewardenConfig
    {
        boolean compact = true;
        boolean locked;
        int opacity = 85;
        String layout = "";
        @Override public boolean hudCompact() { return compact; }
        @Override public boolean hudLocked() { return locked; }
        @Override public int hudOpacity() { return opacity; }
        @Override public String hudWidgetSettings() { return layout; }
    }

    private static final class Fixture
    {
        final ConfigManager manager = mock(ConfigManager.class);
        final OverlayManager overlays = mock(OverlayManager.class);
        final Preferences config = new Preferences();
        final Client client = mock(Client.class);
        final ItemManager items = mock(ItemManager.class);
        final RunewardenResourceOverlay resources = new RunewardenResourceOverlay(client, config, mock(SpriteManager.class));
        final GuideCoachOverlay guide = new GuideCoachOverlay(client, config);
        final SkillingCoachOverlay skilling = new SkillingCoachOverlay(client, config,
            new SkillingCoach(client, items, manager, config), items);
        final CommandDashboardTool dashboard = new CommandDashboardTool(client, items, overlays, config);
        final HarvestClockTool harvest = new HarvestClockTool(client, manager, mock(PluginManager.class), overlays, config);
        final WaveEntryOverlay entry = new WaveEntryOverlay(client, new WaveEntryCoach(client,
            new GuideCatalog(new com.google.gson.Gson()), new GuideMarkerOverlay(client), config,
            Runnable::run, System::nanoTime), config);
        final Overlay[] owned = {resources, guide, skilling, dashboard.overlay(), harvest.overlay(), entry};
        final HudSettingsTool tool = new HudSettingsTool(manager, config, overlays, resources, guide, skilling, dashboard, harvest, entry);
    }
}
