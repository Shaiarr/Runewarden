/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.AsyncBufferedImage;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class BankToolsLayoutTest
{
    @Test public void materialAndTrainingControlsFitNarrowAndRealWrappedSidebar() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Client client = mock(Client.class); ClientThread thread = mock(ClientThread.class);
            ItemManager items = images(thread); GuideCatalog catalog = new GuideCatalog(new Gson());
            ActivityXpTool training = new ActivityXpTool(new ActivityXpCatalog(new Gson(), catalog));
            JPanel calculator = new BankedExperience(client, items, training).createPanel();
            calculator.setSize(206, 18000); layout(calculator); assertFits(calculator);
            JComboBox<?> view = named(calculator, "banked-xp.view", JComboBox.class); view.setSelectedIndex(1);
            training.openMethod("activity:foundry-steel-mithril"); layout(calculator); assertFits(calculator);
            Component rate = named(calculator, "training-xp.rate", JTextField.class);
            assertFalse("Personal pace starts collapsed", rate.getParent().getParent().isVisible());
            RunewardenGuides guides = new RunewardenGuides(client, thread, mock(ConfigManager.class), catalog,
                new GuideNavigation(client, mock(EventBus.class), mock(PluginManager.class)),
                new GuideMarkerOverlay(client), new GuideCoachOverlay(client), mock(OverlayManager.class), items);
            RunewardenPanel host = new RunewardenPanel(guides,
                new RunewardenSightTool(client, thread, mock(OverlayManager.class), mock(EventBus.class), mock(RunewardenConfig.class)),
                new WaveExportTool(client, thread));
            host.addModule("XP calculator", calculator); host.selectModule("XP calculator");
            JComponent wrapped = host.getWrappedPanel(); wrapped.setSize(242, 18000); layout(wrapped);
            assertFits(calculator);
            assertTrue("Calculator retains usable width", calculator.getWidth() >= 190);
        });
    }

    @Test public void switchingToTrainingUsesActiveCardHeightInsteadOfLongMaterialList() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            ClientThread thread = mock(ClientThread.class); GuideCatalog catalog = new GuideCatalog(new Gson());
            JPanel calculator = new BankedExperience(mock(Client.class), images(thread),
                new ActivityXpTool(new ActivityXpCatalog(new Gson(), catalog))).createPanel();
            JCheckBox banked = descendants(calculator).stream().filter(c -> c instanceof JCheckBox)
                .map(c -> (JCheckBox) c).filter(c -> "Banked items only".equals(c.getText())).findFirst().get();
            banked.doClick(); calculator.setSize(206, 18000); layout(calculator);
            JPanel pages = named(calculator, "banked-xp.pages", JPanel.class);
            int longHeight = pages.getPreferredSize().height;
            named(calculator, "banked-xp.view", JComboBox.class).setSelectedIndex(1); layout(calculator);
            Component active = null; for (Component card : pages.getComponents()) if (card.isVisible()) active = card;
            assertNotNull(active); assertEquals(active.getPreferredSize().height, pages.getPreferredSize().height);
            assertTrue("Hidden bank list must not leave empty scroll space", pages.getPreferredSize().height < longHeight);
        });
    }

    @Test public void bankFilterControlsUseWrappingStatusAndFullAvailableWidth() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            GuideBankFilter filter = new GuideBankFilter(mock(Client.class), mock(ClientThread.class),
                mock(net.runelite.client.plugins.bank.BankSearch.class), () -> null, mock(ItemManager.class));
            JPanel panel = filter.createPanel(); panel.setSize(206, 4000); layout(panel); assertFits(panel);
            assertTrue(descendants(panel).stream().filter(c -> c instanceof GuideText).count() >= 3);
            for (Component child : descendants(panel)) if (child instanceof javax.swing.JLabel)
                assertFalse("No fixed-width HTML instructions", String.valueOf(((javax.swing.JLabel) child).getText()).contains("<html>"));
        });
    }

    private static ItemManager images(ClientThread thread)
    {
        ItemManager items = mock(ItemManager.class);
        when(items.getImage(anyInt())).thenAnswer(call -> new AsyncBufferedImage(thread, 36, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB)); return items;
    }
    private static <T> T named(Container root, String name, Class<T> type)
    { for (Component child : descendants(root)) if (name.equals(child.getName())) return type.cast(child); throw new AssertionError(name); }
    private static List<Component> descendants(Container root)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents()) { result.add(child); if (child instanceof Container) result.addAll(descendants((Container) child)); }
        return result;
    }
    private static void layout(Container root)
    { for (int pass = 0; pass < 7; pass++) deepLayout(root); }
    private static void deepLayout(Container root)
    { root.invalidate(); root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) deepLayout((Container) child); }
    private static void assertFits(Container root)
    {
        for (Component child : descendants(root))
        {
            if (!(child instanceof JButton || child instanceof JComboBox || child instanceof JTextField || child instanceof GuideText)) continue;
            boolean visible = true; for (Component ancestor = child; ancestor != root; ancestor = ancestor.getParent())
                if (ancestor == null || !ancestor.isVisible()) { visible = false; break; }
            if (!visible || child.getParent().getWidth() <= 0) continue;
            assertTrue(child.getClass().getSimpleName() + " clipped left", child.getX() >= 0);
            assertTrue(child.getClass().getSimpleName() + " exceeds parent width", child.getX() + child.getWidth() <= child.getParent().getWidth());
            if (child instanceof GuideText) assertTrue("Text has usable width", child.getWidth() >= child.getParent().getWidth() - 22);
        }
    }
}
