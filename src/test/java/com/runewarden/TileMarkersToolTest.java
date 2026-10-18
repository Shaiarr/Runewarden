/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class TileMarkersToolTest
{
    private static final GuideCatalog CATALOG = new GuideCatalog(new Gson());

    @Test public void categoriesHaveBackNavigationAndNeverCreateTheThousandItemDropdown() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            fixture.load();
            SwingUtilities.invokeAndWait(() -> {
                AtomicInteger dashboard = new AtomicInteger();
                fixture.tool.setBackAction(dashboard::incrementAndGet);
                assertFalse(descendants(fixture.panel).stream().anyMatch(c -> c instanceof JComboBox));
                fixture.click("category.BOSSES");
                assertNull(fixture.overlay.selectedPack());
                fixture.click("back");
                assertNotNull(fixture.find("category.WAVES"));
                fixture.click("back");
                assertEquals(1, dashboard.get());
            });
        }
        finally { fixture.stop(); }
    }

    @Test public void rotationPagesLoadExplicitlyAndBackPreservesThePageAndSearch() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            fixture.load();
            SwingUtilities.invokeAndWait(() -> {
                fixture.click("category.WAVES");
                fixture.click("fightcaves");
                fixture.click("group.fight-caves-r1");
                AtomicInteger target = new AtomicInteger();
                fixture.tool.setRotationAction(target::set);
                fixture.click("waitrotation");
                assertEquals(1, target.get());
                assertNull("Choosing an entrance target does not load markers", fixture.overlay.selectedPack());
                JList<?> list = fixture.list();
                assertEquals(TileMarkersTool.PAGE_SIZE, list.getModel().getSize());
                assertEquals(48, list.getFixedCellHeight());
                assertEquals("fight-caves-r1-w1", ((GuideCatalog.MarkerPack) list.getModel().getElementAt(0)).id);
                fixture.click("next");
                assertEquals("fight-caves-r1-w13", ((GuideCatalog.MarkerPack) list.getModel().getElementAt(0)).id);
                list.setSelectedIndex(2);
                fixture.click("open");
                assertNull("Opening details must not load a pack", fixture.overlay.selectedPack());
                fixture.click("load");
                assertEquals("fight-caves-r1-w15", fixture.overlay.selectedPack().id);
                assertTrue(((GuideText) fixture.find("active")).getText().contains("rotation 1"));
                fixture.click("back");
                assertEquals("fight-caves-r1-w13", ((GuideCatalog.MarkerPack) fixture.list().getModel().getElementAt(0)).id);
                assertEquals("fight-caves-r1-w15", ((GuideCatalog.MarkerPack) fixture.list().getSelectedValue()).id);
                ((JTextField) fixture.find("search")).setText("w63");
                assertEquals(1, fixture.list().getModel().getSize());
                fixture.list().setSelectedIndex(0);
                fixture.click("open"); fixture.click("load"); fixture.click("back");
                assertEquals("w63", ((JTextField) fixture.find("search")).getText());
                assertEquals(1, fixture.list().getModel().getSize());
                fixture.click("back"); fixture.click("back"); fixture.click("back");
                assertNotNull(fixture.find("category.BOSSES"));
                assertEquals("fight-caves-r1-w63", fixture.overlay.selectedPack().id);
                ((JCheckBox) fixture.find("visibility")).doClick(0);
                assertFalse(fixture.overlay.isVisible());
                fixture.click("clear");
                assertNull(fixture.overlay.selectedPack());
            });
        }
        finally { fixture.stop(); }
    }

    @Test public void emptyBriefingPacksCannotReplaceLoadedMarkers() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            fixture.load();
            SwingUtilities.invokeAndWait(() -> {
                fixture.overlay.select(CATALOG.pack("sarachnis"));
                fixture.click("category.WAVES");
                fixture.click("group.inferno");
                JList<?> list = fixture.list();
                for (int i = 0; i < list.getModel().getSize(); i++)
                    if ("inferno-jad-reference".equals(((GuideCatalog.MarkerPack) list.getModel().getElementAt(i)).id))
                        list.setSelectedIndex(i);
                assertNotNull(list.getSelectedValue());
                fixture.click("open");
                JButton load = (JButton) fixture.find("load");
                assertFalse(load.isEnabled());
                load.doClick(0);
                assertEquals("sarachnis", fixture.overlay.selectedPack().id);
            });
        }
        finally { fixture.stop(); }
    }

    @Test public void openingPacksHaveOnlyManualLoadingAndStoppedControlsCannotWrite() throws Exception
    {
        Fixture fixture = new Fixture();
        try
        {
            fixture.load();
            SwingUtilities.invokeAndWait(() -> {
                fixture.click("category.WAVES");
                fixture.click("group.inferno");
                JList<?> list = fixture.list();
                for (int i = 0; i < list.getModel().getSize(); i++)
                    if ("inferno-opening-low".equals(((GuideCatalog.MarkerPack) list.getModel().getElementAt(i)).id))
                        list.setSelectedIndex(i);
                fixture.click("open");
                assertNull("No automatic entry control is exposed", fixture.find("armentry"));
                assertNull("Browsing never loads markers", fixture.overlay.selectedPack());
                fixture.click("load");
                assertEquals("inferno-opening-low", fixture.overlay.selectedPack().id);
                fixture.overlay.select(null);
                fixture.tool.stop();
                ((JButton) fixture.find("load")).doClick(0);
                assertNull(fixture.overlay.selectedPack());
            });
        }
        finally { fixture.stop(); }
    }

    @Test public void loadingRunsOffTheEventThreadAndStoppedWorkersCannotPublish() throws Exception
    {
        GuideMarkerOverlay overlay = new GuideMarkerOverlay(mock(Client.class));
        AtomicInteger calls = new AtomicInteger();
        AtomicBoolean loadedOnEventThread = new AtomicBoolean();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), returned = new CountDownLatch(1);
        TileMarkersTool tool = new TileMarkersTool(overlay, () -> {
            loadedOnEventThread.set(SwingUtilities.isEventDispatchThread());
            if (calls.incrementAndGet() == 1)
            {
                entered.countDown();
                try { release.await(3, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                finally { returned.countDown(); }
            }
            return new TileMarkerIndex(CATALOG);
        });
        JComponent[] panel = new JComponent[1];
        try
        {
            SwingUtilities.invokeAndWait(() -> { tool.start(); panel[0] = tool.createPanel(); tool.ensureLoaded(); });
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertFalse(loadedOnEventThread.get());
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(descendants(panel[0]).stream().anyMatch(c -> "runewarden.tilemarkers.category.BOSSES".equals(c.getName())));
                tool.stop();
            });
            release.countDown();
            assertTrue(returned.await(3, TimeUnit.SECONDS));
            SwingUtilities.invokeAndWait(() -> {
                assertFalse(descendants(panel[0]).stream().anyMatch(c -> "runewarden.tilemarkers.category.BOSSES".equals(c.getName())));
                tool.start(); tool.ensureLoaded();
            });
            awaitCategory(panel[0]);
            SwingUtilities.invokeAndWait(tool::ensureLoaded);
            assertEquals("A loaded index is reused", 2, calls.get());
        }
        finally { release.countDown(); SwingUtilities.invokeAndWait(tool::stop); }
    }

    private static void awaitCategory(Container panel) throws Exception
    {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        AtomicBoolean ready = new AtomicBoolean();
        while (!ready.get() && System.nanoTime() < deadline)
        {
            SwingUtilities.invokeAndWait(() -> ready.set(descendants(panel).stream()
                .anyMatch(c -> "runewarden.tilemarkers.category.BOSSES".equals(c.getName()))));
            if (!ready.get()) Thread.sleep(10);
        }
        assertTrue("Marker categories must become available", ready.get());
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

    private static final class Fixture
    {
        final GuideMarkerOverlay overlay = new GuideMarkerOverlay(mock(Client.class));
        final TileMarkersTool tool = new TileMarkersTool(CATALOG, overlay);
        JComponent panel;
        void load() throws Exception
        {
            SwingUtilities.invokeAndWait(() -> { tool.start(); panel = tool.createPanel(); tool.ensureLoaded(); });
            awaitCategory(panel);
        }
        Component find(String suffix)
        {
            return descendants(panel).stream().filter(c -> ("runewarden.tilemarkers." + suffix).equals(c.getName())).findFirst().orElse(null);
        }
        void click(String suffix)
        {
            JButton button = (JButton) find(suffix);
            assertNotNull(suffix, button);
            assertTrue(suffix, button.isEnabled());
            button.doClick(0);
        }
        JList<?> list() { return (JList<?>) find("packs"); }
        void stop() throws Exception { SwingUtilities.invokeAndWait(tool::stop); }
    }
}
