/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class GuideUsabilityTest
{
    @Test public void completingTrainingStepsAdvancesAndPersistsCharacterCompletion() throws Exception
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getIntStack()).thenReturn(new int[] { 1 });
        ClientThread thread = mock(ClientThread.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
        ConfigManager configs = mock(ConfigManager.class);
        RunewardenGuides guides = guides(client, thread, configs);
        JComponent[] panel = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = guides.createPanel();
            guides.openGuide("training:giants-foundry");
        });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("prepare:1", guides.currentStep().id);
            button(panel[0], "Complete step →").doClick();
            assertEquals("prepare:2", guides.currentStep().id);
            button(panel[0], "Complete step →").doClick();
            assertEquals("method:1", guides.currentStep().id);
        });
        verify(configs).setRSProfileConfiguration("runewarden", GuideProgress.completedKey("training:giants-foundry"), "prepare:1");
        verify(configs).setRSProfileConfiguration("runewarden", GuideProgress.positionKey("training:giants-foundry"), 1);
        SwingUtilities.invokeAndWait(() -> button(panel[0], "Complete step →").doClick());
        SwingUtilities.invokeAndWait(() -> assertEquals("method:2", guides.currentStep().id));
        verify(configs).setRSProfileConfiguration("runewarden", GuideProgress.completedKey("training:giants-foundry"),
            "prepare:1\nprepare:2\nmethod:1");
        guides.onGameStateChanged(GameState.LOGIN_SCREEN);
        SwingUtilities.invokeAndWait(() -> { assertNull(guides.selectedGuide()); assertNull(guides.currentStep()); });
    }

    @Test public void savedCharacterPositionAndCompletedStateAreRestored() throws Exception
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getIntStack()).thenReturn(new int[] { 1 });
        ClientThread thread = mock(ClientThread.class);
        doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(thread).invokeLater(any(Runnable.class));
        ConfigManager configs = mock(ConfigManager.class);
        when(configs.getRSProfileConfiguration("runewarden", GuideProgress.positionKey("training:giants-foundry"), Integer.class)).thenReturn(1);
        when(configs.getRSProfileConfiguration("runewarden", GuideProgress.completedKey("training:giants-foundry"), String.class))
            .thenReturn("prepare:1\nprepare:2\nunknown-step");
        RunewardenGuides guides = guides(client, thread, configs);
        JComponent[] panel = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> { panel[0] = guides.createPanel(); guides.openGuide("training:giants-foundry"); });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals("method:1", guides.currentStep().id);
            button(panel[0], "Previous").doClick();
            assertEquals("prepare:2", guides.currentStep().id);
            assertFalse(button(panel[0], "Completed ✓").isEnabled());
            button(panel[0], "Mark step incomplete").doClick();
            assertTrue(button(panel[0], "Complete step →").isEnabled());
        });
    }

    @Test public void longGuideAndItemNotesWrapAtActualNarrowSidebarWidth() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Client client = mock(Client.class);
            ClientThread thread = mock(ClientThread.class);
            ItemManager items = mock(ItemManager.class);
            when(items.getImage(anyInt())).thenAnswer(call -> new AsyncBufferedImage(thread, 36, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB));
            RunewardenGuides guides = new RunewardenGuides(client, thread, mock(ConfigManager.class), new GuideCatalog(new Gson()),
                new GuideNavigation(client, mock(EventBus.class), mock(PluginManager.class)),
                new GuideMarkerOverlay(client), new GuideCoachOverlay(client), mock(OverlayManager.class), items);
            JComponent panel = guides.createPanel();
            guides.openGuide("training:ruby-harvests");
            panel.setSize(201, 22000);
            for (int pass = 0; pass < 5; pass++) layout(panel);
            List<GuideText> texts = new ArrayList<>();
            for (Component component : descendants(panel)) if (component instanceof GuideText) texts.add((GuideText) component);
            assertTrue(texts.stream().anyMatch(text -> text.getText().contains("Butterfly jar")));
            assertTrue(texts.stream().anyMatch(text -> text.getText().contains("Piscatoris")));
            verify(items, atLeastOnce()).getImage(anyInt());
            for (GuideText text : texts)
            {
                assertTrue("Readable UI font required", text.getFont().getSize() >= 13);
                assertTrue(text.getLineWrap());
                assertTrue(text.getWrapStyleWord());
                if (text.getParent() != null && text.getParent().getWidth() > 0)
                    assertTrue("Text exceeds actual parent width: " + text.getText(),
                        text.getX() + text.getWidth() <= text.getParent().getWidth());
            }
            JPanel host = new JPanel();
            host.setSize(190, 1000);
            GuideText longText = new GuideText("A long required item name and a complete explanatory note should wrap without clipping the next words or hiding their quantity.");
            host.add(longText);
            int wide = longText.getPreferredSize().height;
            host.setSize(100, 1000);
            int narrow = longText.getPreferredSize().height;
            assertTrue("Reducing the sidebar width must increase the full visible text height", narrow > wide);
            assertEquals(narrow, longText.getMaximumSize().height);
        });
    }

    @Test public void realModuleCardKeepsGuideTextAndItemCardsAtUsableLeftAlignedWidths() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            Client client = mock(Client.class);
            ClientThread thread = mock(ClientThread.class);
            ConfigManager configs = mock(ConfigManager.class);
            OverlayManager overlays = mock(OverlayManager.class);
            EventBus events = mock(EventBus.class);
            ItemManager items = mock(ItemManager.class);
            when(items.getImage(anyInt())).thenAnswer(call -> new AsyncBufferedImage(thread, 36, 32, java.awt.image.BufferedImage.TYPE_INT_ARGB));
            RunewardenGuides guides = new RunewardenGuides(client, thread, configs, new GuideCatalog(new Gson()),
                new GuideNavigation(client, events, mock(PluginManager.class)), new GuideMarkerOverlay(client),
                new GuideCoachOverlay(client), overlays, items);
            RunewardenPanel root = new RunewardenPanel(guides,
                new RunewardenSightTool(client, thread, overlays, events, mock(RunewardenConfig.class)), new WaveExportTool(client, thread));
            JComponent guidePanel = guides.createPanel();
            guides.openGuide("training:ruby-harvests");
            JComponent wrapped = root.getWrappedPanel();
            wrapped.setSize(242, 1000);
            for (int pass = 0; pass < 9; pass++) layout(wrapped);
            assertTrue(guidePanel.getWidth() >= 190);
            for (Component component : descendants(guidePanel))
            {
                if (!(component instanceof GuideText)) continue;
                GuideText text = (GuideText) component;
                if (text.getText().length() < 15 || text.getParent() instanceof javax.swing.CellRendererPane) continue;
                java.awt.Point origin = SwingUtilities.convertPoint(text, 0, 0, guidePanel);
                boolean itemCard = text.getParent().getLayout() instanceof java.awt.BorderLayout;
                int expectedLeft = itemCard ? 64 : 24;
                assertTrue("Guide text was shifted right by mixed BoxLayout alignment: " + text.getText(), origin.x <= expectedLeft);
                int expectedWidth = guidePanel.getWidth() - (itemCard ? 78 : 20);
                assertTrue("Text lost usable sidebar width: " + text.getText() + " / actual " + text.getWidth(), text.getWidth() >= expectedWidth);
            }
        });
    }

    private static RunewardenGuides guides(Client client, ClientThread thread, ConfigManager configs)
    {
        return new RunewardenGuides(client, thread, configs, new GuideCatalog(new Gson()),
            new GuideNavigation(client, mock(EventBus.class), mock(PluginManager.class)),
            new GuideMarkerOverlay(client), new GuideCoachOverlay(client), mock(OverlayManager.class));
    }

    private static void select(JComponent panel, String query)
    {
        JTextField search = (JTextField) descendants(panel).stream().filter(item -> item instanceof JTextField).findFirst().get();
        search.setText(query);
        JList<?> results = (JList<?>) descendants(panel).stream().filter(item -> item instanceof JList).findFirst().get();
        assertEquals(1, results.getModel().getSize());
        results.setSelectedIndex(0);
    }

    private static JButton button(JComponent panel, String text)
    {
        return (JButton) descendants(panel).stream().filter(item -> item instanceof JButton && text.equals(((JButton) item).getText())).findFirst().get();
    }

    private static void layout(Container container)
    {
        container.doLayout();
        for (Component component : container.getComponents()) if (component instanceof Container) layout((Container) component);
    }

    private static List<Component> descendants(Container root)
    {
        List<Component> result = new ArrayList<>();
        for (Component component : root.getComponents())
        {
            result.add(component);
            if (component instanceof Container) result.addAll(descendants((Container) component));
        }
        return result;
    }
}
