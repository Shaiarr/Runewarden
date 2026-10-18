/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import org.junit.Before;
import org.junit.Test;
import static com.runewarden.RewardSoundboard.SoundPreset.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RewardSoundboardTest
{
    private ScheduledExecutorService executor;
    private final Deque<Runnable> work = new ArrayDeque<>();
    private final List<Runnable> delayed = new ArrayList<>();

    @Before public void scheduler()
    {
        executor = mock(ScheduledExecutorService.class);
        doAnswer(call -> { work.add(call.getArgument(0)); return null; }).when(executor).execute(any(Runnable.class));
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS))).thenAnswer(call -> {
            delayed.add(call.getArgument(0)); return mock(ScheduledFuture.class);
        });
    }

    private void runWork() { while (!work.isEmpty()) work.remove().run(); }

    @Test public void everyBundledPresetHasDistinctShortBoundedPcmWithoutClipping() throws Exception
    {
        Set<String> hashes = new HashSet<>(); int total = 0;
        for (RewardSoundboard.SoundPreset preset : RewardSoundboard.SoundPreset.values())
        {
            if (preset == SILENT) continue;
            RewardSoundboard.Decoded decoded = RewardSoundboard.load(preset);
            assertEquals(22_050, decoded.format.getSampleRate(), 0);
            assertEquals(16, decoded.format.getSampleSizeInBits()); assertEquals(1, decoded.format.getChannels());
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
        Clip clip = mock(Clip.class); List<Integer> lengths = new ArrayList<>();
        RewardSoundboard board = new RewardSoundboard(executor, (format, pcm) -> { lengths.add(pcm.length); return clip; }, () -> 0L);
        board.play(WHITE_CHIME, 50); board.play(GREEN_CHIME, 50); board.play(GOLD_FANFARE, 50);
        assertEquals(1, work.size()); assertTrue(lengths.isEmpty()); verify(clip, never()).start();
        runWork();
        assertEquals(Arrays.asList(RewardSoundboard.load(GOLD_FANFARE).samples.length), lengths);
        verify(clip).start(); assertEquals(RewardSoundboard.Availability.AVAILABLE, board.availability());
    }

    @Test public void newerPlaybackClosesPreviousClipAndOldCleanupCannotStopNewSound()
    {
        Clip first = mock(Clip.class), second = mock(Clip.class); AtomicInteger opened = new AtomicInteger();
        RewardSoundboard board = new RewardSoundboard(executor, (format, pcm) -> opened.getAndIncrement() == 0 ? first : second, () -> 0L);
        board.play(WHITE_CHIME, 100); runWork();
        board.play(BLUE_CHIME, 100); runWork();
        org.mockito.InOrder order = inOrder(first, second);
        order.verify(first).start(); order.verify(first).stop(); order.verify(first).close(); order.verify(second).start();
        delayed.get(0).run(); verify(second, never()).stop();
        delayed.get(1).run(); verify(second).stop(); verify(second).close();
    }

    @Test public void stopBeforeWorkerRunsCancelsPendingPlayback()
    {
        AtomicInteger opened = new AtomicInteger();
        RewardSoundboard board = new RewardSoundboard(executor, (format, pcm) -> { opened.incrementAndGet(); return mock(Clip.class); }, () -> 0L);
        board.play(PURPLE_STING, 100); board.stop(); runWork();
        assertEquals(0, opened.get()); assertEquals(RewardSoundboard.Availability.UNKNOWN, board.availability());
    }

    @Test public void cancellationDuringNativeStartStillClosesTheClipAndDoesNotLeak()
    {
        Clip clip = mock(Clip.class);
        RewardSoundboard board = new RewardSoundboard(executor, (format, pcm) -> clip, () -> 0L);
        doAnswer(call -> { board.stop(); return null; }).when(clip).start();
        board.play(WHITE_CHIME, 100); runWork();
        verify(clip).start(); verify(clip).stop(); verify(clip).close(); assertTrue(work.isEmpty());
    }

    @Test public void replacementWhileMixerOpensNeverStartsTheStaleClip()
    {
        Clip stale = mock(Clip.class), fresh = mock(Clip.class); AtomicInteger opened = new AtomicInteger();
        RewardSoundboard[] holder = new RewardSoundboard[1];
        holder[0] = new RewardSoundboard(executor, (format, pcm) -> {
            if (opened.getAndIncrement() == 0) { holder[0].play(GOLD_FANFARE, 100); return stale; }
            return fresh;
        }, () -> 0L);
        holder[0].play(WHITE_CHIME, 100); runWork();
        verify(stale, never()).start(); verify(stale).close(); verify(fresh).start(); assertEquals(2, opened.get());
    }

    @Test public void unavailableOutputIsGracefulAndRetriesAfterCooldown()
    {
        AtomicLong time = new AtomicLong(1000); AtomicInteger opened = new AtomicInteger(); Clip clip = mock(Clip.class);
        RewardSoundboard board = new RewardSoundboard(executor, (format, pcm) -> {
            if (opened.getAndIncrement() == 0) throw new LineUnavailableException("No audio device");
            return clip;
        }, time::get);
        board.play(WHITE_CHIME, 60); runWork(); assertEquals(RewardSoundboard.Availability.UNAVAILABLE, board.availability());
        board.play(GREEN_CHIME, 60); runWork(); assertEquals(1, opened.get());
        time.set(32_000); board.play(GREEN_CHIME, 60); runWork();
        assertEquals(2, opened.get()); verify(clip).start(); assertEquals(RewardSoundboard.Availability.AVAILABLE, board.availability());
    }

    @Test public void softwareVolumeScalesSignedSamplesAndNeverMutatesCachedPcm()
    {
        byte[] original = {(byte) 255, 127, 0, (byte) 128}; byte[] copy = original.clone();
        assertArrayEquals(new byte[]{0, 64, 0, (byte) 192}, RewardSoundboard.scaleVolume(original, 50));
        assertArrayEquals(new byte[4], RewardSoundboard.scaleVolume(original, 0));
        assertArrayEquals(copy, original); assertArrayEquals(copy, RewardSoundboard.scaleVolume(original, 200));
    }

    @Test public void silentOrZeroVolumeStopsWithoutOpeningMixer()
    {
        AtomicInteger opened = new AtomicInteger();
        RewardSoundboard board = new RewardSoundboard(executor, (format, pcm) -> { opened.incrementAndGet(); return mock(Clip.class); }, () -> 0L);
        board.play(SILENT, 100); board.play(GOLD_FANFARE, 0); runWork(); assertEquals(0, opened.get());
    }

    @Test public void savedIdsAreStableAndUnknownValuesHaveSafeFallback()
    {
        for (RewardSoundboard.SoundPreset preset : RewardSoundboard.SoundPreset.values())
        { assertSame(preset, RewardSoundboard.SoundPreset.fromId(preset.id())); assertSame(preset, RewardSoundboard.SoundPreset.fromId(preset.name())); }
        assertSame(WHITE_CHIME, RewardSoundboard.SoundPreset.fromId("removed-preset"));
        assertSame(WHITE_CHIME, RewardSoundboard.SoundPreset.fromId(null));
    }
}
