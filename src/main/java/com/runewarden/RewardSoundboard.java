/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;

/** Original bundled reward sounds, one active clip and one replaceable pending request. */
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

    @FunctionalInterface
    interface ClipFactory
    { Clip open(AudioFormat format, byte[] samples) throws Exception; }

    static final class Decoded
    {
        final AudioFormat format;
        final byte[] samples;
        Decoded(AudioFormat format, byte[] samples) { this.format = format; this.samples = samples; }
    }

    private static final int MAX_PCM_BYTES = 132_300; // Three seconds of mono 22,050 Hz, 16-bit PCM.
    private static final long RETRY_DELAY_MILLIS = 30_000;
    private final ScheduledExecutorService executor;
    private final ClipFactory clips;
    private final LongSupplier clock;
    private final Map<SoundPreset, Decoded> decoded = new EnumMap<>(SoundPreset.class);
    private volatile Availability availability = Availability.UNKNOWN;
    private SoundRequest pending;
    private Clip active;
    private ScheduledFuture<?> cleanup;
    private boolean workerScheduled;
    private long generation, retryAfter;

    private static final class SoundRequest
    {
        final SoundPreset preset;
        final int volume;
        final long generation;
        SoundRequest(SoundPreset preset, int volume, long generation)
        { this.preset = preset; this.volume = volume; this.generation = generation; }
    }

    @Inject public RewardSoundboard(ScheduledExecutorService executor)
    { this(executor, RewardSoundboard::openClip, System::currentTimeMillis); }

    RewardSoundboard(ScheduledExecutorService executor, ClipFactory clips, LongSupplier clock)
    { this.executor = executor; this.clips = clips; this.clock = clock; }

    /** Never reads resources, opens a mixer or waits on the caller's thread. */
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

    /** Cancels pending playback and closes the current clip on the audio worker. */
    public synchronized void stop()
    {
        ++generation; pending = null;
        scheduleWorker();
    }

    public Availability availability() { return availability; }

    private void scheduleWorker()
    {
        if (workerScheduled) return;
        workerScheduled = true;
        try { executor.execute(this::drain); }
        catch (java.util.concurrent.RejectedExecutionException ignored) { workerScheduled = false; pending = null; }
    }

    private void drain()
    {
        while (true)
        {
            SoundRequest request;
            Clip previous;
            long workGeneration;
            synchronized (this)
            {
                request = pending; pending = null;
                workGeneration = generation;
                previous = active; active = null;
                if (cleanup != null) { cleanup.cancel(false); cleanup = null; }
            }
            close(previous);
            if (request != null)
            {
                Clip opened = null;
                try
                {
                    Decoded sound = decoded.get(request.preset);
                    if (sound == null) { sound = load(request.preset); decoded.put(request.preset, sound); }
                    opened = clips.open(sound.format, scaleVolume(sound.samples, request.volume));
                    boolean accepted;
                    synchronized (this) { accepted = request.generation == generation; }
                    if (accepted)
                    {
                        opened.start();
                        synchronized (this)
                        {
                            if (request.generation == generation)
                            {
                                active = opened;
                                availability = Availability.AVAILABLE; retryAfter = 0;
                                Clip playing = opened;
                                cleanup = executor.schedule(() -> finish(request.generation, playing),
                                    request.preset.durationMillis() + 250L, TimeUnit.MILLISECONDS);
                                opened = null; // Ownership moved to active/finish.
                            }
                        }
                    }
                }
                catch (Exception error)
                {
                    synchronized (this)
                    {
                        if (active == opened) active = null;
                        availability = Availability.UNAVAILABLE; retryAfter = clock.getAsLong() + RETRY_DELAY_MILLIS;
                    }
                }
                finally { close(opened); }
            }
            synchronized (this)
            {
                if (pending == null && generation == workGeneration) { workerScheduled = false; return; }
            }
        }
    }

    private void finish(long expected, Clip playing)
    {
        synchronized (this)
        {
            if (generation != expected || active != playing) return;
            active = null; cleanup = null;
        }
        close(playing);
    }

    private static Clip openClip(AudioFormat format, byte[] samples) throws Exception
    {
        Clip clip = AudioSystem.getClip();
        try { clip.open(format, samples, 0, samples.length); return clip; }
        catch (Exception error) { close(clip); throw error; }
    }

    private static void close(Clip clip)
    {
        if (clip == null) return;
        try { clip.stop(); } catch (RuntimeException ignored) { }
        try { clip.close(); } catch (RuntimeException ignored) { }
    }

    static Decoded load(SoundPreset preset) throws Exception
    {
        if (preset == SoundPreset.SILENT) throw new IllegalArgumentException("Silent has no audio resource");
        InputStream resource = RewardSoundboard.class.getResourceAsStream(preset.resourcePath());
        if (resource == null) throw new IOException("Missing bundled reward sound");
        try (InputStream buffered = new BufferedInputStream(resource);
             AudioInputStream audio = AudioSystem.getAudioInputStream(buffered))
        {
            AudioFormat format = audio.getFormat();
            if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED || format.getSampleSizeInBits() != 16
                || format.getChannels() != 1 || format.isBigEndian() || format.getSampleRate() != 22_050)
                throw new IOException("Unexpected bundled sound format");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; int read;
            while ((read = audio.read(buffer)) != -1)
            {
                if (bytes.size() + read > MAX_PCM_BYTES) throw new IOException("Bundled sound exceeds size limit");
                bytes.write(buffer, 0, read);
            }
            return new Decoded(format, bytes.toByteArray());
        }
    }

    /** Software gain also works when a native output provider has no MASTER_GAIN control. */
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
