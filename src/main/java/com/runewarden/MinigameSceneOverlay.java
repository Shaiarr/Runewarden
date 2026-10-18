/* SPDX-License-Identifier: BSD-2-Clause */
package com.runewarden;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Native resource/station objects plus fixed guide reference tiles. No game interaction. */
final class MinigameSceneOverlay extends Overlay
{
    private static final int MAX_OBJECTS = 512;
    private static final Color GOLD = new Color(224, 190, 113, 180);
    private final Client client;
    private final MinigameCoach coach;
    private final MinigameActivityCatalog catalog;
    private final Map<GameObject, Target> observed = new IdentityHashMap<>();
    private final GuideMarkerOverlay references;
    private volatile Target[] targets = new Target[0];
    private volatile NPC[] npcTargets=new NPC[0];
    private List<WorldPoint> previousTiles = Collections.emptyList();
    private Set<Integer> previousIds = Collections.emptySet();
    private Scene seededScene;
    private WorldView seededView;
    private int seededX, seededY, seededPlane;
    private static final class Target
    {
        final GameObject object;
        final String name;
        final int id;
        Target(GameObject object, String name) { this.object = object; this.name = name; this.id = object.getId(); }
    }
    MinigameSceneOverlay(Client client, MinigameCoach coach, MinigameActivityCatalog catalog)
    {
        this.client = client; this.coach = coach; this.catalog = catalog; references = new GuideMarkerOverlay(client);
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_SCENE);
    }
    synchronized void clear()
    {
        observed.clear(); targets = new Target[0];npcTargets=new NPC[0]; previousTiles = Collections.emptyList(); previousIds = Collections.emptySet();
        references.select(null); seededScene = null; seededView = null;
    }
    synchronized void hide()
    {
        if (targets.length > 0) targets = new Target[0];
        npcTargets=new NPC[0];
        if (!previousIds.isEmpty()) previousIds = Collections.emptySet();
        if (!previousTiles.isEmpty()) { previousTiles = Collections.emptyList(); references.select(null); }
    }
    /** Exactly once per native scene/base/plane, including enabling the plugin while inside. */
    synchronized void ensureSeeded()
    {
        WorldView view = client.getTopLevelWorldView();
        if (view == null || view.getScene() == null) return;
        Scene scene = view.getScene(); int plane = view.getPlane();
        if (view == seededView && scene == seededScene && view.getBaseX() == seededX && view.getBaseY() == seededY && plane == seededPlane) return;
        observed.clear(); targets = new Target[0]; catalog.clearScene();
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null || plane < 0 || plane >= tiles.length || tiles[plane] == null) return;
        seededScene = scene; seededView = view; seededX = view.getBaseX(); seededY = view.getBaseY(); seededPlane = plane;
        Set<Integer> ids = catalog.allHighlightObjectIds();
        for (Tile[] row : tiles[plane])
        {
            if (row == null) continue;
            for (Tile tile : row)
            {
                if (tile == null || tile.getGameObjects() == null) continue;
                for (GameObject object : tile.getGameObjects())
                    if (object != null && ids.contains(object.getId()) && !observed.containsKey(object))
                    { coach.fieldEvents().observe(object,false);catalog.onGameObjectSpawned(object); observe(object); }
            }
        }
    }
    synchronized void observe(GameObject object)
    {
        if (object == null || !catalog.allHighlightObjectIds().contains(object.getId()) || observed.containsKey(object)) return;
        if (observed.size() >= MAX_OBJECTS) return;
        observed.put(object, new Target(object, objectName(object.getId())));
        publishTargets();
    }
    private String objectName(int id)
    {
        ObjectComposition definition = client.getObjectDefinition(id);
        String base = definition == null ? "" : usableName(definition.getName());
        if (definition != null && definition.getImpostorIds() != null)
        {
            ObjectComposition resolved = definition.getImpostor();
            String name = resolved == null ? "" : usableName(resolved.getName());
            if (!name.isEmpty()) return name;
        }
        if (!base.isEmpty()) return base;
        // Foundry's scene IDs are morph wrappers and may deliberately be named "null".
        switch (id)
        {
            case 44619: return "Trip hammer";
            case 44620: return "Grindstone";
            case 44621: return "Polishing wheel";
            case 44631: return "Lava pool";
            case 44632: return "Waterfall";
            case 44776: return "Crucible";
            case 44777: return "Mould jig";
            case 44778: return "Preform storage";
            default: return "Resource";
        }
    }
    private static String usableName(String raw)
    {
        if (raw == null) return "";
        String name = net.runelite.client.util.Text.removeTags(raw).replace('\u00a0', ' ').trim();
        if (name.isEmpty() || name.equalsIgnoreCase("null") || name.equalsIgnoreCase("undefined")) return "";
        return name.substring(0, Math.min(80, name.length()));
    }
    synchronized void remove(GameObject object) { if (observed.remove(object) != null) publishTargets(); }
    synchronized void refresh(MinigameActivityCatalog.Snapshot snapshot)
    {
        if (!MinigameReleasePolicy.allowsAutomaticGuidance(snapshot.activity)
            || coach.reading().activity != snapshot.activity)
        { hide(); return; }
        List<NPC> npcs=new ArrayList<>();WorldView view=client.getTopLevelWorldView();
        if(coach.preferences().npcHighlights&&view!=null&&view.npcs()!=null&&!snapshot.highlightNpcIds.isEmpty())
        {int scanned=0;for(NPC npc:view.npcs()){if(++scanned>2048)break;if(npc!=null&&snapshot.highlightNpcIds.contains(npc.getId())){npcs.add(npc);if(npcs.size()>=64)break;}}}
        npcs.sort(Comparator.comparingInt(NPC::getIndex));npcTargets=npcs.toArray(new NPC[0]);
        if (!previousIds.equals(snapshot.highlightObjectIds))
        { previousIds = snapshot.highlightObjectIds; publishTargets(); }
        if (!previousTiles.equals(snapshot.referenceTiles))
        {
            previousTiles = new ArrayList<>(snapshot.referenceTiles);
            GuideCatalog.MarkerPack pack = new GuideCatalog.MarkerPack(); pack.id = "minigame-reference"; pack.title = snapshot.title;
            pack.tiles = new ArrayList<>(); int number = 1;
            for (WorldPoint point : previousTiles)
            {
                GuideCatalog.Marker marker = new GuideCatalog.Marker();
                marker.regionId = point.getRegionID(); marker.regionX = point.getRegionX(); marker.regionY = point.getRegionY();
                marker.z = point.getPlane(); marker.number = number++; marker.color = "#FFE0BE71";
                pack.tiles.add(marker);
            }
            references.select(pack);
        }
    }
    private void publishTargets()
    {
        List<Target> next = new ArrayList<>();
        for (Target target : observed.values()) if (previousIds.contains(target.id)) next.add(target);
        next.sort(Comparator.comparingInt((Target target) -> target.id)
            .thenComparingInt(target->target.object.getWorldLocation()==null?0:target.object.getWorldLocation().getX())
            .thenComparingInt(target->target.object.getWorldLocation()==null?0:target.object.getWorldLocation().getY()));
        if(next.size()>64)next=new ArrayList<>(next.subList(0,64));targets = next.toArray(new Target[0]);
    }
    int observedCount() { synchronized (this) { return observed.size(); } }
    int targetCount() { return targets.length; }
    int npcTargetCount(){return npcTargets.length;}
    @Override public Dimension render(Graphics2D graphics)
    {
        MinigameCoach.Preferences settings = coach.preferences();
        if (!coach.started() || coach.guidanceDismissed() || !MinigameReleasePolicy.allowsAutomaticGuidance(coach.reading().activity) || (!settings.highlights && !settings.labels)
            || client.getGameState() != GameState.LOGGED_IN) return null;
        WorldView view = client.getTopLevelWorldView();
        if (view == null) return null;
        int number=1;
        for (Target target : targets)
        {
            if (target.object.getWorldView() != view || target.object.getPlane() != view.getPlane()) continue;
            if (settings.highlights)
            {
                Polygon polygon = target.object.getCanvasTilePoly();
                if (polygon != null) OverlayUtil.renderPolygon(graphics, polygon, GOLD);
            }
            if (settings.labels)
            {
                String label=(settings.numberedTargets?number+" · ":"")+target.name;
                Point point = target.object.getCanvasTextLocation(graphics, label, 20);
                if (point != null) OverlayUtil.renderTextLocation(graphics, point, label, HudStyle.WHITE);
            }
            number++;
        }
        if(settings.npcHighlights)for(NPC npc:npcTargets)
        {
            if(npc.getWorldView()!=view)continue;
            String name=usableName(npc.getName());if(name.isEmpty())name="NPC";
            if(settings.highlights&&npc.getCanvasTilePoly()!=null)OverlayUtil.renderPolygon(graphics,npc.getCanvasTilePoly(),GOLD);
            if(settings.labels)
            {String label=(settings.numberedTargets?number+" · ":"")+name;Point point=npc.getCanvasTextLocation(graphics,label,20);if(point!=null)OverlayUtil.renderTextLocation(graphics,point,label,HudStyle.WHITE);}
            number++;
        }
        coach.fieldEvents().render(graphics,settings.highlights,settings.labels,coach.fieldItemManager());
        for (GuideMarkerOverlay.SceneMarker marker : references.sceneMarkers())
        {
            if (settings.highlights)
            {
                Polygon polygon = net.runelite.api.Perspective.getCanvasTilePoly(client, marker.local);
                if (polygon != null) OverlayUtil.renderPolygon(graphics, polygon, GOLD);
            }
            if (settings.labels)
            {
                Point point = net.runelite.api.Perspective.getCanvasTextLocation(client, graphics, marker.local, marker.label, 0);
                if (point != null) OverlayUtil.renderTextLocation(graphics, point, marker.label, HudStyle.GOLD);
            }
        }
        return null;
    }
}
