/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.config.ConfigManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class RewardSettingsTest
{
    @Test public void defaultTiersSeparateDropChanceFromOwnershipAndCacheReads()
    {
        ConfigManager config = mock(ConfigManager.class); RewardSettings settings = new RewardSettings(config, new Gson());
        assertSame(settings.snapshot(), settings.snapshot()); assertTrue(settings.snapshot().collectionUseOwnership);
        assertEquals(RarityTier.WHITE, settings.classifyDenominator(49)); assertEquals(RarityTier.GREEN, settings.classifyDenominator(50));
        assertEquals(RarityTier.BLUE, settings.classifyDenominator(200)); assertEquals(RarityTier.PURPLE, settings.classifyDenominator(1000));
        assertEquals(RarityTier.GOLD, settings.classifyDenominator(5000));
        assertEquals(RarityTier.WHITE, settings.classifyOwnershipPercent(25.01)); assertEquals(RarityTier.GREEN, settings.classifyOwnershipPercent(25.0));
        assertEquals(RarityTier.BLUE, settings.classifyOwnershipPercent(10.0)); assertEquals(RarityTier.PURPLE, settings.classifyOwnershipPercent(2.0));
        assertEquals(RarityTier.GOLD, settings.classifyOwnershipPercent(0.25));
        assertEquals(RarityTier.WHITE, settings.classifyOwnershipPercent(null)); assertEquals(RarityTier.WHITE, settings.classifyDenominator((Double) null));
        verify(config, times(1)).getConfiguration(RewardSettings.GROUP, RewardSettings.KEY); verifyNoMoreInteractions(config);
    }

    @Test public void globalEditsPersistAllCategoriesAndIndependentTierSounds()
    {
        ConfigManager config = mock(ConfigManager.class); AtomicReference<String> json = new AtomicReference<>();
        when(config.getConfiguration(RewardSettings.GROUP, RewardSettings.KEY)).thenAnswer(call -> json.get());
        doAnswer(call -> { json.set(call.getArgument(2)); return null; })
            .when(config).setConfiguration(eq(RewardSettings.GROUP), eq(RewardSettings.KEY), anyString());
        RewardSettings settings = new RewardSettings(config, new Gson());
        settings.update(p ->
        {
            p.musicUnlocks = false; p.combatAchievements = false; p.minTier = RarityTier.PURPLE; p.valueFloor = 500_000;
            p.durationSeconds = 10; p.scalePercent = 120; p.collectionUseOwnership = false;
            p.sound(RarityTier.GOLD).preset = "quiet-tick"; p.sound(RarityTier.GOLD).volume = 42;
        });
        RewardSettings restored = new RewardSettings(config, new Gson()); RewardSettings.Preferences p = restored.snapshot();
        assertFalse(p.musicUnlocks); assertFalse(p.combatAchievements); assertFalse(p.collectionUseOwnership);
        assertEquals(RarityTier.PURPLE, p.minTier); assertEquals(500_000, p.valueFloor); assertEquals(10, p.durationSeconds);
        assertEquals(120, p.scalePercent); assertEquals("quiet-tick", p.sound(RarityTier.GOLD).preset); assertEquals(42, p.sound(RarityTier.GOLD).volume);
        assertEquals("blue-chime", p.sound(RarityTier.BLUE).preset); assertEquals(65, p.sound(RarityTier.BLUE).volume);
        verify(config, never()).getRSProfileKey();
    }

    @Test public void invalidPreferencesAreBoundedAndMalformedDataKeepsSafeDefaults()
    {
        ConfigManager config = mock(ConfigManager.class); RewardSettings settings = new RewardSettings(config, new Gson());
        settings.update(p ->
        {
            p.valueFloor = -9; p.durationSeconds = 1000; p.scalePercent = -1;
            p.greenDenominator = 9000; p.blueDenominator = 2; p.ownershipThresholds = new double[]{1, 2};
            p.sound(RarityTier.BLUE).volume = 900; p.sound(RarityTier.BLUE).preset = "https://invalid/external.wav";
        });
        RewardSettings.Preferences p = settings.snapshot(); assertEquals(0, p.valueFloor); assertEquals(20, p.durationSeconds);
        assertEquals(60, p.scalePercent); assertEquals(50, p.greenDenominator, 0); assertArrayEquals(new double[]{25, 10, 2, .25}, p.ownershipThresholds, 0);
        assertEquals(100, p.sound(RarityTier.BLUE).volume); assertEquals("blue-chime", p.sound(RarityTier.BLUE).preset);
        when(config.getConfiguration(RewardSettings.GROUP, RewardSettings.KEY)).thenReturn("not json"); settings.reload();
        assertEquals(6, settings.snapshot().durationSeconds); assertTrue(settings.snapshot().enabled);
        when(config.getConfiguration(RewardSettings.GROUP, RewardSettings.KEY)).thenReturn("{\"version\":99,\"enabled\":false}"); settings.reload();
        assertTrue(settings.snapshot().enabled);
    }

    @Test public void probabilityAndOwnershipThresholdsAreEditableWithoutCrossContamination()
    {
        RewardSettings settings = new RewardSettings(mock(ConfigManager.class), new Gson());
        settings.update(p ->
        {
            p.greenDenominator = 10; p.blueDenominator = 20; p.purpleDenominator = 100; p.goldDenominator = 500;
            p.ownershipThresholds = new double[]{50, 20, 5, 1};
        });
        assertEquals(RarityTier.GOLD, settings.classifyDenominator(500)); assertEquals(RarityTier.GREEN, settings.classifyOwnershipPercent(25.0));
        assertEquals(RarityTier.WHITE, settings.classifyOwnershipPercent(-1.0)); assertEquals(RarityTier.WHITE, settings.classifyOwnershipPercent(Double.NaN));
        assertEquals(RarityTier.WHITE, settings.classifyDenominator(Double.POSITIVE_INFINITY));
    }

    @Test public void delayedDraftChangesCannotAlterPublishedSettingsAndResetRestoresDefaults()
    {
        RewardSettings settings = new RewardSettings(mock(ConfigManager.class), new Gson());
        AtomicReference<RewardSettings.Preferences> draft = new AtomicReference<>();
        settings.update(p -> { draft.set(p); p.sound(RarityTier.GOLD).volume = 20; });
        draft.get().sound(RarityTier.GOLD).volume = 99; draft.get().ownershipThresholds[0] = 90;
        assertEquals(20, settings.snapshot().sound(RarityTier.GOLD).volume); assertEquals(25, settings.snapshot().ownershipThresholds[0], 0);
        settings.reset(); assertEquals(65, settings.snapshot().sound(RarityTier.GOLD).volume); assertEquals(RarityTier.BLUE, settings.snapshot().minTier);
    }

    @Test public void notificationRetainsSeparateFactsAndSanitizesOnlyPresentationText()
    {
        RewardNotification notification = new RewardNotification(RewardNotification.Kind.COLLECTION_LOG,
            "<col=ff0000>Collection log</col>", "Kodai insignia", -9, 0, RarityTier.BLUE,
            "Snapshot\nreference", "Chambers of Xeric", "1/30 of purple rewards", "10% of tracked accounts", true);
        assertEquals("Collection log", notification.title); assertEquals("Snapshot reference", notification.detail);
        assertEquals(-1, notification.itemId); assertEquals(1, notification.quantity);
        assertEquals("1/30 of purple rewards", notification.oddsText); assertEquals("10% of tracked accounts", notification.ownershipText);
        assertTrue(notification.createdAtMillis > 0); assertTrue(notification.rarityKnown);
    }
}
