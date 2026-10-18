/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class QuestOrderTest
{
    private final GuideCatalog catalog = new GuideCatalog(new Gson());
    private final QuestOrder order = catalog.questOrder();

    @Test public void pinnedRegularAccountRouteKeepsPublishedPositionsAndAliases()
    {
        assertEquals(15358869, order.revision());
        assertEquals(238, order.rowCount());
        assertEquals(1, order.rank("Learning the Ropes"));
        assertEquals(2, order.rank("Cook's Assistant"));
        assertEquals(3, order.rank("Sheep Shearer"));
        assertTrue(order.rank("Waterfall Quest") < order.rank("Dragon Slayer I"));
        assertEquals(order.rank("Recipe for Disaster/Freeing Evil Dave"), order.rank("Recipe for Disaster - Evil Dave"));
        assertEquals(order.rank("Ardougne Diary#Easy"), order.rank("Easy Ardougne Diary"));
        assertEquals(Integer.MAX_VALUE, order.rank("Recipe for Disaster"));
        assertEquals(Integer.MAX_VALUE, order.rank("An unlisted future quest"));
    }

    @Test public void fullCatalogCoveragePreservesAllUnrankedGuides()
    {
        List<GuideCatalog.Entry> quests = order.filter(catalog.search("", "quest"), true, false, Collections.emptySet());
        assertEquals(238, quests.size());
        assertEquals(220, quests.stream().filter(order::ranked).count());
        assertEquals(18, quests.stream().filter(entry -> !order.ranked(entry)).count());
        boolean unranked = false;
        int previous = 0;
        for (GuideCatalog.Entry entry : quests)
        {
            if (!order.ranked(entry)) unranked = true;
            else { assertFalse(unranked); assertTrue(order.rank(entry) >= previous); previous = order.rank(entry); }
        }
    }

    @Test public void searchKeepsRouteOrderAndNameSortIsAvailable()
    {
        List<GuideCatalog.Entry> quests = catalog.search("slayer", "quest");
        List<GuideCatalog.Entry> sorted = order.filter(quests, true, false, Collections.emptySet());
        assertEquals("quest:Vampyre Slayer", sorted.get(0).id);
        assertEquals("quest:Demon Slayer", sorted.get(1).id);
        assertEquals("quest:Demon Slayer", order.filter(quests, false, false, Collections.emptySet()).get(0).id);
    }

    @Test public void hidingCompletedOnlyRemovesExplicitFinishedIds()
    {
        List<GuideCatalog.Entry> quests = catalog.search("", "quest");
        List<GuideCatalog.Entry> visible = order.filter(quests, true, true, Collections.singleton("quest:Cook's Assistant"));
        assertEquals(237, visible.size());
        assertFalse(visible.stream().anyMatch(entry -> "quest:Cook's Assistant".equals(entry.id)));
        assertTrue(visible.stream().anyMatch(entry -> "quest:The Frozen Door".equals(entry.id)));
        assertEquals(238, order.filter(quests, true, false, Collections.singleton("quest:Cook's Assistant")).size());
    }

    @Test public void questPanelDefaultsToRouteAndSearchRetainsSort() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            JComponent panel = guides(mock(Client.class)).createPanel();
            List<Component> all = descendants(panel);
            JComboBox<?> category = (JComboBox<?>) all.stream().filter(c -> c instanceof JComboBox).findFirst().get();
            category.setSelectedIndex(1);
            JComboBox<?> sort = (JComboBox<?>) all.stream().filter(c -> c instanceof JComboBox
                && "Optimal order".equals(((JComboBox<?>) c).getItemAt(0))).findFirst().get();
            assertEquals("Optimal order", sort.getSelectedItem());
            JTextField search = (JTextField) all.stream().filter(c -> c instanceof JTextField).findFirst().get();
            search.setText("slayer");
            JList<?> results = (JList<?>) all.stream().filter(c -> c instanceof JList).findFirst().get();
            assertEquals("Vampyre Slayer", ((GuideCatalog.Entry) results.getModel().getElementAt(0)).title);
            sort.setSelectedIndex(1);
            assertEquals("Demon Slayer", ((GuideCatalog.Entry) results.getModel().getElementAt(0)).title);
            JCheckBox hidden = (JCheckBox) all.stream().filter(c -> c instanceof JCheckBox
                && "Hide completed quests".equals(((JCheckBox) c).getText())).findFirst().get();
            assertTrue(hidden.isSelected());
        });
    }

    @Test public void pendingQuestFactsCannotSurviveCharacterChange() throws Exception
    {
        Client client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getIntStack()).thenReturn(new int[] { 2 });
        RunewardenGuides guides = guides(client);
        JComponent[] panel = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = guides.createPanel();
            ((JComboBox<?>) descendants(panel[0]).stream().filter(c -> c instanceof JComboBox).findFirst().get()).setSelectedIndex(1);
        });
        SwingUtilities.invokeAndWait(() -> {
            guides.onGameStateChanged(GameState.LOGGED_IN);
            guides.onGameTick();
            guides.onProfileChanged();
        });
        SwingUtilities.invokeAndWait(() -> {
            JList<?> results = (JList<?>) descendants(panel[0]).stream().filter(c -> c instanceof JList).findFirst().get();
            assertEquals(238, results.getModel().getSize());
        });
    }

    private RunewardenGuides guides(Client client)
    {
        return new RunewardenGuides(client, mock(ClientThread.class), mock(ConfigManager.class), catalog,
            new GuideNavigation(client, mock(EventBus.class), mock(PluginManager.class)),
            new GuideMarkerOverlay(client), new GuideCoachOverlay(client), mock(OverlayManager.class));
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
