/* SPDX-License-Identifier: GPL-3.0-only */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** A manually loaded immutable tile pack. No NPC, projectile, prayer or wave state is read. */
@Singleton
final class GuideMarkerOverlay extends Overlay
{
    private static final Color DEFAULT_COLOR = new Color(220, 185, 90);
    private final Client client;
    // Swing publishes the pack, filter and precomputed drawing facts together.
    private volatile Selection selection = Selection.EMPTY;
    private volatile boolean visible = true;
    // Only the rendering thread touches this cache. Camera projection still happens each frame.
    private SceneCache cache;

    @Inject GuideMarkerOverlay(Client client)
    {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    void select(GuideCatalog.MarkerPack pack) { select(pack, null); }
    void select(GuideCatalog.MarkerPack pack, List<Integer> numbers)
    {
        selection = pack == null ? Selection.EMPTY : new Selection(pack, numbers);
    }
    void setVisible(boolean value) { visible = value; }

    GuideCatalog.MarkerPack selectedPack() { return selection.pack; }
    List<Integer> selectedNumbers() { return selection.numbers; }
    boolean isVisible() { return visible; }

    static Color color(String raw)
    {
        try
        {
            if (raw != null && raw.matches("#[0-9a-fA-F]{8}")) return new Color((int) Long.parseLong(raw.substring(1), 16), true);
            if (raw != null && raw.matches("#[0-9a-fA-F]{6}")) return Color.decode(raw);
        }
        catch (NumberFormatException ignored) { }
        return DEFAULT_COLOR;
    }

    @Override public Dimension render(Graphics2D graphics)
    {
        for (SceneMarker marker : sceneMarkers())
        {
            Polygon polygon = Perspective.getCanvasTilePoly(client, marker.local);
            if (polygon != null) OverlayUtil.renderPolygon(graphics, polygon, marker.color);
            Point text = Perspective.getCanvasTextLocation(client, graphics, marker.local, marker.label, 0);
            if (text != null) OverlayUtil.renderTextLocation(graphics, text, marker.label, marker.color);
        }
        return null;
    }

    /** Resolves only when the selection or loaded scene changes, including in-place instance updates. */
    List<SceneMarker> sceneMarkers()
    {
        Selection current = selection;
        if (!visible || current.markers.isEmpty()) return Collections.emptyList();
        if (client.getGameState() != GameState.LOGGED_IN)
        {
            cache = null;
            return Collections.emptyList();
        }
        WorldView view = client.getTopLevelWorldView();
        if (view == null) { cache = null; return Collections.emptyList(); }
        int baseX = view.getBaseX(), baseY = view.getBaseY(), plane = view.getPlane();
        int sizeX = view.getSizeX(), sizeY = view.getSizeY();
        boolean instance = view.isInstance();
        Scene scene = view.getScene();
        int[][][] chunks = instance ? view.getInstanceTemplateChunks() : null;
        if (instance && !availableChunks(chunks)) { cache = null; return Collections.emptyList(); }
        if (cache != null && cache.matches(current, view, scene, baseX, baseY, plane, sizeX, sizeY, instance, chunks))
            return cache.markers;

        List<SceneMarker> resolved = new ArrayList<>();
        Set<Integer> sourceChunks = instance ? sourceChunks(chunks, plane) : Collections.emptySet();
        for (PreparedMarker marker : current.markers)
        {
            if (instance)
            {
                // Unrelated source regions/chunks never enter RuneLite's full instance scan.
                if (!sourceChunks.contains(sourceChunk(marker.point))) continue;
                Collection<WorldPoint> points = WorldPoint.toLocalInstance(view, marker.point);
                for (WorldPoint point : points)
                    addInScene(resolved, marker, point, view, baseX, baseY, plane, sizeX, sizeY);
            }
            else addInScene(resolved, marker, marker.point, view, baseX, baseY, plane, sizeX, sizeY);
        }
        cache = new SceneCache(current, view, scene, baseX, baseY, plane, sizeX, sizeY, instance, chunks, resolved);
        return cache.markers;
    }

    private static void addInScene(List<SceneMarker> result, PreparedMarker marker, WorldPoint point,
        WorldView view, int baseX, int baseY, int plane, int sizeX, int sizeY)
    {
        int x = point.getX() - baseX, y = point.getY() - baseY;
        if (point.getPlane() == plane && x >= 0 && y >= 0 && x < sizeX && y < sizeY)
            result.add(new SceneMarker(LocalPoint.fromScene(x, y, view), marker.color, marker.label));
    }

    private static int sourceChunk(WorldPoint point)
    {
        return (point.getPlane() << 21) | ((point.getX() >> 3) << 11) | (point.getY() >> 3);
    }

    private static Set<Integer> sourceChunks(int[][][] chunks, int plane)
    {
        Set<Integer> result = new HashSet<>();
        if (plane < 0 || plane >= chunks.length || chunks[plane] == null) return result;
        for (int[] row : chunks[plane])
        {
            if (row == null) continue;
            for (int chunk : row)
            {
                if (chunk == -1) continue;
                // RuneLite's public instance-template format: source plane, chunk X, chunk Y.
                result.add((((chunk >> 24) & 3) << 21) | (((chunk >> 14) & 1023) << 11) | ((chunk >> 3) & 2047));
            }
        }
        return result;
    }

    private static boolean availableChunks(int[][][] chunks)
    {
        if (chunks == null) return false;
        for (int[][] level : chunks)
        {
            if (level == null) return false;
            for (int[] row : level) if (row == null) return false;
        }
        return true;
    }

    static final class SceneMarker
    {
        final LocalPoint local;
        final Color color;
        final String label;
        SceneMarker(LocalPoint local, Color color, String label)
        {
            this.local = local;
            this.color = color;
            this.label = label;
        }
    }

    private static final class PreparedMarker
    {
        final WorldPoint point;
        final Color color;
        final String label;
        PreparedMarker(GuideCatalog.Marker marker)
        {
            point = marker.point();
            color = color(marker.color);
            label = marker.displayLabel();
        }
    }

    private static final class Selection
    {
        static final Selection EMPTY = new Selection(null, null);
        final GuideCatalog.MarkerPack pack;
        final List<Integer> numbers;
        final List<PreparedMarker> markers;
        Selection(GuideCatalog.MarkerPack pack, List<Integer> numbers)
        {
            this.pack = pack;
            this.numbers = numbers == null ? null : Collections.unmodifiableList(new ArrayList<>(numbers));
            Set<Integer> filter = numbers == null ? null : new HashSet<>(numbers);
            List<PreparedMarker> prepared = new ArrayList<>();
            if (pack != null && pack.tiles != null)
                for (GuideCatalog.Marker marker : pack.tiles)
                    if (marker != null && (filter == null || filter.contains(marker.number)))
                        prepared.add(new PreparedMarker(marker));
            markers = Collections.unmodifiableList(prepared);
        }
    }

    private static final class SceneCache
    {
        final Selection selection;
        final WorldView view;
        final Scene scene;
        final int baseX, baseY, plane, sizeX, sizeY;
        final boolean instance;
        final int[][][] chunks;
        final List<SceneMarker> markers;
        SceneCache(Selection selection, WorldView view, Scene scene, int baseX, int baseY, int plane,
            int sizeX, int sizeY, boolean instance, int[][][] chunks, List<SceneMarker> markers)
        {
            this.selection = selection;
            this.view = view;
            this.scene = scene;
            this.baseX = baseX; this.baseY = baseY; this.plane = plane;
            this.sizeX = sizeX; this.sizeY = sizeY;
            this.instance = instance;
            this.chunks = copyChunks(chunks);
            this.markers = Collections.unmodifiableList(markers);
        }
        boolean matches(Selection selection, WorldView view, Scene scene, int baseX, int baseY, int plane,
            int sizeX, int sizeY, boolean instance, int[][][] chunks)
        {
            return this.selection == selection && this.view == view && this.scene == scene && this.baseX == baseX
                && this.baseY == baseY && this.plane == plane && this.sizeX == sizeX && this.sizeY == sizeY
                && this.instance == instance && Arrays.deepEquals(this.chunks, chunks);
        }
        private static int[][][] copyChunks(int[][][] chunks)
        {
            if (chunks == null) return null;
            int[][][] copy = new int[chunks.length][][];
            for (int z = 0; z < chunks.length; z++)
            {
                if (chunks[z] == null) continue;
                copy[z] = new int[chunks[z].length][];
                for (int x = 0; x < chunks[z].length; x++)
                    copy[z][x] = chunks[z][x] == null ? null : chunks[z][x].clone();
            }
            return copy;
        }
    }
}
