/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;

/** Global presentation preferences. Renderers read one cached snapshot without config or JSON work. */
@Singleton
public final class RewardSettings
{
    public static final String GROUP = "runewardenrewards";
    public static final String KEY = "preferencesV1";
    private static final Set<String> PRESETS = Set.of("white-chime", "green-chime", "blue-chime", "purple-sting", "gold-fanfare", "quiet-tick", "silent");
    private final ConfigManager config;
    private final Gson gson;
    private volatile Preferences preferences;
    private RunewardenDataStore data;

    public RewardSettings(ConfigManager config, Gson gson)
    { this.config = config; this.gson = gson; reload(); }

    @Inject public RewardSettings(ConfigManager config,Gson gson,RunewardenDataStore data)
    {this(config,gson);this.data=data;reload();}

    /** Cached read-only snapshot. Make edits through update so validation and persistence stay atomic. */
    public Preferences snapshot() { return preferences; }

    public synchronized void reload()
    {
        Preferences loaded = null;
        String json = config.getConfiguration(GROUP, KEY);
        if(data!=null){preferences=checked(data.read(GROUP,null,KEY,1,Preferences.class,p->p.version==1&&(json==null||json.length()<=32768),Preferences::new));return;}
        if (json != null && json.length() <= 32_768)
            try { loaded = gson.fromJson(json, Preferences.class); }
            catch (RuntimeException ignored) { /* Malformed local preferences use safe defaults. */ }
        preferences = checked(loaded);
    }

    public synchronized void update(Consumer<Preferences> edit)
    {
        Preferences next = copy(preferences); edit.accept(next); next = checked(next);
        if(data==null)config.setConfiguration(GROUP, KEY, gson.toJson(next));else if(!data.write(GROUP,null,KEY,1,next))return; preferences = next;
    }

    public synchronized void reset()
    {
        Preferences next = checked(new Preferences());if(data!=null){data.reset(new RunewardenDataStore.Address(GROUP,null,KEY));data.write(GROUP,null,KEY,1,next);}else config.setConfiguration(GROUP, KEY, gson.toJson(next)); preferences = next;
    }

    public RarityTier classifyDenominator(Double denominator)
    { return denominator == null ? RarityTier.WHITE : classifyDenominator(denominator.doubleValue()); }
    public RarityTier classifyDenominator(double denominator)
    {
        if (!Double.isFinite(denominator) || denominator < 1) return RarityTier.WHITE;
        Preferences current = preferences;
        if (denominator >= current.goldDenominator) return RarityTier.GOLD;
        if (denominator >= current.purpleDenominator) return RarityTier.PURPLE;
        if (denominator >= current.blueDenominator) return RarityTier.BLUE;
        if (denominator >= current.greenDenominator) return RarityTier.GREEN;
        return RarityTier.WHITE;
    }

    public RarityTier classifyOwnershipPercent(Double percent)
    {
        if (percent == null || !Double.isFinite(percent) || percent < 0 || percent > 100) return RarityTier.WHITE;
        double[] limits = preferences.ownershipThresholds;
        if (percent <= limits[3]) return RarityTier.GOLD;
        if (percent <= limits[2]) return RarityTier.PURPLE;
        if (percent <= limits[1]) return RarityTier.BLUE;
        if (percent <= limits[0]) return RarityTier.GREEN;
        return RarityTier.WHITE;
    }

    private static Preferences checked(Preferences input)
    {
        Preferences result = input == null || input.version != 1 ? new Preferences() : copy(input);
        result.version = 1;
        if (result.minTier == null) result.minTier = RarityTier.BLUE;
        result.valueFloor = Math.max(0, Math.min(1_000_000_000_000_000L, result.valueFloor));
        result.durationSeconds = Math.max(2, Math.min(20, result.durationSeconds));
        result.scalePercent = Math.max(60, Math.min(160, result.scalePercent));
        if (!validThresholds(result))
        { result.greenDenominator = 50; result.blueDenominator = 200; result.purpleDenominator = 1000; result.goldDenominator = 5000; }
        if (!validOwnershipThresholds(result.ownershipThresholds)) result.ownershipThresholds = new double[]{25, 10, 2, 0.25};
        Map<RarityTier, Sound> sounds = new EnumMap<>(RarityTier.class);
        for (RarityTier tier : RarityTier.values())
        {
            Sound configured = result.sounds == null ? null : result.sounds.get(tier);
            Sound sound = configured == null ? defaultSound(tier) : configured.copy();
            if (sound.preset == null || !PRESETS.contains(sound.preset)) sound.preset = defaultSound(tier).preset;
            sound.volume = Math.max(0, Math.min(100, sound.volume)); sounds.put(tier, sound);
        }
        result.sounds = Collections.unmodifiableMap(sounds);
        return result;
    }
    public static boolean validThresholds(Preferences settings)
    {
        return Double.isFinite(settings.greenDenominator) && Double.isFinite(settings.blueDenominator)
            && Double.isFinite(settings.purpleDenominator) && Double.isFinite(settings.goldDenominator)
            && settings.greenDenominator >= 1 && settings.greenDenominator < settings.blueDenominator
            && settings.blueDenominator < settings.purpleDenominator && settings.purpleDenominator < settings.goldDenominator
            && settings.goldDenominator <= 1_000_000_000_000.0;
    }
    public static boolean validOwnershipThresholds(double[] values)
    {
        if (values == null || values.length != 4) return false;
        for (double value : values) if (!Double.isFinite(value) || value <= 0 || value > 100) return false;
        return values[0] > values[1] && values[1] > values[2] && values[2] > values[3];
    }
    private static Preferences copy(Preferences source)
    {
        Preferences result = new Preferences();
        result.version = source.version; result.enabled = source.enabled; result.collectionLogs = source.collectionLogs;
        result.rareDrops = source.rareDrops; result.musicUnlocks = source.musicUnlocks; result.combatAchievements = source.combatAchievements;
        result.minTier = source.minTier; result.valueFloor = source.valueFloor; result.durationSeconds = source.durationSeconds;
        result.scalePercent = source.scalePercent; result.animations = source.animations;
        result.greenDenominator = source.greenDenominator; result.blueDenominator = source.blueDenominator;
        result.purpleDenominator = source.purpleDenominator; result.goldDenominator = source.goldDenominator;
        result.collectionUseOwnership = source.collectionUseOwnership;
        result.ownershipThresholds = source.ownershipThresholds == null ? null : source.ownershipThresholds.clone();
        result.sounds = new EnumMap<>(RarityTier.class);
        if (source.sounds != null) for (Map.Entry<RarityTier, Sound> entry : source.sounds.entrySet())
            if (entry.getKey() != null && entry.getValue() != null) result.sounds.put(entry.getKey(), entry.getValue().copy());
        return result;
    }
    private static Sound defaultSound(RarityTier tier)
    {
        String preset = tier == RarityTier.PURPLE ? "purple-sting" : tier == RarityTier.GOLD ? "gold-fanfare" : tier.name().toLowerCase(java.util.Locale.ROOT) + "-chime";
        return new Sound(true, preset, 65);
    }

    public static final class Sound
    {
        public boolean enabled = true;
        public String preset;
        public int volume = 65;
        public Sound() { }
        public Sound(boolean enabled, String preset, int volume) { this.enabled = enabled; this.preset = preset; this.volume = volume; }
        Sound copy() { return new Sound(enabled, preset, volume); }
    }

    /** Mutable only inside update's draft; production snapshots are treated as read-only. */
    public static final class Preferences
    {
        public int version = 1;
        public boolean enabled = true, collectionLogs = true, rareDrops = true, musicUnlocks = true, combatAchievements = true;
        public RarityTier minTier = RarityTier.BLUE;
        public long valueFloor;
        public int durationSeconds = 6, scalePercent = 100;
        public boolean animations = true;
        public double greenDenominator = 50, blueDenominator = 200, purpleDenominator = 1000, goldDenominator = 5000;
        public boolean collectionUseOwnership = true;
        public double[] ownershipThresholds = {25, 10, 2, 0.25};
        public Map<RarityTier, Sound> sounds = new EnumMap<>(RarityTier.class);
        public Preferences() { for (RarityTier tier : RarityTier.values()) sounds.put(tier, defaultSound(tier)); }
        public Sound sound(RarityTier tier) { return sounds.get(tier == null ? RarityTier.WHITE : tier); }
    }
}
