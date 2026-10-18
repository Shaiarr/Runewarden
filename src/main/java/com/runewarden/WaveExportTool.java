/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.LinkBrowser;

/** Manual links to ordinary written guides. No gameplay state is read or exported. */
@Singleton
final class WaveExportTool
{
    private static final List<String> REFERENCES = Collections.unmodifiableList(Arrays.asList(
        "https://oldschool.runescape.wiki/w/TzHaar_Fight_Cave/Strategies",
        "https://oldschool.runescape.wiki/w/Inferno/Strategies",
        "https://oldschool.runescape.wiki/w/Fortis_Colosseum/Strategies"));
    private final Consumer<String> browser;
    private final List<JButton> buttons = new ArrayList<>();
    private volatile boolean running;
    private volatile long epoch;
    private JPanel panel;

    @Inject WaveExportTool(Client client, ClientThread clientThread)
    { this(LinkBrowser::browse); }

    WaveExportTool(Consumer<String> browser) { this.browser = browser; }

    static List<String> referenceUrls() { return REFERENCES; }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column();
        panel.add(SidebarUi.heading("WAVE REFERENCES"));
        addReference("Fight Caves guide ↗", 0);
        addReference("Inferno guide ↗", 1);
        addReference("Colosseum guide ↗", 2);
        return panel;
    }

    private void addReference(String title, int index)
    {
        JButton button = SidebarUi.button(title, () -> {
            if (running) browser.accept(REFERENCES.get(index));
        });
        button.setName("runewarden.wave.reference." + index);
        button.setEnabled(running);
        buttons.add(button);
        panel.add(button);
    }

    void start() { running = true; refreshButtons(++epoch); }
    void stop() { running = false; refreshButtons(++epoch); }

    private void refreshButtons(long expected)
    {
        Runnable action = () -> {
            if (expected == epoch) buttons.forEach(button -> button.setEnabled(running));
        };
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
