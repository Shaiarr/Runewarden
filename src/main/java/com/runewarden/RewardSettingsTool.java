/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

/** Compact, global reward presentation and five-tier soundboard settings. */
@Singleton
public final class RewardSettingsTool
{
    private final RewardSettings settings;
    private final RewardSoundboard audio;
    private Consumer<RewardNotification> demoAction = notification -> {};
    private JPanel panel;
    private boolean refreshing;
    private JCheckBox enabled, collections, drops, music, combat, animations;
    private JComboBox<RarityTier> minTier;
    private JComboBox<String> collectionMetric;
    private JTextField valueFloor;
    private JSlider duration, scale;
    private GuideText durationLabel, scaleLabel, status;
    private final Map<RarityTier, TierControls> tiers = new EnumMap<>(RarityTier.class);
    private final JTextField[] chanceFields = new JTextField[4], ownershipFields = new JTextField[4];

    @Inject public RewardSettingsTool(RewardSettings settings, RewardSoundboard audio)
    { this.settings = settings; this.audio = audio; }
    public void setDemoAction(Consumer<RewardNotification> action)
    { demoAction = action == null ? notification -> {} : action; }

    public JPanel createPanel()
    {
        if (panel != null) return panel;
        panel = SidebarUi.column();
        JPanel general = SidebarUi.section("Reward celebrations"); panel.add(general);
        enabled = check("Enable celebrations", value -> update(p -> p.enabled = value)); general.add(enabled);
        collections = check("Collection log", value -> update(p -> p.collectionLogs = value)); general.add(collections);
        drops = check("Rare drops", value -> update(p -> p.rareDrops = value)); general.add(drops);
        music = check("Music unlocks", value -> update(p -> p.musicUnlocks = value)); general.add(music);
        combat = check("Combat achievements", value -> update(p -> p.combatAchievements = value)); general.add(combat);

        JPanel soundboard = SidebarUi.section("Soundboard"); panel.add(soundboard);
        for (RarityTier tier : RarityTier.values())
        {
            TierControls controls = new TierControls(); tiers.put(tier, controls);
            controls.body = SidebarUi.column();
            controls.header = SidebarUi.button("▸ " + tier.name(), () ->
            {
                boolean opening = !controls.body.isVisible();
                for (Map.Entry<RarityTier, TierControls> other : tiers.entrySet())
                {
                    boolean visible = opening && other.getKey() == tier;
                    other.getValue().body.setVisible(visible);
                    other.getValue().header.setText((visible ? "▾ " : "▸ ") + other.getKey().name());
                }
                panel.revalidate(); panel.repaint();
            });
            controls.header.setForeground(tier.color()); soundboard.add(controls.header);
            controls.enabled = check("Sound enabled", value -> update(p -> p.sound(tier).enabled = value)); controls.body.add(controls.enabled);
            controls.preset = new JComboBox<>(RewardSoundboard.SoundPreset.values());
            controls.preset.setFont(GuideText.FONT); controls.preset.setMaximumRowCount(7);
            controls.preset.addActionListener(event ->
            {
                RewardSoundboard.SoundPreset chosen = (RewardSoundboard.SoundPreset) controls.preset.getSelectedItem();
                if (chosen != null) update(p -> p.sound(tier).preset = chosen.id());
            });
            controls.body.add(SidebarUi.field("Preset", controls.preset));
            controls.volume = slider(0, 100); controls.volumeLabel = SidebarUi.label("");
            controls.body.add(controls.volumeLabel); controls.body.add(controls.volume);
            controls.volume.addChangeListener(event ->
            {
                controls.volumeLabel.setText("Volume · " + controls.volume.getValue() + "%");
                if (!controls.volume.getValueIsAdjusting()) update(p -> p.sound(tier).volume = controls.volume.getValue());
            });
            JButton listen = SidebarUi.button("Listen", () ->
            {
                RewardSettings.Sound sound = settings.snapshot().sound(tier);
                audio.play(RewardSoundboard.SoundPreset.fromId(sound.preset), sound.volume);
                status.setText(audio.availability() == RewardSoundboard.Availability.UNAVAILABLE ? "Audio output unavailable" : "");
            });
            JButton preview = SidebarUi.button("Popup", () -> demoAction.accept(new RewardNotification(
                RewardNotification.Kind.DEMO, "Reward preview", tier.label() + " tier", 995, 1, tier,
                "", "Preview", "", "", true)));
            controls.body.add(SidebarUi.row(listen, preview)); controls.body.setVisible(false); soundboard.add(controls.body);
        }

        panel.add(SidebarUi.disclosure("Rare-drop filters", filtersPanel()));
        panel.add(SidebarUi.disclosure("Appearance", appearancePanel()));
        panel.add(SidebarUi.disclosure("Rarity thresholds", rarityPanel()));
        status = SidebarUi.label(""); status.setForeground(new Color(240, 163, 129)); panel.add(status);
        refresh(); SidebarUi.install(panel); return panel;
    }

    private JPanel filtersPanel()
    {
        JPanel body = SidebarUi.section(null);
        minTier = new JComboBox<>(RarityTier.values()); minTier.setFont(GuideText.FONT);
        minTier.setRenderer(new DefaultListCellRenderer()
        {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
            {
                super.getListCellRendererComponent(list, value instanceof RarityTier ? ((RarityTier) value).label() : value, index, selected, focus);
                if (value instanceof RarityTier && !selected) setForeground(((RarityTier) value).color());
                return this;
            }
        });
        minTier.addActionListener(event -> update(p -> p.minTier = (RarityTier) minTier.getSelectedItem()));
        body.add(SidebarUi.field("Minimum tier", minTier));
        valueFloor = field(); body.add(SidebarUi.field("Minimum stack value (GP)", valueFloor));
        body.add(SidebarUi.button("Apply filter", () ->
        {
            try
            {
                long value = Long.parseLong(valueFloor.getText().trim().replace(",", ""));
                if (value < 0 || value > 1_000_000_000_000_000L) throw new NumberFormatException();
                update(p -> p.valueFloor = value); status.setText("");
            }
            catch (NumberFormatException invalid) { status.setText("Enter a non-negative GP value."); }
        }));
        return body;
    }

    private JPanel appearancePanel()
    {
        JPanel body = SidebarUi.section(null);
        duration = slider(2, 20); durationLabel = SidebarUi.label(""); body.add(durationLabel); body.add(duration);
        duration.addChangeListener(event ->
        {
            durationLabel.setText("Duration · " + duration.getValue() + " seconds");
            if (!duration.getValueIsAdjusting()) update(p -> p.durationSeconds = duration.getValue());
        });
        scale = slider(60, 160); scaleLabel = SidebarUi.label(""); body.add(scaleLabel); body.add(scale);
        scale.addChangeListener(event ->
        {
            scaleLabel.setText("Popup size · " + scale.getValue() + "%");
            if (!scale.getValueIsAdjusting()) update(p -> p.scalePercent = scale.getValue());
        });
        animations = check("Animate celebrations", value -> update(p -> p.animations = value)); body.add(animations);
        return body;
    }

    private JPanel rarityPanel()
    {
        JPanel body = SidebarUi.section(null);
        collectionMetric = new JComboBox<>(new String[]{"Ownership", "Drop chance"}); collectionMetric.setFont(GuideText.FONT);
        collectionMetric.addActionListener(event -> update(p -> p.collectionUseOwnership = collectionMetric.getSelectedIndex() == 0));
        body.add(SidebarUi.field("Collection rarity metric", collectionMetric));
        body.add(SidebarUi.heading("Ownership (%)"));
        RarityTier[] colored = {RarityTier.GREEN, RarityTier.BLUE, RarityTier.PURPLE, RarityTier.GOLD};
        for (int i = 0; i < colored.length; i++)
        { ownershipFields[i] = field(); body.add(SidebarUi.field(colored[i].label() + " ownership max (%)", ownershipFields[i])); }
        body.add(SidebarUi.heading("Drop chance (1 in)"));
        for (int i = 0; i < colored.length; i++)
        { chanceFields[i] = field(); body.add(SidebarUi.field(colored[i].label() + " denominator min", chanceFields[i])); }
        body.add(SidebarUi.button("Apply thresholds", this::applyThresholds));
        body.add(SidebarUi.button("Reset reward settings", () -> { settings.reset(); refresh(); }));
        return body;
    }

    private void applyThresholds()
    {
        try
        {
            double[] ownership = new double[4], chance = new double[4];
            for (int i = 0; i < 4; i++)
            { ownership[i] = Double.parseDouble(ownershipFields[i].getText().trim().replace(",", ".")); chance[i] = Double.parseDouble(chanceFields[i].getText().trim().replace(",", "")); }
            RewardSettings.Preferences test = new RewardSettings.Preferences();
            test.greenDenominator = chance[0]; test.blueDenominator = chance[1]; test.purpleDenominator = chance[2]; test.goldDenominator = chance[3];
            if (!RewardSettings.validOwnershipThresholds(ownership) || !RewardSettings.validThresholds(test))
            { status.setText("Ownership cutoffs must decrease; chance denominators must increase."); return; }
            update(p ->
            {
                p.ownershipThresholds = ownership; p.greenDenominator = chance[0]; p.blueDenominator = chance[1];
                p.purpleDenominator = chance[2]; p.goldDenominator = chance[3];
            });
            status.setText("");
        }
        catch (NumberFormatException invalid) { status.setText("Enter numeric rarity cutoffs."); }
    }

    private void update(Consumer<RewardSettings.Preferences> change)
    { if (!refreshing) settings.update(change); }
    public void refresh()
    {
        if (panel == null) return;
        refreshing = true;
        try
        {
            RewardSettings.Preferences p = settings.snapshot();
            enabled.setSelected(p.enabled); collections.setSelected(p.collectionLogs); drops.setSelected(p.rareDrops);
            music.setSelected(p.musicUnlocks); combat.setSelected(p.combatAchievements); animations.setSelected(p.animations);
            minTier.setSelectedItem(p.minTier); valueFloor.setText(Long.toString(p.valueFloor));
            duration.setValue(p.durationSeconds); scale.setValue(p.scalePercent);
            durationLabel.setText("Duration · " + p.durationSeconds + " seconds"); scaleLabel.setText("Popup size · " + p.scalePercent + "%");
            collectionMetric.setSelectedIndex(p.collectionUseOwnership ? 0 : 1);
            double[] chance = {p.greenDenominator, p.blueDenominator, p.purpleDenominator, p.goldDenominator};
            for (int i = 0; i < 4; i++) { ownershipFields[i].setText(number(p.ownershipThresholds[i])); chanceFields[i].setText(number(chance[i])); }
            for (Map.Entry<RarityTier, TierControls> entry : tiers.entrySet())
            {
                RewardSettings.Sound sound = p.sound(entry.getKey()); TierControls ui = entry.getValue();
                ui.enabled.setSelected(sound.enabled); ui.preset.setSelectedItem(RewardSoundboard.SoundPreset.fromId(sound.preset));
                ui.volume.setValue(sound.volume); ui.volumeLabel.setText("Volume · " + sound.volume + "%");
            }
            status.setText("");
        }
        finally { refreshing = false; }
        panel.revalidate(); panel.repaint();
    }
    private static String number(double value)
    { return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value); }
    private static JCheckBox check(String title, Consumer<Boolean> change)
    {
        JCheckBox box = new JCheckBox(title); box.setOpaque(false); box.setFont(GuideText.FONT); box.setForeground(SidebarUi.TEXT);
        box.addActionListener(event -> change.accept(box.isSelected())); return box;
    }
    private static JTextField field()
    { JTextField field = new JTextField(); field.setFont(GuideText.FONT); field.setBackground(SidebarUi.CARD); field.setForeground(SidebarUi.TEXT); return field; }
    private static JSlider slider(int min, int max)
    {
        JSlider slider = new JSlider(min, max); slider.setOpaque(false); slider.setMinimumSize(new Dimension(0, 28));
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28)); return slider;
    }
    private static final class TierControls
    {
        JPanel body; JButton header; JCheckBox enabled;
        JComboBox<RewardSoundboard.SoundPreset> preset; JSlider volume; GuideText volumeLabel;
    }
}
