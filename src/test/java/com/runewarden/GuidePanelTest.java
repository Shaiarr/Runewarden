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
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class GuidePanelTest
{
    @Test public void manualWaveSelectionAndAccountResetWorkThroughThePanel() throws Exception
    {
        Client client = mock(Client.class);
        GuideCatalog catalog = new GuideCatalog(new Gson());
        GuideMarkerOverlay markers = new GuideMarkerOverlay(client);
        RunewardenGuides guides = new RunewardenGuides(client, mock(ClientThread.class), mock(ConfigManager.class),
            catalog, new GuideNavigation(client, mock(EventBus.class), mock(PluginManager.class)),
            markers, new GuideCoachOverlay(client), mock(OverlayManager.class));
        JComponent[] panel = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = guides.createPanel();
            ((JTextField) descendants(panel[0]).stream().filter(item -> item instanceof JTextField).findFirst().get()).setText("69 wave guides");
            JList<?> results = (JList<?>) descendants(panel[0]).stream().filter(item -> item instanceof JList).findFirst().get();
            assertEquals(1, results.getModel().getSize());
            results.setSelectedIndex(0);
            JComboBox<?> waves = waves(panel[0]);
            assertNotNull(waves);
            assertEquals(69, waves.getItemCount());
            waves.setSelectedIndex(30);
            assertEquals("Wave 31", waves.getSelectedItem());
            assertSame(catalog.pack(catalog.entry("waves:inferno").sections.get(30).markerPackId), markers.selectedPack());
            JButton next = (JButton) descendants(panel[0]).stream().filter(item -> item instanceof JButton && "Wave →".equals(((JButton) item).getText())).findFirst().get();
            next.doClick();
            assertEquals("Wave 32", waves.getSelectedItem());
            assertSame(catalog.pack(catalog.entry("waves:inferno").sections.get(31).markerPackId), markers.selectedPack());
        });
        SwingUtilities.invokeAndWait(() -> {
            guides.openGuide("boss:hueycoatl");
            assertNotNull(guides.selectedGuide());
            assertEquals("boss:hueycoatl", guides.selectedGuide().id);
            JButton next = (JButton) descendants(panel[0]).stream().filter(item -> item instanceof JButton && "Section →".equals(((JButton) item).getText())).findFirst().get();
            next.doClick();
            assertTrue(descendants(panel[0]).stream().anyMatch(item -> item instanceof JComboBox && "Tail phase · wave movement".equals(((JComboBox<?>) item).getSelectedItem())));
            next.doClick();
            assertTrue(descendants(panel[0]).stream().anyMatch(item -> item instanceof JComboBox && "Tail-tip timing cues".equals(((JComboBox<?>) item).getSelectedItem())));
        });
        guides.onProfileChanged();
        SwingUtilities.invokeAndWait(() -> {
            assertNull(guides.selectedGuide());
            assertNull(guides.currentStep());
            assertNull(markers.selectedPack());
            assertNull(waves(panel[0]));
            assertFalse(descendants(panel[0]).stream().anyMatch(item -> item instanceof JComboBox && "Tail phase · wave movement".equals(((JComboBox<?>) item).getSelectedItem())));
            assertFalse(descendants(panel[0]).stream().anyMatch(item -> item instanceof JButton && "Open synced guide".equals(((JButton) item).getText())));
        });
    }

    @Test public void markerLibraryIsAbsentFromGuideCategoriesAndAllGuideResults() throws Exception
    {
        Client client = mock(Client.class);
        GuideCatalog catalog = new GuideCatalog(new Gson());
        RunewardenGuides guides = new RunewardenGuides(client, mock(ClientThread.class), mock(ConfigManager.class),
            catalog, new GuideNavigation(client, mock(EventBus.class), mock(PluginManager.class)),
            new GuideMarkerOverlay(client), new GuideCoachOverlay(client), mock(OverlayManager.class));
        SwingUtilities.invokeAndWait(() -> {
            JComponent panel = guides.createPanel();
            JList<?> results = (JList<?>) descendants(panel).stream().filter(item -> item instanceof JList).findFirst().get();
            long guideCount = catalog.search("", "").stream().filter(entry -> !"markers".equals(entry.category) && !"quest".equals(entry.category)).count();
            assertEquals(guideCount, results.getModel().getSize());
            for (int i = 0; i < results.getModel().getSize(); i++)
                assertNotEquals("markers", ((GuideCatalog.Entry) results.getModel().getElementAt(i)).category);
            for (Component component : descendants(panel))
            {
                if (component instanceof JComboBox)
                {
                    JComboBox<?> combo = (JComboBox<?>) component;
                    for (int i = 0; i < combo.getItemCount(); i++)
                    {
                        assertNotEquals("Tile packs", combo.getItemAt(i));
                        assertFalse(combo.getItemAt(i) instanceof GuideCatalog.MarkerPack);
                    }
                }
                if (component instanceof JButton) assertNotEquals("Marker source", ((JButton) component).getText());
                if (component instanceof JCheckBox) assertNotEquals("Numbered tile markers", ((JCheckBox) component).getText());
                if (component instanceof GuideText) assertNotEquals("STATIC TILE REFERENCES", ((GuideText) component).getText());
            }
        });
    }

    @Test public void waveSelectionPreservesItsMarkerNumberFilterAndReplacesItOnTheNextWave() throws Exception
    {
        Client client = mock(Client.class);
        GuideCatalog catalog = new GuideCatalog(new Gson());
        GuideCatalog.Entry inferno = catalog.entry("waves:inferno");
        inferno.sections.get(30).markerNumbers = java.util.Arrays.asList(1, 3);
        GuideMarkerOverlay markers = new GuideMarkerOverlay(client);
        RunewardenGuides guides = new RunewardenGuides(client, mock(ClientThread.class), mock(ConfigManager.class),
            catalog, new GuideNavigation(client, mock(EventBus.class), mock(PluginManager.class)),
            markers, new GuideCoachOverlay(client), mock(OverlayManager.class));
        SwingUtilities.invokeAndWait(() -> {
            JComponent panel = guides.createPanel();
            guides.openGuide(inferno.id);
            JComboBox<?> waves = waves(panel);
            waves.setSelectedIndex(30);
            assertSame(catalog.pack(inferno.sections.get(30).markerPackId), markers.selectedPack());
            assertEquals(java.util.Arrays.asList(1, 3), markers.selectedNumbers());
            waves.setSelectedIndex(31);
            assertSame(catalog.pack(inferno.sections.get(31).markerPackId), markers.selectedPack());
            assertNull(markers.selectedNumbers());
        });
    }

    private static JComboBox<?> waves(Container root)
    {
        for (Component component : descendants(root))
        {
            if (component instanceof JComboBox)
            {
                JComboBox<?> combo = (JComboBox<?>) component;
                if (combo.getItemCount() > 0 && "Wave 1".equals(combo.getItemAt(0))) return combo;
            }
        }
        return null;
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
}
