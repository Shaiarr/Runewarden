/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.BorderLayout;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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
    private JPanel panel, stops, supplies;
    private GuideText status, route;
    private JLabel icon;
    private int shownIcon;
    private JComboBox<FarmPatchPlanner.RunType> runs;
    private JComboBox<FarmPatchPlanner.CropChoice> crops;
    private JComboBox<Compost> compost;
    private JSpinner quantity;
    private JButton start, end;
    private FarmPatchPlanner.RunReading view;
    private final Map<String, StopRow> stopRows = new LinkedHashMap<>();
    private List<String> stopOrder = new ArrayList<>();
    private List<Integer> cropIds = new ArrayList<>(), supplyIds = new ArrayList<>();
    private final List<GuideText> supplyText = new ArrayList<>();

    @Inject FarmRunsTool(FarmPatchPlanner planner, ItemManager items) { this.planner = planner; this.items = items; }
    void setNavigation(HarvestNavigation navigation) { planner.setNavigation(navigation); }
    void onMenuOptionClicked(MenuOptionClicked event) { if (started) planner.onMenuOptionClicked(event); }
    void onChatMessage(ChatMessage event) { if (started) planner.onChatMessage(event); }
    void onGameTick() { if (started) planner.onGameTick(); }
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
        runs = new JComboBox<>(FarmPatchPlanner.runTypes().toArray(new FarmPatchPlanner.RunType[0]));
        runs.setName("runewarden.farm-runs.selector");
        runs.addActionListener(event ->
        {
            FarmPatchPlanner.RunType selected = (FarmPatchPlanner.RunType) runs.getSelectedItem();
            if (selected != null) edit(() -> planner.selectRun(selected.id));
        });
        panel.add(SidebarUi.field("Run", runs));
        crops = new JComboBox<>(); crops.setName("runewarden.farm-runs.crop");
        crops.addActionListener(event ->
        {
            FarmPatchPlanner.CropChoice crop = (FarmPatchPlanner.CropChoice) crops.getSelectedItem();
            if (crop != null) edit(() -> planner.chooseSeed(crop.id));
        });
        panel.add(SidebarUi.field("Crop", crops));
        quantity = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
        quantity.setName("runewarden.farm-runs.quantity");
        quantity.addChangeListener(event ->
        { int selected = ((Number) quantity.getValue()).intValue(); edit(() -> planner.setQuantity(selected)); });
        panel.add(SidebarUi.field("Per location", quantity));
        compost = new JComboBox<>(Compost.values()); compost.setName("runewarden.farm-runs.compost");
        compost.addActionListener(event ->
        { Compost selected = (Compost) compost.getSelectedItem(); if (selected != null) edit(() -> planner.setCompost(selected.id)); });
        panel.add(SidebarUi.field("Compost", compost));
        status = SidebarUi.label(" "); route = SidebarUi.label(" "); panel.add(status); panel.add(route);
        start = SidebarUi.button("Start run", () -> edit(planner::startSelectedRun));
        end = SidebarUi.button("End run", () -> edit(planner::stopActiveRun));
        panel.add(SidebarUi.row(start, end));
        JPanel supplyCard = SidebarUi.section("SUPPLIES"), stopCard = SidebarUi.section("LOCATIONS");
        supplies = SidebarUi.column(); stops = SidebarUi.column(); supplyCard.add(supplies); stopCard.add(stops);
        panel.add(supplyCard); panel.add(stopCard); refresh(); return panel;
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
    private static boolean same(FarmPatchPlanner.RunReading first, FarmPatchPlanner.RunReading second)
    {
        return first != null && second != null && first.epoch == second.epoch
            && Objects.equals(first.profile, second.profile) && Objects.equals(first.runId, second.runId);
    }
    void refresh()
    {
        if (panel == null) return;
        FarmPatchPlanner.RunReading next = planner.currentRun();
        boolean changed = !same(view, next); view = next;
        refreshing = true;
        try
        {
            boolean editing = next != null && !next.active;
            runs.setEnabled(editing); crops.setEnabled(editing); quantity.setEnabled(editing); compost.setEnabled(editing);
            start.setEnabled(editing && next.totalStops > 0); end.setEnabled(next != null && next.active);
            if (next == null)
            {
                status.setText("Log in to prepare a farm run."); route.setText(""); icon.setIcon(null); shownIcon = 0;
                if (changed) { stops.removeAll(); supplies.removeAll(); stopRows.clear(); stopOrder.clear(); supplyIds.clear(); }
                return;
            }
            status.setText(next.title + " · " + next.completedStops + " / " + next.totalStops + " stops\n" + next.status);
            route.setText(next.routeAdvice == null ? "" : next.routeAdvice);
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
            row.include.setSelected(stop.selected); row.include.setEnabled(!next.active);
            row.done.setSelected(stop.done); row.done.setEnabled(next.active && stop.id.equals(next.nextStopId) && !stop.done);
            row.skip.setEnabled(next.active && stop.id.equals(next.nextStopId) && !stop.done);
            row.up.setEnabled(!next.active); row.down.setEnabled(!next.active);
            row.state.setText(stop.status + (stop.unlock == null || stop.unlock.isBlank() ? "" : "\n" + stop.unlock));
        }
    }
    private final class StopRow
    {
        final JPanel panel = SidebarUi.column(); final JCheckBox include, done = new JCheckBox("Done");
        final GuideText state = SidebarUi.label(""); final JButton up, down, skip;
        StopRow(FarmPatchPlanner.StopReading stop, FarmPatchPlanner.RunReading expected)
        {
            include = new JCheckBox(stop.label); include.setOpaque(false); include.setForeground(SidebarUi.TEXT);
            include.addActionListener(event ->
            { boolean selected = include.isSelected(); if (!refreshing) apply(expected, () -> planner.includeStop(stop.id, selected)); });
            done.setOpaque(false); done.setForeground(SidebarUi.TEXT);
            done.addActionListener(event -> { if (!refreshing) apply(expected, () -> planner.checkNext(stop.id, false)); });
            up = SidebarUi.button("↑", () -> apply(expected, () -> planner.moveStop(stop.id, -1)));
            down = SidebarUi.button("↓", () -> apply(expected, () -> planner.moveStop(stop.id, 1)));
            skip = SidebarUi.button("Skip", () -> apply(expected, () -> planner.checkNext(stop.id, true)));
            panel.add(include); panel.add(state);
            panel.add(SidebarUi.row(up, down));
            panel.add(SidebarUi.row(done, skip));
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
            supplyText.get(i).setText(supply.name + "\n" + supply.carried + " / " + supply.required + " carried" + (supply.missing > 0 ? " · need " + supply.missing : ""));
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
