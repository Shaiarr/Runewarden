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
    @Test public void retainedPagesUseRealHistoryAndResetForAccountChanges() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            RunewardenPanel host = new RunewardenPanel();
            host.addModule("Overview",new JPanel());
            host.addModule("Harvest runs",new JPanel());
            host.addModule("HUD settings",new JPanel());
            JComboBox<?> selector = (JComboBox<?>) named(host, "runewarden.module.selector");
            JButton back = (JButton) named(host, "runewarden.navigation.back");
            JButton next = (JButton) named(host, "runewarden.navigation.forward");
            JButton home = (JButton) named(host, "runewarden.navigation.home");
            host.selectModule("Harvest runs"); host.selectModule("HUD settings");
            back.doClick(); assertEquals("Harvest runs", selector.getSelectedItem());
            next.doClick(); assertEquals("HUD settings", selector.getSelectedItem());
            home.doClick(); assertEquals("Overview", selector.getSelectedItem());
            back.doClick(); assertEquals("HUD settings", selector.getSelectedItem());
            host.resetNavigation(); assertFalse(back.isEnabled()); assertFalse(next.isEnabled());
            home.doClick(); assertEquals("Overview", selector.getSelectedItem());
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
