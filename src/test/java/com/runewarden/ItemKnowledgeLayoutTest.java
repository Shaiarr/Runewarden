/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.View;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.laf.RuneLiteLAF;
import net.runelite.client.util.AsyncBufferedImage;
import okhttp3.OkHttpClient;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class ItemKnowledgeLayoutTest
{
    private static final class Fixture
    {
        ItemKnowledgeTool tool;
        JPanel itemPanel;
        JComponent wrapped;
    }

    private static Fixture fixture(int itemId) throws Exception
    {
        Fixture fixture = new Fixture();
        Gson gson = new Gson();
        ItemKnowledgeCatalog catalog = new ItemKnowledgeCatalog(gson, new GuideCatalog(gson)); catalog.load();
        Client client = mock(Client.class); ClientThread thread = mock(ClientThread.class);
        ItemManager items = mock(ItemManager.class);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        ItemComposition definition = mock(ItemComposition.class);
        when(definition.getMembersName()).thenReturn(catalog.item(itemId).name);
        when(definition.isTradeable()).thenReturn(true);
        when(definition.getInventoryActions()).thenReturn(new String[]{"Use", null, null, null, "Drop"});
        when(items.getItemComposition(itemId)).thenReturn(definition);
        when(items.canonicalize(itemId)).thenReturn(itemId);
        when(items.getItemPrice(itemId)).thenReturn(1234L);
        when(items.getImage(anyInt())).thenAnswer(call -> new AsyncBufferedImage(thread, 32, 32, BufferedImage.TYPE_INT_ARGB));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        fixture.tool = new ItemKnowledgeTool(client, thread, items, catalog, new SkillingResourceCatalog(gson),
            new ItemSourceService(mock(OkHttpClient.class), gson, executor), executor);
        SwingUtilities.invokeAndWait(() -> {
            RuneLiteLAF.setup(); fixture.tool.start(); fixture.itemPanel = fixture.tool.createPanel();
            PluginPanel host = new PluginPanel() { };
            host.setLayout(new BorderLayout()); host.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            host.add(fixture.itemPanel, BorderLayout.NORTH);
            fixture.wrapped = host.getWrappedPanel(); fixture.wrapped.setSize(225, 1100);
            fixture.tool.onVisibilityChanged(true); fixture.tool.openItem(itemId);
        });
        SwingUtilities.invokeAndWait(() -> fixture.tool.onGameTick());
        SwingUtilities.invokeAndWait(() -> layout(fixture.wrapped));
        return fixture;
    }

    @Test public void itemAndSourceCardsUseRealWrappedRuneLiteViewportWidth() throws Exception
    {
        Fixture fixture = fixture(946);
        SwingUtilities.invokeAndWait(() -> {
            sourceFilter(fixture).setSelectedItem("Shop"); layout(fixture.wrapped);
            int cards = 0;
            for (Component component : descendants(fixture.itemPanel))
            {
                if ("runewarden.items.source-card".equals(component.getName()))
                {
                    cards++; assertTrue("Source card squeezed to " + component.getWidth(), component.getWidth() >= 180);
                    assertTrue("Source card indented", component.getX() <= 2);
                }
                if ("runewarden.items.search".equals(component.getName()))
                    assertTrue("Search input squeezed", component.getWidth() >= 180);
                if ("runewarden.items.fact".equals(component.getName()))
                    for (Component child : ((Container) component).getComponents())
                        assertTrue("Source value clipped by compressed field", child.getY() + child.getHeight()
                            <= component.getHeight() - ((JComponent) component).getInsets().bottom);
            }
            assertEquals(5, cards);
        });
        fixture.tool.stop();
    }

    @Test public void sourceFieldsAreSeparateReadableRowsAndRevisionControlsStartCollapsed() throws Exception
    {
        Fixture fixture = fixture(946);
        SwingUtilities.invokeAndWait(() -> {
            sourceFilter(fixture).setSelectedItem("Shop"); layout(fixture.wrapped);
            assertTrue(descendants(fixture.itemPanel).stream().filter(component -> component instanceof GuideText)
                .map(component -> ((GuideText) component).getText()).anyMatch(value -> value.startsWith("Location: ")));
            assertFalse(descendants(fixture.itemPanel).stream().filter(component -> component instanceof GuideText)
                .map(component -> ((GuideText) component).getText()).anyMatch(value -> value.contains("Location:") && value.contains(" · ")));
            JCheckBox details = (JCheckBox) descendants(fixture.itemPanel).stream()
                .filter(component -> component instanceof JCheckBox && ((JCheckBox) component).getText().equals("Source details")).findFirst().get();
            assertFalse(details.isSelected());
            assertFalse(hasAction(fixture, "Open source revision")); details.doClick(); layout(fixture.wrapped);
            assertTrue(hasAction(fixture, "Open source revision"));
        });
        fixture.tool.stop();
    }

    @Test public void longGuideButtonsGrowVerticallyAndStayInsideTheirParent() throws Exception
    {
        Fixture fixture = fixture(946);
        SwingUtilities.invokeAndWait(() -> {
            layout(fixture.wrapped);
            for (Component component : descendants(fixture.itemPanel))
            {
                if (!(component instanceof AbstractButton)) continue;
                AbstractButton button = (AbstractButton) component;
                assertTrue("Button exceeds parent width: " + button.getToolTipText(), button.getX() + button.getWidth() <= button.getParent().getWidth());
                View view = (View) button.getClientProperty(BasicHTML.propertyKey);
                if (view != null)
                    assertTrue("Wrapped button text clipped: " + button.getToolTipText(), button.getHeight()
                        >= Math.ceil(view.getPreferredSpan(View.Y_AXIS)) + button.getInsets().top + button.getInsets().bottom);
            }
        });
        fixture.tool.stop();
    }

    private static boolean hasAction(Fixture fixture, String prefix)
    {
        return descendants(fixture.itemPanel).stream().filter(component -> component instanceof AbstractButton)
            .map(component -> ((AbstractButton) component).getToolTipText()).anyMatch(value -> value != null && value.startsWith(prefix));
    }

    private static JComboBox<?> sourceFilter(Fixture fixture)
    { return (JComboBox<?>) descendants(fixture.itemPanel).stream().filter(component -> "runewarden.items.source-filter".equals(component.getName())).findFirst().get(); }

    private static void layout(Container root)
    {
        for (int pass = 0; pass < 6; pass++) recursiveLayout(root);
    }
    private static void recursiveLayout(Container root)
    { root.doLayout(); for (Component child : root.getComponents()) if (child instanceof Container) recursiveLayout((Container) child); }
    private static List<Component> descendants(Container root)
    {
        List<Component> components = new ArrayList<>();
        for (Component child : root.getComponents())
        { components.add(child); if (child instanceof Container) components.addAll(descendants((Container) child)); }
        return components;
    }

    /** Local visual QA uses the same actual PluginPanel wrapper as the regression tests. */
    public static void main(String[] args) throws Exception
    {
        Fixture fixture = fixture(Integer.parseInt(args.length > 1 ? args[1] : "946"));
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> filter = sourceFilter(fixture); if (args.length < 3) filter.setSelectedItem("Shop");
            layout(fixture.wrapped);
            for(Component c:descendants(fixture.wrapped)) if(c instanceof javax.swing.JScrollPane) ((javax.swing.JScrollPane)c).getViewport().setViewPosition(new java.awt.Point(0,0));
            BufferedImage image = new BufferedImage(225, 1100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics(); fixture.wrapped.printAll(graphics); graphics.dispose();
            try { ImageIO.write(image, "png", new java.io.File(args[0])); }
            catch (java.io.IOException error) { throw new AssertionError(error); }
        });
        fixture.tool.stop();
    }
}
