/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;

/** Owns only the lifetime of a manually selected boss lesson, never combat decisions. */
final class BossGuideLifecycle
{
    private final Client client;
    private final GuideCatalog catalog;
    private String selectedId;
    private Set<Long> sourceAreas = Collections.emptySet(), otherTheatreRooms = Collections.emptySet();
    private boolean entered, enteredInstance, gauntlet, tombs, sotetseg;
    private int tombsLeaveTicks;
    private static final int[] TOA_ROOMS = {14160, 15698, 15700, 14162, 14164, 15186, 15188, 14674, 14676, 15184, 15696, 14672};
    private int membershipVarbit, membershipMinimum;
    private int observedRegion, observedPlane, discoveryTicks;
    private WorldPoint discoveredAt;
    private Set<Long> chamber = Collections.emptySet();

    BossGuideLifecycle(Client client, GuideCatalog catalog)
    { this.client = client; this.catalog = catalog; }

    static boolean isBoss(GuideCatalog.Entry entry)
    { return entry != null && ("boss".equals(entry.category) || "waves".equals(entry.category)); }

    synchronized void reset()
    {
        selectedId = null;
        sourceAreas = Collections.emptySet(); otherTheatreRooms = Collections.emptySet();
        chamber = Collections.emptySet();
        entered = enteredInstance = gauntlet = tombs = sotetseg = false; tombsLeaveTicks = 0;
        discoveredAt = null; discoveryTicks = 0; membershipVarbit = -1; membershipMinimum = 1;
    }

    /** Unknown/loading scenes cannot prove an exit. A pre-entry lesson remains open. */
    synchronized boolean leftArena(GuideCatalog.Entry entry)
    {
        if (!isBoss(entry)) { reset(); return false; }
        if (!entry.id.equals(selectedId))
        {
            reset(); selectedId = entry.id;
            sourceAreas = areas(entry);
            tombs = entry.id.equals("encounter:tombs-of-amascut") || entry.id.equals("encounter:tombs-of-amascut-expert-mode");
            sotetseg = entry.id.equals("encounter:sotetseg");
            if (sotetseg)
            {
                Set<Long> others = new HashSet<>();
                addPack(others, "tob (all)"); addPack(others, "nylocas vasilias");
                addPack(others, "xarpus"); addPack(others, "verzik vitur");
                others.removeAll(sourceAreas); otherTheatreRooms = Collections.unmodifiableSet(others);
            }
            gauntlet = entry.id.equals("encounter:the-gauntlet") || entry.id.equals("encounter:the-corrupted-gauntlet")
                || entry.id.equals("encounter:crystalline-hunllef") || entry.id.equals("encounter:corrupted-hunllef");
            if (gauntlet) membershipVarbit = VarbitID.PLAYER_IN_GAUNTLET;
            if (entry.id.equals("encounter:chambers-of-xeric") || entry.id.equals("encounter:chambers-of-xeric-challenge-mode"))
                membershipVarbit = VarbitID.RAIDS_CLIENT_INDUNGEON;
            // RuneLite's public Varbits.THEATRE_OF_BLOOD contract: 1 party, 2 inside,
            // 3 dead spectator. Whole-raid guides must survive room and floor changes.
            if (entry.id.equals("encounter:theatre-of-blood") || entry.id.equals("encounter:theatre-of-blood-hard-mode"))
            { membershipVarbit = VarbitID.TOB_CLIENT_PARTYSTATUS; membershipMinimum = 2; }
        }
        if (client.getGameState() != GameState.LOGGED_IN) return false;
        Player player = client.getLocalPlayer();
        WorldView view = client.getTopLevelWorldView();
        if (player == null || view == null) return false;
        WorldPoint point = sourcePoint(view, player.getLocalLocation(), player.getWorldLocation());
        if (point == null) return false;
        if (tombs)
        {
            // Exact approved ToA plugin contract, including its room-transition grace:
            // docs/boss-arena-source-notes.md. No party-status values are inferred.
            net.runelite.api.widgets.Widget status = client.getWidget(481, 40);
            boolean inside = contains(TOA_ROOMS, point.getRegionID()) || status != null && !status.isHidden();
            if (inside) { entered = true; tombsLeaveTicks = 3; }
            else if (entered) --tombsLeaveTicks;
            return entered && tombsLeaveTicks <= 0;
        }
        if (sotetseg && entered)
        {
            // Its maze is another source template inside the same native ToB run.
            // A standing-marker pack is not a complete list of combat-phase maps.
            return client.getVarbitValue(VarbitID.TOB_CLIENT_PARTYSTATUS) < 2 || otherTheatreRooms.contains(area(point));
        }
        if (membershipVarbit >= 0)
        {
            boolean inside = client.getVarbitValue(membershipVarbit) >= membershipMinimum;
            if (inside) entered = true;
            return entered && !inside;
        }
        boolean instance = view.isInstance();
        boolean sourced = sourceAreas.contains(area(point));
        NPC boss = instance && sourced || entered && (instance || !chamber.isEmpty()) ? null : nearbyBoss(entry, player, view);
        boolean observeRoom = boss != null && (!point.equals(discoveredAt) || ++discoveryTicks >= 5);
        ChamberReading room = ChamberReading.UNKNOWN;
        if (!instance && chamber.isEmpty() && observeRoom)
        {
            discoveredAt = point; discoveryTicks = 0;
            room = inspectChamber(view, player.getLocalLocation(), boss.getWorldLocation());
        }
        if (!entered)
        {
            // Static instanced arenas also work before the first wave has spawned.
            // A visible boss behind a closed door is not evidence that we entered.
            boolean observed = boss != null && (instance || room.reachable);
            if (!(sourced && instance) && !observed) return false;
            if (!sourced && observed) sourceAreas = Collections.emptySet();
            entered = true; enteredInstance = instance;
            observedRegion = point.getRegionID(); observedPlane = point.getPlane();
        }
        if (enteredInstance != instance) return true;
        if (!sourceAreas.isEmpty() ? !sourced : point.getRegionID() != observedRegion || point.getPlane() != observedPlane)
            return true;
        if (!chamber.isEmpty() && !chamber.contains(tile(point))) return true;
        if (!room.tiles.isEmpty()) chamber = room.tiles;
        return false;
    }

    private Set<Long> areas(GuideCatalog.Entry entry)
    {
        Set<Long> result = new HashSet<>();
        addPack(result, entry.markerPackId);
        if (entry.sections != null) for (GuideCatalog.Section section : entry.sections) addPack(result, section.markerPackId);
        String id = entry.id;
        // Coordinates are the same source regions as the shipped, attributed marker packs.
        if (id.startsWith("waves:fight-caves:") || id.equals("boss:fight-caves")
            || id.equals("encounter:fight-caves") || id.equals("encounter:tztok-jad")) result.add(area(9551, 0));
        if (id.equals("waves:inferno") || id.equals("encounter:inferno") || id.equals("encounter:tzkal-zuk")) result.add(area(9043, 0));
        if (id.equals("waves:colosseum") || id.equals("encounter:fortis-colosseum") || id.equals("encounter:sol-heredit")) result.add(area(7216, 0));
        if (id.equals("encounter:theatre-of-blood-hard-mode")) addPack(result, "tob (all)");
        // RaidRoom from the exact approved ToA source, rather than puzzle marker locations.
        int[] rooms = toaBossRooms(id);
        if (rooms.length > 0)
        {
            result.clear();
            for (int region : rooms) for (int plane = 0; plane < 4; plane++) result.add(area(region, plane));
        }
        return Collections.unmodifiableSet(result);
    }

    private static int[] toaBossRooms(String id)
    {
        switch (id)
        {
            case "encounter:akkha": return new int[]{14676};
            case "encounter:ba-ba": return new int[]{15188};
            case "encounter:kephri": return new int[]{14164};
            case "encounter:zebak": return new int[]{15700};
            case "encounter:elidinis-warden":
            case "encounter:tumeken-s-warden": return new int[]{15184, 15696};
            default: return new int[0];
        }
    }

    private static boolean contains(int[] values, int value)
    { for (int candidate : values) if (candidate == value) return true; return false; }

    private void addPack(Set<Long> areas, String id)
    {
        if (id == null) return;
        GuideCatalog.MarkerPack pack = catalog.pack(id);
        if (pack != null && pack.tiles != null)
            for (GuideCatalog.Marker marker : pack.tiles) areas.add(area(marker.regionId, marker.z));
    }

    private static NPC nearbyBoss(GuideCatalog.Entry entry, Player player, WorldView view)
    {
        NPC candidate = player.getInteracting() instanceof NPC ? (NPC) player.getInteracting() : null;
        if (matches(entry, candidate) && near(player.getWorldLocation(), candidate.getWorldLocation())) return candidate;
        if (view.npcs() == null) return null;
        NPC closest = null; int distance = 17, examined = 0;
        for (NPC npc : view.npcs())
        {
            if (++examined > 4096) break;
            if (!matches(entry, npc) || !near(player.getWorldLocation(), npc.getWorldLocation())) continue;
            int next = player.getWorldLocation().distanceTo(npc.getWorldLocation());
            if (next < distance) { closest = npc; distance = next; }
        }
        return closest;
    }

    static boolean matches(GuideCatalog.Entry entry, NPC npc)
    {
        if (entry == null || npc == null || npc.getName() == null) return false;
        String name = normalize(npc.getName()), title = normalize(entry.title);
        if (name.equals(title)) return true;
        // Native names for collective guides; no attack/projectile/prayer state is read.
        if (entry.id.equals("encounter:grotesque-guardians")) return name.equals("dusk") || name.equals("dawn");
        if (entry.id.equals("encounter:the-royal-titans")) return name.equals("branda the fire queen") || name.equals("eldric the ice king");
        if (entry.id.equals("boss:mole")) return name.equals("giant mole");
        if (entry.id.equals("boss:kbd")) return name.equals("king black dragon");
        if (entry.id.equals("boss:muspah")) return name.equals("phantom muspah");
        return false;
    }

    private static String normalize(String name)
    { return name.toLowerCase(Locale.ROOT).replace('\u2019', '\'').replaceFirst("^the ", "").trim(); }

    private static boolean near(WorldPoint player, WorldPoint boss)
    { return player != null && boss != null && player.getPlane() == boss.getPlane() && player.distanceTo(boss) <= 16; }

    static WorldPoint sourcePoint(WorldView view, LocalPoint local, WorldPoint world)
    {
        if (!view.isInstance()) return world;
        if (local == null || view.getScene() == null) return null;
        int[][][] chunks = view.getInstanceTemplateChunks();
        int plane = view.getPlane(), x = local.getSceneX() >> 3, y = local.getSceneY() >> 3;
        if (chunks == null || plane < 0 || plane >= chunks.length || chunks[plane] == null || x < 0 || x >= chunks[plane].length
            || chunks[plane][x] == null || y < 0 || y >= chunks[plane][x].length || chunks[plane][x][y] == -1) return null;
        return WorldPoint.fromLocalInstance(view.getScene(), local, plane);
    }

    private static long area(WorldPoint point) { return area(point.getRegionID(), point.getPlane()); }
    private static long area(int region, int plane) { return ((long) plane << 32) | (region & 0xffffffffL); }
    private static long tile(WorldPoint point) { return ((long) point.getPlane() << 32) | ((long) point.getX() << 16) | point.getY(); }

    /** Returns only finite chambers, using public native movement flags in both directions. */
    static Set<Long> closedChamber(WorldView view, LocalPoint player, WorldPoint boss)
    { return inspectChamber(view, player, boss).tiles; }

    private static final class ChamberReading
    {
        static final ChamberReading UNKNOWN = new ChamberReading(false, Collections.emptySet());
        final boolean reachable; final Set<Long> tiles;
        ChamberReading(boolean reachable, Set<Long> tiles) { this.reachable = reachable; this.tiles = tiles; }
    }

    private static ChamberReading inspectChamber(WorldView view, LocalPoint player, WorldPoint boss)
    {
        CollisionData[] maps = view.getCollisionMaps(); int plane = view.getPlane();
        if (player == null || boss == null || maps == null || plane < 0 || plane >= maps.length || maps[plane] == null) return ChamberReading.UNKNOWN;
        int[][] flags = maps[plane].getFlags();
        int sx = player.getSceneX(), sy = player.getSceneY();
        if (!valid(flags, sx, sy)) return ChamberReading.UNKNOWN;
        Set<Long> result = new HashSet<>(); Set<Long> visited = new HashSet<>(); ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{sx, sy}); visited.add(((long) sx << 32) | sy);
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        int[] out = {CollisionDataFlag.BLOCK_MOVEMENT_EAST, CollisionDataFlag.BLOCK_MOVEMENT_WEST, CollisionDataFlag.BLOCK_MOVEMENT_NORTH, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH};
        int[] in = {CollisionDataFlag.BLOCK_MOVEMENT_WEST, CollisionDataFlag.BLOCK_MOVEMENT_EAST, CollisionDataFlag.BLOCK_MOVEMENT_SOUTH, CollisionDataFlag.BLOCK_MOVEMENT_NORTH};
        boolean includesBoss = false;
        while (!queue.isEmpty())
        {
            int[] next = queue.remove(); int x = next[0], y = next[1];
            if (x == 0 || y == 0 || x == flags.length - 1 || y == flags[x].length - 1 || result.size() >= 1600) return new ChamberReading(includesBoss, Collections.emptySet());
            WorldPoint point = new WorldPoint(view.getBaseX() + x, view.getBaseY() + y, plane);
            result.add(tile(point));
            if (point.distanceTo(boss) <= 1) includesBoss = true;
            for (int d = 0; d < 4; d++)
            {
                int nx = x + dx[d], ny = y + dy[d];
                if (!valid(flags, nx, ny) || (flags[x][y] & out[d]) != 0 || (flags[nx][ny] & (in[d] | CollisionDataFlag.BLOCK_MOVEMENT_FULL)) != 0) continue;
                long key = ((long) nx << 32) | ny;
                if (visited.add(key)) queue.add(new int[]{nx, ny});
            }
        }
        return new ChamberReading(includesBoss, includesBoss ? Collections.unmodifiableSet(result) : Collections.emptySet());
    }

    private static boolean valid(int[][] flags, int x, int y)
    { return flags != null && x >= 0 && x < flags.length && flags[x] != null && y >= 0 && y < flags[x].length; }
}
