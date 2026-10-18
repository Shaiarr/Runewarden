/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.util.LinkBrowser;

/** A bounded, lazy browser for local marker data, separate from guide navigation. */
@Singleton
final class TileMarkersTool
{
    static final int PAGE_SIZE = 12;
    private static final Color BACKGROUND = new Color(20, 20, 21), CARD = new Color(31, 30, 27);
    private static final Color GOLD = new Color(234, 201, 124), WHITE = new Color(240, 238, 232);
    private enum Page { CATEGORIES, GROUPS, ROTATIONS, PACKS, DETAIL }
    private final GuideMarkerOverlay overlay;
    private final Supplier<TileMarkerIndex> loader;
    private final Map<String, String> queries = new HashMap<>();
    private final Map<String, Integer> pages = new HashMap<>();
    private volatile boolean started;
    private long epoch;
    private SwingWorker<TileMarkerIndex, Void> worker;
    private TileMarkerIndex index;
    private JPanel panel, body;
    private GuideText active;
    private JCheckBox visible;
    private JButton clear;
    private Timer activeTimer;
    private Runnable backAction = () -> {};
    private IntConsumer rotationAction = value -> {};
    private Page page = Page.CATEGORIES, detailParent = Page.PACKS;
    private TileMarkerIndex.Category category;
    private TileMarkerIndex.Group group;
    private GuideCatalog.MarkerPack focused;

    @Inject TileMarkersTool(GuideCatalog catalog, GuideMarkerOverlay overlay)
    {
        this(overlay, () -> new TileMarkerIndex(catalog));
    }

    TileMarkersTool(GuideMarkerOverlay overlay, Supplier<TileMarkerIndex> loader)
    {
        this.overlay = overlay;
        this.loader = loader;
    }

    void setBackAction(Runnable action) { backAction = action == null ? () -> {} : action; }

    void setRotationAction(IntConsumer action) { rotationAction = action == null ? value -> {} : action; }

    void start()
    {
        started = true;
        onEdt(() -> {
            if (started && panel != null && panel.isShowing()) shown();
        });
    }

    void stop()
    {
        started = false;
        onEdt(() -> {
            epoch++;
            if (worker != null) worker.cancel(false);
            worker = null;
            if (activeTimer != null) activeTimer.stop();
        });
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = vertical();
        panel.setName("runewarden.tilemarkers");
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 8, 0));
        GuideText title = text("TILE MARKERS");
        title.setForeground(GOLD);
        panel.add(title);
        active = text("");
        active.setName("runewarden.tilemarkers.active");
        panel.add(active);
        visible = new JCheckBox("Show markers", overlay.isVisible());
        visible.setName("runewarden.tilemarkers.visibility");
        visible.setOpaque(false);
        visible.setFont(GuideText.FONT);
        visible.setForeground(WHITE);
        visible.addActionListener(event -> {
            if (started) { overlay.setVisible(visible.isSelected()); refreshActive(); }
        });
        panel.add(visible);
        clear = button("Clear markers", "clear", () -> { overlay.select(null); refreshActive(); });
        panel.add(clear);
        body = vertical();
        panel.add(body);
        panel.addComponentListener(new ComponentAdapter()
        {
            @Override public void componentShown(ComponentEvent event) { shown(); }
            @Override public void componentHidden(ComponentEvent event)
            { if (activeTimer != null) activeTimer.stop(); }
        });
        activeTimer = new Timer(1000, event -> refreshActive());
        refreshActive();
        body.add(text("Loading tile markers…"));
        return panel;
    }

    private void shown()
    {
        if (!started) return;
        refreshActive();
        activeTimer.start();
        ensureLoaded();
    }

    /** Catalog indexing never blocks the Swing event thread or the game client. */
    void ensureLoaded()
    {
        if (!started || panel == null) return;
        if (index != null) { render(); return; }
        if (worker != null) return;
        long expected = epoch;
        worker = new SwingWorker<TileMarkerIndex, Void>()
        {
            @Override protected TileMarkerIndex doInBackground() { return loader.get(); }
            @Override protected void done()
            {
                if (!started || expected != epoch || worker != this) return;
                worker = null;
                try { index = get(); render(); }
                catch (CancellationException ignored) { }
                catch (InterruptedException error) { showLoadError(); }
                catch (ExecutionException error) { showLoadError(); }
            }
        };
        worker.execute();
    }

    private void showLoadError()
    {
        body.removeAll();
        body.add(text("Marker library unavailable"));
        body.add(button("Try again", "retry", this::ensureLoaded));
        changed();
    }

    private void refreshActive()
    {
        if (active == null) return;
        GuideCatalog.MarkerPack selected = overlay.selectedPack();
        String summary = selected == null ? "No markers loaded" : contextLabel(selected);
        if (!overlay.isVisible() && selected != null) summary += " · Hidden";
        if (!summary.equals(active.getText())) active.setText(summary);
        clear.setEnabled(selected != null);
        visible.setSelected(overlay.isVisible());
    }

    private void render()
    {
        if (index == null || body == null) return;
        body.removeAll();
        switch (page)
        {
            case CATEGORIES: renderCategories(); break;
            case GROUPS: renderGroups(false); break;
            case ROTATIONS: renderGroups(true); break;
            case PACKS: renderPacks(); break;
            case DETAIL: renderDetail(); break;
            default: throw new IllegalStateException("Unknown marker page");
        }
        refreshActive();
        changed();
    }

    private void renderCategories()
    {
        body.add(button("← Command dashboard", "back", () -> backAction.run()));
        heading("Categories");
        for (TileMarkerIndex.Category choice : index.categories())
        {
            int total = 0;
            for (TileMarkerIndex.Group entry : index.groups(choice)) total += entry.packs.size();
            final TileMarkerIndex.Category target = choice;
            body.add(button(choice.title + " · " + total, "category." + choice.name(), () -> {
                category = target;
                page = Page.GROUPS;
                render();
            }));
        }
    }

    private void renderGroups(boolean rotations)
    {
        body.add(button(rotations ? "← Wave guides" : "← Categories", "back", () -> {
            page = rotations ? Page.GROUPS : Page.CATEGORIES;
            render();
        }));
        heading(rotations ? "Fight Caves · Rotations" : category.title);
        JTextField search = search("Search activities");
        body.add(search);
        JPanel choices = vertical();
        body.add(choices);
        Runnable refresh = () -> {
            choices.removeAll();
            String needle = search.getText().trim().toLowerCase(Locale.ROOT);
            List<TileMarkerIndex.Group> available = index.groups(category);
            boolean fightCaves = false;
            for (TileMarkerIndex.Group entry : available)
            {
                boolean rotation = isRotation(entry);
                if (rotations != rotation && (rotations || category == TileMarkerIndex.Category.WAVES))
                {
                    if (!rotations && rotation && (needle.isEmpty() || "fight caves".contains(needle)
                        || entry.title.toLowerCase(Locale.ROOT).contains(needle))) fightCaves = true;
                    continue;
                }
                if (!needle.isEmpty() && !(entry.title + " " + entry.id).toLowerCase(Locale.ROOT).contains(needle)) continue;
                choices.add(button(entry.title + (entry.packs.size() > 1 ? " · " + entry.packs.size() : ""),
                    "group." + entry.id, () -> openGroup(entry, rotations)));
            }
            if (fightCaves)
                choices.add(button("Fight Caves · 15 rotations", "fightcaves", () -> { page = Page.ROTATIONS; render(); }), 0);
            if (choices.getComponentCount() == 0) choices.add(text("No matching activities"));
            changed();
        };
        listen(search, refresh);
        refresh.run();
    }

    private static boolean isRotation(TileMarkerIndex.Group entry)
    { return entry.id.startsWith("fight-caves-r"); }

    private void openGroup(TileMarkerIndex.Group entry, boolean rotations)
    {
        group = entry;
        if (entry.packs.size() == 1)
        {
            focused = entry.packs.get(0);
            detailParent = rotations ? Page.ROTATIONS : Page.GROUPS;
            page = Page.DETAIL;
        }
        else page = Page.PACKS;
        render();
    }

    private void renderPacks()
    {
        body.add(button(isRotation(group) ? "← Fight Caves rotations" : "← " + category.title, "back", () -> {
            page = isRotation(group) ? Page.ROTATIONS : Page.GROUPS;
            render();
        }));
        heading(group.title);
        if (isRotation(group))
        {
            int rotation = Integer.parseInt(group.id.substring("fight-caves-r".length()));
            body.add(button("Wait for rotation " + rotation, "waitrotation", () -> rotationAction.accept(rotation)));
        }
        JTextField search = search("Search marker packs");
        search.setText(queries.getOrDefault(group.id, ""));
        body.add(search);
        GuideText count = text("");
        body.add(count);
        DefaultListModel<GuideCatalog.MarkerPack> model = new DefaultListModel<>();
        JList<GuideCatalog.MarkerPack> list = new JList<GuideCatalog.MarkerPack>(model)
        {
            @Override public boolean getScrollableTracksViewportWidth() { return true; }
        };
        list.setName("runewarden.tilemarkers.packs");
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(BACKGROUND);
        list.setForeground(WHITE);
        list.setFixedCellHeight(48);
        list.setFixedCellWidth(180);
        list.setCellRenderer(new PackRenderer());
        JScrollPane scroll = new JScrollPane(list);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(190, 288));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 288));
        scroll.setMinimumSize(new Dimension(0, 96));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(scroll);
        JButton open = button("Open pack", "open", () -> {
            if (list.getSelectedValue() == null) return;
            focused = list.getSelectedValue();
            detailParent = Page.PACKS;
            page = Page.DETAIL;
            render();
        });
        open.setEnabled(false);
        list.addListSelectionListener(event -> open.setEnabled(list.getSelectedValue() != null));
        list.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent event)
            {
                int row = list.locationToIndex(event.getPoint());
                if (event.getClickCount() == 2 && row >= 0 && list.getCellBounds(row, row).contains(event.getPoint()))
                    open.doClick(0);
            }
        });
        body.add(open);
        JPanel navigation = new JPanel(new GridLayout(1, 2, 6, 0));
        navigation.setOpaque(false);
        navigation.setAlignmentX(Component.LEFT_ALIGNMENT);
        navigation.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        JButton previous = button("← Previous", "previous", () -> {});
        JButton next = button("Next →", "next", () -> {});
        navigation.add(previous); navigation.add(next);
        body.add(navigation);
        Runnable refresh = () -> {
            String query = search.getText();
            queries.put(group.id, query);
            List<GuideCatalog.MarkerPack> filtered = filter(group.packs, query);
            int last = Math.max(0, (filtered.size() - 1) / PAGE_SIZE);
            int current = Math.min(last, Math.max(0, pages.getOrDefault(group.id, 0)));
            pages.put(group.id, current);
            model.clear();
            int start = current * PAGE_SIZE;
            for (int row = start; row < Math.min(filtered.size(), start + PAGE_SIZE); row++) model.addElement(filtered.get(row));
            if (focused != null) list.setSelectedValue(focused, true);
            count.setText(filtered.isEmpty() ? "No matching packs" : (start + 1) + "–" + (start + model.size())
                + " / " + filtered.size() + " · Page " + (current + 1) + " / " + (last + 1));
            previous.setEnabled(current > 0);
            next.setEnabled(current < last);
            changed();
        };
        previous.addActionListener(event -> { pages.put(group.id, pages.getOrDefault(group.id, 0) - 1); refresh.run(); });
        next.addActionListener(event -> { pages.put(group.id, pages.getOrDefault(group.id, 0) + 1); refresh.run(); });
        listen(search, () -> { pages.put(group.id, 0); refresh.run(); });
        refresh.run();
    }

    private void renderDetail()
    {
        String parent = detailParent == Page.PACKS ? group.title
            : detailParent == Page.ROTATIONS ? "Fight Caves rotations" : category.title;
        body.add(button("← " + parent, "back", () -> { page = detailParent; render(); }));
        heading(contextLabel(focused));
        body.add(text(focused.tiles.isEmpty() ? "Reference pack · No tiles"
            : focused.tiles.size() + (focused.tiles.size() == 1 ? " tile" : " tiles")));
        JButton load = button("Load markers", "load", () -> { overlay.select(focused); refreshActive(); });
        load.setEnabled(!focused.tiles.isEmpty());
        body.add(load);
        if (focused.source != null && !focused.source.isEmpty())
            body.add(button("Marker source ↗", "source", () -> LinkBrowser.browse(focused.source)));
        if (focused.wiki != null && !focused.wiki.isEmpty())
            body.add(button("Wiki ↗", "wiki", () -> LinkBrowser.browse(focused.wiki)));
    }

    static List<GuideCatalog.MarkerPack> filter(List<GuideCatalog.MarkerPack> packs, String query)
    {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return packs;
        List<GuideCatalog.MarkerPack> result = new ArrayList<>();
        for (GuideCatalog.MarkerPack pack : packs)
            if ((TileMarkerIndex.label(pack) + " " + pack.id).toLowerCase(Locale.ROOT).contains(needle)) result.add(pack);
        return Collections.unmodifiableList(result);
    }

    private static String contextLabel(GuideCatalog.MarkerPack pack)
    {
        return pack.id.startsWith("fight-caves-r") && pack.title != null
            ? pack.title : TileMarkerIndex.label(pack);
    }

    private void heading(String value)
    {
        GuideText heading = text(value);
        heading.setForeground(GOLD);
        body.add(heading);
    }

    private void changed()
    {
        if (body == null) return;
        body.revalidate(); body.repaint();
        panel.revalidate(); panel.repaint();
    }

    private static JPanel vertical()
    {
        JPanel result = new JPanel();
        result.setLayout(new BoxLayout(result, BoxLayout.Y_AXIS));
        result.setBackground(BACKGROUND);
        result.setAlignmentX(Component.LEFT_ALIGNMENT);
        return result;
    }

    private static GuideText text(String value) { return new GuideText(value); }

    private JButton button(String label, String name, Runnable action)
    {
        JButton result = new JButton(label);
        result.setName("runewarden.tilemarkers." + name);
        result.setFont(GuideText.FONT);
        result.setBackground(CARD);
        result.setForeground(GOLD);
        result.setFocusPainted(false);
        result.setAlignmentX(Component.LEFT_ALIGNMENT);
        result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        result.setPreferredSize(new Dimension(190, 36));
        result.addActionListener(event -> { if (started) action.run(); });
        return result;
    }

    private static JTextField search(String tooltip)
    {
        JTextField result = new JTextField();
        result.setName("runewarden.tilemarkers.search");
        result.setFont(GuideText.FONT);
        result.setBackground(CARD);
        result.setForeground(WHITE);
        result.setCaretColor(GOLD);
        result.setToolTipText(tooltip);
        result.setAlignmentX(Component.LEFT_ALIGNMENT);
        result.getAccessibleContext().setAccessibleName(tooltip);
        result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return result;
    }

    private static void listen(JTextField field, Runnable action)
    {
        field.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent event) { action.run(); }
            public void removeUpdate(DocumentEvent event) { action.run(); }
            public void changedUpdate(DocumentEvent event) { action.run(); }
        });
    }

    private static void onEdt(Runnable action)
    { if (SwingUtilities.isEventDispatchThread()) action.run(); else SwingUtilities.invokeLater(action); }

    /** Reuses two labels; fixed cell dimensions avoid preferred-size walks over the library. */
    private static final class PackRenderer extends JPanel implements ListCellRenderer<GuideCatalog.MarkerPack>
    {
        private final JLabel title = new JLabel(), subtitle = new JLabel();
        PackRenderer()
        {
            super(new BorderLayout(0, 3));
            setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
            title.setFont(GuideText.FONT); title.setForeground(WHITE);
            subtitle.setFont(GuideText.FONT); subtitle.setForeground(GOLD);
            add(title, BorderLayout.CENTER); add(subtitle, BorderLayout.SOUTH);
        }
        @Override public Component getListCellRendererComponent(JList<? extends GuideCatalog.MarkerPack> list,
            GuideCatalog.MarkerPack value, int row, boolean selected, boolean focus)
        {
            String label = TileMarkerIndex.label(value);
            title.setText(label);
            subtitle.setText(value.tiles.size() + (value.tiles.size() == 1 ? " tile" : " tiles"));
            setToolTipText(label);
            setBackground(selected ? new Color(66, 54, 29) : BACKGROUND);
            return this;
        }
    }
}
