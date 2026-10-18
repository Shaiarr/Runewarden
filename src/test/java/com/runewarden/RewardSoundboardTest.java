/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.client.audio.AudioPlayer;
import org.junit.Before;
import org.junit.Test;
import static com.runewarden.RewardSoundboard.SoundPreset.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RewardSoundboardTest
{
    private ScheduledExecutorService executor;
    private final Deque<Runnable> work = new ArrayDeque<>();
    private final Deque<Runnable> delayed = new ArrayDeque<>();
    private final List<Long> delays = new ArrayList<>();
    private final AtomicLong clock = new AtomicLong();
    private final RecordingPlayer audio = new RecordingPlayer();

    @Before public void scheduler()
    {
        executor = mock(ScheduledExecutorService.class);
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(call -> {
            delayed.add(call.getArgument(0)); delays.add(call.getArgument(1)); return mock(ScheduledFuture.class);
        });
    }

    private RewardSoundboard board() { return new RewardSoundboard(executor, audio, clock::get); }
    private void runWork() { while (!work.isEmpty()) work.remove().run(); }
    private void runDelayed() { delayed.remove().run(); runWork(); }

    @Test public void everyBundledPresetHasDistinctShortBoundedPcmWithoutClipping() throws Exception
    {
        Set<String> hashes = new HashSet<>(); int total = 0;
        for (RewardSoundboard.SoundPreset preset : RewardSoundboard.SoundPreset.values())
        {
            if (preset == SILENT) continue;
            RewardSoundboard.Decoded decoded = RewardSoundboard.load(preset);
            ByteBuffer header = ByteBuffer.wrap(decoded.wave).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(22_050, header.getInt(24)); assertEquals(16, header.getShort(34)); assertEquals(1, header.getShort(22));
            assertEquals(decoded.samples.length, header.getInt(40));
            double duration = decoded.samples.length * 1000.0 / 44_100;
            assertEquals(preset.durationMillis(), duration, 3);
            assertTrue(decoded.samples.length <= 132_300); total += decoded.samples.length;
            int peak = 0;
            for (int i = 0; i < decoded.samples.length; i += 2)
                peak = Math.max(peak, Math.abs((short) ((decoded.samples[i] & 255) | decoded.samples[i + 1] << 8)));
            assertTrue("Sound must be audible", peak > 1000);
            assertTrue("Sound should have headroom", peak < 24_000);
            hashes.add(Arrays.toString(MessageDigest.getInstance("SHA-256").digest(decoded.samples)));
        }
        assertEquals(6, hashes.size()); assertTrue("Original fixed asset set should stay small", total < 200_000);
    }

    @Test public void playbackIsOffCallerThreadAndEventStormRetainsOnlyNewestPendingSound() throws Exception
    {
        RewardSoundboard board = board();
        board.play(WHITE_CHIME, 50); board.play(GREEN_CHIME, 50); board.play(GOLD_FANFARE, 50);
        assertEquals(1, work.size()); assertEquals(0, audio.attempts);
        runWork();
        assertEquals(1, audio.waves.size());
        assertArrayEquals(RewardSoundboard.waveAtVolume(RewardSoundboard.load(GOLD_FANFARE), 50), audio.waves.get(0));
        assertEquals(RewardSoundboard.Availability.AVAILABLE, board.availability());
    }

    @Test public void busyPlaybackKeepsOnlyNewestRequestAndWaitsForTheDurationGuard() throws Exception
    {
        RewardSoundboard board = board(); board.play(WHITE_CHIME, 100); runWork();
        board.play(BLUE_CHIME, 100); board.play(GOLD_FANFARE, 100); runWork();
        assertEquals(1, audio.waves.size()); assertEquals(1, delayed.size());
        assertEquals(Arrays.asList(530L), delays);
        clock.set(529); runDelayed();
        assertEquals(1, audio.waves.size()); assertEquals(1, delayed.size());
        assertEquals(Arrays.asList(530L, 1L), delays);
        clock.set(530); runDelayed();
        assertEquals(2, audio.waves.size()); assertTrue(delayed.isEmpty());
        assertArrayEquals(RewardSoundboard.load(GOLD_FANFARE).wave, audio.waves.get(1));
    }

    @Test public void stopBeforeWorkerRunsCancelsPendingPlayback()
    {
        RewardSoundboard board = board(); board.play(PURPLE_STING, 100); board.stop(); runWork();
        assertEquals(0, audio.attempts); assertEquals(RewardSoundboard.Availability.UNKNOWN, board.availability());
    }

    @Test public void stopCancelsWaitingSoundButAlreadySubmittedAudioHasNoPublicStopHandle()
    {
        RewardSoundboard board = board(); board.play(GOLD_FANFARE, 100); runWork();
        board.play(WHITE_CHIME, 100); board.stop();
        clock.set(1700); runDelayed();
        assertEquals(1, audio.waves.size()); assertTrue(delayed.isEmpty());
        board.play(BLUE_CHIME, 100); runWork(); assertEquals(2, audio.waves.size());
    }

    @Test public void cancellationWhileNativePlayerRunsPreventsAnyQueuedFollowup()
    {
        RewardSoundboard board = board();
        audio.onPlay = () -> { board.play(GOLD_FANFARE, 100); board.stop(); };
        board.play(WHITE_CHIME, 100); runWork();
        assertEquals(1, audio.attempts); assertTrue(work.isEmpty()); assertTrue(delayed.isEmpty());
    }

    @Test public void replacementDuringNativeHandoffWaitsRatherThanStartingOverlappingAudio() throws Exception
    {
        RewardSoundboard board = board();
        audio.onPlay = () -> { audio.onPlay = () -> { }; board.play(GOLD_FANFARE, 100); };
        board.play(WHITE_CHIME, 100); runWork();
        assertEquals(1, audio.attempts); assertEquals(1, delayed.size());
        clock.set(530); runDelayed();
        assertEquals(2, audio.attempts); assertArrayEquals(RewardSoundboard.load(GOLD_FANFARE).wave, audio.waves.get(1));
    }

    @Test public void unavailableOutputIsGracefulDropsQueuedRequestsAndRetriesAfterCooldown()
    {
        RewardSoundboard board = board(); audio.fail = true;
        audio.onPlay = () -> board.play(GOLD_FANFARE, 60);
        board.play(WHITE_CHIME, 60); runWork();
        assertEquals(RewardSoundboard.Availability.UNAVAILABLE, board.availability()); assertEquals(1, audio.attempts);
        board.play(GREEN_CHIME, 60); runWork(); assertEquals(1, audio.attempts);
        clock.set(30_000); audio.fail = false; audio.onPlay = () -> { };
        board.play(GREEN_CHIME, 60); runWork();
        assertEquals(2, audio.attempts); assertEquals(RewardSoundboard.Availability.AVAILABLE, board.availability());
    }

    @Test public void publicPlayerReceivesValidWaveWithIndependentSoftwareVolumeAndNeutralNativeGain() throws Exception
    {
        RewardSoundboard board = board(); board.play(WHITE_CHIME, 35); runWork();
        RewardSoundboard.Decoded source = RewardSoundboard.load(WHITE_CHIME);
        byte[] played = audio.waves.get(0);
        assertArrayEquals(Arrays.copyOf(source.wave, 44), Arrays.copyOf(played, 44));
        assertArrayEquals(RewardSoundboard.scaleVolume(source.samples, 35), Arrays.copyOfRange(played, 44, played.length));
        assertEquals(Arrays.asList(0f), audio.gains);
        clock.set(530); board.play(WHITE_CHIME, 100); runWork();
        assertArrayEquals(source.wave, audio.waves.get(1));
    }

    @Test public void softwareVolumeScalesSignedSamplesAndNeverMutatesCachedPcm()
    {
        byte[] original = {(byte) 255, 127, 0, (byte) 128}; byte[] copy = original.clone();
        assertArrayEquals(new byte[]{0, 64, 0, (byte) 192}, RewardSoundboard.scaleVolume(original, 50));
        assertArrayEquals(new byte[4], RewardSoundboard.scaleVolume(original, 0));
        assertArrayEquals(copy, original); assertArrayEquals(copy, RewardSoundboard.scaleVolume(original, 200));
    }

    @Test public void silentOrZeroVolumeCancelsPendingWithoutCallingNativePlayer()
    {
        RewardSoundboard board = board(); board.play(BLUE_CHIME, 100); board.play(SILENT, 100); runWork();
        board.play(GOLD_FANFARE, 100); board.play(GOLD_FANFARE, 0); runWork(); assertEquals(0, audio.attempts);
    }

    @Test public void malformedOrOversizedWaveIsRejectedBeforeNativePlayback() throws Exception
    {
        byte[] original = RewardSoundboard.load(WHITE_CHIME).wave;
        byte[] invalid = original.clone(); invalid[22] = 2;
        assertThrows(IOException.class, () -> RewardSoundboard.decode(invalid));
        assertThrows(IOException.class, () -> RewardSoundboard.decode(Arrays.copyOf(original, 132_345)));
        assertThrows(IOException.class, () -> RewardSoundboard.decode(Arrays.copyOf(original, original.length - 1)));
        assertThrows(IOException.class, () -> RewardSoundboard.decode(new byte[12]));
    }

    @Test public void executorRejectionDropsRequestWithoutBreakingFuturePlayback()
    {
        RewardSoundboard board = board();
        doThrow(new RejectedExecutionException()).when(executor).execute(any(Runnable.class));
        board.play(WHITE_CHIME, 100); assertEquals(0, audio.attempts);
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
        board.play(BLUE_CHIME, 100); runWork(); assertEquals(1, audio.attempts);
    }

    @Test public void rejectedDelayedWorkerCannotPlayDroppedSoundOrBypassDurationGuard() throws Exception
    {
        RewardSoundboard board = board(); board.play(WHITE_CHIME, 100); runWork();
        doThrow(new RejectedExecutionException()).when(executor)
            .schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        board.play(GOLD_FANFARE, 100); runWork(); assertEquals(1, audio.attempts);
        clock.set(530); board.play(BLUE_CHIME, 100); runWork();
        assertEquals(2, audio.attempts); assertTrue(delayed.isEmpty());
        assertArrayEquals(RewardSoundboard.load(BLUE_CHIME).wave, audio.waves.get(1));
    }

    @Test public void savedIdsAreStableAndUnknownValuesHaveSafeFallback()
    {
        for (RewardSoundboard.SoundPreset preset : RewardSoundboard.SoundPreset.values())
        { assertSame(preset, RewardSoundboard.SoundPreset.fromId(preset.id())); assertSame(preset, RewardSoundboard.SoundPreset.fromId(preset.name())); }
        assertSame(WHITE_CHIME, RewardSoundboard.SoundPreset.fromId("removed-preset"));
        assertSame(WHITE_CHIME, RewardSoundboard.SoundPreset.fromId(null));
    }

    private static final class RecordingPlayer extends AudioPlayer
    {
        final List<byte[]> waves = new ArrayList<>(); final List<Float> gains = new ArrayList<>();
        Runnable onPlay = () -> { }; int attempts; boolean fail;
        @Override public void play(InputStream stream, float gain) throws IOException
        {
            attempts++; onPlay.run();
            if (fail) throw new IOException("No audio device");
            waves.add(stream.readAllBytes()); gains.add(gain);
        }
    }
}
