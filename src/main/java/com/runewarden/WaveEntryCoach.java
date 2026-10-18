/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Entrance-only preparation. This class never observes or advances combat waves. */
@Singleton
final class WaveEntryCoach
{
    private enum Area { OTHER, CAVES_LOBBY, CAVES }
    private static final WorldPoint CAVES_ENTRANCE = new WorldPoint(2438, 5167, 0);
    private static final long SECOND = 1_000_000_000L;
    private static final long ENTRY_FRESHNESS = 5 * SECOND;
    // Fight Caves rotation table adapted from Damen's BSD-2-Clause FC Spawn Predictor.
    // Copyright (c) 2022, Damen <gh: damencs>. Full notice: licenses/spawn-predictor-BSD-2-Clause.txt.
    private static final int[] ROTATIONS = {4, 2, 9, 11, 13, 1, 6, 15, 10, 8, 5, 3, 12, 14, 7};

    static final class Snapshot
    {
        static final Snapshot EMPTY = new Snapshot("", "", "", "", false);
        final String title, primary, secondary, detail;
        final boolean ready;
        Snapshot(String title, String primary, String secondary, String detail, boolean ready)
        { this.title = title; this.primary = primary; this.secondary = secondary; this.detail = detail; this.ready = ready; }
        boolean visible() { return !title.isEmpty(); }
    }

    private final Client client;
    private final GuideCatalog catalog;
    private final GuideMarkerOverlay markers;
    private final RunewardenConfig config;
    private final Executor executor;
    private final LongSupplier nanoTime;
    private volatile boolean started;
    private volatile long epoch;
    private volatile Map<String, GuideCatalog.MarkerPack> openingPacks = Collections.emptyMap();
    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile int selectedRotation = 1;
    private int configuredRotation = 1;
    private Area previous = Area.OTHER;
    private int clockMinute = -1, clockSecond = -1, confirmedRotation = -1;
    private long clockChangedAt, confirmedAt;
    private boolean calibrated;

    @Inject WaveEntryCoach(Client client, GuideCatalog catalog, GuideMarkerOverlay markers,
        RunewardenConfig config, ScheduledExecutorService executor)
    { this(client, catalog, markers, config, executor, System::nanoTime); }

    WaveEntryCoach(Client client, GuideCatalog catalog, GuideMarkerOverlay markers,
        RunewardenConfig config, Executor executor, LongSupplier nanoTime)
    {
        this.client = client; this.catalog = catalog; this.markers = markers;
        this.config = config; this.executor = executor; this.nanoTime = nanoTime;
    }

    void start()
    {
        started = true;
        long expected = ++epoch;
        resetObservation();
        configuredRotation = selectedRotation = validRotation(config.fightCavesTargetRotation());
        // Parsing/indexing the bundled library must never block the game or Swing thread.
        executor.execute(() -> {
            Map<String, GuideCatalog.MarkerPack> loaded = new HashMap<>();
            for (int rotation = 1; rotation <= 15; rotation++) addPack(loaded, "fight-caves-r" + rotation + "-w1");
            if (started && expected == epoch) openingPacks = Collections.unmodifiableMap(loaded);
        });
    }

    private void addPack(Map<String, GuideCatalog.MarkerPack> loaded, String id)
    { GuideCatalog.MarkerPack pack = catalog.pack(id); if (isFirstWaveSpawnPack(pack)) loaded.put(id, pack); }

    /** Entry loading is restricted to bundled first-wave NPC origins, never standing tiles. */
    static boolean isFirstWaveSpawnPack(GuideCatalog.MarkerPack pack)
    {
        if (pack == null || pack.id == null || !pack.id.matches("fight-caves-r(?:[1-9]|1[0-5])-w1")
            || pack.tiles == null || pack.tiles.isEmpty()) return false;
        for (GuideCatalog.Marker tile : pack.tiles)
            if (tile == null || tile.regionId != 9551 || tile.label == null || !tile.label.startsWith("SPAWN · "))
                return false;
        return true;
    }

    void stop()
    { started = false; ++epoch; openingPacks = Collections.emptyMap(); resetObservation(); }

    /** The caller persists this preference in our own configuration group. */
    void selectRotation(int rotation)
    {
        if (rotation < 1 || rotation > 15) throw new IllegalArgumentException("Rotation must be 1–15");
        selectedRotation = rotation;
    }

    Snapshot snapshot() { return snapshot; }

    void onGameStateChanged(GameState state)
    {
        if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN || state == GameState.CONNECTION_LOST)
            resetObservation();
        else if (state != GameState.LOGGED_IN) snapshot = Snapshot.EMPTY;
        // Preserve the preceding lobby across LOADING; it is the entry edge, not a new run on login.
    }

    void onGameTick()
    {
        if (!started || !config.waveEntryCoach())
        { resetObservation(); return; }
        if (client.getGameState() != GameState.LOGGED_IN)
        { snapshot = Snapshot.EMPTY; return; }
        if (client.getLocalPlayer() == null || client.getTopLevelWorldView() == null)
        { snapshot = Snapshot.EMPTY; return; }
        int configured = validRotation(config.fightCavesTargetRotation());
        if (configured != configuredRotation) { configuredRotation = configured; selectedRotation = configured; }
        Area current = area();
        long now = nanoTime.getAsLong();
        if (current == Area.CAVES_LOBBY)
        {
            readClock(now);
            snapshot = cavesSnapshot();
        }
        else snapshot = Snapshot.EMPTY;

        if (previous == Area.CAVES_LOBBY && current == Area.CAVES && calibrated
            && confirmedRotation > 0 && now - confirmedAt >= 0 && now - confirmedAt <= ENTRY_FRESHNESS)
            loadOpening("fight-caves-r" + confirmedRotation + "-w1");

        if (current != Area.CAVES_LOBBY) resetClock();
        previous = current;
    }

    private void loadOpening(String id)
    {
        GuideCatalog.MarkerPack pack = openingPacks.get(id);
        if (isFirstWaveSpawnPack(pack)) markers.select(pack);
        // Exactly once at entry. A later manual wave, marker clear or guide choice is never replaced.
    }

    private Area area()
    {
        Player player = client.getLocalPlayer();
        WorldView view = client.getTopLevelWorldView();
        if (player == null || view == null) return Area.OTHER;
        WorldPoint point = player.getWorldLocation();
        if (view.isInstance())
        {
            if (contains(view.getMapRegions(), 9551)) return Area.CAVES;
            return Area.OTHER;
        }
        if (point == null) return Area.OTHER;
        if (contains(view.getMapRegions(), 9808) && point.distanceTo(CAVES_ENTRANCE) <= 18) return Area.CAVES_LOBBY;
        return Area.OTHER;
    }

    private static boolean contains(int[] regions, int region)
    { if (regions != null) for (int candidate : regions) if (candidate == region) return true; return false; }
    private static int validRotation(int rotation) { return Math.max(1, Math.min(15, rotation)); }

    private void readClock(long now)
    {
        int minute = client.getVarpValue(VarPlayerID.DATE_MINUTES);
        int second = client.getVarbitValue(VarbitID.DATE_SECONDS_PAST_MINUTE);
        if (minute < 0 || second < 0 || second > 59) { resetClock(); return; }
        if (minute == clockMinute && second < clockSecond) { resetClock(); return; }
        if (clockMinute >= 0 && minute != clockMinute)
        {
            boolean nextMinute = minute == clockMinute + 1 || (clockMinute == 1439 && minute == 0);
            calibrated = nextMinute;
        }
        if (minute != clockMinute || second != clockSecond) clockChangedAt = now;
        else if (now - clockChangedAt > 3 * SECOND) { resetClock(); return; }
        clockMinute = minute; clockSecond = second;
        if (calibrated && second >= 5 && second < 50)
        { confirmedRotation = rotationAt(minute); confirmedAt = now; }
        else confirmedRotation = -1;
    }

    private Snapshot cavesSnapshot()
    {
        String target = "Wait for rotation " + selectedRotation;
        if (!calibrated || clockMinute < 0)
            return new Snapshot("FIGHT CAVES · ENTRY", target, "Syncing server minute…", "Confirming the server clock", false);
        int rotation = rotationAt(clockMinute);
        boolean boundary = clockSecond < 5 || clockSecond >= 50;
        boolean ready = !boundary && rotation == selectedRotation;
        String state = ready ? "Rotation " + rotation + " available · Enter now"
            : boundary ? "Minute boundary · Wait for confirmation" : "Current rotation " + rotation;
        int wait = secondsToTarget(clockMinute, clockSecond, selectedRotation);
        String detail = ready ? "Window closes in " + (50 - clockSecond) + "s"
            : "Target window in " + formatWait(wait);
        return new Snapshot("FIGHT CAVES · ENTRY", target, state, detail, ready);
    }

    static int rotationAt(int minute)
    {
        if (minute < 0) return -1;
        int column = minute % 16;
        return ROTATIONS[(column == 0 || column == 15) ? 0 : column];
    }

    static int secondsToTarget(int minute, int second, int target)
    {
        if (minute < 0 || second < 0 || second > 59 || target < 1 || target > 15) return -1;
        for (int ahead = 0; ahead <= 16; ahead++)
        {
            if (rotationAt(minute + ahead) != target) continue;
            if (ahead == 0 && second < 50) return Math.max(0, 5 - second);
            if (ahead > 0) return ahead * 60 + 5 - second;
        }
        return -1;
    }

    private static String formatWait(int seconds)
    { return seconds < 0 ? "—" : seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s"; }

    private void resetClock()
    { clockMinute = clockSecond = confirmedRotation = -1; calibrated = false; clockChangedAt = confirmedAt = 0; }
    private void resetObservation()
    { previous = Area.OTHER; resetClock(); snapshot = Snapshot.EMPTY; }
}
