/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.GameObject;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.game.ItemManager;

/** Compact farm preparation and checklist; all account mutations stay in the client-thread observer. */
@Singleton
final class FarmRunsTool
{
    private final FarmPatchPlanner planner;
    private final ItemManager items;
    private volatile boolean started;
    private boolean refreshing;
    private Timer timer;
    private JPanel panel, preparation, currentStop, stopCard, stops, supplies, compostField;
    private GuideText phase, status, route, cropLabel, quantityLabel, currentTitle, currentState, preparationNotes;
    private JProgressBar progress;
    private boolean allLocations;
    private JLabel icon;
    private int shownIcon;
    private JComboBox<FarmPatchPlanner.RunType> runs;
    private JComboBox<FarmPatchPlanner.CropChoice> crops;
    private JComboBox<Compost> compost;
    private JSpinner quantity;
    private JButton start, end, done, skip, locations;
    private FarmPatchPlanner.RunReading view, currentStopView;
    private final Map<String, StopRow> stopRows = new LinkedHashMap<>();
    private List<String> stopOrder = new ArrayList<>();
    private List<Integer> cropIds = new ArrayList<>(), supplyIds = new ArrayList<>();
    private final List<GuideText> supplyText = new ArrayList<>();
    private JPanel summaryPanel;
    private GuideText summaryStatus;
    private JProgressBar summaryProgress;
    private final Map<String, JButton> quickRuns = new LinkedHashMap<>();
    private Runnable openAction = () -> {};

    @Inject FarmRunsTool(FarmPatchPlanner planner, ItemManager items) { this.planner = planner; this.items = items; }
    void setOpenAction(Runnable action) { openAction = action == null ? () -> {} : action; }
    JPanel createSummaryPanel()
    {
        if (summaryPanel != null) return summaryPanel;
        summaryPanel = SidebarUi.section("HARVEST RUN");
        summaryStatus = SidebarUi.label(""); summaryStatus.setName("runewarden.farm-summary.status"); summaryPanel.add(summaryStatus);
        summaryProgress = progressBar(); summaryPanel.add(summaryProgress);
        for (String[] entry : new String[][] {{"herb", "Herb"}, {"birdhouse", "Birdhouse"}, {"tree", "Tree"}, {"fruit", "Fruit tree"}})
        {
            String id = entry[0]; JButton button = SidebarUi.button(entry[1], () -> openPreparation(id));
            button.setName("runewarden.farm-summary." + id); quickRuns.put(id, button);
        }
        summaryPanel.add(SidebarUi.row(quickRuns.get("herb"), quickRuns.get("birdhouse")));
        summaryPanel.add(SidebarUi.row(quickRuns.get("tree"), quickRuns.get("fruit")));
        refresh(); return summaryPanel;
    }
    private void openPreparation(String id)
    {
        FarmPatchPlanner.RunReading expected = planner.currentRun();
        if (expected == null || expected.active && !id.equals(expected.runId)) return;
        planner.withView(expected, () ->
        {
            if (!expected.active) planner.selectRun(id);
            SwingUtilities.invokeLater(() -> { if (started) { refresh(); openAction.run(); } });
        });
    }
    private void refreshSummary(FarmPatchPlanner.RunReading next)
    {
        if (summaryPanel == null) return;
        summaryStatus.setText(next == null ? "Log in to prepare a run" : next.active
            ? next.title + "\nNext · " + next.nextStopLabel : "Prepare herb, birdhouse or tree runs");
        summaryProgress.setVisible(next != null && next.active);
        if (next != null)
        {
            refreshProgress(summaryProgress, next);
        }
        quickRuns.forEach((id, button) -> button.setEnabled(next != null && (!next.active || id.equals(next.runId))));
    }
    void setNavigation(HarvestNavigation navigation) { planner.setNavigation(navigation); }
    void onMenuOptionClicked(MenuOptionClicked event) { if (started) planner.onMenuOptionClicked(event); }
    void onChatMessage(ChatMessage event) { if (started) planner.onChatMessage(event); }
    void onGameTick() { if (started) planner.onGameTick(); }
    void onSceneLoading() { if (started) planner.onSceneLoading(); }
    void onGameObjectChanged(GameObject object) { if (started) planner.onGameObjectChanged(object); }
    void clear() { planner.clear(); SwingUtilities.invokeLater(this::refresh); }
    void start()
    {
        if (started) return;
        started = true; planner.start();
        SwingUtilities.invokeLater(() ->
        {
            if (!started) return;
            if (timer != null) timer.stop();
            timer = new Timer(1000, event -> refresh()); timer.start(); refresh();
        });
    }
    void stop()
    {
        started = false; planner.stop();
        SwingUtilities.invokeLater(() -> { if (timer != null) timer.stop(); timer = null; refresh(); });
    }
    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column(); panel.add(SidebarUi.heading("FARM RUNS"));
        icon = new JLabel(); panel.add(icon);
        phase = named(SidebarUi.heading("PREPARE"), "phase"); panel.add(phase);
        status = SidebarUi.label(" "); panel.add(status);
        progress = named(progressBar(), "progress"); panel.add(progress);
        preparation = named(SidebarUi.section("PREPARATION"), "preparation"); panel.add(preparation);
        runs = named(new JComboBox<>(FarmPatchPlanner.runTypes().toArray(new FarmPatchPlanner.RunType[0])), "selector");
        runs.addActionListener(event ->
        {
            FarmPatchPlanner.RunType selected = (FarmPatchPlanner.RunType) runs.getSelectedItem();
            if (selected != null) edit(() -> planner.selectRun(selected.id));
        });
        preparation.add(SidebarUi.field("Run", runs));
        crops = named(new JComboBox<>(), "crop");
        crops.addActionListener(event ->
        {
            FarmPatchPlanner.CropChoice crop = (FarmPatchPlanner.CropChoice) crops.getSelectedItem();
            if (crop != null) edit(() -> planner.chooseSeed(crop.id));
        });
        cropLabel = named(SidebarUi.label("Seed"), "crop-label");
        cropLabel.setForeground(SidebarUi.MUTED); preparation.add(cropLabel); preparation.add(crops);
        quantity = named(new JSpinner(new SpinnerNumberModel(0, 0, 100, 1)), "quantity");
        quantity.addChangeListener(event ->
        { int selected = ((Number) quantity.getValue()).intValue(); edit(() -> planner.setQuantity(selected)); });
        quantityLabel = named(SidebarUi.label("Seeds per patch"), "quantity-label");
        quantityLabel.setForeground(SidebarUi.MUTED); preparation.add(quantityLabel); preparation.add(quantity);
        compost = named(new JComboBox<>(Compost.values()), "compost");
        compost.addActionListener(event ->
        { Compost selected = (Compost) compost.getSelectedItem(); if (selected != null) edit(() -> planner.setCompost(selected.id)); });
        compostField = SidebarUi.field("Compost", compost); preparation.add(compostField);
        currentStop = named(SidebarUi.section("CURRENT LOCATION"), "current-stop");
        currentTitle = named(SidebarUi.heading(""), "current-title");
        currentState = named(SidebarUi.label(""), "current-state");
        route = named(SidebarUi.label(" "), "route");
        currentStop.add(currentTitle); currentStop.add(currentState); currentStop.add(route);
        done = named(SidebarUi.button("Done", () -> checkCurrent(false)), "current-done");
        skip = named(SidebarUi.button("Skip", () -> checkCurrent(true)), "current-skip");
        currentStop.add(SidebarUi.row(done, skip)); panel.add(currentStop);
        start = SidebarUi.button("Start run", () -> edit(planner::startSelectedRun));
        end = SidebarUi.button("End run", () -> edit(planner::stopActiveRun));
        panel.add(SidebarUi.row(start, end));
        JPanel supplyCard = SidebarUi.section("SUPPLIES"); stopCard = named(SidebarUi.section("LOCATIONS"), "locations");
        locations = named(SidebarUi.button("Show all locations", () -> { allLocations = !allLocations; refresh(); }), "show-locations");
        supplies = SidebarUi.column(); stops = SidebarUi.column(); supplyCard.add(supplies); stopCard.add(stops);
        preparationNotes = named(SidebarUi.label(""), "preparation-notes");
        preparationNotes.setForeground(SidebarUi.MUTED); supplyCard.add(preparationNotes);
        panel.add(supplyCard); panel.add(locations); panel.add(stopCard); refresh(); return panel;
    }
    private static <T extends JComponent> T named(T component, String suffix)
    {
        component.setName("runewarden.farm-runs." + suffix); return component;
    }
    private static JProgressBar progressBar()
    {
        JProgressBar bar = new JProgressBar(); bar.setStringPainted(true);
        bar.setForeground(SidebarUi.GOLD); bar.setBackground(SidebarUi.BACKGROUND);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20)); return bar;
    }
    private static void refreshProgress(JProgressBar bar, FarmPatchPlanner.RunReading run)
    {
        bar.setMaximum(Math.max(1, run.totalStops)); bar.setValue(run.completedStops);
        bar.setString(run.completedStops + " / " + run.totalStops + " locations");
    }
    private static String stopStatus(FarmPatchPlanner.StopReading stop)
    {
        return stop.status + (stop.unlock == null || stop.unlock.isBlank() ? "" : "\n" + stop.unlock);
    }
    private void edit(Runnable action)
    {
        if (refreshing || view == null) return;
        apply(view, action);
    }
    private void apply(FarmPatchPlanner.RunReading expected, Runnable action)
    {
        planner.withView(expected, () -> { action.run(); SwingUtilities.invokeLater(this::refresh); });
    }
    private void checkCurrent(boolean skipped)
    {
        FarmPatchPlanner.RunReading expected = currentStopView;
        if (expected != null && expected.active && expected.nextStopId != null)
            apply(expected, () -> planner.checkNext(expected.nextStopId, skipped));
    }
    private static boolean same(FarmPatchPlanner.RunReading first, FarmPatchPlanner.RunReading second)
    {
        return first != null && second != null && Objects.equals(first.profile, second.profile)
            && Objects.equals(first.runId, second.runId);
    }
    void refresh()
    {
        if (panel == null && summaryPanel == null) return;
        FarmPatchPlanner.RunReading next = planner.currentRun();
        refreshSummary(next);
        if (panel == null) return;
        boolean changed = !same(view, next);
        if (changed || next != null && next.active && (view == null || !view.active)) allLocations = false;
        view = next;
        refreshing = true;
        try
        {
            boolean editing = next != null && !next.active;
            boolean active = next != null && next.active;
            runs.setEnabled(editing); crops.setEnabled(editing); quantity.setEnabled(editing); compost.setEnabled(editing);
            start.setEnabled(editing && next.totalStops > 0); end.setEnabled(next != null && next.active);
            preparation.setVisible(!active); currentStop.setVisible(active); start.setVisible(!active); end.setVisible(active);
            locations.setVisible(active); locations.setText(allLocations ? "Hide all locations" : "Show all locations");
            stopCard.setVisible(!active || allLocations); progress.setVisible(next != null);
            phase.setText(active ? "ON THE RUN" : "PREPARE");
            currentStopView = active ? next : null;
            if (next == null)
            {
                status.setText("Log in to prepare a farm run."); route.setText(""); icon.setIcon(null); shownIcon = 0;
                preparationNotes.setText("");
                if (changed) { stops.removeAll(); supplies.removeAll(); stopRows.clear(); stopOrder.clear(); supplyIds.clear(); }
                return;
            }
            status.setText(next.title + "\n" + next.status);
            refreshProgress(progress, next);
            boolean birdhouse = "birdhouse".equals(next.runId);
            boolean tree = "tree".equals(next.runId) || "fruit".equals(next.runId) || "hardwood".equals(next.runId);
            cropLabel.setText(birdhouse ? "Birdhouse type" : tree ? "Sapling" : "Seed");
            quantityLabel.setText(birdhouse ? "Birdhouses per location" : tree ? "Saplings per patch" : "Seeds per patch");
            quantityLabel.setVisible(!birdhouse && !tree); quantity.setVisible(!birdhouse && !tree);
            crops.getAccessibleContext().setAccessibleName(cropLabel.getText());
            quantity.getAccessibleContext().setAccessibleName(quantityLabel.getText());
            compostField.setVisible(!birdhouse);
            preparationNotes.setText(String.join("\n", next.preparationNotes));
            route.setText(next.routeAdvice == null ? "" : next.routeAdvice);
            FarmPatchPlanner.StopReading current = next.stops.stream().filter(stop -> stop.id.equals(next.nextStopId)).findFirst().orElse(null);
            currentTitle.setText(current == null ? "" : current.label);
            currentState.setText(current == null ? "" : stopStatus(current));
            done.setEnabled(active && current != null && !current.done); skip.setEnabled(done.isEnabled());
            if (!runs.isPopupVisible())
                for (int i = 0; i < runs.getItemCount(); i++) if (runs.getItemAt(i).id.equals(next.runId)) runs.setSelectedIndex(i);
            List<FarmPatchPlanner.CropChoice> choices = planner.cropChoices();
            List<Integer> ids = new ArrayList<>(); for (FarmPatchPlanner.CropChoice crop : choices) ids.add(crop.id);
            if (!crops.isPopupVisible())
            {
                if (!cropIds.equals(ids))
                { crops.removeAllItems(); for (FarmPatchPlanner.CropChoice crop : choices) crops.addItem(crop); cropIds = ids; }
                for (int i = 0; i < crops.getItemCount(); i++) if (crops.getItemAt(i).id == next.seedItemId) crops.setSelectedIndex(i);
            }
            boolean typing = quantity.isFocusOwner() || quantity.getEditor().isFocusOwner()
                || quantity.getEditor() instanceof JSpinner.DefaultEditor && ((JSpinner.DefaultEditor) quantity.getEditor()).getTextField().isFocusOwner();
            if (!typing)
            {
                ((SpinnerNumberModel) quantity.getModel()).setMinimum(next.quantity < 1 ? 0 : 1);
                quantity.setValue(Math.max(0, Math.min(100, next.quantity)));
            }
            if (!compost.isPopupVisible()) for (Compost value : Compost.values()) if (value.id == next.compostItemId) compost.setSelectedItem(value);
            FarmPatchPlanner.RunType type = (FarmPatchPlanner.RunType) runs.getSelectedItem();
            int image = next.seedItemId > 0 ? next.seedItemId : type == null ? 0 : type.iconId;
            if (items != null && image > 0 && shownIcon != image)
            { shownIcon = image; icon.setIcon(new ImageIcon(items.getImage(image))); }
            refreshStops(next, changed); refreshSupplies(next, changed);
        }
        finally { refreshing = false; panel.revalidate(); panel.repaint(); }
    }
    private void refreshStops(FarmPatchPlanner.RunReading next, boolean changed)
    {
        List<String> order = new ArrayList<>(); for (FarmPatchPlanner.StopReading stop : next.stops) order.add(stop.id);
        if (changed || !order.equals(stopOrder))
        {
            stops.removeAll(); stopRows.clear(); stopOrder = order;
            for (FarmPatchPlanner.StopReading stop : next.stops)
            {
                StopRow row = new StopRow(stop, next); stopRows.put(stop.id, row); stops.add(row.panel);
            }
        }
        for (FarmPatchPlanner.StopReading stop : next.stops)
        {
            StopRow row = stopRows.get(stop.id);
            row.expected = next; row.panel.setVisible(!next.active || stop.selected);
            row.include.setSelected(stop.selected); row.include.setEnabled(!next.active);
            row.up.setEnabled(!next.active); row.down.setEnabled(!next.active);
            row.order.setVisible(!next.active);
            row.state.setText((stop.done ? "Done · " : "") + stopStatus(stop));
        }
    }
    private final class StopRow
    {
        final JPanel panel = SidebarUi.column(); final JCheckBox include;
        final GuideText state = SidebarUi.label(""); final JButton up, down;
        final JPanel order;
        FarmPatchPlanner.RunReading expected;
        StopRow(FarmPatchPlanner.StopReading stop, FarmPatchPlanner.RunReading expected)
        {
            this.expected = expected;
            include = new JCheckBox(stop.label); include.setOpaque(false); include.setForeground(SidebarUi.TEXT);
            include.addActionListener(event ->
            { boolean selected = include.isSelected(); if (!refreshing) apply(this.expected, () -> planner.includeStop(stop.id, selected)); });
            up = SidebarUi.button("↑", () -> apply(this.expected, () -> planner.moveStop(stop.id, -1)));
            down = SidebarUi.button("↓", () -> apply(this.expected, () -> planner.moveStop(stop.id, 1)));
            panel.add(include); panel.add(state);
            order = SidebarUi.row(up, down); panel.add(order);
        }
    }
    private void refreshSupplies(FarmPatchPlanner.RunReading next, boolean changed)
    {
        List<Integer> ids = new ArrayList<>(); for (FarmPatchPlanner.SupplyReading supply : next.supplies) ids.add(supply.itemId);
        if (changed || !ids.equals(supplyIds))
        {
            supplies.removeAll(); supplyText.clear(); supplyIds = ids;
            for (FarmPatchPlanner.SupplyReading supply : next.supplies)
            {
                JLabel image = new JLabel(); if (items != null) items.getImage(supply.itemId).addTo(image);
                GuideText text = SidebarUi.label(""); supplyText.add(text);
                JPanel row = new JPanel(new BorderLayout(5, 0)); row.setOpaque(false);
                row.add(image, BorderLayout.WEST); row.add(text, BorderLayout.CENTER); supplies.add(row);
            }
        }
        for (int i = 0; i < next.supplies.size(); i++)
        {
            FarmPatchPlanner.SupplyReading supply = next.supplies.get(i);
            supplyText.get(i).setText(supply.name + "\n" + supply.carried + " / " + supply.required + " carried"
                + (supply.missing > 0 ? " · need " + supply.missing : "") + (supply.note.isEmpty() ? "" : "\n" + supply.note));
        }
    }
    private static String runGroup(String group)
    { return "fruit-tree".equals(group) ? "fruit" : "flower".equals(group) ? "allotment" : "bird-house".equals(group) ? "birdhouse" : group; }
    static boolean supportsGroup(String group)
    { String id=runGroup(group);return FarmPatchPlanner.runTypes().stream().anyMatch(type->type.id.equals(id)); }
    boolean openGroup(String group)
    {
        if(!supportsGroup(group))return false;
        planner.selectRun(runGroup(group));SwingUtilities.invokeLater(this::refresh);return true;
    }
    private enum Compost
    {
        NONE(0, "None"), NORMAL(6032, "Compost"), SUPER(6034, "Supercompost"), ULTRA(21483, "Ultracompost");
        final int id; final String label;
        Compost(int id, String label) { this.id = id; this.label = label; }
        @Override public String toString() { return label; }
    }
}
