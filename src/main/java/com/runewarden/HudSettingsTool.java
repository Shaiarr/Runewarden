/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.DataFlavor;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;

/** Local controls for Runewarden's own HUD modules and saved overlay positions. */
@Singleton
final class HudSettingsTool
{
    private static final Color GOLD = new Color(224, 190, 113);
    private static final Color TEXT = new Color(240, 238, 232);
    private final ConfigManager configManager;
    private final RunewardenConfig config;
    private final OverlayManager overlays;
    private final Map<String, JCheckBox> choices = new LinkedHashMap<>();
    private final Map<String, BooleanSupplier> values = new LinkedHashMap<>();
    private volatile boolean started;
    private volatile long generation;
    private boolean refreshing;
    private JPanel panel;
    private JSlider opacity;
    private GuideText opacityValue;
    private JButton reset;
    private JComboBox<String> widget, preset;
    private JComboBox<HudPreferences.Visibility> visibility;
    private JComboBox<HudPreferences.Frame> frame;
    private JComboBox<HudPreferences.Values> displayedValues;
    private JSlider scale, font, widgetOpacity;
    private JTextField accent, text, background;
    private JPanel preview;
    private GuideText layoutStatus;
    private final List<javax.swing.JComponent> editorControls = new ArrayList<>();
    private final List<Runnable> preferenceRefreshers = new ArrayList<>();
    private String positionProfile, lastPositions;
    private boolean reloadPositions;
    private final Map<String, Overlay> extraWidgets=new LinkedHashMap<>();
    private final Map<String,java.util.function.Consumer<Boolean>> extraLocks=new LinkedHashMap<>();
    private HudResizeController resize;
    @Inject void setResizeController(HudResizeController value) { resize = value; }
    void registerWidget(String key,Overlay overlay,java.util.function.Consumer<Boolean> lock)
    { if(!HudPreferences.known(key)||overlay==null)throw new IllegalArgumentException("Unknown HUD widget");extraWidgets.put(key,overlay);extraLocks.put(key,lock);if(started){lock.accept(config.hudLocked());if(resize!=null)resize.register(key,overlay);} }

    @Inject HudSettingsTool(ConfigManager configManager, RunewardenConfig config, OverlayManager overlays)
    {
        this.configManager = configManager;
        this.config = config;
        this.overlays = overlays;
    }

    JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column();
        panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 8, 0));
        heading("LAYOUT");
        choice("Compact spacing", "hudCompact", config::hudCompact);
        choice("Lock HUD positions and sizes", "hudLocked", config::hudLocked);
        opacityValue = SidebarUi.label("");
        opacityValue.setFont(GuideText.FONT);
        opacityValue.setForeground(TEXT);
        align(opacityValue);
        panel.add(opacityValue);
        opacity = new JSlider(40, 100, boundedOpacity());
        opacity.setName("hudOpacity");
        opacity.setOpaque(false);
        opacity.setForeground(GOLD);
        opacity.setFont(GuideText.FONT.deriveFont(11f));
        opacity.setMajorTickSpacing(20);
        opacity.setPaintTicks(true);
        opacity.setPaintLabels(true);
        java.util.Enumeration<?> labels = opacity.getLabelTable().elements();
        while (labels.hasMoreElements())
        {
            JLabel label = (JLabel) labels.nextElement();
            label.setForeground(TEXT);
            label.setFont(GuideText.FONT.deriveFont(11f));
        }
        opacity.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        align(opacity);
        opacity.addChangeListener(event ->
        {
            opacityValue.setText("Background opacity · " + opacity.getValue() + "%");
            if (started && !refreshing && !opacity.getValueIsAdjusting()
                && opacity.getValue() != boundedOpacity())
            {
                configManager.setConfiguration(RunewardenConfig.GROUP, "hudOpacity", opacity.getValue());
            }
        });
        panel.add(opacity);
        heading("ON-SCREEN MODULES");
        choice("Resource bars", "resourceHud", config::resourceHud);
        choice("Inventory preview", "inventoryPreview", config::inventoryPreview);
        choice("Character dashboard", "dashboardHud", config::dashboardHud);
        choice("Harvest reminders", "harvestHud", config::harvestHud);
        choice("Active harvest run", "farmRunHud", config::farmRunHud);
        choice("Slayer task card", "slayerTaskHud", config::slayerTaskHud);
        choice("Node timers", "nodeTimers", config::nodeTimers);
        enumChoice("Node countdowns", "nodeTimerDisplay", RunewardenConfig.TimerDisplay.values(), config::nodeTimerDisplay);
        enumChoice("Harvest countdowns", "harvestTimerDisplay", RunewardenConfig.TimerDisplay.values(), config::harvestTimerDisplay);
        enumChoice("Effect countdowns", "effectTimerDisplay", RunewardenConfig.TimerDisplay.values(), config::effectTimerDisplay);
        choice("Outfit reminders", "skillingOutfitReminder", config::skillingOutfitReminder);
        choice("Skilling resource hover", "resourceTooltips", config::resourceTooltips);
        heading("CHARACTER DASHBOARD");
        choice("Worn equipment", "dashboardEquipment", config::dashboardEquipment);
        choice("Clock", "clockHud", config::clockHud);
        choice("UTC time", "clockUtc", config::clockUtc);
        heading("INVENTORY & TICKS");
        enumChoice("Inventory visibility", "inventoryPreviewMode", RunewardenConfig.InventoryMode.values(), config::inventoryPreviewMode);
        choice("Hide preview while banking", "inventoryHideBank", config::inventoryHideBank);
        choice("Inventory stack counts", "inventoryCounts", config::inventoryCounts);
        choice("Tick indicator", "tickDisplay", config::tickDisplay);
        choice("Click training cue", "clickTrainingCue", config::clickTrainingCue);
        enumChoice("Tick appearance", "tickDisplayMode", RunewardenConfig.TickDisplay.values(), config::tickDisplayMode);
        choice("Tick sound", "tickSound", config::tickSound);
        numberChoice("Ticks in cycle", "tickCycle", 1, 12, config.tickCycle());
        numberChoice("Health alert %", "healthAlert", 1, 100, config.healthAlert());
        numberChoice("Prayer alert %", "prayerAlert", 1, 100, config.prayerAlert());
        createEditor();
        panel.add(Box.createVerticalStrut(12));
        reset = SidebarUi.button("Reset HUD positions and sizes", this::resetPositions);
        reset.setName("resetHudPositions");
        reset.setFont(GuideText.FONT);
        reset.setForeground(GOLD);
        reset.setBackground(new Color(35, 34, 30));
        reset.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        align(reset);
        panel.add(reset);
        refresh();
        return panel;
    }

    void start()
    {
        generation++;
        started = true;
        reloadPositions = true;
        applyMovementLock();
        if (resize != null) resize.start(owned());
        refreshLater();
    }

    /** Native registration discards a stored location when a card is not movable. */
    void prepareForOverlayRegistration()
    {
        applyMovementLock(false);
    }

    void stop()
    {
        started = false;
        if (resize != null) resize.stop();
        generation++;
        extraWidgets.clear(); extraLocks.clear();
        refreshLater();
    }

    void onProfileChanged()
    {
        positionProfile = ""; lastPositions = null; reloadPositions = true;
        onConfigChanged();
    }

    void onConfigChanged()
    {
        if (!started) return;
        applyMovementLock();
        if (resize != null) resize.validateProfile();
        refreshLater();
    }

    void onConfigChanged(String key)
    {if("hudWidgetSettings".equals(key)||"hudLocked".equals(key)||choices.containsKey(key))onConfigChanged();}

    private void applyMovementLock()
    {
        applyMovementLock(config.hudLocked());
    }
    private void applyMovementLock(boolean locked)
    {
        extraLocks.values().forEach(action->action.accept(locked));
    }

    private void resetPositions()
    {
        if (!started) return;
        if (resize != null) resize.cancel();
        extraWidgets.values().forEach(overlays::resetOverlay);
        Map<String, HudPreferences.Style> styles = new LinkedHashMap<>(safeStyles());
        boolean changed = false;
        for (Map.Entry<String, HudPreferences.Style> entry : styles.entrySet())
            if (entry.getValue().scale != 100) { entry.setValue(entry.getValue().withScale(100)); changed = true; }
        if (changed) saveLayout(HudPreferences.encode(styles));
        applyMovementLock();
    }

    private void refreshLater()
    {
        long expected = generation;
        Runnable action = () ->
        {
            if (expected == generation && panel != null) refresh();
        };
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }

    private void refresh()
    {
        refreshing = true;
        try
        {
            choices.forEach((key, choice) ->
            {
                choice.setSelected(values.get(key).getAsBoolean());
                choice.setEnabled(started);
            });
            opacity.setValue(boundedOpacity());
            opacity.setEnabled(started);
            opacityValue.setText("Background opacity · " + boundedOpacity() + "%");
            reset.setEnabled(started);
            for (javax.swing.JComponent control : editorControls) control.setEnabled(started);
            for (Runnable refresher : preferenceRefreshers) refresher.run();
            if (widget != null) refreshEditor();
        }
        finally { refreshing = false; }
    }

    private int boundedOpacity() { return Math.max(40, Math.min(100, config.hudOpacity())); }

    private void createEditor()
    {
        heading("WIDGET EDITOR");
        widget = combo("hudWidget", HudPreferences.WIDGETS);
        widget.addActionListener(event -> { if (!refreshing) refreshEditor(); });
        scale = slider("Widget scale %", "widgetScale", 60, 180, 100);
        font = slider("Text size", "widgetFont", 9, 18, 12);
        widgetOpacity = slider("Widget opacity %", "widgetOpacity", 10, 100, 85);
        accent = field("Accent · hex colour", "widgetAccent");
        text = field("Text · hex colour", "widgetText");
        background = field("Background · hex colour", "widgetBackground");
        label("Visible when"); visibility = combo("widgetVisibility", HudPreferences.Visibility.values());
        label("Frame"); frame = combo("widgetFrame", HudPreferences.Frame.values());
        label("Resource values"); displayedValues = combo("widgetValues", HudPreferences.Values.values());
        preview = new JPanel()
        {
            @Override protected void paintComponent(Graphics graphics)
            {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                try
                {
                    HudPreferences.Style style = editorStyle();
                    g.translate(8, 8);
                    HudStyle.panel(g, Math.max(100, getWidth() - 16), 70, style);
                    g.setColor(style.accent); g.setFont(HudStyle.label(style.font)); g.drawString("RUNEWARDEN", 10, 24);
                    g.setColor(style.text); g.setFont(HudStyle.body(style.font)); g.drawString("99 / 99", 10, 49);
                }
                catch (IllegalArgumentException ignored) { }
                finally { g.dispose(); }
            }
        };
        preview.setOpaque(false); preview.setPreferredSize(new Dimension(206, 86));
        preview.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86)); align(preview); panel.add(preview);
        for (JSlider slider : new JSlider[] {scale, font, widgetOpacity}) slider.addChangeListener(event -> preview.repaint());
        button("Apply widget style", "applyHudWidget", () ->
        {
            Map<String, HudPreferences.Style> styles = new LinkedHashMap<>(safeStyles());
            styles.put((String) widget.getSelectedItem(), editorStyle());
            saveLayout(HudPreferences.encode(styles)); layoutStatus.setText("Widget updated");
        });
        button("Reset this widget style", "resetHudWidget", () ->
        {
            Map<String, HudPreferences.Style> styles = new LinkedHashMap<>(safeStyles());
            styles.remove((String) widget.getSelectedItem()); saveLayout(HudPreferences.encode(styles));
            layoutStatus.setText("Widget style reset");
        });
        heading("SAVED LAYOUTS");
        preset = combo("hudPreset", new String[] {"PvM", "Skilling", "AFK"});
        button("Load layout preset", "loadHudPreset", () ->
        {
            String name = (String) preset.getSelectedItem();
            String saved = configManager.getRSProfileKey() == null ? null
                : configManager.getRSProfileConfiguration(RunewardenConfig.GROUP, "hudPreset" + name);
            saveLayout(saved == null ? HudPreferences.preset(name, boundedOpacity()) : saved);
            String positions = configManager.getRSProfileKey() == null ? null
                : configManager.getRSProfileConfiguration(RunewardenConfig.GROUP, "hudPresetPositions" + name);
            if (positions != null) applyPositions(positions);
            layoutStatus.setText(name + " layout loaded");
        });
        button("Save layout to preset", "saveHudPreset", () ->
        {
            if (configManager.getRSProfileKey() == null) { layoutStatus.setText("Log in to save a character preset"); return; }
            configManager.setRSProfileConfiguration(RunewardenConfig.GROUP, "hudPreset" + preset.getSelectedItem(), HudPreferences.encode(safeStyles()));
            configManager.setRSProfileConfiguration(RunewardenConfig.GROUP, "hudPresetPositions" + preset.getSelectedItem(), HudLayoutPositions.capture(configManager, owned()));
            layoutStatus.setText("Character preset saved");
        });
        button("Copy layout code", "copyHudLayout", () ->
        {
            String code = HudLayoutPositions.share(HudPreferences.encode(safeStyles()), HudLayoutPositions.capture(configManager, owned()));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code), null);
            layoutStatus.setText("Layout code copied");
        });
        button("Paste layout code", "pasteHudLayout", () ->
        {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
            String code = value instanceof String ? ((String) value).trim() : "";
            if (code.startsWith("RUNEWARDEN-LAYOUT:"))
            {
                String[] layout = HudLayoutPositions.unshare(code);
                saveLayout(layout[0]); applyPositions(layout[1]);
            }
            else saveLayout(HudPreferences.unshare(code));
            layoutStatus.setText("Shared layout loaded");
        });
        layoutStatus = label("Alt-drag moves · Ctrl-drag an edge resizes.");
        layoutStatus.setName("hudLayoutStatus");
    }

    private Map<String, HudPreferences.Style> safeStyles()
    {
        try { return HudPreferences.decode(config.hudWidgetSettings()); }
        catch (IllegalArgumentException ignored) { return java.util.Collections.emptyMap(); }
    }
    private void saveLayout(String layout)
    {
        HudPreferences.decode(layout);
        configManager.setConfiguration(RunewardenConfig.GROUP, "hudWidgetSettings", layout);
        refreshEditor();
    }
    private HudPreferences.Style editorStyle()
    {
        return new HudPreferences.Style(scale.getValue(), font.getValue(), widgetOpacity.getValue(),
            HudPreferences.color(accent.getText().trim()), HudPreferences.color(text.getText().trim()), HudPreferences.color(background.getText().trim()),
            (HudPreferences.Visibility) visibility.getSelectedItem(), (HudPreferences.Frame) frame.getSelectedItem(),
            (HudPreferences.Values) displayedValues.getSelectedItem());
    }
    private void refreshEditor()
    {
        if (widget == null || preview == null) return;
        HudPreferences.Style style = HudStyle.settings(config, (String) widget.getSelectedItem());
        scale.setValue(style.scale); font.setValue(style.font); widgetOpacity.setValue(style.opacity);
        accent.setText(String.format("%06x", style.accent.getRGB() & 0xffffff));
        text.setText(String.format("%06x", style.text.getRGB() & 0xffffff));
        background.setText(String.format("%06x", style.background.getRGB() & 0xffffff));
        visibility.setSelectedItem(style.visibility); frame.setSelectedItem(style.frame); displayedValues.setSelectedItem(style.values);
        displayedValues.setEnabled(started && "resources".equals(widget.getSelectedItem()));
        preview.repaint();
    }
    private GuideText label(String title)
    {
        GuideText result = SidebarUi.label(title); result.setForeground(TEXT);
        result.setBorder(BorderFactory.createEmptyBorder(5, 0, 3, 0)); align(result); panel.add(result); return result;
    }
    private <T> JComboBox<T> combo(String name, T[] options)
    {
        JComboBox<T> result = new JComboBox<>(options); result.setName(name); result.setFont(GuideText.FONT);
        result.setBackground(new Color(35, 34, 30)); result.setForeground(TEXT);
        result.setRenderer(new DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
            {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                setFont(GuideText.FONT); setText(displayName(value));
                if (!selected) { setBackground(new Color(35, 34, 30)); setForeground(TEXT); }
                return this;
            }
        });
        result.setMinimumSize(new Dimension(0, 28));
        result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); align(result); panel.add(result); editorControls.add(result); return result;
    }
    private <T> void enumChoice(String title, String key, T[] options, java.util.function.Supplier<T> value)
    {
        label(title); JComboBox<T> result = combo(key, options); result.setSelectedItem(value.get());
        result.addActionListener(event ->
        {
            if (started && !refreshing) configManager.setConfiguration(RunewardenConfig.GROUP, key, result.getSelectedItem());
        });
        preferenceRefreshers.add(() -> result.setSelectedItem(value.get()));
    }
    private JSlider slider(String title, String key, int min, int max, int initial)
    {
        GuideText titleLabel = label(title + " · " + initial);
        JSlider result = new JSlider(min, max, HudPreferences.clamp(initial, min, max));
        result.setName(key); result.setOpaque(false); result.setForeground(GOLD);
        result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30)); align(result); panel.add(result); editorControls.add(result);
        result.addChangeListener(event -> titleLabel.setText(title + " · " + result.getValue()));
        return result;
    }
    private void numberChoice(String title, String key, int min, int max, int initial)
    {
        JSlider result = slider(title, key, min, max, initial);
        result.addChangeListener(event ->
        {
            if (started && !refreshing && !result.getValueIsAdjusting()) configManager.setConfiguration(RunewardenConfig.GROUP, key, result.getValue());
        });
        preferenceRefreshers.add(() -> result.setValue("tickCycle".equals(key) ? config.tickCycle()
            : "healthAlert".equals(key) ? config.healthAlert() : config.prayerAlert()));
    }
    private JTextField field(String title, String name)
    {
        label(title); JTextField result = new JTextField(6); result.setName(name); result.setFont(GuideText.FONT);
        result.setBackground(new Color(35, 34, 30)); result.setForeground(TEXT);
        result.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28)); align(result); panel.add(result); editorControls.add(result); return result;
    }
    private interface EditorAction { void run() throws Exception; }
    private void button(String title, String name, EditorAction action)
    {
        JButton button = SidebarUi.button(title, () ->
        {
            if (!started) return;
            try { action.run(); }
            catch (Exception exception) { layoutStatus.setText(exception instanceof IllegalArgumentException
                ? exception.getMessage() : "Clipboard unavailable"); }
        });
        button.setName(name); button.setMinimumSize(new Dimension(0, 30));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34)); align(button); panel.add(button); editorControls.add(button);
    }

    private Map<String, Overlay> owned()
    {
        return new LinkedHashMap<>(extraWidgets);
    }
    private void applyPositions(String encoded)
    {
        HudLayoutPositions.decode(encoded);
        applyMovementLock(false);
        try { HudLayoutPositions.apply(encoded, configManager, overlays, owned()); }
        finally { applyMovementLock(); }
    }
    /** Called on the client thread after the character profile is resolved. */
    void profileTick(String profile)
    {
        if (resize != null) resize.validateProfile();
        if (!started || profile == null) return;
        if (reloadPositions || !profile.equals(positionProfile))
        {
            String saved = configManager.getRSProfileConfiguration(RunewardenConfig.GROUP, "hudCharacterPositions");
            boolean first = positionProfile == null;
            positionProfile = profile;
            reloadPositions = false;
            if (saved == null) saved = first ? HudLayoutPositions.capture(configManager, owned()) : "RWP1|";
            try { applyPositions(saved); }
            catch (IllegalArgumentException ignored) { saved = "RWP1|"; applyPositions(saved); }
            lastPositions = saved;
            configManager.setRSProfileConfiguration(RunewardenConfig.GROUP, "hudCharacterPositions", saved);
        }
        String current = HudLayoutPositions.capture(configManager, owned());
        if (!current.equals(lastPositions))
        {
            configManager.setRSProfileConfiguration(RunewardenConfig.GROUP, "hudCharacterPositions", current);
            lastPositions = current;
        }
    }

    private void heading(String title)
    {
        if (panel.getComponentCount() > 0) panel.add(Box.createVerticalStrut(12));
        GuideText heading = SidebarUi.heading(title);
        heading.setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));
        align(heading);
        panel.add(heading);
    }

    private void choice(String label, String key, BooleanSupplier value)
    {
        JCheckBox choice = new JCheckBox(label);
        choice.setName(key);
        choice.setFont(GuideText.FONT);
        choice.setForeground(TEXT);
        choice.setOpaque(false);
        choice.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        align(choice);
        choice.addActionListener(event ->
        {
            if (started && !refreshing)
                configManager.setConfiguration(RunewardenConfig.GROUP, key, choice.isSelected());
        });
        choices.put(key, choice);
        values.put(key, value);
        panel.add(choice);
    }

    private static void align(javax.swing.JComponent component) { component.setAlignmentX(Component.LEFT_ALIGNMENT); }

    private static String displayName(Object value)
    {
        if (value == null) return "";
        if (value == RunewardenConfig.InventoryMode.CLOSED_TAB) return "Inventory closed";
        if (value == RunewardenConfig.InventoryMode.COMBAT || value == HudPreferences.Visibility.COMBAT) return "In combat";
        if (value == HudPreferences.Visibility.OUT_OF_COMBAT) return "Outside combat";
        if (value == HudPreferences.Values.CURRENT_MAX) return "Current / maximum";
        if (value == HudPreferences.Values.CURRENT) return "Current only";
        if (value == HudPreferences.Values.PERCENT) return "Percentage";
        if (value == RunewardenConfig.TickDisplay.PULSE) return "Pulse / dot";
        if (value == RunewardenConfig.TickDisplay.BAR) return "Sweeping bar";
        if (value == RunewardenConfig.TickDisplay.DOTS) return "Cycle dots";
        if (value instanceof Enum)
        {
            String name = ((Enum<?>) value).name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
        String name = value.toString();
        return HudPreferences.known(name) ? Character.toUpperCase(name.charAt(0)) + name.substring(1) : name;
    }
}
