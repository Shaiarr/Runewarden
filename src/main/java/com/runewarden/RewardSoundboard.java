/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.audio.AudioPlayer;

/** Bundled reward sounds through RuneLite's audio service, with one replaceable pending request. */
@Singleton
public final class RewardSoundboard
{
    public enum Availability { UNKNOWN, AVAILABLE, UNAVAILABLE }

    public enum SoundPreset
    {
        WHITE_CHIME("white-chime", "White · Soft chime", 280),
        GREEN_CHIME("green-chime", "Green · Rising chime", 430),
        BLUE_CHIME("blue-chime", "Blue · Crystal chime", 650),
        PURPLE_STING("purple-sting", "Purple · Arcane sting", 950),
        GOLD_FANFARE("gold-fanfare", "Gold · Warden fanfare", 1450),
        QUIET_TICK("quiet-tick", "Quiet · Gentle tick", 180),
        SILENT("silent", "Silent", 0);

        private final String id, displayName;
        private final int durationMillis;
        SoundPreset(String id, String displayName, int durationMillis)
        { this.id = id; this.displayName = displayName; this.durationMillis = durationMillis; }
        public String id() { return id; }
        public String displayName() { return displayName; }
        public int durationMillis() { return durationMillis; }
        public String resourcePath() { return this == SILENT ? null : "/com/runewarden/sounds/" + id + ".wav"; }
        public static SoundPreset fromId(String id)
        {
            if (id != null) for (SoundPreset preset : values())
                if (preset.id.equalsIgnoreCase(id) || preset.name().equalsIgnoreCase(id)) return preset;
            return WHITE_CHIME;
        }
        @Override public String toString() { return displayName; }
    }

    static final class Decoded
    {
        final byte[] wave, samples;
        Decoded(byte[] wave) { this.wave = wave; this.samples = Arrays.copyOfRange(wave, 44, wave.length); }
    }

    private static final int MAX_PCM_BYTES = 132_300; // Three seconds of mono 22,050 Hz, 16-bit PCM.
    private static final long PLAYBACK_GUARD_MILLIS = 250;
    private static final long RETRY_DELAY_MILLIS = 30_000;
    private final ScheduledExecutorService executor;
    private final AudioPlayer audio;
    private final LongSupplier clock;
    private final Map<SoundPreset, Decoded> decoded = new EnumMap<>(SoundPreset.class);
    private volatile Availability availability = Availability.UNKNOWN;
    private SoundRequest pending;
    private boolean workerScheduled, pacing;
    private long generation, retryAfter, nextPlaybackAt;

    private static final class SoundRequest
    {
        final SoundPreset preset;
        final int volume;
        final long generation;
        SoundRequest(SoundPreset preset, int volume, long generation)
        { this.preset = preset; this.volume = volume; this.generation = generation; }
    }

    @Inject public RewardSoundboard(ScheduledExecutorService executor, AudioPlayer audio)
    { this(executor, audio, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime())); }

    RewardSoundboard(ScheduledExecutorService executor, AudioPlayer audio, LongSupplier clock)
    { this.executor = executor; this.audio = audio; this.clock = clock; }

    /** Never reads resources or calls the audio service on the caller's thread. */
    public void play(SoundPreset preset, int volume)
    {
        if (preset == null || preset == SoundPreset.SILENT || volume <= 0) { stop(); return; }
        synchronized (this)
        {
            if (availability == Availability.UNAVAILABLE && clock.getAsLong() < retryAfter) return;
            pending = new SoundRequest(preset, Math.min(100, volume), ++generation);
            scheduleWorker();
        }
    }

    /**
     * Cancels pending playback. RuneLite's public player has no stop handle:
     * a sound already submitted finishes naturally. Bundled audio is at most 1.45 seconds long.
     */
    public synchronized void stop()
    {
        ++generation; pending = null;
    }

    public Availability availability() { return availability; }

    private void scheduleWorker()
    {
        if (workerScheduled || pending == null) return;
        workerScheduled = true;
        long delay = pacing ? Math.max(0, nextPlaybackAt - clock.getAsLong()) : 0;
        try
        {
            if (delay > 0) executor.schedule(this::drain, delay, TimeUnit.MILLISECONDS);
            else executor.execute(this::drain);
        }
        catch (java.util.concurrent.RejectedExecutionException ignored) { workerScheduled = false; pending = null; }
    }

    private void drain()
    {
        while (true)
        {
            SoundRequest request;
            synchronized (this)
            {
                if (pending == null) { workerScheduled = false; return; }
                if (availability == Availability.UNAVAILABLE && clock.getAsLong() < retryAfter)
                { pending = null; workerScheduled = false; return; }
                if (pacing && clock.getAsLong() < nextPlaybackAt)
                { workerScheduled = false; scheduleWorker(); return; }
                pacing = false;
                request = pending; pending = null;
            }
            try
            {
                Decoded sound = decoded.get(request.preset);
                if (sound == null) { sound = load(request.preset); decoded.put(request.preset, sound); }
                byte[] wave = waveAtVolume(sound, request.volume);
                synchronized (this) { if (request.generation != generation) continue; }
                // AudioPlayer owns the device and native playback lifetime. Software gain
                // keeps our volume independent of device-specific native gain controls.
                try (InputStream stream = new ByteArrayInputStream(wave)) { audio.play(stream, 0f); }
                synchronized (this)
                {
                    availability = Availability.AVAILABLE; retryAfter = 0;
                    // The public service cannot cut off a previous sound. Serialize our
                    // short presets by their duration plus a guard, retaining only the newest waiting request.
                    pacing = true;
                    nextPlaybackAt = clock.getAsLong() + request.preset.durationMillis() + PLAYBACK_GUARD_MILLIS;
                    workerScheduled = false; scheduleWorker();
                }
                return;
            }
            catch (Exception error)
            {
                synchronized (this)
                { availability = Availability.UNAVAILABLE; retryAfter = clock.getAsLong() + RETRY_DELAY_MILLIS; }
            }
        }
    }

    static Decoded load(SoundPreset preset) throws IOException
    {
        if (preset == SoundPreset.SILENT) throw new IllegalArgumentException("Silent has no audio resource");
        InputStream resource = RewardSoundboard.class.getResourceAsStream(preset.resourcePath());
        if (resource == null) throw new IOException("Missing bundled reward sound");
        try (InputStream input = resource)
        {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int read;
            while ((read = input.read(buffer)) != -1)
            {
                if (bytes.size() + read > MAX_PCM_BYTES + 44) throw new IOException("Bundled sound exceeds size limit");
                bytes.write(buffer, 0, read);
            }
            return decode(bytes.toByteArray());
        }
    }

    /** Accept only the deterministic PCM WAV format produced by our bundled generator. */
    static Decoded decode(byte[] wave) throws IOException
    {
        if (wave.length < 44 || wave.length > MAX_PCM_BYTES + 44
            || !tag(wave, 0, "RIFF") || !tag(wave, 8, "WAVE") || !tag(wave, 12, "fmt ") || !tag(wave, 36, "data")
            || number(wave, 4, 4) != wave.length - 8 || number(wave, 16, 4) != 16
            || number(wave, 20, 2) != 1 || number(wave, 22, 2) != 1
            || number(wave, 24, 4) != 22_050 || number(wave, 28, 4) != 44_100
            || number(wave, 32, 2) != 2 || number(wave, 34, 2) != 16
            || number(wave, 40, 4) != wave.length - 44 || (wave.length - 44) % 2 != 0)
            throw new IOException("Unexpected bundled sound format");
        return new Decoded(wave);
    }

    private static boolean tag(byte[] bytes, int offset, String expected)
    {
        for (int i = 0; i < expected.length(); i++) if (bytes[offset + i] != expected.charAt(i)) return false;
        return true;
    }

    private static long number(byte[] bytes, int offset, int count)
    {
        long value = 0;
        for (int i = 0; i < count; i++) value |= (long) (bytes[offset + i] & 255) << (8 * i);
        return value;
    }

    static byte[] waveAtVolume(Decoded sound, int volume)
    {
        if (volume >= 100) return sound.wave;
        byte[] wave = sound.wave.clone();
        byte[] scaled = scaleVolume(sound.samples, volume);
        System.arraycopy(scaled, 0, wave, 44, scaled.length);
        return wave;
    }

    /** Software gain does not depend on the native output provider's gain support. */
    static byte[] scaleVolume(byte[] source, int volume)
    {
        int bounded = Math.max(0, Math.min(100, volume));
        if (bounded == 100) return source;
        byte[] scaled = new byte[source.length];
        for (int i = 0; i + 1 < source.length; i += 2)
        {
            int sample = (short) ((source[i] & 255) | source[i + 1] << 8);
            int next = (int) Math.round(sample * (bounded / 100.0));
            scaled[i] = (byte) next; scaled[i + 1] = (byte) (next >> 8);
        }
        return scaled;
    }
}
