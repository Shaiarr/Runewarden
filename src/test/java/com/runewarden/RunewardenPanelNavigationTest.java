/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Component;
import java.awt.Container;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class RunewardenPanelNavigationTest
{
    @Test public void guideItemAndHomeJourneyUsesRealSelectorAndResetsForAccountChanges() throws Exception
    {
        SidebarLayoutTest.Fixture fixtures = new SidebarLayoutTest.Fixture();
        SwingUtilities.invokeAndWait(() -> {
            RunewardenPanel host = new RunewardenPanel(fixtures.get(RunewardenGuides.class),
                fixtures.get(RunewardenSightTool.class), fixtures.get(WaveExportTool.class));
            host.addModule("Command dashboard", new JPanel(), 0);
            host.addModule("Item database", new JPanel());
            JComboBox<?> selector = (JComboBox<?>) named(host, "runewarden.module.selector");
            JButton back = (JButton) named(host, "runewarden.navigation.back");
            JButton next = (JButton) named(host, "runewarden.navigation.forward");
            JButton home = (JButton) named(host, "runewarden.navigation.home");
            host.selectModule("Guides"); host.selectModule("Item database");
            back.doClick(); assertEquals("Guides", selector.getSelectedItem());
            next.doClick(); assertEquals("Item database", selector.getSelectedItem());
            home.doClick(); assertEquals("Command dashboard", selector.getSelectedItem());
            back.doClick(); assertEquals("Item database", selector.getSelectedItem());
            host.resetNavigation(); assertFalse(back.isEnabled()); assertFalse(next.isEnabled());
            home.doClick(); assertEquals("Command dashboard", selector.getSelectedItem());
        });
    }

    private static Component named(Container parent, String name)
    {
        for (Component child : parent.getComponents())
        {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container)
            { Component result = named((Container) child, name); if (result != null) return result; }
        }
        return null;
    }
}
