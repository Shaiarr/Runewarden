/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.itemstats.ItemStatChanges;
import net.runelite.client.ui.overlay.Overlay;

/** Account-scoped observations for the effects HUD; native timer units stay explicit. */
@Singleton
public final class FieldCoach
{
    public static final String GROUP = "runewardenfield";
    private final Client client;
    private final ConfigManager configs;
    private final Notifier notifier;
    private final FieldStatusOverlay overlay;
    private final ConsumablePreviewOverlay consumables;
    private final Map<String, NativeClock> clocks = new LinkedHashMap<>();
    private volatile Reading reading = Reading.EMPTY;
    private volatile java.util.Set<String> activeEffectKeys=java.util.Collections.emptySet();
    private java.util.function.Supplier<List<Row>> additionalRows=java.util.Collections::emptyList;
    private volatile boolean enabled = true, boosts = true, previews = true, alerts;
    private volatile int warningSeconds = 15;
    private volatile boolean running;
    private volatile String profile;
    private volatile long epoch;
    private int baselineTick;
    private JPanel panel, values;

    static final class Spec
    {
        final String key, label; final int varbit, unit;
        Spec(String key, String label, int varbit, int unit)
        { this.key = key; this.label = label; this.varbit = varbit; this.unit = unit; }
    }

    /** Multipliers are the public RuneLite 1.13.2 TimersAndBuffs native mappings. */
    static final Spec[] SPECS = {
        new Spec("antifire", "Antifire", VarbitID.ANTIFIRE_POTION, 30),
        new Spec("super-antifire", "Super antifire", VarbitID.SUPER_ANTIFIRE_POTION, 20),
        new Spec("stamina", "Stamina", VarbitID.STAMINA_DURATION, 10),
        new Spec("divine-combat", "Divine combat", VarbitID.DIVINECOMBAT_POTION_TIME, 1),
        new Spec("divine-bastion", "Divine bastion", VarbitID.DIVINEBASTION_POTION_TIME, 1),
        new Spec("divine-battlemage", "Divine battlemage", VarbitID.DIVINEBATTLEMAGE_POTION_TIME, 1),
        new Spec("divine-attack", "Divine attack", VarbitID.DIVINEATTACK_POTION_TIME, 1),
        new Spec("divine-strength", "Divine strength", VarbitID.DIVINESTRENGTH_POTION_TIME, 1),
        new Spec("divine-defence", "Divine defence", VarbitID.DIVINEDEFENCE_POTION_TIME, 1),
        new Spec("divine-ranging", "Divine ranging", VarbitID.DIVINERANGE_POTION_TIME, 1),
        new Spec("divine-magic", "Divine magic", VarbitID.DIVINEMAGIC_POTION_TIME, 1),
        new Spec("salts", "Smelling salts", VarbitID.TOA_MIDRAIDLOOT_STATS_TIMER, 25),
        new Spec("overload-nmz", "Overload (NMZ)", VarbitID.NZONE_OVERLOAD_POTION_EFFECTS, 25),
        new Spec("overload-raids", "Overload (raids)", VarbitID.RAIDS_OVERLOAD_TIMER, 25),
        new Spec("overload-deadman", "Blighted overload", VarbitID.DEADMAN_OVERLOAD_POTION_EFFECTS, 25),
        new Spec("remedy", "Menaphite remedy", VarbitID.STATRENEWAL_POTION_TIMER, 25),
        new Spec("goading", "Goading", VarbitID.GOADING_POTION_TIMER, 6),
        new Spec("prayer-regen", "Prayer regeneration", VarbitID.PRAYER_REGENERATION_POTION_TIMER, 12),
        new Spec("heart", "Heart cooldown", VarbitID.IMBUED_HEART_TIMER, 10),
        new Spec("surge", "Surge cooldown", VarbitID.SURGE_POTION_TIMER, 10)
    };

    public static final class Row
    {
        public final String label, value;
        public final boolean warning;
        public final long remainingTicks, observedWindowTicks;
        public final boolean estimated;
        Row(String label, String value, boolean warning)
        { this(label, value, warning, 0, 0, false); }
        Row(String label, String value, boolean warning, long remaining, long window, boolean estimated)
        { this.label = label; this.value = value; this.warning = warning;remainingTicks=remaining;observedWindowTicks=window;this.estimated=estimated; }
    }

    public static final class Reading
    {
        public static final Reading EMPTY = new Reading(Collections.emptyList(), Collections.emptyList());
        public final List<Row> effects, boosts;
        Reading(List<Row> effects, List<Row> boosts)
        { this.effects = Collections.unmodifiableList(new ArrayList<>(effects)); this.boosts = Collections.unmodifiableList(new ArrayList<>(boosts)); }
    }

    @Inject public FieldCoach(Client client, ConfigManager configs, RunewardenConfig hudConfig,
        ItemManager items, ItemStatChanges stats, HoverCardOverlay hovers, Notifier notifier)
    {
        this.client = client; this.configs = configs; this.notifier = notifier;
        overlay = new FieldStatusOverlay(client, hudConfig, this);
        consumables = new ConsumablePreviewOverlay(client, items, stats, hovers, this);
    }

    public void start() { reload(); running = true; clear(); }
    public void stop() { running = false; clear(); }
    public void clear()
    {
        profile = null; epoch++; clocks.clear(); activeEffectKeys=java.util.Collections.emptySet(); reading = Reading.EMPTY; consumables.clear(); refresh();
    }
    public Reading snapshot() { return running && profile != null && profile.equals(configs.getRSProfileKey()) ? reading : Reading.EMPTY; }
    public void setAdditionalRows(java.util.function.Supplier<List<Row>> rows) { additionalRows=rows==null?java.util.Collections::emptyList:rows; }
    public java.util.Set<String> activeEffectKeys() { return running&&enabled&&profile!=null&&profile.equals(configs.getRSProfileKey())?activeEffectKeys:java.util.Collections.emptySet(); }
    public java.util.Set<String> knownEffectKeys() { if(!running||!enabled||profile==null||!profile.equals(configs.getRSProfileKey()))return java.util.Collections.emptySet();java.util.Set<String> keys=new java.util.LinkedHashSet<>();for(Spec s:SPECS)keys.add(s.key);keys.add("antipoison");keys.add("antivenom");return java.util.Collections.unmodifiableSet(keys); }
    public boolean enabled() { return enabled; }
    public boolean previewsEnabled() { return running && previews; }
    public List<Overlay> overlays() { return List.of(overlay, consumables); }
    public void onConfigChanged(ConfigChanged event) { if (GROUP.equals(event.getGroup())) reload(); }
    public void onGameStateChanged(GameStateChanged event)
    { if (event.getGameState() != GameState.LOGGED_IN) clear(); }
    /** Polling on GameTick avoids deriving durations from unrelated or stale var events. */
    public void onVarbitChanged(VarbitChanged event) { }

    public void onGameTick()
    {
        String current = configs.getRSProfileKey();
        if (!running || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
            || current == null || current.isEmpty()) { if (profile != null) clear(); return; }
        if (!current.equals(profile))
        { clear(); profile = current; baselineTick = client.getTickCount(); }
        if (!enabled && !boosts) { activeEffectKeys=java.util.Collections.emptySet();reading = Reading.EMPTY; return; }
        int tick = client.getTickCount();
        List<Row> effectRows = new ArrayList<>(), boostRows = new ArrayList<>();
        java.util.Set<String> activeKeys=new java.util.LinkedHashSet<>();
        if (enabled)
        {
            int combat = client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME);
            int bastion = client.getVarbitValue(VarbitID.DIVINEBASTION_POTION_TIME);
            int battlemage = client.getVarbitValue(VarbitID.DIVINEBATTLEMAGE_POTION_TIME);
            for (Spec spec : SPECS)
            {
                int raw = Math.max(0, client.getVarbitValue(spec.varbit));
                if ("stamina".equals(spec.key) && client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 1) raw = 0;
                if (raw > 0) activeKeys.add(spec.key);
                if ((spec.key.equals("divine-attack") || spec.key.equals("divine-strength")) && combat >= raw) raw = 0;
                if (spec.key.equals("divine-defence") && Math.max(combat, Math.max(bastion, battlemage)) >= raw) raw = 0;
                if (spec.key.equals("divine-ranging") && bastion >= raw) raw = 0;
                if (spec.key.equals("divine-magic") && battlemage >= raw) raw = 0;
                observe(effectRows, spec.key, spec.label, raw, spec.unit, tick);
            }
            int poison = client.getVarpValue(VarPlayerID.POISON);
            if (poison > 0) effectRows.add(new Row(poison >= 1_000_000 ? "Venom" : "Poison", "ACTIVE", true));
            // The value below -38 includes venom immunity, then transitions into poison immunity.
            observe(effectRows, "antipoison", "Poison immunity", poison < 0 && poison >= -38 ? -poison : 0, 30, tick);
            observe(effectRows, "antivenom", "Venom immunity", poison < -38 ? -poison - 38 : 0, 30, tick);
            if(poison<0&&poison>=-38)activeKeys.add("antipoison");if(poison< -38)activeKeys.add("antivenom");
        }
        if (boosts) for (Skill skill : Skill.values())
        {
            if (skill == Skill.OVERALL || skill == Skill.HITPOINTS || skill == Skill.PRAYER) continue;
            int base = client.getRealSkillLevel(skill), boosted = client.getBoostedSkillLevel(skill);
            if (base <= 0 || boosted == base) continue;
            int change = boosted - base;
            boostRows.add(new Row(skill.getName(), boosted + "/" + base + " (" + (change > 0 ? "+" : "") + change + ")", change < 0));
        }
        if(enabled)effectRows.addAll(additionalRows.get());activeEffectKeys=java.util.Collections.unmodifiableSet(activeKeys);
        reading = new Reading(effectRows, boostRows); refresh();
    }

    private void observe(List<Row> rows, String key, String label, int raw, int unit, int tick)
    {
        NativeClock clock = clocks.computeIfAbsent(key, ignored -> new NativeClock());
        Range range = clock.observe(raw, unit, tick);
        if (raw <= 0) return;
        boolean warning = range.highTicks <= warningSeconds * 1000L / 600;
        rows.add(new Row(label, range.text(), warning, range.highTicks, clock.observedWindowTicks, !range.synchronizedPhase));
        if (alerts && warning && !clock.warned && tick - baselineTick > 3 && range.synchronizedPhase)
        { notifier.notify("Runewarden: " + label + " · " + range.text()); clock.warned = true; }
    }

    /** A native value can be a coarse bucket. Initial partial buckets are shown as a range. */
    static final class NativeClock
    {
        int previous = -1, changedTick;
        boolean synchronizedPhase, warned;
        long observedWindowTicks;
        Range observe(int raw, int unit, int tick)
        {
            raw = Math.max(0, Math.min(1_000_000, raw));
            if (raw != previous)
            {
                synchronizedPhase = previous >= 0 || unit == 1;
                if (raw > previous) { warned = false; observedWindowTicks=(long)raw*unit; }
                if(raw==0)observedWindowTicks=0;
                previous = raw; changedTick = tick;
            }
            long high = (long) raw * unit;
            if (synchronizedPhase) high = Math.max(0, high - Math.max(0, tick - changedTick));
            // If the server has not refreshed a coarse bucket, retain uncertainty instead of declaring expiry.
            if (raw > 0 && high == 0) synchronizedPhase = false;
            if (!synchronizedPhase) high = (long) raw * unit;
            long low = synchronizedPhase || unit == 1 ? high : (long) Math.max(0, raw - 1) * unit;
            return new Range(low, high, synchronizedPhase || unit == 1);
        }
    }

    static final class Range
    {
        final long lowTicks, highTicks; final boolean synchronizedPhase;
        Range(long low, long high, boolean phase) { lowTicks = low; highTicks = high; synchronizedPhase = phase; }
        String text()
        {
            String high = duration(highTicks);
            return lowTicks == highTicks ? high : duration(lowTicks) + "–" + high;
        }
    }
    static String duration(long ticks)
    { long seconds = (Math.max(0, ticks) * 600 + 999) / 1000; return seconds / 60 + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60); }

    private void reload()
    {
        enabled = option("effects", true); boosts = option("boosts", true); previews = option("previews", true); alerts = option("alerts", false);
        String value = configs.getConfiguration(GROUP, "warningSeconds");
        try { warningSeconds = Math.max(3, Math.min(120, Integer.parseInt(value))); }
        catch (RuntimeException ignored) { warningSeconds = 15; }
        if (!enabled) activeEffectKeys = Collections.emptySet();
        Reading before = reading; reading = new Reading(enabled ? before.effects : Collections.emptyList(),boosts ? before.boosts : Collections.emptyList());
        refresh();
    }
    private boolean option(String key, boolean fallback)
    { String value = configs.getConfiguration(GROUP, key); return value == null ? fallback : Boolean.parseBoolean(value); }
    private JCheckBox toggle(String title, String key, boolean selected)
    {
        JCheckBox box = new JCheckBox(title, selected); box.setOpaque(false); box.setForeground(SidebarUi.TEXT); box.setFont(GuideText.FONT);
        box.addActionListener(event -> { configs.setConfiguration(GROUP, key, box.isSelected()); reload(); }); return box;
    }
    public JPanel createPanel()
    {
        panel = SidebarUi.column(); panel.add(SidebarUi.heading("Effects & supplies"));
        values = SidebarUi.column(); panel.add(values);
        JPanel settings = SidebarUi.column();
        settings.add(toggle("Effects HUD", "effects", enabled)); settings.add(toggle("Show stat changes", "boosts", boosts));
        settings.add(toggle("Food & potion previews", "previews", previews)); settings.add(toggle("Expiry alerts", "alerts", alerts));
        JSpinner seconds = new JSpinner(new SpinnerNumberModel(warningSeconds, 3, 120, 1));
        seconds.addChangeListener(event -> { configs.setConfiguration(GROUP, "warningSeconds", seconds.getValue()); reload(); });
        settings.add(SidebarUi.field("Warn below (seconds)", seconds)); panel.add(SidebarUi.disclosure("Display & alerts", settings));
        panel.addHierarchyListener(event -> { if ((event.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && panel.isShowing()) refresh(); });
        render(reading); return panel;
    }
    private void refresh()
    {
        if (values == null || panel == null || !panel.isShowing()) return;
        Reading captured = snapshot(); long generation = epoch;
        SwingUtilities.invokeLater(() ->
        {
            if (values == null || generation != epoch) return;
            render(captured);
        });
    }
    private void render(Reading captured)
    {
        values.removeAll();
        if (!running || profile == null || !profile.equals(configs.getRSProfileKey())) values.add(SidebarUi.label("Log in to view effects."));
        else if (captured.effects.isEmpty() && captured.boosts.isEmpty()) values.add(SidebarUi.label("No active effects or stat changes."));
        else
        {
            for (Row row : captured.effects) values.add(SidebarUi.label(row.label + " · " + row.value));
            if (!captured.boosts.isEmpty())
            { values.add(SidebarUi.heading("Stats")); for (Row row : captured.boosts) values.add(SidebarUi.label(row.label + " · " + row.value)); }
        }
        values.revalidate(); values.repaint();
    }
}
