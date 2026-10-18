/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.laf.RuneLiteLAF;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SidebarThemeTest
{
    @Test public void allLocalSidebarModulesStayDarkAndReadableWhenRendered() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            LookAndFeel previous = UIManager.getLookAndFeel();
            try
            {
                assertTrue("RuneLite's dark look and feel must load", RuneLiteLAF.setup());
                Client client = mock(Client.class);
                ClientThread clientThread = mock(ClientThread.class);
                ConfigManager configs = mock(ConfigManager.class);
                EventBus events = mock(EventBus.class);
                OverlayManager overlays = mock(OverlayManager.class);
                RunewardenGuides guides = new RunewardenGuides(client, clientThread, configs,
                    new GuideCatalog(new Gson()), new GuideNavigation(client, events, mock(PluginManager.class)),
                    new GuideMarkerOverlay(client), new GuideCoachOverlay(client), overlays);
                RunewardenPanel panel = new RunewardenPanel(guides,
                    new RunewardenSightTool(client, clientThread, overlays, events, mock(RunewardenConfig.class)),
                    new WaveExportTool(client, clientThread));
                for(java.util.Map.Entry<String,JComponent> entry:SidebarLayoutTest.allModules(new SidebarLayoutTest.Fixture()).entrySet())panel.addModule(entry.getKey(),entry.getValue());
                JComboBox<?> modules = (JComboBox<?>) descendants(panel).stream()
                    .filter(component -> "runewarden.module.selector".equals(component.getName())).findFirst().get();
                Container cards = (Container) descendants(panel).stream()
                    .filter(component -> "runewarden.module.cards".equals(component.getName())).findFirst().get();
                assertTrue(modules.getItemCount()>=35);
                assertEquals("Guides", modules.getItemAt(0));
                assertEquals("Visibility", modules.getItemAt(1));
                assertEquals("Wave references", modules.getItemAt(2));
                JComponent root = panel.getWrappedPanel();
                root.setSize(300, 900);
                for (int index = 0; index < modules.getItemCount(); index++)
                {
                    modules.setSelectedIndex(index);
                    layout(root);
                    String tab = modules.getSelectedItem().toString();
                    Container active = null;
                    for (Component child : cards.getComponents()) if (child.isVisible()) active = (Container) child;
                    assertNotNull(active);
                    List<Component> visible = visibleDescendants(active);
                    assertTrue(tab + " must contain actual controls", visible.stream()
                        .anyMatch(component -> component instanceof AbstractButton || component instanceof javax.swing.JTextField || component instanceof JComboBox) || visible.stream().anyMatch(component -> component instanceof GuideText && ((GuideText)component).getText().startsWith("Log in")));
                    for (Component component : visible)
                    {
                        if (component instanceof JComponent && ((JComponent) component).isOpaque())
                        {
                            assertTrue(tab + " contains a bright background: " + component.getClass().getSimpleName(),
                                luminance(component.getBackground()) < 0.18);
                        }
                        if (component.isEnabled() && hasText(component))
                        {
                            assertTrue(tab + " contains unreadable text: " + component.getClass().getSimpleName(),
                                contrast(component.getForeground(), backdrop(component)) >= 4.5);
                        }
                    }
                    BufferedImage image = new BufferedImage(300, 900, BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics = image.createGraphics();
                    try { root.printAll(graphics); }
                    finally { graphics.dispose(); }
                    int dark = 0, light = 0;
                    for (int y = 0; y < image.getHeight(); y++)
                        for (int x = 0; x < image.getWidth(); x++)
                        {
                            double value = luminance(new Color(image.getRGB(x, y)));
                            if (value < 0.18) dark++;
                            if (value > 0.45) light++;
                        }
                    assertTrue(tab + " rendered a light panel instead of a dark sidebar", dark > 300 * 900 * 0.85);
                    assertTrue(tab + " rendered without visible light text or accents", light > 100);
                }
                // The list uses renderer components; exercise both its selected and ordinary text colors too.
                JList<?> results = (JList<?>) descendants(panel).stream()
                    .filter(component -> component instanceof JList).findFirst().get();
                assertTrue(results.getModel().getSize() > 0);
                assertListTextContrast(results, false);
                assertListTextContrast(results, true);
            }
            catch (Exception error) { throw new AssertionError(error); }
            finally
            {
                try { UIManager.setLookAndFeel(previous); }
                catch (Exception error) { throw new AssertionError(error); }
            }
        });
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void assertListTextContrast(JList list, boolean selected)
    {
        Component renderer = list.getCellRenderer().getListCellRendererComponent(list,
            list.getModel().getElementAt(0), 0, selected, false);
        assertTrue("Guide result text must remain readable when selected=" + selected,
            contrast(renderer.getForeground(), renderer.getBackground()) >= 4.5);
    }

    private static boolean hasText(Component component)
    {
        if (component instanceof JLabel) return !((JLabel) component).getText().isEmpty();
        if (component instanceof AbstractButton)
        {
            String text = ((AbstractButton) component).getText();
            return text != null && !text.isEmpty();
        }
        return component instanceof JTextComponent;
    }

    private static Color backdrop(Component component)
    {
        Component current = component;
        while (current instanceof JComponent && !((JComponent) current).isOpaque()) current = current.getParent();
        return current == null ? Color.BLACK : current.getBackground();
    }

    private static double contrast(Color first, Color second)
    {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(Color color)
    {
        return 0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue());
    }

    private static double channel(int value)
    {
        double scaled = value / 255.0;
        return scaled <= 0.04045 ? scaled / 12.92 : Math.pow((scaled + 0.055) / 1.055, 2.4);
    }

    private static void layout(Container container)
    {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
    }

    private static List<Component> descendants(Container root)
    {
        List<Component> result = new ArrayList<>();
        for (Component child : root.getComponents())
        {
            result.add(child);
            if (child instanceof Container) result.addAll(descendants((Container) child));
        }
        return result;
    }

    private static List<Component> visibleDescendants(Container root)
    {
        List<Component> result = new ArrayList<>();
        result.add(root);
        for (Component child : root.getComponents())
        {
            if (!child.isVisible()) continue;
            if (child instanceof Container) result.addAll(visibleDescendants((Container) child));
            else result.add(child);
        }
        return result;
    }
}
