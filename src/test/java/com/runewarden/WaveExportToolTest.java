/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Component;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class WaveExportToolTest
{
    @Test public void referenceUrlsAreFixedWikiPagesWithoutAnyGameplayParameters()
    {
        assertEquals(3, WaveExportTool.referenceUrls().size());
        for (String url : WaveExportTool.referenceUrls())
        {
            URI uri = URI.create(url);
            assertEquals("https", uri.getScheme());
            assertEquals("oldschool.runescape.wiki", uri.getHost());
            assertTrue(uri.getPath().endsWith("/Strategies"));
            assertNull(uri.getRawQuery()); assertNull(uri.getRawFragment());
        }
        assertThrows(UnsupportedOperationException.class, () -> WaveExportTool.referenceUrls().clear());
    }

    @Test public void referencesOpenOnlyAfterAnExplicitClickWhileRunning() throws Exception
    {
        List<String> opened = new ArrayList<>();
        WaveExportTool tool = new WaveExportTool(opened::add);
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = tool.createPanel();
            assertSame(panel, tool.createPanel());
            List<JButton> buttons = buttons(panel);
            assertEquals(3, buttons.size());
            for (Component child : panel.getComponents()) assertFalse(child instanceof JCheckBox);
            assertTrue(opened.isEmpty());
            buttons.forEach(button -> assertFalse(button.isEnabled()));
            tool.start();
            assertTrue(opened.isEmpty());
            for (JButton button : buttons) { assertTrue(button.isEnabled()); button.doClick(0); }
            assertEquals(WaveExportTool.referenceUrls(), opened);
            tool.stop();
            for (JButton button : buttons)
            {
                assertFalse(button.isEnabled());
                button.setEnabled(true); button.doClick(0);
            }
            assertEquals(3, opened.size());
            tool.start(); buttons.get(1).doClick(0);
            assertEquals(4, opened.size());
            assertEquals(WaveExportTool.referenceUrls().get(1), opened.get(3));
            tool.stop();
        });
    }

    @Test public void injectedConstructorHasNoDependencyOnAClientSession() throws Exception
    {
        WaveExportTool tool = new WaveExportTool(null, null);
        SwingUtilities.invokeAndWait(() -> {
            tool.start(); assertEquals(3, buttons(tool.createPanel()).size()); tool.stop();
        });
    }

    private static List<JButton> buttons(JPanel panel)
    {
        List<JButton> buttons = new ArrayList<>();
        for (Component child : panel.getComponents()) if (child instanceof JButton) buttons.add((JButton) child);
        return buttons;
    }
}
