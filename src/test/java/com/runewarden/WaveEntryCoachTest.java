/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import java.lang.reflect.Proxy;

public class WaveEntryCoachTest
{
    private Client client;
    private Player player;
    private WorldView view;
    private final class Config implements RunewardenConfig
    {
        boolean enabled = true;
        int target = 1;
        @Override public boolean waveEntryCoach() { return enabled; }
        @Override public int fightCavesTargetRotation() { return target; }
        @Override public boolean infernoOpeningOnEntry() { return true; }
        @Override public boolean colosseumOpeningOnEntry() { return true; }
    }
    private Config config;
    private GameState state;
    private boolean instanced;
    private int region, minute, second;
    private WorldPoint point;
    private GuideCatalog catalog;
    private GuideMarkerOverlay markers;
    private WaveEntryCoach coach;
    private AtomicLong clock;
    private GuideCatalog.MarkerPack[] caves;
    private GuideCatalog.MarkerPack inferno, colosseum;

    @Before public void prepare()
    {
        state = GameState.LOGGED_IN;
        client = fake(Client.class, (name, args) -> {
            switch (name)
            {
                case "getGameState": return state;
                case "getLocalPlayer": return player;
                case "getTopLevelWorldView": return view;
                case "getVarpValue": assertEquals(VarPlayerID.DATE_MINUTES, args[0]); return minute;
                case "getVarbitValue": assertEquals(VarbitID.DATE_SECONDS_PAST_MINUTE, args[0]); return second;
                default: throw new AssertionError("Unexpected client access: " + name);
            }
        });
        player = fake(Player.class, (name, args) -> {
            if ("getWorldLocation".equals(name)) return point;
            throw new AssertionError("Unexpected player access: " + name);
        });
        view = fake(WorldView.class, (name, args) -> {
            if ("isInstance".equals(name)) return instanced;
            if ("getMapRegions".equals(name)) return new int[]{region};
            throw new AssertionError("Unexpected scene access: " + name);
        });
        config = new Config(); catalog = new GuideCatalog(new Gson()); markers = new GuideMarkerOverlay(client);
        caves = new GuideCatalog.MarkerPack[16];
        for (int rotation = 1; rotation <= 15; rotation++)
        {
            caves[rotation] = catalog.pack("fight-caves-r" + rotation + "-w1");
            assertNotNull(caves[rotation]);
        }
        inferno = catalog.pack("inferno-opening-low"); colosseum = catalog.pack("colosseum-wave-reference");
        assertNotNull(inferno); assertNotNull(colosseum);
        clock = new AtomicLong(1_000_000_000L);
        coach = new WaveEntryCoach(client, catalog, markers, config, Runnable::run, clock::get);
        outside(2438, 5167, 9808);
        coach.start();
    }

    private void outside(int x, int y, int region)
    {
        instanced = false; this.region = region; point = new WorldPoint(x, y, 0);
    }
    private void inside(int region)
    {
        instanced = true; this.region = region; point = new WorldPoint((region >> 8) * 64 + 20, (region & 255) * 64 + 20, 0);
    }
    private void tick(int minute, int second)
    {
        this.minute = minute; this.second = second;
        clock.addAndGet(600_000_000L); coach.onGameTick();
    }
    private void calibrated(int minute, int second)
    { tick(minute - 1, 59); tick(minute, 0); tick(minute, second); }

    @Test public void matchesEveryPinnedPredictorRotationAndTheRepeatedMinute()
    {
        int[] expected = {4, 2, 9, 11, 13, 1, 6, 15, 10, 8, 5, 3, 12, 14, 7, 4, 4};
        for (int minute = 0; minute < expected.length; minute++)
            assertEquals(expected[minute], WaveEntryCoach.rotationAt(minute));
        assertEquals(-1, WaveEntryCoach.rotationAt(-1));
        assertEquals(4, WaveEntryCoach.rotationAt(1439));
        assertEquals(4, WaveEntryCoach.rotationAt(1440));
    }

    @Test public void countdownUsesAConfirmedWindowIncludingRepeatedRotationFour()
    {
        assertEquals(0, WaveEntryCoach.secondsToTarget(5, 10, 1));
        assertEquals(3, WaveEntryCoach.secondsToTarget(5, 2, 1));
        assertEquals(715, WaveEntryCoach.secondsToTarget(5, 10, 2));
        assertEquals(15, WaveEntryCoach.secondsToTarget(15, 50, 4));
        assertEquals(-1, WaveEntryCoach.secondsToTarget(1, 60, 4));
    }

    @Test public void waitsForAnObservedServerMinuteThenShowsSelectedRotation()
    {
        tick(4, 30);
        assertFalse(coach.snapshot().ready);
        assertEquals("Syncing server minute…", coach.snapshot().secondary);
        tick(4, 59); tick(5, 0);
        assertFalse(coach.snapshot().ready);
        tick(5, 5);
        assertTrue(coach.snapshot().ready);
        assertTrue(coach.snapshot().secondary.contains("Rotation 1 available"));
        coach.selectRotation(3); tick(5, 6);
        assertFalse(coach.snapshot().ready);
        assertEquals("Wait for rotation 3", coach.snapshot().primary);
    }

    @Test public void loadsActualPredictedFirstSpawnOnceAndPreservesLaterManualChoices()
    {
        calibrated(5, 10);
        inside(9551); coach.onGameTick();
        assertSame(caves[1], markers.selectedPack());
        assertFalse(coach.snapshot().visible());
        markers.select(caves[7]);
        markers.select(null);
        for (int i = 0; i < 8; i++) coach.onGameTick();
        assertNull(markers.selectedPack());
    }

    @Test public void requestedRotationNeverOverridesTheActualRotationOnEntry()
    {
        config.target = 7;
        calibrated(5, 10);
        assertEquals("Wait for rotation 7", coach.snapshot().primary);
        inside(9551); coach.onGameTick();
        assertSame(caves[1], markers.selectedPack());
    }

    @Test public void loadingRetainsTheEntranceEdgeWithoutReapplyingOnReconnect()
    {
        calibrated(5, 10);
        coach.onGameStateChanged(GameState.LOADING);
        state = GameState.LOADING; coach.onGameTick();
        state = GameState.LOGGED_IN; inside(9551);
        coach.onGameStateChanged(GameState.LOGGED_IN); coach.onGameTick();
        assertSame(caves[1], markers.selectedPack());
        markers.select(caves[7]);
        coach.onGameStateChanged(GameState.CONNECTION_LOST);
        coach.onGameStateChanged(GameState.LOGGED_IN); coach.onGameTick();
        assertSame(caves[7], markers.selectedPack());
    }

    @Test public void loginInsideNeverLoadsWaveOneOverAnExistingRun()
    {
        inside(9551); coach.onGameTick(); coach.onGameTick();
        assertNull(markers.selectedPack());
        coach.stop(); coach.start(); coach.onGameTick();
        assertNull(markers.selectedPack());
    }

    @Test public void uncertainBoundariesAndUncalibratedEntryNeverGuessAPack()
    {
        tick(5, 10); inside(9551); coach.onGameTick();
        assertNull(markers.selectedPack());
        outside(2438, 5167, 9808); calibrated(5, 49); tick(5, 50);
        assertTrue(coach.snapshot().secondary.contains("Wait for confirmation"));
        inside(9551); coach.onGameTick();
        assertNull(markers.selectedPack());
    }

    @Test public void staleOrDiscontinuousServerClockCannotLoadAnOpening()
    {
        calibrated(5, 10); clock.addAndGet(6_000_000_000L);
        inside(9551); coach.onGameTick();
        assertNull(markers.selectedPack());
        outside(2438, 5167, 9808); calibrated(5, 10); tick(9, 10);
        assertFalse(coach.snapshot().ready);
        inside(9551); coach.onGameTick();
        assertNull(markers.selectedPack());
    }

    @Test public void disabledCoachAndDistantLobbyLocationsDoNothing()
    {
        config.enabled = false;
        calibrated(5, 10); inside(9551); coach.onGameTick();
        assertNull(markers.selectedPack());
        config.enabled = true;
        outside(2480, 5170, 9808); calibrated(5, 10);
        assertFalse(coach.snapshot().visible());
        inside(9551); coach.onGameTick(); assertNull(markers.selectedPack());
    }

    @Test public void otherArenasNeverLoadStandingPacksEvenWithLegacySettingsEnabled()
    {
        outside(2496, 5122, 9808); coach.onGameTick();
        assertFalse(coach.snapshot().visible());
        inside(9043); coach.onGameTick(); assertNull(markers.selectedPack());
        outside(1799, 9506, 7316); coach.onGameTick();
        assertFalse(coach.snapshot().visible());
        inside(7216); coach.onGameTick(); assertNull(markers.selectedPack());
    }

    @Test public void onlyAllFifteenBundledFirstWaveNpcOriginPacksPassTheEntryBoundary()
    {
        for (int rotation = 1; rotation <= 15; rotation++)
        {
            assertTrue(WaveEntryCoach.isFirstWaveSpawnPack(caves[rotation]));
            assertFalse(WaveEntryCoach.isFirstWaveSpawnPack(catalog.pack("fight-caves-r" + rotation + "-w2")));
        }
        assertFalse(WaveEntryCoach.isFirstWaveSpawnPack(inferno));
        assertFalse(WaveEntryCoach.isFirstWaveSpawnPack(colosseum));
        assertFalse(WaveEntryCoach.isFirstWaveSpawnPack(null));
        GuideCatalog.MarkerPack pack = new GuideCatalog.MarkerPack();
        pack.id = "fight-caves-r1-w1"; pack.tiles = new java.util.ArrayList<>();
        assertFalse(WaveEntryCoach.isFirstWaveSpawnPack(pack));
        GuideCatalog.Marker tile = new GuideCatalog.Marker();
        tile.regionId = 9551; tile.label = "Stand here"; pack.tiles.add(tile);
        assertFalse(WaveEntryCoach.isFirstWaveSpawnPack(pack));
        tile.label = "SPAWN · Tz-Kih"; tile.regionId = 9043;
        assertFalse(WaveEntryCoach.isFirstWaveSpawnPack(pack));
        tile.regionId = 9551; assertTrue(WaveEntryCoach.isFirstWaveSpawnPack(pack));
        pack.id = "fight-caves-r16-w1"; assertFalse(WaveEntryCoach.isFirstWaveSpawnPack(pack));
    }

    @Test public void invalidTargetsAreRejectedAndDefaultConfigShowsTheEntranceCard()
    {
        assertTrue(new RunewardenConfig() {}.waveEntryCoach());
        for (int invalid : new int[]{0, 16})
        {
            try { coach.selectRotation(invalid); fail("invalid rotation"); }
            catch (IllegalArgumentException expected) { }
        }
    }

    @Test public void stoppedLifecycleAndStalledClockCannotLeaveAnAvailableCard()
    {
        calibrated(5, 10); assertTrue(coach.snapshot().ready);
        clock.addAndGet(4_000_000_000L); coach.onGameTick();
        assertFalse(coach.snapshot().ready);
        coach.stop(); assertFalse(coach.snapshot().visible());
        coach.onGameTick(); assertFalse(coach.snapshot().visible());
        coach.start(); tick(5, 10); assertFalse(coach.snapshot().ready);
    }

    private interface Read { Object get(String name, Object[] arguments); }
    private static <T> T fake(Class<T> type, Read read)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> read.get(method.getName(), args == null ? new Object[0] : args)));
    }
}
