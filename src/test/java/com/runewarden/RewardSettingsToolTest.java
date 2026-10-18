/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RewardSettingsToolTest
{
    @Test public void nativeWidthKeepsFiveTierSoundboardVisibleAndAdvancedFieldsCollapsed() throws Exception
    {
        RewardSettings settings = new RewardSettings(mock(ConfigManager.class), new Gson());
        java.util.concurrent.ScheduledExecutorService executor = mock(java.util.concurrent.ScheduledExecutorService.class);
        RewardSoundboard audio = new RewardSoundboard(executor, mock(net.runelite.client.audio.AudioPlayer.class));
        RewardSettingsTool tool = new RewardSettingsTool(settings, audio); AtomicReference<RewardNotification> preview = new AtomicReference<>(); tool.setDemoAction(preview::set);
        SwingUtilities.invokeAndWait(() ->
        {
            assertTrue(net.runelite.client.ui.laf.RuneLiteLAF.setup()); JPanel panel = tool.createPanel(); narrow(panel);
            assertEquals(0, components(panel, JTextField.class).stream().filter(field -> visible(panel, field)).count());
            for (RarityTier tier : RarityTier.values())
            { JButton button = button(panel, "▸ " + tier.name()); assertTrue(visible(panel, button)); assertTrue(button.getWidth() >= 180); }
            assertTrue(panel.getPreferredSize().height < 650); savePreview(panel, "rewards-settings");
            button(panel, "▸ BLUE").doClick(); narrow(panel);
            buttonVisible(panel, "Listen").doClick(); verify(executor).execute(any(Runnable.class));
            buttonVisible(panel, "Popup").doClick(); assertEquals(RarityTier.BLUE, preview.get().tier); assertEquals(RewardNotification.Kind.DEMO, preview.get().kind);
            JSlider volume = components(panel, JSlider.class).stream().filter(slider -> visible(panel, slider)).findFirst().get();
            volume.setValue(35); assertEquals(35, settings.snapshot().sound(RarityTier.BLUE).volume);
            JComboBox<?> preset = components(panel, JComboBox.class).stream().filter(box -> visible(panel, box)).findFirst().get();
            preset.setSelectedItem(RewardSoundboard.SoundPreset.QUIET_TICK); assertEquals("quiet-tick", settings.snapshot().sound(RarityTier.BLUE).preset);
            assertTrue(preset.getWidth() >= 180);
        });
    }

    @Test public void advancedMetricsHaveSeparateLabelsAndRejectInvalidOrdering() throws Exception
    {
        RewardSettings settings = new RewardSettings(mock(ConfigManager.class), new Gson());
        RewardSettingsTool tool = new RewardSettingsTool(settings, new RewardSoundboard(mock(java.util.concurrent.ScheduledExecutorService.class), mock(net.runelite.client.audio.AudioPlayer.class)));
        SwingUtilities.invokeAndWait(() ->
        {
            JPanel panel = tool.createPanel(); button(panel, "▸ Rarity thresholds").doClick(); narrow(panel);
            assertTrue(components(panel, GuideText.class).stream().anyMatch(text -> visible(panel, text) && "Ownership (%)".equals(text.getText())));
            assertTrue(components(panel, GuideText.class).stream().anyMatch(text -> visible(panel, text) && "Drop chance (1 in)".equals(text.getText())));
            List<JTextField> fields = new ArrayList<>(); for (JTextField field : components(panel, JTextField.class)) if (visible(panel, field)) fields.add(field);
            fields.get(0).setText("1"); fields.get(1).setText("10"); button(panel, "Apply thresholds").doClick();
            assertArrayEquals(new double[]{25, 10, 2, .25}, settings.snapshot().ownershipThresholds, 0);
        });
    }

    private static JButton button(Container panel, String text)
    { return components(panel, JButton.class).stream().filter(button -> text.equals(button.getText())).findFirst().get(); }
    private static JButton buttonVisible(Container panel, String text)
    { return components(panel, JButton.class).stream().filter(button -> text.equals(button.getText()) && visible(panel, button)).findFirst().get(); }
    private static <T> List<T> components(Container root, Class<T> type)
    {
        List<T> result = new ArrayList<>();
        for (Component child : root.getComponents()) { if (type.isInstance(child)) result.add(type.cast(child)); if (child instanceof Container) result.addAll(components((Container) child, type)); }
        return result;
    }
    private static boolean visible(Container root, Component child)
    { for (Component current = child; current != null && current != root; current = current.getParent()) if (!current.isVisible()) return false; return true; }
    private static void narrow(JPanel panel)
    { panel.setSize(206, 3000); layout(panel); panel.setSize(206, panel.getPreferredSize().height); layout(panel); }
    private static void layout(Container parent)
    { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container) layout((Container) child); }
    private static void savePreview(JPanel panel, String name)
    {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(206, Math.max(1, panel.getHeight()), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D graphics = image.createGraphics(); panel.printAll(graphics); graphics.dispose();
        java.io.File file = new java.io.File("build/reports/" + name + "-206px.png"); file.getParentFile().mkdirs();
        try { javax.imageio.ImageIO.write(image, "png", file); } catch (java.io.IOException error) { throw new AssertionError(error); }
    }
}
