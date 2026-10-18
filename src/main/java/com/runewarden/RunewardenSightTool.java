/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedObjectSet;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.LinkBrowser;

/** Current collision visibility for a hovered NPC, with an optional manually selected target. */
@Singleton
final class RunewardenSightTool
{
    private static final Color GOLD = new Color(224, 190, 113);
    private static final Color BLACK = new Color(20, 20, 21);
    private static final Color WHITE = new Color(233, 229, 219);
    private final Client client;
    private final RunewardenConfig config;
    private final ClientThread clientThread;
    private final OverlayManager overlayManager;
    private final EventBus eventBus;
    private final RunewardenSightOverlay overlay;
    private final MobKnowledgeCatalog knowledge;
    private volatile boolean started;
    private volatile boolean showOverlay = true;
    private volatile NPC selected;
    private volatile boolean hoverReading;
    private volatile SightReading reading = SightReading.empty("Refresh targets, then choose an NPC.");
    private long epoch;
    private JPanel panel;
    private JComboBox<TargetChoice> targets;
    private JTextArea status;
    private JButton wiki;
    private volatile int lastNpcId = -1;
    private boolean updatingTargets;

    @Inject RunewardenSightTool(Client client, ClientThread clientThread, OverlayManager overlayManager, EventBus eventBus,
        RunewardenConfig config, MobKnowledgeCatalog knowledge, HoverCardOverlay hoverCards)
    {
        this.client = client;
        this.config = config;
        this.clientThread = clientThread;
        this.overlayManager = overlayManager;
        this.eventBus = eventBus;
        this.knowledge = knowledge;
        overlay = new RunewardenSightOverlay(client, this, hoverCards == null ? new HoverCardOverlay(client) : hoverCards);
    }

    RunewardenSightTool(Client client, ClientThread clientThread, OverlayManager overlayManager, EventBus eventBus,
        RunewardenConfig config, MobKnowledgeCatalog knowledge)
    { this(client, clientThread, overlayManager, eventBus, config, knowledge, null); }

    RunewardenSightTool(Client client, ClientThread clientThread, OverlayManager overlayManager, EventBus eventBus, RunewardenConfig config)
    {
        this(client, clientThread, overlayManager, eventBus, config, null);
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BLACK);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 0, 10, 0));
        JPanel controls = SidebarUi.section(null);
        GuideText title = SidebarUi.heading("SIGHT CHECK");
        controls.add(title);
        JButton refresh = SidebarUi.button("Refresh targets", () -> clientThread.invokeLater(this::refreshTargets));
        sidebarButton(refresh);
        controls.add(refresh);
        targets = new JComboBox<>(new TargetChoice[]{TargetChoice.placeholder()});
        targets.setName("runewarden.sight.targets");
        targets.setFont(GuideText.FONT);
        targets.setAlignmentX(0);
        targets.setMinimumSize(new java.awt.Dimension(0, 29));
        targets.setPreferredSize(new java.awt.Dimension(190, 29));
        targets.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 29));
        targets.setForeground(WHITE);
        targets.setBackground(new Color(35, 33, 29));
        targets.setToolTipText("NPCs currently loaded in your player's WorldView.");
        targets.addActionListener(event ->
        {
            if (updatingTargets) return;
            TargetChoice choice = (TargetChoice) targets.getSelectedItem();
            clientThread.invokeLater(() -> select(choice == null ? null : choice.npc));
        });
        controls.add(targets);
        JCheckBox visible = new JCheckBox("Show sight line", showOverlay);
        visible.setFont(GuideText.FONT);
        visible.setAlignmentX(0);
        visible.setOpaque(false);
        visible.setForeground(WHITE);
        visible.addActionListener(event -> showOverlay = visible.isSelected());
        controls.add(visible);
        JButton clear = SidebarUi.button("Clear target", () ->
        {
            updatingTargets = true;
            targets.setSelectedIndex(0);
            updatingTargets = false;
            clientThread.invokeLater(() -> select(null));
        });
        sidebarButton(clear);
        controls.add(clear);
        wiki = SidebarUi.button("NPC Wiki", () ->
        {
            int id = lastNpcId;
            if (id >= 0) LinkBrowser.browse(MobKnowledgeCatalog.wikiLookup(id));
        });
        sidebarButton(wiki);
        wiki.setEnabled(lastNpcId >= 0);
        controls.add(wiki);
        panel.add(controls, BorderLayout.NORTH);
        status = SidebarUi.label(reading.message);
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setOpaque(false);
        status.setForeground(WHITE);
        status.setFont(GuideText.FONT);
        status.setToolTipText("Current collision visibility between actor footprints. Sight alone does not establish attack range or a safe spot.");
        JPanel reference = SidebarUi.section("TARGET REFERENCE"); reference.add(status);
        panel.add(reference, BorderLayout.CENTER);
        SidebarUi.install(panel);
        return panel;
    }

    private static void sidebarButton(JButton button)
    {
        button.setAlignmentX(0); button.setFont(GuideText.FONT);
        button.setMinimumSize(new java.awt.Dimension(0, 30));
        button.setPreferredSize(new java.awt.Dimension(190, 30));
        button.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30));
    }

    void start()
    {
        if (started) return;
        epoch++;
        started = true;
        selected = null;
        lastNpcId = -1;
        publish(SightReading.empty(config.hoverNpcSight() ? hoverPrompt() : "Choose a target"));
        eventBus.register(this);
        overlayManager.add(overlay);
    }

    void stop()
    {
        if (!started) return;
        started = false;
        epoch++;
        selected = null;
        lastNpcId = -1;
        hoverReading = false;
        eventBus.unregister(this);
        overlayManager.remove(overlay);
        publish(SightReading.empty("Sight lab stopped."));
        clearChoices();
    }

    boolean overlayVisible() { return started && showOverlay; }
    SightReading reading() { return reading; }
    boolean isHoverReading() { return hoverReading; }
    boolean hoverActive()
    { return config.hoverNpcSight() && (config.hoverNpcSightAlways() || client.isKeyPressed(KeyCode.KC_SHIFT)); }
    private String hoverPrompt()
    { return config.hoverNpcSightAlways() ? "Hover an NPC" : "Hold Shift and hover an NPC"; }

    /** Called after the native menu is rebuilt; hover never issues a game action. */
    void onClientTick()
    {
        if (!started) return;
        if (!hoverActive())
        {
            if (hoverReading)
            {
                hoverReading = false;
                publish(selected == null ? SightReading.empty(config.hoverNpcSight() ? hoverPrompt() : "Choose an NPC to inspect its sight lines.") : measure(selected));
            }
            return;
        }
        hoverReading = true;
        NPC hovered = hoveredNpc();
        publish(hovered == null ? SightReading.empty(hoverPrompt()) : measure(hovered));
    }

    NPC hoveredNpc()
    {
        if (client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen()) return null;
        Point mouse = client.getMouseCanvasPosition();
        if (mouse == null || mouse.getX() < 0 || mouse.getY() < 0
            || mouse.getX() >= client.getCanvasWidth() || mouse.getY() >= client.getCanvasHeight()) return null;
        MenuEntry[] entries = client.getMenuEntries();
        if (entries == null || entries.length == 0) return null;
        MenuEntry entry = entries[entries.length - 1];
        if (entry == null) return null;
        if (entry.getType() == MenuAction.WALK)
        {
            // Right-click Attack / shift-walk can put WALK above every NPC operation.
            // Only this scene action permits looking beneath the top entry; widgets,
            // objects and ground-item operations must never expose an underlying NPC.
            for (int i = entries.length - 2; i >= 0; i--)
            {
                MenuEntry candidate = entries[i];
                if (candidate != null && isNpcAction(candidate.getType()))
                {
                    NPC npc = npcFromEntry(candidate);
                    if (npc != null) return npc;
                }
            }
            return null;
        }
        return isNpcAction(entry.getType()) ? npcFromEntry(entry) : null;
    }

    private NPC npcFromEntry(MenuEntry entry)
    {
        NPC npc = entry.getNpc();
        if (npc != null) return npc;
        WorldView view = client.getWorldView(entry.getWorldViewId());
        if (view == null) return null;
        IndexedObjectSet<? extends NPC> npcs = view.npcs();
        return npcs == null ? null : npcs.byIndex(entry.getIdentifier());
    }

    private static boolean isNpcAction(MenuAction action)
    {
        return action == MenuAction.NPC_FIRST_OPTION || action == MenuAction.NPC_SECOND_OPTION
            || action == MenuAction.NPC_THIRD_OPTION || action == MenuAction.NPC_FOURTH_OPTION
            || action == MenuAction.NPC_FIFTH_OPTION || action == MenuAction.EXAMINE_NPC
            || action == MenuAction.WIDGET_TARGET_ON_NPC || action == MenuAction.ITEM_USE_ON_NPC;
    }

    private WorldView playerWorldView(Player player)
    {
        WorldView view = player == null ? null : player.getWorldView();
        return view == null ? client.getTopLevelWorldView() : view;
    }

    /** Called on the client thread, only after an explicit refresh button press. */
    void refreshTargets()
    {
        if (!started) return;
        Player player = client.getLocalPlayer();
        if (client.getGameState() != GameState.LOGGED_IN || player == null)
        {
            selected = null;
            publish(SightReading.empty("Log in to choose a target."));
            clearChoices();
            return;
        }
        WorldView view = playerWorldView(player);
        List<TargetChoice> choices = new ArrayList<>();
        if (view != null && view.npcs() != null)
        {
            for (NPC npc : view.npcs())
            {
                if (npc != null && !npc.isDead() && npc.getName() != null && !npc.getName().trim().isEmpty()
                    && (npc.getWorldView() == null || npc.getWorldView() == view))
                {
                    choices.add(new TargetChoice(npc, cleanName(npc.getName()) + " [" + npc.getIndex() + "]"));
                }
            }
        }
        choices.sort(Comparator.comparing(choice -> choice.label));
        if (choices.size() > 256) choices = new ArrayList<>(choices.subList(0, 256));
        final List<TargetChoice> options = choices;
        final NPC previous = selected;
        final long expectedEpoch = epoch;
        SwingUtilities.invokeLater(() ->
        {
            if (!started || epoch != expectedEpoch || targets == null) return;
            DefaultComboBoxModel<TargetChoice> model = new DefaultComboBoxModel<>();
            model.addElement(TargetChoice.placeholder());
            TargetChoice keep = null;
            for (TargetChoice choice : options) { model.addElement(choice); if (choice.npc == previous) keep = choice; }
            updatingTargets = true;
            targets.setModel(model);
            targets.setSelectedItem(keep == null ? model.getElementAt(0) : keep);
            updatingTargets = false;
        });
        if (selected != null && choices.stream().noneMatch(choice -> choice.npc == selected)) selected = null;
        if (hoverActive()) return;
        publish(selected == null ? SightReading.empty(choices.isEmpty() ? "No loaded NPC targets in this view." : "Choose an NPC to inspect its sight lines.") : measure(selected));
    }

    void select(NPC npc)
    {
        if (!started) return;
        selected = npc;
        if (hoverActive()) return;
        hoverReading = false;
        publish(npc == null ? SightReading.empty("Choose an NPC to inspect its sight lines.") : measure(npc));
    }

    @Subscribe public void onGameTick(GameTick event)
    {
        if (!started || selected == null || hoverActive()) return;
        SightReading next = measure(selected);
        if (next.target == null) selected = null;
        publish(next);
    }

    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (!started || event.getGameState() == GameState.LOGGED_IN) return;
        selected = null;
        lastNpcId = -1;
        publish(SightReading.empty("Refresh targets after the scene loads."));
        clearChoices();
    }

    SightReading measure(NPC npc)
    {
        Player player = client.getLocalPlayer();
        if (client.getGameState() != GameState.LOGGED_IN || player == null || npc == null) return SightReading.empty("Log in and choose a target.");
        WorldView view = playerWorldView(player);
        if (view == null || (npc.getWorldView() != null && npc.getWorldView() != view)) return SightReading.empty("Target is outside your current WorldView.");
        IndexedObjectSet<? extends NPC> npcs = view.npcs();
        if (npcs == null || npcs.byIndex(npc.getIndex()) != npc || npc.isDead()) return SightReading.empty("Target left the scene. Refresh targets.");
        WorldArea playerArea = player.getWorldArea(), targetArea = npc.getWorldArea();
        if (playerArea == null || targetArea == null || playerArea.getPlane() != targetArea.getPlane()) return SightReading.empty("Target is on another plane or its position is unavailable.");
        if (view.getCollisionMaps() == null) return SightReading.empty("Scene collision data is loading.");
        try
        {
            // WorldArea explicitly documents that these directions can differ.
            boolean playerToNpc = playerArea.hasLineOfSightTo(view, targetArea);
            boolean npcToPlayer = targetArea.hasLineOfSightTo(view, playerArea);
            int distance = playerArea.distanceTo(targetArea);
            String name = cleanName(npc.getName());
            NPCComposition composition = npc.getTransformedComposition();
            int id = composition == null ? npc.getId() : composition.getId();
            lastNpcId = id;
            MobKnowledgeCatalog.Card facts = knowledge == null || !config.hoverMobFacts() ? null : knowledge.describe(id, name);
            String message = name + "\n\nYour sight → target: " + (playerToNpc ? "CLEAR" : "BLOCKED")
                + "\nTarget sight → you: " + (npcToPlayer ? "CLEAR" : "BLOCKED") + "\nFootprint distance: " + distance + " tiles";
            if (facts != null) message += "\n\n" + facts.variant + "\n" + String.join("\n", facts.lines);
            return new SightReading(npc, player, name, playerToNpc, npcToPlayer, distance, message, facts);
        }
        catch (IndexOutOfBoundsException | NullPointerException exception)
        {
            return SightReading.empty("Scene collision data is changing. Refresh targets.");
        }
    }

    private void publish(SightReading next)
    {
        SightReading previous = reading;
        reading = next;
        if (previous.message.equals(next.message)) return;
        SwingUtilities.invokeLater(() ->
        {
            if (reading != next) return;
            if (status != null) status.setText(next.message);
            if (wiki != null) wiki.setEnabled(lastNpcId >= 0);
        });
    }

    private void clearChoices()
    {
        final long expectedEpoch = epoch;
        SwingUtilities.invokeLater(() ->
        {
            if (targets == null || epoch != expectedEpoch) return;
            updatingTargets = true;
            targets.setModel(new DefaultComboBoxModel<>(new TargetChoice[]{TargetChoice.placeholder()}));
            updatingTargets = false;
        });
    }

    static String cleanName(String raw)
    {
        String value = raw == null ? "NPC" : raw.replaceAll("<[^>]*>", "").trim();
        return value.isEmpty() ? "NPC" : value.substring(0, Math.min(60, value.length()));
    }

    static final class TargetChoice
    {
        final NPC npc;
        final String label;
        TargetChoice(NPC npc, String label) { this.npc = npc; this.label = label; }
        static TargetChoice placeholder() { return new TargetChoice(null, "Choose a target…"); }
        @Override public String toString() { return label; }
    }

    static final class SightReading
    {
        final NPC target;
        final Player player;
        final String targetName;
        final boolean playerToNpc;
        final boolean npcToPlayer;
        final int distance;
        final String message;
        final MobKnowledgeCatalog.Card knowledge;
        SightReading(NPC target, Player player, String targetName, boolean playerToNpc, boolean npcToPlayer, int distance, String message)
        {
            this(target, player, targetName, playerToNpc, npcToPlayer, distance, message, null);
        }
        SightReading(NPC target, Player player, String targetName, boolean playerToNpc, boolean npcToPlayer, int distance, String message,
            MobKnowledgeCatalog.Card knowledge)
        {
            this.target = target; this.player = player; this.targetName = targetName; this.playerToNpc = playerToNpc; this.npcToPlayer = npcToPlayer; this.distance = distance; this.message = message; this.knowledge = knowledge;
        }
        static SightReading empty(String message) { return new SightReading(null, null, "", false, false, 0, message); }
    }
}
